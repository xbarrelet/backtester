package ch.xavier.backtester.backtesting;

import ch.xavier.backtester.backtesting.model.*;
import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class WalkForwardService {

    private static final int TOP_RESULTS_COUNT = 10;

    private final BacktesterService backtesterService;


    public WalkForwardService(BacktesterService backtesterService) {
        this.backtesterService = backtesterService;
    }

    /**
     * Performs walk-forward optimization and testing
     *
     * @param quotes          Full dataset of quotes
     * @param strategyFactory Function to create strategy with different parameters
     * @param params          Trading parameters with walk-forward settings
     * @param parameterGrid   Map of parameter name to list of values to test
     * @return Walk-forward optimization results
     */
    public Mono<WalkForwardResult> runWalkForward(
            List<Quote> quotes,
            Function<Map<String, Object>, TradingStrategy> strategyFactory,
            TradingParameters params,
            Map<String, List<Object>> parameterGrid,
            MarketPhaseClassifier classifier,
            PerformanceMetricType metricType) {

        // Convert days to number of candles based on quote frequency
        int msPerCandle = estimateMillisecondsPerCandles(quotes);
        int trainCandles = convertDaysToCandles(params.getWalkForwardWindow(), msPerCandle);
        int testCandles = convertDaysToCandles(params.getTestWindow(), msPerCandle);
        int stepCandles = convertDaysToCandles(params.getWalkForwardStep(), msPerCandle);

        List<BacktestResult> windowResults = new ArrayList<>();
        Map<MarketPhaseClassifier.MarketPhase, List<ParameterPerformance>> topParametersByPhase = new ConcurrentHashMap<>();
        List<BacktestResult> outOfSampleResults = new ArrayList<>();

        // Generate all possible parameter combinations
        List<Map<String, Object>> allCombinations = generateParameterCombinations(parameterGrid);

        return Flux.range(0, (quotes.size() - trainCandles - testCandles) / stepCandles + 1)
                .flatMap(window -> {
                    int trainStart = window * stepCandles;
                    int trainEnd = trainStart + trainCandles;
                    int testStart = trainEnd;
                    int testEnd = Math.min(testStart + testCandles, quotes.size());

                    if (testStart >= quotes.size() || testEnd <= testStart) {
                        return Mono.empty();
                    }

                    List<Quote> trainData = quotes.subList(trainStart, trainEnd);
                    List<Quote> testData = quotes.subList(testStart, testEnd);

                    String trainStartDate = formatDate(quotes.get(trainStart).getTimestamp());
                    String trainEndDate = formatDate(quotes.get(trainEnd-1).getTimestamp());
                    String testStartDate = formatDate(quotes.get(testStart).getTimestamp());
                    String testEndDate = formatDate(quotes.get(testEnd-1).getTimestamp());

                    log.info("Window {}: Training on {} to {} (quotes {}-{}), testing on {} to {} (quotes {}-{})",
                            window, trainStartDate, trainEndDate, trainStart, trainEnd,
                            testStartDate, testEndDate, testStart, testEnd);

                    // Find top parameter sets on training data
                    return findTopParameters(trainData, strategyFactory, params, allCombinations)
                            .flatMap(topParams -> {
                                // Create strategy with the best parameters
                                Map<String, Object> bestParams = topParams.getFirst().getParameters();
                                TradingStrategy optimizedStrategy = strategyFactory.apply(topParams.getFirst().getParameters());

                                // Log the best parameters
                                log.info("Window {}: Best parameters: {}, Performance metric: {}",
                                        window, bestParams, topParams.getFirst().getPerformanceMetric());

                                // Get market phase for this window
                                MarketPhaseClassifier.MarketPhase windowPhase = determineMarketPhase(trainData,
                                        classifier);
                                log.info("Window {}: Predominant market phase: {}", window, windowPhase);

                                // Store top parameters for this phase
                                synchronized (topParametersByPhase) {
                                    topParametersByPhase.compute(windowPhase, (phase, existingList) -> {
                                        if (existingList == null) {
                                            return new ArrayList<>(topParams);
                                        } else {
                                            existingList.addAll(topParams);
                                            // Sort by performance metric and keep top 10
                                            existingList.sort(Comparator.comparing(ParameterPerformance::getPerformanceMetric).reversed());
                                            return existingList.size() > TOP_RESULTS_COUNT ?
                                                    existingList.subList(0, TOP_RESULTS_COUNT) : existingList;
                                        }
                                    });
                                }

                                // Run backtest on test data with optimal parameters
                                return backtesterService.backtest(testData, optimizedStrategy,
                                                MarketPhaseClassifier.MarketPhase.UNKNOWN, params)
                                        .map(result -> {
                                            // Store results
                                            log.info("Window {}: Test results - Win rate: {}%, Return: {}%, Sharpe: {}",
                                                    window,
                                                    result.getWinRate() * 100,
                                                    result.getTotalReturn() * 100,
                                                    result.getSharpeRatio());

                                            synchronized (windowResults) {
                                                windowResults.add(result);
                                            }

                                            synchronized (outOfSampleResults) {
                                                outOfSampleResults.add(result);
                                            }

                                            return result;
                                        });
                            });
                })
                .then(Mono.fromCallable(() -> {
                    // Aggregate all out-of-sample results
                    List<Trade> allTrades = outOfSampleResults.stream()
                            .flatMap(r -> {
                                if (r.getTrades() == null) {
                                    return Stream.empty();
                                } else {
                                    return r.getTrades().stream();
                                }
                            })
                            .collect(Collectors.toList());

                    BacktestResult aggregatedResult = aggregateResults(allTrades, params);

                    return new WalkForwardResult(windowResults, aggregatedResult, topParametersByPhase);
                }));
    }

    private String formatDate(Date date) {
        return new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm").format(date);
    }

    private Mono<List<ParameterPerformance>> findTopParameters(
            List<Quote> trainData,
            Function<Map<String, Object>, TradingStrategy> strategyFactory,
            TradingParameters params,
            List<Map<String, Object>> allCombinations) {

        // Run backtest for each parameter combination
        return Flux.fromIterable(allCombinations)
                .flatMap(combination -> {
                    TradingStrategy strategy = strategyFactory.apply(combination);
                    return backtesterService.backtest(trainData, strategy,
                                    MarketPhaseClassifier.MarketPhase.UNKNOWN, params)
                            .map(result -> new ParameterPerformance(
                                    combination,
                                    result,
                                    result.getSharpeRatio()  // Using Sharpe as the performance metric
                            ));
                })
                .collectList()
                .map(results -> {
                    // Sort by performance metric (sharpe ratio) descending
                    results.sort(Comparator.comparing(ParameterPerformance::getPerformanceMetric).reversed());

                    // Return top 10 results (or all if fewer)
                    return results.size() > TOP_RESULTS_COUNT ?
                            results.subList(0, TOP_RESULTS_COUNT) : results;
                });
    }

    private MarketPhaseClassifier.MarketPhase determineMarketPhase(List<Quote> quotes, MarketPhaseClassifier classifier) {
        // Count occurrences of each phase
        Map<MarketPhaseClassifier.MarketPhase, Integer> phaseCounts = new HashMap<>();

        for (int i = 0; i < quotes.size(); i++) {
            MarketPhaseClassifier.MarketPhase phase = classifier.classify(quotes, i);
            phaseCounts.merge(phase, 1, Integer::sum);
        }

        // Return the most frequent phase
        return phaseCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(MarketPhaseClassifier.MarketPhase.UNKNOWN);
    }

    private BacktestResult aggregateResults(List<Trade> allTrades, TradingParameters params) {
        if (allTrades.isEmpty()) {
            return BacktestResult.builder()
                    .finalFunds(params.getInitialCapital())
                    .totalReturn(0)
                    .winRate(0)
                    .profitFactor(0)
                    .totalTrades(0)
                    .build();
        }

        int winningCount = 0;
        int losingCount = 0;
        double totalProfit = 0;
        double totalLoss = 0;
        double maxDrawdown = 0;
        double peak = params.getInitialCapital();
        double equity = params.getInitialCapital();
        double sumWinAmount = 0;
        double sumLossAmount = 0;
        long totalDays = 0;

        // Sort trades by exit time to process them chronologically
        allTrades.sort(Comparator.comparing(Trade::getExitTime));

        for (Trade trade : allTrades) {
            equity += trade.getProfitAmount();

            if (equity > peak) {
                peak = equity;
            }
            double drawdown = (peak - equity) / peak;
            maxDrawdown = Math.max(maxDrawdown, drawdown);

            if (trade.getProfit() > 0) {
                winningCount++;
                totalProfit += trade.getProfitAmount();
                sumWinAmount += trade.getProfitAmount();
            } else {
                losingCount++;
                totalLoss -= trade.getProfitAmount();
                sumLossAmount -= trade.getProfitAmount();
            }

            long tradeDays = (trade.getExitTime().getTime() - trade.getEntryTime().getTime())
                    / (1000 * 60 * 60 * 24);
            totalDays += tradeDays;
        }

        double winRate = (double) winningCount / allTrades.size();
        double profitFactor = totalLoss > 0 ? totalProfit / totalLoss : totalProfit > 0 ? Double.MAX_VALUE : 0;
        double avgWinAmount = winningCount > 0 ? sumWinAmount / winningCount : 0;
        double avgLossAmount = losingCount > 0 ? sumLossAmount / losingCount : 0;
        double avgTradeAmount = (sumWinAmount - sumLossAmount) / allTrades.size();
        double avgTradeLength = (double) totalDays / allTrades.size();

        double totalDaysDouble = (double) totalDays;
        double annualized = totalDaysDouble > 0 ?
                Math.pow((equity / params.getInitialCapital()), (252.0 / totalDaysDouble)) - 1.0 : 0;

        // Calculate daily returns for Sharpe/Sortino
        Map<String, Double> dailyReturns = new HashMap<>();
        double currentEquity = params.getInitialCapital();

        for (Trade trade : allTrades) {
            String day = new java.text.SimpleDateFormat("yyyy-MM-dd").format(trade.getExitTime());
            currentEquity += trade.getProfitAmount();
            dailyReturns.merge(day, trade.getProfitAmount() / currentEquity, Double::sum);
        }

        double[] sharpeSortino = calculateSharpeAndSortino(dailyReturns);

        return BacktestResult.builder()
                .finalFunds(equity)
                .totalReturn((equity - params.getInitialCapital()) / params.getInitialCapital())
                .annualizedReturn(annualized)
                .sharpeRatio(sharpeSortino[0])
                .sortinoRatio(sharpeSortino[1])
                .profitFactor(profitFactor)
                .winRate(winRate)
                .maxDrawdown(maxDrawdown)
                .totalTrades(allTrades.size())
                .winningTrades(winningCount)
                .losingTrades(losingCount)
                .avgWinAmount(avgWinAmount)
                .avgLossAmount(avgLossAmount)
                .avgTradeAmount(avgTradeAmount)
                .avgTradeLength(avgTradeLength)
                .trades(allTrades)
                .strategyName("Walk-Forward Optimized")
                .build();
    }

    // Reusing the same method from BacktesterService
    private double[] calculateSharpeAndSortino(Map<String, Double> dailyReturns) {
        if (dailyReturns.isEmpty()) {
            return new double[]{0, 0};
        }

        double sum = dailyReturns.values().stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / dailyReturns.size();

        double squaredSum = dailyReturns.values().stream()
                .mapToDouble(r -> Math.pow(r - mean, 2)).sum();
        double variance = squaredSum / dailyReturns.size();
        double stdDev = Math.sqrt(variance);

        double downsideSquaredSum = dailyReturns.values().stream()
                .filter(r -> r < 0)
                .mapToDouble(r -> Math.pow(r, 2))
                .sum();
        double downsideDeviation = Math.sqrt(downsideSquaredSum / dailyReturns.size());

        double dailyRiskFree = 0.02 / 365;

        double sharpeRatio = stdDev > 0 ?
                (mean - dailyRiskFree) / stdDev * Math.sqrt(252) : 0;
        double sortinoRatio = downsideDeviation > 0 ?
                (mean - dailyRiskFree) / downsideDeviation * Math.sqrt(252) : 0;

        return new double[]{sharpeRatio, sortinoRatio};
    }


    private List<Map<String, Object>> generateParameterCombinations(Map<String, List<Object>> parameterGrid) {
        List<Map<String, Object>> combinations = new ArrayList<>();
        generateCombinations(new HashMap<>(), new ArrayList<>(parameterGrid.entrySet()), 0, combinations);
        return combinations;
    }

    private void generateCombinations(
            Map<String, Object> current,
            List<Map.Entry<String, List<Object>>> entries,
            int index,
            List<Map<String, Object>> result) {

        if (index == entries.size()) {
            result.add(new HashMap<>(current));
            return;
        }

        Map.Entry<String, List<Object>> entry = entries.get(index);
        String paramName = entry.getKey();
        List<Object> values = entry.getValue();

        for (Object value : values) {
            current.put(paramName, value);
            generateCombinations(current, entries, index + 1, result);
        }
    }

    private int estimateMillisecondsPerCandles(List<Quote> quotes) {
        if (quotes.size() < 2) return (int) TimeUnit.DAYS.toMillis(1); // Default to 1 day

        long avgDiff = 0;
        int count = 0;
        for (int i = 1; i < Math.min(100, quotes.size()); i++) {
            avgDiff += quotes.get(i).getTimestamp().getTime() - quotes.get(i - 1).getTimestamp().getTime();
            count++;
        }
        return (int) (avgDiff / count);
    }

    private int convertDaysToCandles(int days, int msPerCandle) {
        return (int) (TimeUnit.DAYS.toMillis(days) / msPerCandle);
    }
}
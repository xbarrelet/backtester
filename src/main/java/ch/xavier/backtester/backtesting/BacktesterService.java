package ch.xavier.backtester.backtesting;

import ch.xavier.backtester.backtesting.model.*;
import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.strategy.StrategiesFactory;
import ch.xavier.backtester.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BacktesterService {

    /**
     * Performs grid search backtesting across all parameter combinations
     *
     * @param quotes          List of quotes for backtesting
     * @param strategyName    Name of the strategy to backtest on
     * @param params          Trading parameters
     * @param parametersGrid  Map of parameter names to possible values
     * @param topResultsCount Number of top results to return
     * @return List of top parameter combinations with their results
     */
    public Mono<List<ParameterPerformance>> backtestParameterGrid(
            List<Quote> quotes,
            String strategyName,
            TradingParameters params,
            Map<String, List<Object>> parametersGrid,
            PerformanceMetricType metricType,
            int topResultsCount) {

        AtomicInteger counter = new AtomicInteger(0);

        return Flux.fromIterable(StrategiesFactory.generateAllStrategyParameters(parametersGrid))
                .flatMap(combination -> {
                    log.info("Counter:{}", counter.incrementAndGet());

                    TradingStrategy strategy = StrategiesFactory.getStrategy(strategyName, params, combination);
                    return backtest(quotes, strategy, MarketPhaseClassifier.MarketPhase.UNKNOWN, params)
                            .map(result -> new ParameterPerformance(
                                    combination,
                                    result,
                                    calculateMetricValue(result, metricType)
                            ));
                })
                .collectList()
                .map(results -> {
                    results.sort(Comparator.comparing(ParameterPerformance::getPerformanceMetric).reversed());
                    return results.size() > topResultsCount ?
                            results.subList(0, topResultsCount) : results;
                });
    }

    private double calculateMetricValue(BacktestResult result, PerformanceMetricType metricType) {
        return switch (metricType) {
            case SHARPE_RATIO -> result.getSharpeRatio();
            case SORTINO_RATIO -> result.getSortinoRatio();
            case WIN_RATE -> result.getWinRate();
            case TOTAL_RETURN -> result.getTotalReturn();
            case PROFIT_FACTOR -> result.getProfitFactor();
            case MAXIMUM_DRAWDOWN -> -result.getMaxDrawdown(); // Negative because lower is better
        };
    }

    /**
     * Performs backtest for a specific strategy in a specific market phase
     *
     * @param quotes   List of quotes for backtesting
     * @param strategy Trading strategy to test
     * @param phase    Market phase classification
     * @param params   Trading parameters
     * @return Backtest result
     */
    public Mono<BacktestResult> backtest(List<Quote> quotes, TradingStrategy strategy,
                                         MarketPhaseClassifier.MarketPhase phase, TradingParameters params) {
        return Mono.fromCallable(() -> executeBacktest(quotes, strategy, phase, params))
                .subscribeOn(Schedulers.parallel());
    }

    /**
     * Backtest a single strategy across different market phases
     */
    public Mono<BacktestResult> backtestStrategyAcrossPhases(
            String strategyName,
            TradingStrategy strategy,
            Map<MarketPhaseClassifier.MarketPhase, List<Quote>> phaseQuotes,
            TradingParameters params) {

        Map<MarketPhaseClassifier.MarketPhase, BacktestResult> phaseResults = new ConcurrentHashMap<>();

        return Flux.fromIterable(phaseQuotes.entrySet())
                .filter(entry -> entry.getKey() != MarketPhaseClassifier.MarketPhase.UNKNOWN &&
                        !entry.getValue().isEmpty())
                .flatMap(entry -> {
                    MarketPhaseClassifier.MarketPhase phase = entry.getKey();
                    List<Quote> quotes = entry.getValue();

                    log.info("Backtesting {} in {} market phase with {} quotes",
                            strategyName, phase, quotes.size());

                    return backtest(quotes, strategy, phase, params)
                            .doOnNext(result -> phaseResults.put(phase, result));
                })
                .then(Mono.fromCallable(() -> {
                    // Combine all trades from different phases
                    List<Trade> allTrades = phaseResults.values().stream()
                            .flatMap(result -> result.getTrades().stream())
                            .collect(Collectors.toList());

                    // Calculate overall metrics
                    BacktestResult combinedResult = calculateResults(allTrades,
                            params.getInitialCapital() + allTrades.stream().mapToDouble(Trade::getProfitAmount).sum(),
                            params.getInitialCapital());

                    combinedResult.setPhaseResults(phaseResults);
                    combinedResult.setStrategyName(strategyName);
                    return combinedResult;
                }));
    }

    /**
     * Core backtesting logic - synchronous method called from reactive wrappers
     */
    private BacktestResult executeBacktest(List<Quote> quotes, TradingStrategy strategy,
                                           MarketPhaseClassifier.MarketPhase phase,
                                           TradingParameters params) {
        double equity = params.getInitialCapital();
        double maxEquity = equity;
        double currentDrawdown = 0;
        List<Position> openPositions = new ArrayList<>();
        List<Trade> completedTrades = new ArrayList<>();

        // Track daily returns for Sharpe & Sortino calculation
        Map<String, Double> dailyReturns = new HashMap<>();
        String currentDay = "";
        double dayStartEquity = equity;

        for (int i = 0; i < quotes.size(); i++) {
            Quote quote = quotes.get(i);

            // Track a new day for returns calculation
            String quoteDay = new java.text.SimpleDateFormat("yyyy-MM-dd").format(quote.getTimestamp());

            if (!quoteDay.equals(currentDay)) {
                if (!currentDay.isEmpty()) {
                    dailyReturns.put(currentDay, (equity - dayStartEquity) / dayStartEquity);
                }
                currentDay = quoteDay;
                dayStartEquity = equity;
            }

            // Update positions & check for exits
            updatePositions(strategy, openPositions, quote, quotes, i, completedTrades, params);

            // Check if we can open a new position when we have none open
            if (openPositions.isEmpty() && i > 0) {
                int signal = strategy.generateSignal(quotes, i);

                if (signal != 0) {
                    boolean isLong = signal > 0;

                    double stopLossPrice = strategy.calculateStopLossPrice(isLong, quotes, i);

                    double entryPrice = quote.getClose().doubleValue();
                    double positionSize = strategy.calculatePositionSize(equity, quote, stopLossPrice);

                    double takeProfitPrice = strategy.calculateTakeProfitPrice(isLong, quotes, i);

                    // Create position
                    Position position = Position.builder()
                            .entryPrice(entryPrice)
                            .size(positionSize)
                            .currentStopLossPrice(stopLossPrice)
                            .takeProfitPrice(takeProfitPrice)
                            .isLong(isLong)
                            .entryIndex(i)
                            .marketPhase(phase)
                            .priceHistory(new ArrayList<>())
                            .build();

                    // Account for fees
                    double entryFee = entryPrice * positionSize * params.getTakerFee();
                    equity -= entryFee;

                    openPositions.add(position);
                }
            }

            if (maxEquity > 0) {
                currentDrawdown = (maxEquity - equity) / maxEquity;

                // Stop trading if max drawdown exceeded
                if (currentDrawdown > params.getMaxDrawdown()) {
                    log.warn("Stopping backtest - max drawdown of {}% exceeded ({}%)",
                            params.getMaxDrawdown() * 100, currentDrawdown * 100);
                    break;
                }
            }

            // Update equity peak for drawdown calculation
            if (equity > maxEquity) {
                maxEquity = equity;
            }
        }

        // Close remaining open positions
        if (!openPositions.isEmpty() && !quotes.isEmpty()) {
            Quote lastQuote = quotes.get(quotes.size() - 1);
            closeAllPositions(openPositions, lastQuote, quotes, completedTrades, params);
        }

        // Calculate final day's return
        if (!currentDay.isEmpty()) {
            dailyReturns.put(currentDay, (equity - dayStartEquity) / dayStartEquity);
        }

        // Calculate metrics
        BacktestResult result = calculateResults(completedTrades, equity, params.getInitialCapital());

        // Calculate Sharpe & Sortino using daily returns
        if (!dailyReturns.isEmpty()) {
            double[] sharpeSortino = calculateSharpeAndSortino(dailyReturns);
            result.setSharpeRatio(sharpeSortino[0]);
            result.setSortinoRatio(sharpeSortino[1]);
        }

        // Track trades by phase for reporting
        Map<MarketPhaseClassifier.MarketPhase, BacktestResult> phaseResults = new HashMap<>();
        phaseResults.put(phase, result);
        result.setPhaseResults(phaseResults);
        result.setStrategyName(strategy.getClass().getSimpleName());

        return result;
    }

    private void updatePositions(TradingStrategy strategy, List<Position> positions, Quote quote,
                                 List<Quote> quotes, int index, List<Trade> completedTrades, TradingParameters params) {
        double highPrice = quote.getHigh().doubleValue();
        double lowPrice = quote.getLow().doubleValue();
        double closePrice = quote.getClose().doubleValue();

        Iterator<Position> iterator = positions.iterator();

        while (iterator.hasNext()) {
            Position position = iterator.next();
            position.getPriceHistory().add(closePrice);

            boolean exitPosition = false;
            double exitPrice = closePrice; // Default to close price

            // Check for take profit hit - use high for long positions, low for shorts
            if ((position.isLong() && highPrice >= position.getTakeProfitPrice()) ||
                    (!position.isLong() && lowPrice <= position.getTakeProfitPrice())) {
                exitPosition = true;
                exitPrice = position.getTakeProfitPrice(); // Use target price as exit price
            }

            // Check for stop loss hit - use low for long positions, high for shorts
            else if ((position.isLong() && lowPrice <= position.getCurrentStopLossPrice()) ||
                    (!position.isLong() && highPrice >= position.getCurrentStopLossPrice())) {
                exitPosition = true;
                exitPrice = position.getCurrentStopLossPrice(); // Use stop price as exit price
            }

            if (exitPosition) {
                // Calculate profit using the realistic exit price
                double profit = position.isLong() ?
                        (exitPrice - position.getEntryPrice()) / position.getEntryPrice() :
                        (position.getEntryPrice() - exitPrice) / position.getEntryPrice();

                // Account for fees
                double exitFee = exitPrice * position.getSize() * params.getTakerFee();

                // Add trade
                completedTrades.add(new Trade(
                        quotes.get(position.getEntryIndex()).getTimestamp(),
                        quote.getTimestamp(),
                        position.getEntryPrice(),
                        exitPrice,
                        position.getSize(),
                        profit,
                        position.getEntryPrice() * position.getSize() * profit - exitFee,
                        position.isLong(),
                        calculateMAE(position.getPriceHistory(), position.isLong(), position.getEntryPrice()),
                        calculateMFE(position.getPriceHistory(), position.isLong(), position.getEntryPrice()),
                        position.getMarketPhase()
                ));

                // Remove position
                iterator.remove();
            } else {
                // Update trailing stop using strategy
                double newStopPrice = strategy.updateStopLoss(position, quotes, index);

                // Only update if the stop price improves position
                if ((position.isLong() && newStopPrice > position.getCurrentStopLossPrice()) ||
                        (!position.isLong() && newStopPrice < position.getCurrentStopLossPrice())) {
                    position.setCurrentStopLossPrice(newStopPrice);
                }
            }
        }
    }

    private void closeAllPositions(List<Position> positions, Quote quote, List<Quote> allQuotes,
                                   List<Trade> completedTrades, TradingParameters params) {
        double closePrice = quote.getClose().doubleValue();
        double highPrice = quote.getHigh().doubleValue();
        double lowPrice = quote.getLow().doubleValue();

        for (Position position : positions) {
            // Determine a more realistic exit price
            double exitPrice = closePrice;

            // Check if target would have been hit during the candle
            if ((position.isLong() && highPrice >= position.getTakeProfitPrice()) ||
                    (!position.isLong() && lowPrice <= position.getTakeProfitPrice())) {
                exitPrice = position.getTakeProfitPrice();
            }
            // Check if stop would have been hit during the candle
            else if ((position.isLong() && lowPrice <= position.getCurrentStopLossPrice()) ||
                    (!position.isLong() && highPrice >= position.getCurrentStopLossPrice())) {
                exitPrice = position.getCurrentStopLossPrice();
            }

            double profit = position.isLong() ?
                    (exitPrice - position.getEntryPrice()) / position.getEntryPrice() :
                    (position.getEntryPrice() - exitPrice) / position.getEntryPrice();

            double exitFee = exitPrice * position.getSize() * params.getTakerFee();

            completedTrades.add(new Trade(
                    allQuotes.get(position.getEntryIndex()).getTimestamp(),
                    quote.getTimestamp(),
                    position.getEntryPrice(),
                    exitPrice,
                    position.getSize(),
                    profit,
                    position.getEntryPrice() * position.getSize() * profit - exitFee,
                    position.isLong(),
                    calculateMAE(position.getPriceHistory(), position.isLong(), position.getEntryPrice()),
                    calculateMFE(position.getPriceHistory(), position.isLong(), position.getEntryPrice()),
                    position.getMarketPhase()
            ));
        }

        positions.clear();
    }

    private double calculateMAE(List<Double> priceHistory, boolean isLong, double entryPrice) {
        // Same implementation as before
        double mae = 0;
        for (double price : priceHistory) {
            double excursion = isLong ?
                    (price - entryPrice) / entryPrice :
                    (entryPrice - price) / entryPrice;
            mae = Math.min(mae, excursion);
        }
        return mae;
    }

    private double calculateMFE(List<Double> priceHistory, boolean isLong, double entryPrice) {
        // Same implementation as before
        double mfe = 0;
        for (double price : priceHistory) {
            double excursion = isLong ?
                    (price - entryPrice) / entryPrice :
                    (entryPrice - price) / entryPrice;
            mfe = Math.max(mfe, excursion);
        }
        return mfe;
    }

    private double[] calculateSharpeAndSortino(Map<String, Double> dailyReturns) {
        // Same implementation as before
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

    private BacktestResult calculateResults(List<Trade> trades, double finalEquity, double initialCapital) {
        if (trades.isEmpty()) {
            return BacktestResult.builder()
                    .finalFunds(finalEquity)
                    .totalReturn((finalEquity - initialCapital) / initialCapital)
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
        double peak = initialCapital;
        double equity = initialCapital;
        double sumWinAmount = 0;
        double sumLossAmount = 0;
        long totalDays = 0;

        for (Trade trade : trades) {
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

        double winRate = trades.isEmpty() ? 0 : (double) winningCount / trades.size();
        double profitFactor = totalLoss > 0 ? totalProfit / totalLoss : totalProfit > 0 ? Double.MAX_VALUE : 0;
        double avgWinAmount = winningCount > 0 ? sumWinAmount / winningCount : 0;
        double avgLossAmount = losingCount > 0 ? sumLossAmount / losingCount : 0;
        double avgTradeAmount = trades.isEmpty() ? 0 : (sumWinAmount - sumLossAmount) / trades.size();
        double avgTradeLength = trades.isEmpty() ? 0 : (double) totalDays / trades.size();

        double totalDaysDouble = (double) totalDays;
        double annualized = totalDaysDouble > 0 ?
                Math.pow((finalEquity / initialCapital), (252.0 / totalDaysDouble)) - 1.0 : 0;

        return BacktestResult.builder()
                .finalFunds(finalEquity)
                .totalReturn((finalEquity - initialCapital) / initialCapital)
                .annualizedReturn(annualized)
                .profitFactor(profitFactor)
                .winRate(winRate)
                .maxDrawdown(maxDrawdown)
                .totalTrades(trades.size())
                .winningTrades(winningCount)
                .losingTrades(losingCount)
                .avgWinAmount(avgWinAmount)
                .avgLossAmount(avgLossAmount)
                .avgTradeAmount(avgTradeAmount)
                .avgTradeLength(avgTradeLength)
                .trades(trades)
                .build();
    }
}
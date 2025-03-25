package ch.xavier.backtester;


import ch.xavier.backtester.backtesting.BacktesterService;
import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.backtesting.WalkForwardService;
import ch.xavier.backtester.backtesting.model.BacktestResult;
import ch.xavier.backtester.backtesting.model.ParameterPerformance;
import ch.xavier.backtester.backtesting.model.PerformanceMetricType;
import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import ch.xavier.backtester.marketphase.SinglePhaseClassifier;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.quote.QuoteService;
import ch.xavier.backtester.strategy.StrategiesFactory;
import ch.xavier.backtester.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
@Slf4j
public class MainBacktester {

    public MainBacktester(@Autowired QuoteService quoteService,
                          @Autowired BacktesterService backtesterService,
                          @Autowired WalkForwardService walkForwardService) {

        // BACKTESTING PARAMETERS
        String symbol = "RUNE";
        String timeframe = "1m";
        PerformanceMetricType metricType = PerformanceMetricType.COMPOSITE_SCORE;
        int numberOfResultsToKeep = 3;

//        String strategyName = "DoubleTapStrategy";
//        String strategyName = "VortexStrategy";
        String strategyName = "DivergenceStrategy";
//        String strategyName = "PullbackStrategy";
//        String strategyName = "VwapStrategy";

        Map<String, List<Object>> parametersGrid = StrategiesFactory.getParametersGrid(strategyName);

        log.info("Starting backtesting!");

        List<Quote> quotes = quoteService.getUpToDateQuotes(symbol, timeframe)
                .sort(Comparator.comparing(quote -> quote.getTimestamp().getTime()))
                .collectList()
                .block();
        log.info("Retrieved {} quotes for backtesting", quotes.size());

        TradingParameters parameters = TradingParameters.builder()
                .minNumberOfTrades(getMinTradesForTimeframe(timeframe, quotes.size()))
                .build();

        MarketPhaseClassifier classifier = getMarketPhaseClassifier();

        // 90% training, 10% validation
        int splitIndex = (int) (quotes.size() * 0.9);
        List<Quote> trainingQuotes = quotes.subList(0, splitIndex);
        List<Quote> validationQuotes = quotes.subList(splitIndex, quotes.size());
        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> validationPhases =
                classifyQuotesPerMarketPhase(validationQuotes, classifier);
        log.info("Split quotes into {} training quotes and {} validation quotes",
                trainingQuotes.size(), validationQuotes.size());

        disableWalkForwardServiceLogs();
        log.info("Starting optimization");


//        backtestUsingGridOfParameters(backtesterService, trainingQuotes, validationPhases, parameters,
//                parametersGrid, classifier, metricType, numberOfResultsToKeep, strategyName);

        backtestUsingAllMetrics(backtesterService, trainingQuotes, validationPhases, parameters,
                parametersGrid, classifier, numberOfResultsToKeep, strategyName);

//        backtestUsingWalkForwarding(walkForwardService, backtesterService, trainingQuotes, validationPhases,
//                parameters, parametersGrid, classifier, metricType, numberOfResultsToKeep, strategyName);
    }


    private static MarketPhaseClassifier getMarketPhaseClassifier() {
        //        TrendClassifier classifier = new TrendClassifier(50);
//        MovingWindowClassifier classifier = new MovingWindowClassifier(new TrendClassifier(50), 24, 0.6);
//        MarketPhaseClassifier classifier = new CombinedMarketPhaseClassifier(List.of(
//                new TrendClassifier(50),
//                new MovingAverageClassifier(20, 100, 0.01),
//                new VolatilityClassifier(20, 0.015, 0.035)
//        ));
        MarketPhaseClassifier classifier = new SinglePhaseClassifier();
        return classifier;
    }

    private void disableWalkForwardServiceLogs() {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(WalkForwardService.class)).setLevel(ch.qos.logback.classic.Level.ERROR);
    }

    private static void backtestUsingGridOfParameters(BacktesterService backtesterService,
                                                      List<Quote> trainingQuotes,
                                                      Map<MarketPhaseClassifier.MarketPhase, List<Quote>> validationPhases,
                                                      TradingParameters parameters,
                                                      Map<String, List<Object>> parameterGrid,
                                                      MarketPhaseClassifier classifier,
                                                      PerformanceMetricType metricType,
                                                      int numberOfResultsToKeep,
                                                      String strategyName) {
        // Classify training data by market phase
        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> trainingPhases =
                classifyQuotesPerMarketPhase(trainingQuotes, classifier);

        // For each market phase, find best parameters and validate them
        trainingPhases.forEach((phase, phaseQuotes) -> {
            if (phaseQuotes.isEmpty()) {
                log.info("Skipping {} market phase - no quotes", phase);
                return;
            }

            log.info("Optimizing for {} market phase with {} quotes", phase, phaseQuotes.size());

            backtesterService.backtestParameterGrid(
                            phaseQuotes,
                            strategyName,
                            parameters,
                            parameterGrid,
                            metricType,
                            false)
                    .subscribe(results -> {

                        log.info("");
                        log.info("Top results before filtering");
                        logGridResults(phase, metricType, results.subList(0, Math.min(results.size(), numberOfResultsToKeep - 1)));

//                        log.info("");
//                        log.info("Top results after filtering");
//                        results = results.stream()
//                                .filter(result -> result.getResult().getTotalTrades() > parameters.getMinNumberOfTrades())
//                                .filter(result -> result.getResult().getMaxDrawdown() * 100 < parameters.getMaxDrawdown())
//                                .toList();
//                        logGridResults(phase, metricType, results.subList(0, Math.min(results.size(), numberOfResultsToKeep - 1)));

                        // Validate if we have results and validation data for this phase
                        if (!results.isEmpty()) {
                            // Extract all top parameter sets
                            List<Map<String, Object>> topParamsList = results.stream()
                                    .limit(numberOfResultsToKeep)
                                    .map(ParameterPerformance::getParameters)
                                    .toList();

                            List<Quote> phaseValidationQuotes = validationPhases.get(phase);

                            if (phaseValidationQuotes != null && !phaseValidationQuotes.isEmpty()) {
                                validateBestParameters(backtesterService, phase, topParamsList,
                                        phaseValidationQuotes, parameters, strategyName);
                            } else {
                                log.info("No validation data available for {} market phase", phase);
                            }
                        }
                    });
        });
    }

    private static void backtestUsingWalkForwarding(WalkForwardService walkForwardService,
                                                    BacktesterService backtesterService,
                                                    List<Quote> trainingQuotes,
                                                    Map<MarketPhaseClassifier.MarketPhase, List<Quote>> validationPhases,
                                                    TradingParameters parameters,
                                                    Map<String, List<Object>> parameterGrid,
                                                    MarketPhaseClassifier classifier,
                                                    PerformanceMetricType metricType,
                                                    int numberOfResultsToKeep,
                                                    String strategyName) {
        walkForwardService.runWalkForward(trainingQuotes,
                        strategyName,
                        parameters,
                        parameterGrid,
                        classifier,
                        metricType,
                        numberOfResultsToKeep)
                .subscribe(result -> {
                    log.info("");
                    log.info("Walk-forward results:");
                    log.info("Win rate: {}%, Total return: {}%",
                            String.format("%.2f", result.getAggregatedResult().getWinRate() * 100),
                            String.format("%.2f", result.getAggregatedResult().getTotalReturn() * 100));

                    // Print top parameters per phase and validate them
                    result.getTopParametersByPhase().forEach((phase, params) -> {
                        log.info("Top parameters for {} market:", phase);
                        params.forEach(p -> log.info("Parameters: {}, {}: {}",
                                p.getParameters(), metricType.name(),
                                String.format("%.2f", p.getPerformanceMetric())));

                        // Validate all top parameters if we have validation data for this phase
                        if (!params.isEmpty()) {
                            // Extract all parameter sets
                            List<Map<String, Object>> topParamsList = params.stream()
                                    .map(ParameterPerformance::getParameters)
                                    .toList();

                            List<Quote> phaseValidationQuotes = validationPhases.get(phase);

                            if (phaseValidationQuotes != null && !phaseValidationQuotes.isEmpty()) {
                                validateBestParameters(backtesterService, phase, topParamsList,
                                        phaseValidationQuotes, parameters, strategyName);
                            } else {
                                log.info("No validation data available for {} market phase", phase);
                            }
                        }
                    });
                });
    }

    private static void backtestUsingAllMetrics(BacktesterService backtesterService,
                                                List<Quote> trainingQuotes,
                                                Map<MarketPhaseClassifier.MarketPhase, List<Quote>> validationPhases,
                                                TradingParameters parameters,
                                                Map<String, List<Object>> parameterGrid,
                                                MarketPhaseClassifier classifier,
                                                int numberOfResultsToKeep,
                                                String strategyName) {
        // Classify training data by market phase
        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> trainingPhases =
                classifyQuotesPerMarketPhase(trainingQuotes, classifier);

        // For each market phase, find best parameters for each metric type
        trainingPhases.forEach((phase, phaseQuotes) -> {
            if (phaseQuotes.isEmpty()) {
                log.info("Skipping {} market phase - no quotes", phase);
                return;
            }

            log.info("Processing {} market phase with {} quotes", phase, phaseQuotes.size());

            // Run a single backtest for all metrics at once
            backtesterService.backtestParameterGridForAllMetrics(
                            phaseQuotes, strategyName, parameters, parameterGrid,
                            numberOfResultsToKeep, false)
                    .subscribe(resultsByMetric -> {
                        // Log results for each metric
                        resultsByMetric.forEach((metricType, results) -> {
//                            log.info("=== Top parameters for {} ===", metricType);
//                            logGridResults(phase, metricType, results);

                            // Validate top parameters for this metric on validation data
//                            List<Map<String, Object>> paramsList = results.stream()
//                                    .map(ParameterPerformance::getParameters)
//                                    .collect(Collectors.toList());

//                            List<Quote> phaseValidationQuotes = validationPhases.get(phase);
//                            if (phaseValidationQuotes != null && !phaseValidationQuotes.isEmpty()) {
//                                log.info("Validating top parameters for {} metric:", metricType);
//                                validateBestParameters(backtesterService, phase, paramsList,
//                                        phaseValidationQuotes, parameters, strategyName);
//                            }
                        });

                        // Log a comprehensive summary
                        logAllMetricsSummary(phase, resultsByMetric);
                    });
        });
    }

    private static void logAllMetricsSummary(
            MarketPhaseClassifier.MarketPhase phase,
            Map<PerformanceMetricType, List<ParameterPerformance>> allMetricResults) {
        log.info("");
        log.info("==== SUMMARY OF TOP PARAMETERS FOR {} MARKET PHASE ====", phase);

        allMetricResults.forEach((metric, results) -> {
            log.info("");
            log.info("=== Top parameters for {} ===", metric);
            for (int i = 0; i < results.size(); i++) {
                ParameterPerformance perf = results.get(i);
//                log.info("#{}: Score: {}, Return: {}%, Trades: {}, Win Rate: {}%, Parameters: {}",
//                        i + 1,
//                        String.format("%.4f", perf.getPerformanceMetric()),
//                        String.format("%.2f", perf.getResult().getTotalReturn() * 100),
//                        perf.getResult().getTotalTrades(),
//                        String.format("%.2f", perf.getResult().getWinRate() * 100),
//                        perf.getParameters());

                log.info("Result #{} parameters for {} market using these parameters: {}. " +
                                "Trades: {}, Return: {}%, Win rate: {}%, Sharpe: {}, Sortino: {}, " +
                                "Profit Factor:{}, Avg Trade Amount:{}, Max Drawdown: {}%",
                        i + 1,
                        phase,
                        perf.getParameters(),
                        perf.getResult().getTotalTrades(),
                        String.format("%.2f", perf.getResult().getTotalReturn() * 100),
                        String.format("%.2f", perf.getResult().getWinRate() * 100),
                        String.format("%.2f", perf.getResult().getSharpeRatio()),
                        String.format("%.2f", perf.getResult().getSortinoRatio()),
                        String.format("%.2f", perf.getResult().getProfitFactor()),
                        String.format("%.2f", perf.getResult().getAvgTradeAmount()),
                        String.format("%.2f", perf.getResult().getMaxDrawdown() * 100));
            }

            log.info("");
        });
    }

    private static Map<MarketPhaseClassifier.MarketPhase, List<Quote>> classifyQuotesPerMarketPhase(
            List<Quote> quotes, MarketPhaseClassifier classifier) {
        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes = new HashMap<>();

        for (int i = 0; i < quotes.size(); i++) {
            MarketPhaseClassifier.MarketPhase phase = classifier.classify(quotes, i);
            marketPhaseQuotes.computeIfAbsent(phase, _ -> new ArrayList<>()).add(quotes.get(i));
        }

        marketPhaseQuotes.remove(MarketPhaseClassifier.MarketPhase.UNKNOWN);
        return marketPhaseQuotes;
    }

    private static void validateBestParameters(BacktesterService backtesterService,
                                               MarketPhaseClassifier.MarketPhase phase,
                                               List<Map<String, Object>> paramsList,
                                               List<Quote> validationQuotes,
                                               TradingParameters parameters,
                                               String strategyName) {
        for (int i = 0; i < paramsList.size(); i++) {
            Map<String, Object> strategyParameters = paramsList.get(i);
            final int rank = i + 1;

            TradingStrategy strategy = StrategiesFactory.getStrategy(strategyName, parameters, strategyParameters);

            backtesterService.backtest(validationQuotes, strategy, phase, parameters, true)
                    .subscribe(result -> {
                        log.info("Validating #{} parameters for {} market on {} quotes using these parameters: {}. " +
                                        "Trades: {}, Return: {}%, Win rate: {}%, Sharpe: {}, Sortino: {}, " +
                                        "Profit Factor:{}, Avg Trade Amount:{}, Max Drawdown: {}%",
                                rank,
                                phase,
                                validationQuotes.size(),
                                strategyParameters,
                                result.getTotalTrades(),
                                String.format("%.2f", result.getTotalReturn() * 100),
                                String.format("%.2f", result.getWinRate() * 100),
                                String.format("%.2f", result.getSharpeRatio()),
                                String.format("%.2f", result.getSortinoRatio()),
                                String.format("%.2f", result.getProfitFactor()),
                                String.format("%.2f", result.getAvgTradeAmount()),
                                String.format("%.2f", result.getMaxDrawdown() * 100));
                    });
        }
    }

    private static void logGridResults(MarketPhaseClassifier.MarketPhase phase,
                                       PerformanceMetricType metricType,
                                       List<ParameterPerformance> results) {
        log.info("Grid search results for {} market phase (top parameter combinations by {}):", phase, metricType);
        for (int i = 0; i < results.size(); i++) {
            ParameterPerformance perf = results.get(i);
            BacktestResult result = perf.getResult();

            log.info("Result #{} parameters for {} market using these parameters: {}. " +
                            "Trades: {}, Return: {}%, Win rate: {}%, Sharpe: {}, Sortino: {}, " +
                            "Profit Factor:{}, Avg Trade Amount:{}, Max Drawdown: {}%",
                    i + 1,
                    phase,
                    perf.getParameters(),
                    result.getTotalTrades(),
                    String.format("%.2f", result.getTotalReturn() * 100),
                    String.format("%.2f", result.getWinRate() * 100),
                    String.format("%.2f", result.getSharpeRatio()),
                    String.format("%.2f", result.getSortinoRatio()),
                    String.format("%.2f", result.getProfitFactor()),
                    String.format("%.2f", result.getAvgTradeAmount()),
                    String.format("%.2f", result.getMaxDrawdown() * 100));
        }
    }

    private static List<Map<String, Object>> deduplicateParameters(List<Map<String, Object>> paramsList) {
        Map<String, Map<String, Object>> uniqueParameterSets = new LinkedHashMap<>();

        // Group identical parameter sets by what they actually do
        for (Map<String, Object> params : paramsList) {
            // Create a fingerprint by including only parameters that affect behavior
            // For ZScoreStrategy: exclude atrPeriod since it's unused
            StringBuilder fingerprint = new StringBuilder();
            params.entrySet().stream()
                    .filter(entry -> !"atrPeriod".equals(entry.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> fingerprint.append(entry.getKey())
                            .append("=")
                            .append(entry.getValue())
                            .append(";"));

            // Keep only the first parameter set with this behavior fingerprint
            uniqueParameterSets.putIfAbsent(fingerprint.toString(), params);
        }

        return new ArrayList<>(uniqueParameterSets.values());
    }

    /**
     * Calculates the minimum number of trades required for statistically significant backtesting
     * based on timeframe and available historical data.
     *
     * @param timeframe  The timeframe string (e.g., "15m", "4h", "1d")
     * @param quotesSize The total number of candles/quotes available
     * @return The minimum number of trades expected for meaningful backtest results
     */
    public static int getMinTradesForTimeframe(String timeframe, int quotesSize) {
        // Extract numeric value and unit from timeframe
        int timeValue = Integer.parseInt(timeframe.replaceAll("[^0-9]", ""));
        String timeUnit = timeframe.replaceAll("[0-9]", "");

        // Calculate candles per week for this timeframe
        int candlesPerWeek = switch (timeUnit) {
            case "m" -> 7 * 24 * 60 / timeValue;       // minutes in a week / minutes per candle
            case "h" -> 7 * 24 / timeValue;            // hours in a week / hours per candle
            case "d" -> 7 / timeValue;                 // days in a week / days per candle
            case "w" -> 1 / timeValue;                 // weeks / weeks per candle
            default -> 168;                            // default to hourly (168 hours per week)
        };

        // Calculate approximate weeks of data available
        double weeksOfData = (double) quotesSize / candlesPerWeek;

        // Base minimum trades per year (52 weeks)
        int baseMinTradesPerYear = switch (timeUnit) {
            case "m" -> timeValue <= 5 ? 400 : timeValue <= 30 ? 200 : 100;  // Minute timeframes
            case "h" -> timeValue <= 4 ? 80 : 40;                            // Hour timeframes
            case "d" -> 20;                                                   // Daily timeframes
            case "w" -> 10;                                                   // Weekly timeframes
            default -> 50;                                                    // Default case
        };

        // Scale by data duration (in years)
        int scaledMinTrades = (int) Math.ceil(baseMinTradesPerYear * (weeksOfData / 52.0));

        // Enforce reasonable limits
        return Math.max(10, Math.min(scaledMinTrades, quotesSize / 10));
    }
}


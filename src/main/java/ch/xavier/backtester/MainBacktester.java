package ch.xavier.backtester;


import ch.xavier.backtester.backtesting.BacktesterService;
import ch.xavier.backtester.backtesting.WalkForwardService;
import ch.xavier.backtester.backtesting.model.BacktestResult;
import ch.xavier.backtester.backtesting.model.ParameterPerformance;
import ch.xavier.backtester.backtesting.model.PerformanceMetricType;
import ch.xavier.backtester.backtesting.model.TradingParameters;
import ch.xavier.backtester.marketphase.*;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.quote.QuoteService;
import ch.xavier.backtester.strategy.StrategiesFactory;
import ch.xavier.backtester.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.IntStream;

@Service
@Slf4j
public class MainBacktester {

    public MainBacktester(@Autowired QuoteService quoteService,
                          @Autowired BacktesterService backtesterService,
                          @Autowired WalkForwardService walkForwardService) {

        // BACKTESTING PARAMETERS
        String symbol = "BTC";
        String timeframe = "1h";
        PerformanceMetricType metricType = PerformanceMetricType.SHARPE_RATIO;
        int numberOfResultsToKeep = 3;

        String strategyName = "SMACrossover";
        Map<String, List<Object>> parametersGrid = Map.of(
                "fastPeriod", generateIntRange(10, 30, 2),
                "slowPeriod", generateIntRange(50, 200, 10),
                "useTrailingSL", List.of("true", "false")
        );


        log.info("Starting backtesting!");

        TradingParameters parameters = TradingParameters.builder().build();

        List<Quote> quotes = quoteService.getUpToDateQuotes(symbol, timeframe)
                .sort(Comparator.comparing(quote -> quote.getTimestamp().getTime()))
                .collectList()
                .block();
        log.info("Retrieved {} quotes for backtesting", quotes.size());

        TrendClassifier classifier = new TrendClassifier(50);
//        MovingWindowClassifier classifier = new MovingWindowClassifier(new TrendClassifier(50), 24, 0.6);
//        MarketPhaseClassifier classifier = new CombinedMarketPhaseClassifier(List.of(
//                new TrendClassifier(50),
//                new MovingAverageClassifier(20, 100, 0.01),
//                new VolatilityClassifier(20, 0.015, 0.035)
//        ));
//        MarketPhaseClassifier classifier = new SinglePhaseClassifier();

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


        backtestWithValidation(backtesterService, trainingQuotes, validationPhases, parameters,
                parametersGrid, classifier, metricType, numberOfResultsToKeep, strategyName);

        backtestUsingWalkForwarding(walkForwardService, backtesterService, trainingQuotes, validationPhases,
                parameters, parametersGrid, classifier, metricType, numberOfResultsToKeep, strategyName);
    }

    private void disableWalkForwardServiceLogs() {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(WalkForwardService.class)).setLevel(ch.qos.logback.classic.Level.ERROR);
    }

    private static void backtestWithValidation(BacktesterService backtesterService,
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
                            numberOfResultsToKeep)
                    .subscribe(results -> {
                        // Log optimization results
                        logGridResults(phase, metricType, results);

                        // Validate if we have results and validation data for this phase
                        if (!results.isEmpty()) {
                            // Extract all top parameter sets
                            List<Map<String, Object>> topParamsList = results.stream()
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

    private static List<Object> generateIntRange(int from, int to, int interval) {
        return IntStream.iterate(from, n -> n <= to, n -> n + interval)
                .boxed()
                .map(i -> (Object) i)
                .toList();
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

            backtesterService.backtest(validationQuotes, strategy, phase, parameters)
                    .subscribe(result -> {
                        log.info("Validating #{} parameters for {} market on {} quotes using these parameters: {}. " +
                                        "Trades: {}, Return: {}%, Win rate: {}%, Sharpe: {}, Sortino: {}, Max Drawdown: {}%",
                                rank,
                                phase,
                                validationQuotes.size(),
                                strategyParameters,
                                result.getTotalTrades(),
                                String.format("%.2f", result.getTotalReturn() * 100),
                                String.format("%.2f", result.getWinRate() * 100),
                                String.format("%.2f", result.getSharpeRatio()),
                                String.format("%.2f", result.getSortinoRatio()),
                                String.format("%.2f", result.getMaxDrawdown() * 100));
                    });
        }
    }

    private static void logGridResults(MarketPhaseClassifier.MarketPhase phase,
                                       PerformanceMetricType metricType,
                                       List<ParameterPerformance> results) {
        log.info("");
        log.info("Grid search results for {} market phase (top 10 parameter combinations by {}):",
                phase, metricType);
        for (int i = 0; i < results.size(); i++) {
            ParameterPerformance perf = results.get(i);
            BacktestResult result = perf.getResult();
//            log.info("#{}: Parameters: {}, {}: {}, Return: {}%, Win rate: {}%",
//                    i + 1,
//                    perf.getParameters(),
//                    metricType,
//                    String.format("%.2f", perf.getPerformanceMetric()),
//                    String.format("%.2f", result.getTotalReturn() * 100),
//                    String.format("%.2f", result.getWinRate() * 100));
//
            log.info("Validating #{} parameters for {} market using these parameters: {}. " +
                            "Trades: {}, Return: {}%, Win rate: {}%, Sharpe: {}, Sortino: {}, Max Drawdown: {}%",
                    i + 1,
                    phase,
                    perf.getParameters(),
                    result.getTotalTrades(),
                    String.format("%.2f", result.getTotalReturn() * 100),
                    String.format("%.2f", result.getWinRate() * 100),
                    String.format("%.2f", result.getSharpeRatio()),
                    String.format("%.2f", result.getSortinoRatio()),
                    String.format("%.2f", result.getMaxDrawdown() * 100));
        }
    }
}


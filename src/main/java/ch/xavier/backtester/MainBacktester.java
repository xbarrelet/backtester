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
import ch.xavier.backtester.strategy.SMACrossoverStrategy;
import ch.xavier.backtester.visualization.XChartVisualizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.IntStream;

@Service
@Slf4j
public class MainBacktester {

    public MainBacktester(@Autowired QuoteService quoteService,
                          @Autowired XChartVisualizer visualizer,
                          @Autowired BacktesterService backtesterService,
                          @Autowired WalkForwardService walkForwardService) {

        // BACKTESTING PARAMETERS
        String symbol = "BTC";
        String timeframe = "1h";
        int numberOfResultsToKeep = 5;
        PerformanceMetricType metricType = PerformanceMetricType.TOTAL_RETURN;

        log.info("Starting backtesting!");

        List<Quote> quotes = quoteService.getUpToDateQuotes(symbol, timeframe)
                .sort(Comparator.comparing(quote -> quote.getTimestamp().getTime()))
                .collectList()
                .block();
        log.info("Retrieved {} quotes for backtesting", quotes.size());

        // 90% training, 10% validation
        int splitIndex = (int) (quotes.size() * 0.9);
        List<Quote> trainingQuotes = quotes.subList(0, splitIndex);
        List<Quote> validationQuotes = quotes.subList(splitIndex, quotes.size());
        log.info("Split quotes into {} training quotes and {} validation quotes",
                trainingQuotes.size(), validationQuotes.size());

//        MarketPhaseClassifier classifier = new CombinedMarketPhaseClassifier(List.of(
//                new MovingAverageClassifier(20, 100, 0.01),
//                new VolatilityClassifier(20, 0.015, 0.035))
//        );
        MarketPhaseClassifier classifier = new SinglePhaseClassifier();


        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> validationPhases =
                classifyQuotesPerMarketPhase(validationQuotes, classifier, false);
        disableWalkForwardServiceLogs();

        log.info("Starting optimization");

        TradingParameters parameters = TradingParameters.builder().build();

        Map<String, List<Object>> parameterGrid = new HashMap<>();
        parameterGrid.put("fastMaPeriod", generateIntRange(10, 30, 2));
        parameterGrid.put("slowMaPeriod", generateIntRange(50, 200, 10));


        backtestWithValidation(backtesterService, trainingQuotes, validationPhases, parameters,
                parameterGrid, classifier, metricType, numberOfResultsToKeep);

        backtestUsingWalkForwarding(walkForwardService, backtesterService, trainingQuotes, validationPhases,
                parameters, parameterGrid, classifier, metricType, numberOfResultsToKeep);
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
                                               int numberOfResultsToKeep) {
        // Classify training data by market phase
        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> trainingPhases =
                classifyQuotesPerMarketPhase(trainingQuotes, classifier, false);

        // For each market phase, find best parameters and validate them
        trainingPhases.forEach((phase, phaseQuotes) -> {
            if (phaseQuotes.isEmpty()) {
                log.info("Skipping {} market phase - no quotes", phase);
                return;
            }

            log.info("Optimizing for {} market phase with {} quotes", phase, phaseQuotes.size());

            backtesterService.backtestParameterGrid(
                            phaseQuotes,
                            params -> createStrategy(params, parameters),
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
                                        phaseValidationQuotes, parameters);
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
                                                    int numberOfResultsToKeep) {
        walkForwardService.runWalkForward(trainingQuotes,
                        params -> createStrategy(params, parameters),
                        parameters,
                        parameterGrid,
                        classifier,
                        metricType,
                        numberOfResultsToKeep)
                .subscribe(result -> {
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
                                        phaseValidationQuotes, parameters);
                            } else {
                                log.info("No validation data available for {} market phase", phase);
                            }
                        }
                    });
                });
    }

    private static Map<MarketPhaseClassifier.MarketPhase, List<Quote>> classifyQuotesPerMarketPhase(
            List<Quote> quotes, MarketPhaseClassifier classifier, boolean includeUnknownPhase) {
        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes = new HashMap<>();

        for (int i = 0; i < quotes.size(); i++) {
            MarketPhaseClassifier.MarketPhase phase = classifier.classify(quotes, i);
            marketPhaseQuotes.computeIfAbsent(phase, _ -> new ArrayList<>()).add(quotes.get(i));
        }

        if (!includeUnknownPhase) {
            marketPhaseQuotes.remove(MarketPhaseClassifier.MarketPhase.UNKNOWN);
        }

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
                                               TradingParameters parameters) {
        for (int i = 0; i < paramsList.size(); i++) {
            Map<String, Object> params = paramsList.get(i);
            final int rank = i + 1;

            SMACrossoverStrategy strategy = createStrategy(params, parameters);

            backtesterService.backtest(validationQuotes, strategy, phase, parameters)
                    .subscribe(result -> {
                        log.info("Validating #{} parameters for {} market on {} quotes using these parameters: {}. " +
                                        "Trades: {}, Return: {}%, Win rate: {}%, Sharpe: {}, Sortino: {}, Max Drawdown: {}%",
                                rank,
                                phase,
                                validationQuotes.size(),
                                params,
                                result.getTotalTrades(),
                                String.format("%.2f", result.getTotalReturn() * 100),
                                String.format("%.2f", result.getWinRate() * 100),
                                String.format("%.2f", result.getSharpeRatio()),
                                String.format("%.2f", result.getSortinoRatio()),
                                String.format("%.2f", result.getMaxDrawdown() * 100));
                    });
        }
    }

    private static SMACrossoverStrategy createStrategy(Map<String, Object> params,
                                                       TradingParameters parameters) {
        int fastMaPeriod = Integer.parseInt(params.get("fastMaPeriod").toString());
        int slowMaPeriod = Integer.parseInt(params.get("slowMaPeriod").toString());

        String name = String.format("SMA Crossover (%d,%d)", fastMaPeriod, slowMaPeriod);
        return new SMACrossoverStrategy(name, parameters, fastMaPeriod, slowMaPeriod);
    }

    private static void logGridResults(MarketPhaseClassifier.MarketPhase phase,
                                       PerformanceMetricType metricType,
                                       List<ParameterPerformance> results) {
        log.info("Grid search results for {} market phase (top 10 parameter combinations by {}):",
                phase, metricType);
        for (int i = 0; i < results.size(); i++) {
            ParameterPerformance perf = results.get(i);
            BacktestResult result = perf.getResult();
            log.info("#{}: Parameters: {}, {}: {}, Return: {}%, Win rate: {}%",
                    i + 1,
                    perf.getParameters(),
                    metricType,
                    String.format("%.2f", perf.getPerformanceMetric()),
                    String.format("%.2f", result.getTotalReturn() * 100),
                    String.format("%.2f", result.getWinRate() * 100));
        }
    }
}

package ch.xavier.backtester;


import ch.xavier.backtester.backtesting.*;
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

        String symbol = "BTC";
        String timeframe = "1h";

        log.info("Starting backtesting!");

        List<Quote> quotes = quoteService.getUpToDateQuotes(symbol, timeframe)
                .sort(Comparator.comparing(quote -> quote.getTimestamp().getTime()))
                .collectList()
                .block();
        log.info("Retrieved {} quotes for backtesting", quotes.size());

        MarketPhaseClassifier classifier = new CombinedMarketPhaseClassifier(List.of(
                new MovingAverageClassifier(20, 100, 0.01),
                new VolatilityClassifier(20, 0.015, 0.035))
        );
//        MarketPhaseClassifier classifier = new SinglePhaseClassifier();

        printStatsAboutMarketPhases(visualizer, quotes, classifier);
        disableWalkForwardServiceLogs();

        log.info("Starting optimization");

        // Create trading parameters
        TradingParameters parameters = TradingParameters.builder().build();

        PerformanceMetricType metricType = PerformanceMetricType.SORTINO_RATIO;

        // Example of using the walk-forward service
        Map<String, List<Object>> parameterGrid = new HashMap<>();
        parameterGrid.put("fastMaPeriod", generateIntRange(10, 30, 2));
        parameterGrid.put("slowMaPeriod", generateIntRange(50, 200, 10));

        backtestUsingGridSearch(backtesterService, quotes, parameters, parameterGrid, metricType);

        backtestUsingWalkForwarding(walkForwardService, quotes, parameters, parameterGrid, classifier, metricType);

        log.info("Backtesting completed!");
    }

    private static void printStatsAboutMarketPhases(XChartVisualizer visualizer, List<Quote> quotes, MarketPhaseClassifier classifier) {
        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes = classifyQuotesPerMarketPhase(quotes,
                classifier);
        visualizer.generateVisualizationOfMarketPhases(marketPhaseQuotes);

        log.info("Market phase breakdown:");
        marketPhaseQuotes.forEach((phase, phaseQuotes) -> {
            log.info("{}: {} quotes", phase, phaseQuotes.size());
        });
    }

    private void disableWalkForwardServiceLogs() {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(WalkForwardService.class)).setLevel(ch.qos.logback.classic.Level.ERROR);
    }

    private static void backtestUsingGridSearch(BacktesterService backtesterService,
                                                List<Quote> quotes,
                                                TradingParameters parameters,
                                                Map<String, List<Object>> parameterGrid,
                                                PerformanceMetricType metricType) {
        backtesterService.backtestParameterGrid(
                        quotes,
                        params -> {
                            int fastMaPeriod = Integer.parseInt(params.get("fastMaPeriod").toString());
                            int slowMaPeriod = Integer.parseInt(params.get("slowMaPeriod").toString());

                            String name = String.format("SMA Crossover (%d,%d)", fastMaPeriod, slowMaPeriod);
                            return new SMACrossoverStrategy(name, parameters, fastMaPeriod, slowMaPeriod);
                        },
                        parameters,
                        parameterGrid,
                        metricType,
                        10) // Get top 10 results
                .subscribe(results -> {
                    log.info("Grid search results (top 10 parameter combinations by {}):", metricType);
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
                });
    }

    private static void backtestUsingWalkForwarding(WalkForwardService walkForwardService,
                                                    List<Quote> quotes,
                                                    TradingParameters parameters, Map<String, List<Object>> parameterGrid,
                                                    MarketPhaseClassifier classifier,
                                                    PerformanceMetricType metricType) {
        walkForwardService.runWalkForward(quotes,
                        params -> {
                            int fastMaPeriod = Integer.parseInt(params.get("fastMaPeriod").toString());
                            int slowMaPeriod = Integer.parseInt(params.get("slowMaPeriod").toString());

                            String name = String.format("SMA Crossover (%d,%d)", fastMaPeriod, slowMaPeriod);
                            return new SMACrossoverStrategy(name, parameters, fastMaPeriod, slowMaPeriod);
                        },
                        parameters,
                        parameterGrid,
                        classifier,
                        metricType)
                .subscribe(result -> {
                    log.info("Walk-forward results:");
                    log.info("Win rate: {}%, Total return: {}%",
                            String.format("%.2f", result.getAggregatedResult().getWinRate() * 100),
                            String.format("%.2f", result.getAggregatedResult().getTotalReturn() * 100));

                    // Print top parameters per phase
                    result.getTopParametersByPhase().forEach((phase, params) -> {
                        log.info("Top parameters for {} market:", phase);
                        params.forEach(p -> log.info("Parameters: {}, {}: {}",
                                p.getParameters(), metricType.name(), String.format("%.2f", p.getPerformanceMetric())));
                    });
                });
    }

    private static Map<MarketPhaseClassifier.MarketPhase, List<Quote>> classifyQuotesPerMarketPhase(List<Quote> quotes,
                                                                                                    MarketPhaseClassifier classifier) {
        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes = new HashMap<>();

        for (int i = 0; i < quotes.size(); i++) {
            MarketPhaseClassifier.MarketPhase phase = classifier.classify(quotes, i);
            marketPhaseQuotes.computeIfAbsent(phase, _ -> new ArrayList<>()).add(quotes.get(i));
        }

        return marketPhaseQuotes;
    }

    private static List<Object> generateIntRange(int from, int to, int interval) {
        return IntStream.iterate(from, n -> n <= to, n -> n + interval)
                .boxed()
                .map(i -> (Object) i)
                .toList();
    }
}

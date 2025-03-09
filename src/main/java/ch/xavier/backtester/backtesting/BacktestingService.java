package ch.xavier.backtester.backtesting;


import ch.xavier.backtester.indicator.McGinley;
import ch.xavier.backtester.marketphase.CombinedMarketPhaseClassifier;
import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import ch.xavier.backtester.marketphase.MovingAverageClassifier;
import ch.xavier.backtester.marketphase.VolatilityClassifier;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.quote.QuoteService;
import ch.xavier.backtester.visualization.XChartVisualizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class BacktestingService {

    public BacktestingService(@Autowired QuoteService quoteService,
                              @Autowired XChartVisualizer visualizer,
                              @Autowired RealisticBacktesterService backtesterService) throws InterruptedException {
        String symbol = "BTC";
        String timeframe = "1h";

        log.info("Starting backtesting!");

        List<Quote> quotes = quoteService.getUpToDateQuotes(symbol, timeframe).collectList().block();
        log.info("Retrieved {} quotes for backtesting", quotes.size());

        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes = classifyQuotesPerMarketPhase(quotes);
        visualizer.generateVisualizationOfMarketPhases(marketPhaseQuotes);

        log.info("Market phase breakdown:");
        marketPhaseQuotes.forEach((phase, phaseQuotes) -> {
            log.info("{}: {} quotes", phase, phaseQuotes.size());
        });

        log.info("Starting optimization");

        // Create trading parameters
        TradingParameters params = new TradingParameters();

        // Run phase-specific optimization for McGinley
        Map<MarketPhaseClassifier.MarketPhase, Integer> optimalPeriods = new HashMap<>();
        Map<MarketPhaseClassifier.MarketPhase, BacktestResult> phaseResults = new HashMap<>();

        for (Map.Entry<MarketPhaseClassifier.MarketPhase, List<Quote>> entry : marketPhaseQuotes.entrySet()) {
            MarketPhaseClassifier.MarketPhase phase = entry.getKey();
            List<Quote> phaseQuotes = entry.getValue();

            if (phase != MarketPhaseClassifier.MarketPhase.UNKNOWN && phaseQuotes.size() > 30) {
                log.info("Optimizing McGinley for {} market", phase);

                // Find optimal period for this market phase
                int bestPeriod = optimizeMcGinleyForPhase(phaseQuotes, phase, params, backtesterService);
                optimalPeriods.put(phase, bestPeriod);

                // Run backtest with optimal settings for this phase
                McGinley mcginley = new McGinley(bestPeriod);
                BacktestResult result = backtesterService.backtest(phaseQuotes, mcginley, phase, params);
                phaseResults.put(phase, result);

                log.info("{} market results: Win rate: {}%, Profit factor: {}, Return: {}%",
                        phase,
                        String.format("%.2f", result.getWinRate() * 100),
                        String.format("%.2f", result.getProfitFactor()),
                        String.format("%.2f", result.getTotalReturn() * 100));
            }
        }

        // Visualize results
        visualizer.visualizeBacktestResults(phaseResults, optimalPeriods);

        log.info("Backtesting completed!");

        Thread.sleep(1000000000);
    }

    private static Map<MarketPhaseClassifier.MarketPhase, List<Quote>> classifyQuotesPerMarketPhase(List<Quote> quotes) {
        MarketPhaseClassifier classifier = new CombinedMarketPhaseClassifier(List.of(
                new MovingAverageClassifier(20, 100, 0.01),
                new VolatilityClassifier(20, 0.015, 0.035))
        );

        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes = new HashMap<>();

        for (int i = 0; i < quotes.size(); i++) {
            MarketPhaseClassifier.MarketPhase phase = classifier.classify(quotes, i);
            marketPhaseQuotes.computeIfAbsent(phase, _ -> new ArrayList<>()).add(quotes.get(i));
        }

        return marketPhaseQuotes;
    }

    private int optimizeMcGinleyForPhase(List<Quote> quotes,
                                         MarketPhaseClassifier.MarketPhase phase,
                                         TradingParameters params,
                                         RealisticBacktesterService backtesterService) {
        List<Integer> periodLengths = List.of(8, 10, 12, 14, 16, 18, 20, 22, 24);
        double bestMetric = 0;
        int bestPeriod = 14; // Default

        for (Integer period : periodLengths) {
            McGinley mcginley = new McGinley(period);
            BacktestResult result = backtesterService.backtest(quotes, mcginley, phase, params);

            // Define scoring function based on phase
            double score = calculatePhaseScore(result, phase);

            if (score > bestMetric) {
                bestMetric = score;
                bestPeriod = period;
            }

            log.info("Period: {}, Win rate: {}%, PF: {}, Score: {}",
                    period,
                    String.format("%.2f", result.getWinRate() * 100),
                    String.format("%.2f", result.getProfitFactor()),
                    String.format("%.2f", score));
        }

        return bestPeriod;
    }

    private double calculatePhaseScore(BacktestResult result, MarketPhaseClassifier.MarketPhase phase) {
        switch (phase) {
            case BULLISH:
                // For bull markets, prioritize return metrics
                return result.getProfitFactor() * result.getTotalReturn();
            case BEARISH:
                // For bear markets, prioritize drawdown control and win rate
                return result.getWinRate() / (result.getMaxDrawdown() + 0.01);
            case SIDEWAYS:
                // For sideways markets, prioritize consistency
                return result.getWinRate() * result.getProfitFactor();
            default:
                // Default comparison by profit factor
                return result.getProfitFactor();
        }
    }
}

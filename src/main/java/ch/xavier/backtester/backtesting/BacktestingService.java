package ch.xavier.backtester.backtesting;


import ch.xavier.backtester.indicator.McGinleyDynamic;
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

import java.util.*;

@Service
@Slf4j
public class BacktestingService {

    public BacktestingService(@Autowired QuoteService quoteService,
                              @Autowired XChartVisualizer visualizer) throws InterruptedException {
        String symbol = "BTC";
        String timeframe = "1h";

        log.info("Starting backtesting!");

        List<Quote> quotes = quoteService.getUpToDateQuotes(symbol, timeframe).collectList().block();

        log.info("Retrieved {} quotes for backtesting", quotes.size());

        MarketPhaseClassifier classifier = new CombinedMarketPhaseClassifier(List.of(
                        new MovingAverageClassifier(20, 100, 0.01),
                        new VolatilityClassifier(20, 0.015, 0.035))
        );

        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes = new HashMap<>();

        for (int i = 0; i < quotes.size(); i++) {
            MarketPhaseClassifier.MarketPhase phase = classifier.classify(quotes, i);
            marketPhaseQuotes.computeIfAbsent(phase, _ -> new ArrayList<>()).add(quotes.get(i));
        }

        log.info("Market phase breakdown:");
        log.info("Bullish: {} quotes", marketPhaseQuotes.getOrDefault(MarketPhaseClassifier.MarketPhase.BULLISH, List.of()).size());
        log.info("Bearish: {} quotes", marketPhaseQuotes.getOrDefault(MarketPhaseClassifier.MarketPhase.BEARISH, List.of()).size());
        log.info("Sideways: {} quotes", marketPhaseQuotes.getOrDefault(MarketPhaseClassifier.MarketPhase.SIDEWAYS, List.of()).size());

        visualizer.visualizeMarketPhases(marketPhaseQuotes);

        // Now you can backtest your indicators separately on each market phase
//        backTestIndicatorByMarketPhase(quotes, marketPhaseQuotes);

        Thread.sleep(1000000000);
    }

    private void backTestIndicatorByMarketPhase(List<Quote> allQuotes, Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes) {
        // Example: Test McGinley Dynamic
        McGinleyDynamic mcginley = new McGinleyDynamic(14);

        // Test on bullish phase
        List<Quote> bullishQuotes = marketPhaseQuotes.get(MarketPhaseClassifier.MarketPhase.BULLISH);
        if (bullishQuotes != null && !bullishQuotes.isEmpty()) {
            log.info("Testing McGinley on bullish market");
            // Run your backtesting logic here
        }

        // Similarly test on bearish and sideways phases
        // ...
    }
}

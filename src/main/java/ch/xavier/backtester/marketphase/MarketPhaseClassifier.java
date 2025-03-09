package ch.xavier.backtester.marketphase;

import ch.xavier.backtester.quote.Quote;

import java.util.List;

public interface MarketPhaseClassifier {
    enum MarketPhase {
        BULLISH, BEARISH, SIDEWAYS, UNKNOWN
    }

    MarketPhase classify(List<Quote> quotes, int index);
}

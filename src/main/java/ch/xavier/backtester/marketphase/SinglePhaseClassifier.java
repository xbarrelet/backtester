package ch.xavier.backtester.marketphase;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class SinglePhaseClassifier implements MarketPhaseClassifier {

    private final MarketPhase phase;

    public SinglePhaseClassifier() {
        this(MarketPhase.UNKNOWN); // Default to UNKNOWN
    }

    @Override
    public MarketPhase classify(List<Quote> quotes, int index) {
        return phase;
    }
}
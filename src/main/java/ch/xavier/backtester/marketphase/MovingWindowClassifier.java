package ch.xavier.backtester.marketphase;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class MovingWindowClassifier implements MarketPhaseClassifier {
    private final MarketPhaseClassifier baseClassifier;
    private final int windowSize;
    private final double threshold;

    @Override
    public MarketPhase classify(List<Quote> quotes, int index) {
        if (index < windowSize) return MarketPhase.UNKNOWN;

        Map<MarketPhase, Integer> phaseCounts = new HashMap<>();

        // Collect classifications over the window
        for (int i = 0; i < windowSize; i++) {
            int windowIndex = index - i;
            MarketPhase phase = baseClassifier.classify(quotes, windowIndex);

            if (phase != MarketPhase.UNKNOWN) {
                phaseCounts.put(phase, phaseCounts.getOrDefault(phase, 0) + 1);
            }
        }

        // Find dominant phase
        MarketPhase dominantPhase = MarketPhase.UNKNOWN;
        int maxCount = 0;

        for (Map.Entry<MarketPhase, Integer> entry : phaseCounts.entrySet()) {
            double ratio = (double) entry.getValue() / windowSize;

            if (ratio >= threshold && entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominantPhase = entry.getKey();
            }
        }

        return dominantPhase;
    }
}
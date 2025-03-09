package ch.xavier.backtester.marketphase;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class CombinedMarketPhaseClassifier implements MarketPhaseClassifier {
    private final List<MarketPhaseClassifier> classifiers;

    @Override
    public MarketPhase classify(List<Quote> quotes, int index) {
        Map<MarketPhase, Integer> votes = new HashMap<>();

        for (MarketPhaseClassifier classifier : classifiers) {
            MarketPhase phase = classifier.classify(quotes, index);
            votes.put(phase, votes.getOrDefault(phase, 0) + 1);
        }

        // Find phase with most votes
        MarketPhase winner = MarketPhase.UNKNOWN;
        int maxVotes = 0;

        for (Map.Entry<MarketPhase, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                winner = entry.getKey();
            }
        }

        return winner;
    }
}
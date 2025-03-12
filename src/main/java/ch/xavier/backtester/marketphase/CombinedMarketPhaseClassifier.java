package ch.xavier.backtester.marketphase;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;

import java.util.*;

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
        List<MarketPhase> winners = new ArrayList<>();
        int maxVotes = 0;

        for (Map.Entry<MarketPhase, Integer> entry : votes.entrySet()) {
            if (entry.getKey() == MarketPhase.UNKNOWN) continue;

            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                winners.clear();
                winners.add(entry.getKey());
            } else if (entry.getValue() == maxVotes) {
                winners.add(entry.getKey());
            }
        }

        // If no clear winner or tie
        if (winners.isEmpty()) {
            return MarketPhase.UNKNOWN;
        } else if (winners.size() == 1) {
            return winners.get(0);
        } else {
            // Deterministic tiebreaking
            int hash = Objects.hash(quotes.get(index).getTimestamp().getTime());
            return winners.get(Math.abs(hash % winners.size()));
        }
    }
}
package ch.xavier.backtester.marketphase;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class AdxClassifier implements MarketPhaseClassifier {
    private final int period;
    private final double trendThreshold;

    @Override
    public MarketPhase classify(List<Quote> quotes, int index) {
        if (index < period) return MarketPhase.UNKNOWN;

        double adx = calculateADX(quotes, index);
        double plusDI = calculatePlusDI(quotes, index);
        double minusDI = calculateMinusDI(quotes, index);

        if (adx < trendThreshold) {
            return MarketPhase.SIDEWAYS;
        } else if (plusDI > minusDI) {
            return MarketPhase.BULLISH;
        } else {
            return MarketPhase.BEARISH;
        }
    }

    // ADX calculation methods would go here
    private double calculateADX(List<Quote> quotes, int index) {
        // Implementation of ADX calculation
        // This is simplified - real ADX is more complex
        double[] diDifferences = new double[period];
        double[] diSums = new double[period];

        for (int i = 0; i < period; i++) {
            int idx = index - period + 1 + i;
            if (idx <= 0) continue;

            double plusDI = calculatePlusDI(quotes, idx);
            double minusDI = calculateMinusDI(quotes, idx);

            diDifferences[i] = Math.abs(plusDI - minusDI);
            diSums[i] = plusDI + minusDI;
        }

        double dx = 0;
        for (int i = 0; i < period; i++) {
            if (diSums[i] > 0) {
                dx += (diDifferences[i] / diSums[i]) * 100.0;
            }
        }

        return dx / period;
    }

    private double calculatePlusDI(List<Quote> quotes, int index) {
        // Implementation here
        return 0; // Placeholder
    }

    private double calculateMinusDI(List<Quote> quotes, int index) {
        // Implementation here
        return 0; // Placeholder
    }
}
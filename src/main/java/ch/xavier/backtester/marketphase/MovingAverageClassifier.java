package ch.xavier.backtester.marketphase;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class MovingAverageClassifier implements MarketPhaseClassifier {
    private final int shortPeriod;
    private final int longPeriod;
    private final double sidewaysThreshold;

    @Override
    public MarketPhase classify(List<Quote> quotes, int index) {
        if (index < longPeriod) return MarketPhase.UNKNOWN;

        double shortMA = calculateSMA(quotes, index, shortPeriod);
        double longMA = calculateSMA(quotes, index, longPeriod);
        double maSlope = calculateSlope(quotes, index, longPeriod);

        // Calculate percentage difference
        double maDiff = Math.abs((shortMA - longMA) / longMA);

        if (maDiff < sidewaysThreshold) {
            return MarketPhase.SIDEWAYS;
        } else if (shortMA > longMA && maSlope > 0) {
            return MarketPhase.BULLISH;
        } else {
            return MarketPhase.BEARISH;
        }
    }

    private double calculateSMA(List<Quote> quotes, int index, int period) {
        double sum = 0;
        for (int i = Math.max(0, index - period + 1); i <= index; i++) {
            sum += quotes.get(i).getClose();
        }
        return sum / period;
    }

    private double calculateSlope(List<Quote> quotes, int index, int period) {
        // Linear regression slope over the period
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = period;

        for (int i = 0; i < n; i++) {
            int idx = index - n + 1 + i;
            if (idx < 0) continue;

            sumX += i;
            sumY += quotes.get(idx).getClose();
            sumXY += i * quotes.get(idx).getClose();
            sumX2 += i * i;
        }

        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }
}
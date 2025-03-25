package ch.xavier.backtester.marketphase;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.List;

@AllArgsConstructor
public class TrendClassifier implements MarketPhaseClassifier {
    private final int period;

    @Override
    public MarketPhase classify(List<Quote> quotes, int index) {
        if (index < period) return MarketPhase.UNKNOWN;

        double slope = calculateLinearRegressionSlope(quotes, index, period);
        double r2 = calculateR2(quotes, index, period);

        if (Math.abs(slope) < 0.0001 || r2 < 0.3) {
            return MarketPhase.SIDEWAYS;
        } else if (slope > 0) {
            return MarketPhase.BULLISH;
        } else {
            return MarketPhase.BEARISH;
        }
    }

    private double calculateLinearRegressionSlope(List<Quote> quotes, int index, int period) {
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = Math.min(period, index + 1);

        for (int i = 0; i < n; i++) {
            int idx = index - i;
            double x = i;
            double y = quotes.get(idx).getClose();

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        // Calculate slope: (n*sumXY - sumX*sumY) / (n*sumX2 - sumX*sumX)
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    private double calculateR2(List<Quote> quotes, int index, int period) {
        int n = Math.min(period, index + 1);
        double[] x = new double[n];
        double[] y = new double[n];

        // Fill arrays with data
        for (int i = 0; i < n; i++) {
            x[i] = i;
            y[i] = quotes.get(index - i).getClose();
        }

        // Calculate means
        double xMean = Arrays.stream(x).average().orElse(0);
        double yMean = Arrays.stream(y).average().orElse(0);

        // Calculate R²
        double numerator = 0;
        double denominatorX = 0;
        double denominatorY = 0;

        for (int i = 0; i < n; i++) {
            double xDiff = x[i] - xMean;
            double yDiff = y[i] - yMean;
            numerator += xDiff * yDiff;
            denominatorX += xDiff * xDiff;
            denominatorY += yDiff * yDiff;
        }

        double correlation = numerator / (Math.sqrt(denominatorX) * Math.sqrt(denominatorY));
        return correlation * correlation; // R² is the square of correlation
    }
}
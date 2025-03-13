package ch.xavier.backtester.indicator.confirmation;

import ch.xavier.backtester.indicator.Indicator;
import ch.xavier.backtester.quote.Quote;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@Setter
@Getter
public class TTMSqueeze implements Indicator {
    private int length = 20;
    private double bbMult = 2.0;
    private double kcMult = 2.0;
    private boolean requireCrossing = true;
    private boolean useGreenRedConfirmation = true;
    private boolean inverseSignals = false;
    private boolean highlightNoSqueeze = true;  // Added missing parameter

    private TTMSqueezeResult previousResult;

    @Override
    public double calculate(int index, List<Quote> quotes) {
        TTMSqueezeResult result = calculateResult(index, quotes);
        previousResult = result;  // Store for next calculation
        return result.getMomentum();
    }

    public TTMSqueezeResult calculateResult(int index, List<Quote> quotes) {
        if (index < length) {
            return new TTMSqueezeResult(false, 0, 0);
        }

        // Calculate basis and bands
        double basis = calculateSMA(quotes, index, length);
        double dev = calculateStdDev(quotes, index, length, basis);

        double bbUpper = basis + bbMult * dev;
        double bbLower = basis - bbMult * dev;

        double kcDev = calculateTrSMA(quotes, index, length);
        double kcUpper = basis + kcDev * kcMult;
        double kcLower = basis - kcDev * kcMult;

        // Simplified to just one squeeze condition
        boolean noSqueeze = bbLower < kcLower || bbUpper > kcUpper;

        // Price average and momentum calculations
        double highest = calculateHighest(quotes, index, length);
        double lowest = calculateLowest(quotes, index, length);
        double priceAvg = (((highest + lowest) / 2) + basis) / 2;

        double momentum = calculateLinearRegression(quotes, index, length, priceAvg);

        return new TTMSqueezeResult(noSqueeze, momentum, basis);
    }

    // Signal generation logic
    public boolean isLongSignal(int index, List<Quote> quotes) {
        if (index < length) return false;

        TTMSqueezeResult result = calculateResult(index, quotes);
        boolean momRising = result.getMomentum() > (previousResult != null ? previousResult.getMomentum() : 0);

        // Basic signal determination
        boolean basicLong = useGreenRedConfirmation  // Fixed variable name
                ? (result.getMomentum() > 0 && momRising)
                : result.getMomentum() > 0;

        // Apply squeeze condition if highlighted
        boolean longSignal = highlightNoSqueeze
                ? result.isNoSqueeze() && basicLong
                : basicLong;

        // Handle crossing check
        boolean finalLongSignal = longSignal;

        if (requireCrossing && index > length) {
            // Store current result
            TTMSqueezeResult currentResult = result;

            // Get previous state
            TTMSqueezeResult prevResult = calculateResult(index - 1, quotes);
            boolean prevMomRising = prevResult.getMomentum() >
                    (index > length + 1 ? calculateResult(index - 2, quotes).getMomentum() : 0);

            boolean prevBasicLong = useGreenRedConfirmation  // Fixed variable name
                    ? (prevResult.getMomentum() > 0 && prevMomRising)
                    : prevResult.getMomentum() > 0;

            boolean prevLongSignal = highlightNoSqueeze
                    ? prevResult.isNoSqueeze() && prevBasicLong
                    : prevBasicLong;

            // Signal only on crossing
            finalLongSignal = !prevLongSignal && longSignal;

            // Restore current result for next call
            previousResult = currentResult;
        }

        // Apply inverse if needed
        return inverseSignals ? isShortSignal(index, quotes) : finalLongSignal;
    }

    public boolean isShortSignal(int index, List<Quote> quotes) {
        if (index < length) return false;

        TTMSqueezeResult result = calculateResult(index, quotes);
        boolean momFalling = result.getMomentum() < (previousResult != null ? previousResult.getMomentum() : 0);

        // Basic signal determination
        boolean basicShort = useGreenRedConfirmation  // Fixed variable name
                ? (result.getMomentum() < 0 && momFalling)
                : result.getMomentum() < 0;

        // Apply squeeze condition if highlighted
        boolean shortSignal = highlightNoSqueeze
                ? result.isNoSqueeze() && basicShort
                : basicShort;

        // Handle crossing check
        boolean finalShortSignal = shortSignal;

        if (requireCrossing && index > length) {
            // Store current result
            TTMSqueezeResult currentResult = result;

            // Get previous state
            TTMSqueezeResult prevResult = calculateResult(index - 1, quotes);
            boolean prevMomFalling = prevResult.getMomentum() <
                    (index > length + 1 ? calculateResult(index - 2, quotes).getMomentum() : 0);

            boolean prevBasicShort = useGreenRedConfirmation  // Fixed variable name
                    ? (prevResult.getMomentum() < 0 && prevMomFalling)
                    : prevResult.getMomentum() < 0;

            boolean prevShortSignal = highlightNoSqueeze
                    ? prevResult.isNoSqueeze() && prevBasicShort
                    : prevBasicShort;

            // Signal only on crossing
            finalShortSignal = !prevShortSignal && shortSignal;

            // Restore current result for next call
            previousResult = currentResult;
        }

        // Apply inverse if needed
        return inverseSignals ? isLongSignal(index, quotes) : finalShortSignal;
    }

    // Helper calculation methods
    private double calculateSMA(List<Quote> quotes, int index, int period) {
        double sum = 0;
        for (int i = Math.max(0, index - period + 1); i <= index; i++) {
            sum += quotes.get(i).getClose().doubleValue();
        }
        return sum / Math.min(period, index + 1);
    }

    private double calculateStdDev(List<Quote> quotes, int index, int period, double mean) {
        double sum = 0;
        for (int i = Math.max(0, index - period + 1); i <= index; i++) {
            double diff = quotes.get(i).getClose().doubleValue() - mean;
            sum += diff * diff;
        }
        return Math.sqrt(sum / Math.min(period, index + 1));
    }

    private double calculateTrSMA(List<Quote> quotes, int index, int period) {
        double sum = 0;
        for (int i = Math.max(1, index - period + 1); i <= index; i++) {
            sum += calculateTR(quotes, i);
        }
        return sum / Math.min(period, index + 1);
    }

    private double calculateTR(List<Quote> quotes, int index) {
        if (index == 0) return quotes.get(0).getHigh().doubleValue() - quotes.get(0).getLow().doubleValue();

        double highLow = quotes.get(index).getHigh().doubleValue() - quotes.get(index).getLow().doubleValue();
        double highClosePrev = Math.abs(quotes.get(index).getHigh().doubleValue() - quotes.get(index - 1).getClose().doubleValue());
        double lowClosePrev = Math.abs(quotes.get(index).getLow().doubleValue() - quotes.get(index - 1).getClose().doubleValue());

        return Math.max(Math.max(highLow, highClosePrev), lowClosePrev);
    }

    private double calculateHighest(List<Quote> quotes, int index, int period) {
        double highest = Double.MIN_VALUE;
        for (int i = Math.max(0, index - period + 1); i <= index; i++) {
            highest = Math.max(highest, quotes.get(i).getHigh().doubleValue());
        }
        return highest;
    }

    private double calculateLowest(List<Quote> quotes, int index, int period) {
        double lowest = Double.MAX_VALUE;
        for (int i = Math.max(0, index - period + 1); i <= index; i++) {
            lowest = Math.min(lowest, quotes.get(i).getLow().doubleValue());
        }
        return lowest;
    }

    private double calculateLinearRegression(List<Quote> quotes, int index, int period, double priceAvg) {
        double[] y = new double[period];
        double[] x = new double[period];

        for (int i = 0; i < period; i++) {
            int idx = index - period + 1 + i;
            if (idx < 0) continue;

            x[i] = i;
            y[i] = quotes.get(idx).getClose().doubleValue() - priceAvg;
        }

        return linearRegression(x, y);
    }

    private double linearRegression(double[] x, double[] y) {
        int n = x.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
        }

        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    @Getter
    public static class TTMSqueezeResult {
        private final boolean noSqueeze;
        private final double momentum;
        private final double basis;

        public TTMSqueezeResult(boolean noSqueeze, double momentum, double basis) {
            this.noSqueeze = noSqueeze;
            this.momentum = momentum;
            this.basis = basis;
        }
    }
}
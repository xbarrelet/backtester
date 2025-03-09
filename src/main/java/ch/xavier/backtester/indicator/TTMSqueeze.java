package ch.xavier.backtester.indicator;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@AllArgsConstructor
public class TTMSqueeze {
    private final int length;
    private final double bbMult;
    private final double kcMult1;
    private final double kcMult2;
    private final double kcMult3;

    public TTMSqueezeResult calculate(int index, List<Quote> quotes) {
        if (index < length) {
            return new TTMSqueezeResult(false, false, false, false, 0, 0);
        }

        // Calculate basis (SMA of close)
        double basis = calculateSMA(quotes, index);

        // Standard deviation for BB
        double dev = calculateStdev(quotes, index, basis);

        // Bollinger Bands
        double bbUpper = basis + bbMult * dev;
        double bbLower = basis - bbMult * dev;

        // Average True Range for KC
        double atr = calculateATR(quotes, index);

        // Keltner Channels
        double kc1Upper = basis + kcMult1 * atr;
        double kc1Lower = basis - kcMult1 * atr;
        double kc2Upper = basis + kcMult2 * atr;
        double kc2Lower = basis - kcMult2 * atr;
        double kc3Upper = basis + kcMult3 * atr;
        double kc3Lower = basis - kcMult3 * atr;

        // Squeeze conditions
        boolean noSqueeze = bbLower < kc3Lower || bbUpper > kc3Upper;
        boolean lowSqueeze = bbLower >= kc3Lower || bbUpper <= kc3Upper;
        boolean midSqueeze = bbLower >= kc2Lower || bbUpper <= kc2Upper;
        boolean highSqueeze = bbLower >= kc1Lower || bbUpper <= kc1Upper;

        // Calculate momentum
        double highest = calculateHighest(quotes, index);
        double lowest = calculateLowest(quotes, index);
        double priceAvg = (highest + lowest + basis) / 3;
        double momentum = linearRegression(quotes, index, priceAvg);

        return new TTMSqueezeResult(noSqueeze, lowSqueeze, midSqueeze,
                highSqueeze, momentum, basis);
    }

    private double calculateSMA(List<Quote> quotes, int index) {
        double sum = 0;
        for (int i = Math.max(0, index - length + 1); i <= index; i++) {
            sum += quotes.get(i).getClose().doubleValue();
        }
        return sum / length;
    }

    private double calculateStdev(List<Quote> quotes, int index, double mean) {
        double sum = 0;
        for (int i = Math.max(0, index - length + 1); i <= index; i++) {
            double diff = quotes.get(i).getClose().doubleValue() - mean;
            sum += diff * diff;
        }
        return Math.sqrt(sum / length);
    }

    private double calculateATR(List<Quote> quotes, int index) {
        double sum = 0;
        for (int i = Math.max(1, index - length + 1); i <= index; i++) {
            sum += tr(quotes, i);
        }
        return sum / length;
    }

    private double tr(List<Quote> quotes, int index) {
        double high = quotes.get(index).getHigh().doubleValue();
        double low = quotes.get(index).getLow().doubleValue();
        double prevClose = quotes.get(index - 1).getClose().doubleValue();

        return Math.max(high - low,
                Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
    }

    private double calculateHighest(List<Quote> quotes, int index) {
        double highest = Double.MIN_VALUE;
        for (int i = Math.max(0, index - length + 1); i <= index; i++) {
            highest = Math.max(highest, quotes.get(i).getHigh().doubleValue());
        }
        return highest;
    }

    private double calculateLowest(List<Quote> quotes, int index) {
        double lowest = Double.MAX_VALUE;
        for (int i = Math.max(0, index - length + 1); i <= index; i++) {
            lowest = Math.min(lowest, quotes.get(i).getLow().doubleValue());
        }
        return lowest;
    }

    private double linearRegression(List<Quote> quotes, int index, double priceAvg) {
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = length;

        for (int i = 0; i < n; i++) {
            int idx = index - n + 1 + i;
            if (idx < 0) continue;

            double y = quotes.get(idx).getClose().doubleValue() - priceAvg;
            double x = i;

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        return intercept + slope * (n - 1);
    }

    @Getter
    public static class TTMSqueezeResult {
        private final boolean noSqueeze;
        private final boolean lowSqueeze;
        private final boolean midSqueeze;
        private final boolean highSqueeze;
        private final double momentum;
        private final double basis;

        public TTMSqueezeResult(boolean noSqueeze, boolean lowSqueeze,
                                boolean midSqueeze, boolean highSqueeze,
                                double momentum, double basis) {
            this.noSqueeze = noSqueeze;
            this.lowSqueeze = lowSqueeze;
            this.midSqueeze = midSqueeze;
            this.highSqueeze = highSqueeze;
            this.momentum = momentum;
            this.basis = basis;
        }
    }
}
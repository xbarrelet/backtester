package ch.xavier.backtester.indicator;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
public class VortexIndicator implements Indicator {
    private final int length;

    @Override
    public double calculate(int index, List<Quote> quotes) {
        VortexResult result = calculateResult(index, quotes);
        return result.getVortexPos() - result.getVortexNeg();
    }

    public VortexResult calculateResult(int index, List<Quote> quotes) {
        if (index < length) {
            return new VortexResult(0, 0);
        }

        double vortexPlus = 0;
        double vortexMinus = 0;
        double vortexSum = 0;

        for (int i = Math.max(1, index - length + 1); i <= index; i++) {
            double high = quotes.get(i).getHigh().doubleValue();
            double low = quotes.get(i).getLow().doubleValue();
            double prevHigh = quotes.get(i-1).getHigh().doubleValue();
            double prevLow = quotes.get(i-1).getLow().doubleValue();

            vortexPlus += Math.abs(high - prevLow);
            vortexMinus += Math.abs(low - prevHigh);
            vortexSum += calculateTR(quotes, i);
        }

        double vortexPos = vortexPlus / vortexSum;
        double vortexNeg = vortexMinus / vortexSum;

        return new VortexResult(vortexPos, vortexNeg);
    }

    private double calculateTR(List<Quote> quotes, int index) {
        if (index <= 0) return 0;

        double high = quotes.get(index).getHigh().doubleValue();
        double low = quotes.get(index).getLow().doubleValue();
        double prevClose = quotes.get(index-1).getClose().doubleValue();

        return Math.max(high - low,
                Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
    }

    @Getter
    public static class VortexResult {
        private final double vortexPos;
        private final double vortexNeg;

        public VortexResult(double vortexPos, double vortexNeg) {
            this.vortexPos = vortexPos;
            this.vortexNeg = vortexNeg;
        }
    }
}
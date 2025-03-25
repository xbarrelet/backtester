package ch.xavier.backtester.indicator.baseline;

import ch.xavier.backtester.indicator.Indicator;
import ch.xavier.backtester.quote.Quote;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@Setter
@Getter
public class QuantumAdaptiveMA implements Indicator {
    private int adxLength = 2;
    private double weightFactor = 10.0;
    private int maLength = 6;

    @Override
    public double calculate(int index, List<Quote> quotes) {
        if (index <= 0) {
            return quotes.get(index).getClose();
        }

        double[] powerBulls = new double[index + 1];
        double[] powerBears = new double[index + 1];
        double[] strRange = new double[index + 1];
        double[] diagX = new double[index + 1];
        double[] varMA = new double[index + 1];

        // Initialize first values
        powerBulls[0] = 0.0;
        powerBears[0] = 0.0;
        strRange[0] = quotes.getFirst().getHigh() - quotes.getFirst().getLow();
        diagX[0] = 0.0;
        varMA[0] = quotes.getFirst().getClose();

        for (int i = 1; i <= index; i++) {
            Quote current = quotes.get(i);
            Quote previous = quotes.get(i - 1);

            double hi = current.getHigh();
            double hi1 = previous.getHigh();
            double lo = current.getLow();
            double lo1 = previous.getLow();
            double close = current.getClose();
            double close1 = previous.getClose();

            double bulls1 = 0.5 * (Math.abs(hi - hi1) + (hi - hi1));
            double bears1 = 0.5 * (Math.abs(lo1 - lo) + (lo1 - lo));

            double bears = bulls1 > bears1 ? 0 : bulls1 == bears1 ? 0 : bears1;
            double bulls = bulls1 < bears1 ? 0 : bulls1 == bears1 ? 0 : bulls1;

            powerBulls[i] = (weightFactor * powerBulls[i - 1] + bulls) / (weightFactor + 1);
            powerBears[i] = (weightFactor * powerBears[i - 1] + bears) / (weightFactor + 1);

            double trueRange = Math.max(hi - lo, Math.max(Math.abs(hi - close1), Math.abs(lo - close1)));
            strRange[i] = (weightFactor * strRange[i - 1] + trueRange) / (weightFactor + 1);

            double posDI = strRange[i] > 0 ? powerBulls[i] / strRange[i] : 0;
            double negDI = strRange[i] > 0 ? powerBears[i] / strRange[i] : 0;
            double diDiff = posDI + negDI > 0 ? Math.abs(posDI - negDI) / (posDI + negDI) : 0;

            diagX[i] = (weightFactor * diagX[i - 1] + diDiff) / (weightFactor + 1);

            // Find lowest and highest ADX values in the specified length
            double adxLow = Double.MAX_VALUE;
            double adxHigh = Double.MIN_VALUE;

            for (int j = Math.max(0, i - adxLength + 1); j <= i; j++) {
                adxLow = Math.min(adxLow, diagX[j]);
                adxHigh = Math.max(adxHigh, diagX[j]);
            }

            double adxMin = Math.min(1000000.0, adxLow);
            double adxMax = Math.max(-1.0, adxHigh);
            double adxDiff = adxMax - adxMin;

            double adxConstant = adxDiff > 0 ? (diagX[i] - adxMin) / adxDiff : 0;

            varMA[i] = ((2 - adxConstant) * varMA[i - 1] + adxConstant * close) / 2;
        }

        // Calculate SMA of varMA for final result
        double sum = 0;
        int count = 0;
        for (int i = Math.max(0, index - maLength + 1); i <= index; i++) {
            sum += varMA[i];
            count++;
        }

        return sum / count;
    }
}
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
public class WavePulseIndicator implements Indicator {
    private int smoothingPeriod = 21;
    private double constantFactor = 0.4;
    private boolean useWavePulse = true;
    private boolean crossConfirmation = true;
    private boolean inverseSignals = false;
    private boolean useColorSignals = true;

    @Override
    public double calculate(int index, List<Quote> quotes) {
        if (index <= 0) {
            return quotes.get(index).getClose();
        }

        // Calculate coefficients
        double diVal = (smoothingPeriod - 1.0) / 2.0 + 1.0;
        double c1Val = 2.0 / (diVal + 1.0);
        double c2Val = 1.0 - c1Val;
        double c3Val = 3.0 * (constantFactor * constantFactor + constantFactor * constantFactor * constantFactor);
        double c4Val = -3.0 * (2.0 * constantFactor * constantFactor + constantFactor + constantFactor * constantFactor * constantFactor);
        double c5Val = 3.0 * constantFactor + 1.0 + constantFactor * constantFactor * constantFactor + 3.0 * constantFactor * constantFactor;

        // We need arrays to store the intermediate values for each calculation step
        double[] i1Vals = new double[index + 1];
        double[] i2Vals = new double[index + 1];
        double[] i3Vals = new double[index + 1];
        double[] i4Vals = new double[index + 1];
        double[] i5Vals = new double[index + 1];
        double[] i6Vals = new double[index + 1];

        // Initialize with first value
        i1Vals[0] = quotes.getFirst().getClose();
        i2Vals[0] = i1Vals[0];
        i3Vals[0] = i2Vals[0];
        i4Vals[0] = i3Vals[0];
        i5Vals[0] = i4Vals[0];
        i6Vals[0] = i5Vals[0];

        // Calculate intermediate values for each position
        for (int i = 1; i <= index; i++) {
            double close = quotes.get(i).getClose();

            i1Vals[i] = c1Val * close + c2Val * i1Vals[i - 1];
            i2Vals[i] = c1Val * i1Vals[i] + c2Val * i2Vals[i - 1];
            i3Vals[i] = c1Val * i2Vals[i] + c2Val * i3Vals[i - 1];
            i4Vals[i] = c1Val * i3Vals[i] + c2Val * i4Vals[i - 1];
            i5Vals[i] = c1Val * i4Vals[i] + c2Val * i5Vals[i - 1];
            i6Vals[i] = c1Val * i5Vals[i] + c2Val * i6Vals[i - 1];
        }

        // Calculate final wave value
        return -constantFactor * constantFactor * constantFactor * i6Vals[index] +
                c3Val * i5Vals[index] +
                c4Val * i4Vals[index] +
                c5Val * i3Vals[index];
    }

    /**
     * Returns the trend signal based on the current and previous values
     * 1 for bullish, -1 for bearish
     */
    public int getTrendSignal(int index, List<Quote> quotes) {
        if (index <= 0) {
            return 0;
        }

        double currentValue = calculate(index, quotes);
        double previousValue = calculate(index - 1, quotes);

        return currentValue > previousValue ? 1 : -1;
    }

    private SignalResult calculateSignals(int index, List<Quote> quotes) {
        // Calculate both signals in one pass
        if (index <= 0) {
            return new SignalResult(false, false);
        }

        // Calculate basic signal conditions
        double wavePulseValue = calculate(index, quotes);
        double close = quotes.get(index).getClose();
        int wavePulseSignal = getTrendSignal(index, quotes);

        boolean wavePulseLongBasic = wavePulseSignal > 0 && close > wavePulseValue;
        boolean wavePulseShortBasic = wavePulseSignal < 0 && close < wavePulseValue;

        // Apply configuration
        boolean wavePulseLong = useWavePulse ?
                useColorSignals ? wavePulseLongBasic : close > wavePulseValue : true;
        boolean wavePulseShort = useWavePulse ?
                useColorSignals ? wavePulseShortBasic : close < wavePulseValue : true;

        // Apply cross confirmation
        if (crossConfirmation && index > 0) {
            // Calculate previous signals
            SignalResult prev = calculateBasicSignals(index - 1, quotes);
            wavePulseLong = !prev.longSignal && wavePulseLong;
            wavePulseShort = !prev.shortSignal && wavePulseShort;
        }

        // Apply inverse logic
        if (inverseSignals) {
            boolean temp = wavePulseLong;
            wavePulseLong = wavePulseShort;
            wavePulseShort = temp;
        }

        return new SignalResult(wavePulseLong, wavePulseShort);
    }

    private SignalResult calculateBasicSignals(int index, List<Quote> quotes) {
        // Simplified version without cross or inverse logic
        if (index < 0) return new SignalResult(false, false);

        double value = calculate(index, quotes);
        double close = quotes.get(index).getClose();
        int signal = getTrendSignal(index, quotes);

        boolean longSignal = useWavePulse ?
                useColorSignals ? (signal > 0 && close > value) : close > value : true;
        boolean shortSignal = useWavePulse ?
                useColorSignals ? (signal < 0 && close < value) : close < value : true;

        return new SignalResult(longSignal, shortSignal);
    }

    private static class SignalResult {
        final boolean longSignal;
        final boolean shortSignal;

        SignalResult(boolean longSignal, boolean shortSignal) {
            this.longSignal = longSignal;
            this.shortSignal = shortSignal;
        }
    }

    public boolean isLongSignal(int index, List<Quote> quotes) {
        return calculateSignals(index, quotes).longSignal;
    }

    public boolean isShortSignal(int index, List<Quote> quotes) {
        return calculateSignals(index, quotes).shortSignal;
    }
}
package ch.xavier.backtester.strategy.concrete;

import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.indicator.misc.MACD;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.strategy.BaseStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class MacdDivergenceStrategy extends BaseStrategy {
    private boolean initialized = false;

    // MACD parameters
    private int fastLength = 12;
    private int slowLength = 26;
    private int signalLength = 9;
    private MACD macd;

    // Divergence detection parameters
    private int pivotLookbackLeft = 5;
    private int pivotLookbackRight = 5;
    private int rangeLower = 5;
    private int rangeUpper = 60;
    private boolean dontTouchZero = true;

    public MacdDivergenceStrategy(TradingParameters parameters) {
        super(parameters);
    }

    @Override
    public int generateSignal(List<Quote> quotes, int index) {
        if (!initialized && quotes != null && !quotes.isEmpty()) {
            macd = new MACD(fastLength, slowLength, signalLength);
            macd.calculate(quotes);
            initialized = true;
        }

        if (!initialized ||
                index < pivotLookbackLeft + pivotLookbackRight + slowLength ||
                index >= quotes.size()) {
            return 0;
        }

        // Check for bullish (long) signal
        if (detectBullishDivergence(quotes, index)) {
            return 1;
        }

        // Check for bearish (short) signal if we're not only doing long trades
        if (detectBearishDivergence(quotes, index)) {
            return -1;
        }

        return 0;
    }

    // Private methods for divergence detection
    private boolean detectBullishDivergence(List<Quote> quotes, int index) {
        // Check for pivot low in oscillator
        Integer pivotLowIndex = findPivotLow(macd.getHistogram(), index - pivotLookbackRight, pivotLookbackLeft, pivotLookbackRight);
        if (pivotLowIndex == null) {
            return false;
        }

        // Find previous pivot low
        Integer prevPivotLowIndex = findPivotLow(macd.getHistogram(), pivotLowIndex - 1, pivotLookbackLeft, pivotLookbackRight);
        if (prevPivotLowIndex == null) {
            return false;
        }

        // Check if we're in range
        int barsSince = pivotLowIndex - prevPivotLowIndex;
        if (barsSince < rangeLower || barsSince > rangeUpper) {
            return false;
        }

        // Oscillator: Higher Low
        double currentOscLow = macd.getHistogram()[pivotLowIndex];
        double prevOscLow = macd.getHistogram()[prevPivotLowIndex];
        boolean oscHigherLow = currentOscLow > prevOscLow && currentOscLow < 0;

        // Price: Lower Low
        double currentPriceLow = quotes.get(pivotLowIndex).getLow();
        double prevPriceLow = quotes.get(prevPivotLowIndex).getLow();
        boolean priceLowerLow = currentPriceLow < prevPriceLow;

        // Check zero line condition
        boolean belowZero = true;
        if (dontTouchZero) {
            double highest = -Double.MAX_VALUE;
            for (int i = Math.max(0, pivotLowIndex - (pivotLookbackLeft + pivotLookbackRight + 5));
                 i <= pivotLowIndex + pivotLookbackRight; i++) {
                if (i < macd.getHistogram().length) {
                    highest = Math.max(highest, macd.getHistogram()[i]);
                }
            }
            belowZero = highest < 0;
        }

        return oscHigherLow && priceLowerLow && belowZero;
    }

    private boolean detectBearishDivergence(List<Quote> quotes, int index) {
        // Check for pivot high in oscillator
        Integer pivotHighIndex = findPivotHigh(macd.getHistogram(), index - pivotLookbackRight, pivotLookbackLeft, pivotLookbackRight);
        if (pivotHighIndex == null) {
            return false;
        }

        // Find previous pivot high
        Integer prevPivotHighIndex = findPivotHigh(macd.getHistogram(), pivotHighIndex - 1, pivotLookbackLeft, pivotLookbackRight);
        if (prevPivotHighIndex == null) {
            return false;
        }

        // Check if we're in range
        int barsSince = pivotHighIndex - prevPivotHighIndex;
        if (barsSince < rangeLower || barsSince > rangeUpper) {
            return false;
        }

        // Oscillator: Lower High
        double currentOscHigh = macd.getHistogram()[pivotHighIndex];
        double prevOscHigh = macd.getHistogram()[prevPivotHighIndex];
        boolean oscLowerHigh = currentOscHigh < prevOscHigh && currentOscHigh > 0;

        // Price: Higher High
        double currentPriceHigh = quotes.get(pivotHighIndex).getHigh();
        double prevPriceHigh = quotes.get(prevPivotHighIndex).getHigh();
        boolean priceHigherHigh = currentPriceHigh > prevPriceHigh;

        // Check zero line condition
        boolean aboveZero = true;
        if (dontTouchZero) {
            double lowest = Double.MAX_VALUE;
            for (int i = Math.max(0, pivotHighIndex - (pivotLookbackLeft + pivotLookbackRight + 5));
                 i <= pivotHighIndex + pivotLookbackRight; i++) {
                if (i < macd.getHistogram().length) {
                    lowest = Math.min(lowest, macd.getHistogram()[i]);
                }
            }
            aboveZero = lowest > 0;
        }

        return oscLowerHigh && priceHigherHigh && aboveZero;
    }

    private Integer findPivotLow(double[] values, int startIndex, int lookbackLeft, int lookbackRight) {
        if (startIndex < lookbackLeft || startIndex >= values.length - lookbackRight) {
            return null;
        }

        for (int i = startIndex; i >= lookbackLeft; i--) {
            boolean isPivotLow = true;
            double currentValue = values[i];

            // Check left side
            for (int left = 1; left <= lookbackLeft; left++) {
                if (i - left >= 0 && values[i - left] < currentValue) {
                    isPivotLow = false;
                    break;
                }
            }

            // Check right side
            if (isPivotLow) {
                for (int right = 1; right <= lookbackRight; right++) {
                    if (i + right < values.length && values[i + right] < currentValue) {
                        isPivotLow = false;
                        break;
                    }
                }
            }

            if (isPivotLow) {
                return i;
            }
        }

        return null;
    }

    private Integer findPivotHigh(double[] values, int startIndex, int lookbackLeft, int lookbackRight) {
        if (startIndex < lookbackLeft || startIndex >= values.length - lookbackRight) {
            return null;
        }

        for (int i = startIndex; i >= lookbackLeft; i--) {
            boolean isPivotHigh = true;
            double currentValue = values[i];

            // Check left side
            for (int left = 1; left <= lookbackLeft; left++) {
                if (i - left >= 0 && values[i - left] > currentValue) {
                    isPivotHigh = false;
                    break;
                }
            }

            // Check right side
            if (isPivotHigh) {
                for (int right = 1; right <= lookbackRight; right++) {
                    if (i + right < values.length && values[i + right] > currentValue) {
                        isPivotHigh = false;
                        break;
                    }
                }
            }

            if (isPivotHigh) {
                return i;
            }
        }

        return null;
    }

    @Override
    public void setParameters(Map<String, Object> parameters) {
        super.setParameters(parameters);

        if (parameters.containsKey("fastLength")) {
            this.fastLength = (int) parameters.get("fastLength");
        }
        if (parameters.containsKey("slowLength")) {
            this.slowLength = (int) parameters.get("slowLength");
        }
        if (parameters.containsKey("signalLength")) {
            this.signalLength = (int) parameters.get("signalLength");
        }
        if (parameters.containsKey("pivotLookbackLeft")) {
            this.pivotLookbackLeft = (int) parameters.get("pivotLookbackLeft");
        }
        if (parameters.containsKey("pivotLookbackRight")) {
            this.pivotLookbackRight = (int) parameters.get("pivotLookbackRight");
        }
        if (parameters.containsKey("rangeLower")) {
            this.rangeLower = (int) parameters.get("rangeLower");
        }
        if (parameters.containsKey("rangeUpper")) {
            this.rangeUpper = (int) parameters.get("rangeUpper");
        }
        if (parameters.containsKey("dontTouchZero")) {
            this.dontTouchZero = (boolean) parameters.get("dontTouchZero");
        }

        // Reset initialization flag to force recalculation with new parameters
        initialized = false;
    }
}
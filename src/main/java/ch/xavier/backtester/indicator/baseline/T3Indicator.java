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
public class T3Indicator implements Indicator {
    private int fastLength;
    private int slowLength;
    private double factor;

    @Override
    public double calculate(int index, List<Quote> quotes) {
        // Use fast T3 as the default calculation
        return calculateT3(quotes, index, fastLength);
    }

    /**
     * Calculate T3 value for a specific price series and position
     */
    public double calculateT3(List<Quote> quotes, int position, int length) {
        if (position < length) {
            return 0;
        }

        double[] prices = new double[position + 1];
        for (int i = 0; i <= position; i++) {
            prices[i] = quotes.get(i).getClose();
        }

        // EMA calculations
        double[] ema1 = calculateEMA(prices, length);
        double[] ema2 = calculateEMA(ema1, length);
        double[] ema3 = calculateEMA(ema2, length);
        double[] ema4 = calculateEMA(ema3, length);
        double[] ema5 = calculateEMA(ema4, length);
        double[] ema6 = calculateEMA(ema5, length);

        // T3 coefficients
        double c1 = -factor * factor * factor;
        double c2 = 3 * factor * factor + 3 * factor * factor * factor;
        double c3 = -6 * factor * factor - 3 * factor - 3 * factor * factor * factor;
        double c4 = 1 + 3 * factor + factor * factor * factor + 3 * factor * factor;

        return c1 * ema6[position] + c2 * ema5[position] + c3 * ema4[position] + c4 * ema3[position];
    }

    private double[] calculateEMA(double[] prices, int period) {
        double[] ema = new double[prices.length];
        double multiplier = 2.0 / (period + 1);

        // Initialize with SMA
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += prices[i];
        }
        ema[period - 1] = sum / period;

        // Calculate remaining EMAs
        for (int i = period; i < prices.length; i++) {
            ema[i] = (prices[i] - ema[i - 1]) * multiplier + ema[i - 1];
        }

        return ema;
    }
}
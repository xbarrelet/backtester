package ch.xavier.backtester.indicator.misc;

import ch.xavier.backtester.quote.Quote;
import lombok.Getter;

import java.util.List;

public class Stochastic {
    @Getter
    private double[] k; // %K line (fast)
    @Getter
    private double[] d; // %D line (slow)

    private final int kPeriod;
    private final int dPeriod;
    private final int smoothK;
    private final boolean isFull;

    /**
     * Fast Stochastic constructor
     */
    public Stochastic(int kPeriod, int dPeriod) {
        this(kPeriod, dPeriod, 1, false);
    }

    /**
     * Full constructor with all parameters
     *
     * @param kPeriod The lookback period for calculating %K
     * @param dPeriod The smoothing period for calculating %D
     * @param smoothK The smoothing period for %K (1 for FastStoch, >1 for SlowStoch)
     * @param isFull  True for Full Stochastic, false for Fast
     */
    public Stochastic(int kPeriod, int dPeriod, int smoothK, boolean isFull) {
        this.kPeriod = kPeriod;
        this.dPeriod = dPeriod;
        this.smoothK = smoothK;
        this.isFull = isFull;
    }

    public void calculate(List<Quote> quotes) {
        int size = quotes.size();
        k = new double[size];
        d = new double[size];

        // Step 1: Calculate raw %K for each candle
        double[] rawK = new double[size];

        for (int i = kPeriod - 1; i < size; i++) {
            double highestHigh = Double.MIN_VALUE;
            double lowestLow = Double.MAX_VALUE;

            // Find highest high and lowest low over kPeriod
            for (int j = i - (kPeriod - 1); j <= i; j++) {
                Quote quote = quotes.get(j);
                highestHigh = Math.max(highestHigh, quote.getHigh());
                lowestLow = Math.min(lowestLow, quote.getLow());
            }

            // Calculate Raw %K = (Current Close - Lowest Low) / (Highest High - Lowest Low) * 100
            if (highestHigh != lowestLow) {
                Quote current = quotes.get(i);
                rawK[i] = ((current.getClose() - lowestLow) / (highestHigh - lowestLow)) * 100;
            } else {
                rawK[i] = 50; // If no range, use 50 as default
            }
        }

        // Step 2: Calculate %K (smoothed if needed)
        if (smoothK == 1 && !isFull) {
            // Fast Stochastic uses raw %K values directly
            System.arraycopy(rawK, 0, k, 0, size);
        } else {
            // Slow/Full Stochastic smooths the %K using SMA
            calculateSMA(rawK, k, smoothK);
        }

        // Step 3: Calculate %D (SMA of %K)
        calculateSMA(k, d, dPeriod);
    }

    private void calculateSMA(double[] source, double[] dest, int period) {
        for (int i = 0; i < source.length; i++) {
            if (i < period - 1) {
                dest[i] = 0;
                continue;
            }

            double sum = 0;
            for (int j = i - (period - 1); j <= i; j++) {
                sum += source[j];
            }
            dest[i] = sum / period;
        }
    }
}
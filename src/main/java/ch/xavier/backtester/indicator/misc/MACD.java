package ch.xavier.backtester.indicator.misc;

import ch.xavier.backtester.indicator.Indicator;
import ch.xavier.backtester.quote.Quote;

import java.util.List;

public class MACD implements Indicator {
    private final int fastLength;
    private final int slowLength;
    private final int signalLength;

    private double[] macdLine;
    private double[] signalLine;
    private double[] histogram;

    /**
     * Creates a MACD indicator with specified parameters
     *
     * @param fastLength   The period for the fast EMA
     * @param slowLength   The period for the slow EMA
     * @param signalLength The period for the signal line EMA
     */
    public MACD(int fastLength, int slowLength, int signalLength) {
        this.fastLength = fastLength;
        this.slowLength = slowLength;
        this.signalLength = signalLength;
    }

    /**
     * Calculates MACD components for all quotes at once
     *
     * @param quotes List of quotes to process
     */
    public void calculate(List<Quote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return;
        }

        int size = quotes.size();
        macdLine = new double[size];
        signalLine = new double[size];
        histogram = new double[size];

        // Calculate EMAs
        double[] fastEma = calculateEMA(quotes, fastLength);
        double[] slowEma = calculateEMA(quotes, slowLength);

        // Calculate MACD Line = Fast EMA - Slow EMA
        for (int i = 0; i < size; i++) {
            macdLine[i] = fastEma[i] - slowEma[i];
        }

        // Calculate Signal Line = EMA of MACD Line
        signalLine = calculateEMA(macdLine, signalLength);

        // Calculate Histogram = MACD Line - Signal Line
        for (int i = 0; i < size; i++) {
            histogram[i] = macdLine[i] - signalLine[i];
        }
    }

    /**
     * Calculates EMA for price data
     */
    private double[] calculateEMA(List<Quote> quotes, int period) {
        int size = quotes.size();
        double[] ema = new double[size];

        // Initialize with SMA for first value
        double sum = 0;
        for (int i = 0; i < Math.min(period, size); i++) {
            sum += quotes.get(i).getClose();
        }

        ema[period - 1] = sum / period;

        // Calculate multiplier: (2 / (period + 1))
        double multiplier = 2.0 / (period + 1);

        // Calculate EMA: EMA = Close * multiplier + EMA(previous) * (1 - multiplier)
        for (int i = period; i < size; i++) {
            ema[i] = quotes.get(i).getClose() * multiplier +
                    ema[i - 1] * (1 - multiplier);
        }

        return ema;
    }

    /**
     * Calculates EMA for array data
     */
    private double[] calculateEMA(double[] values, int period) {
        int size = values.length;
        double[] ema = new double[size];

        // Initialize with SMA for first value
        double sum = 0;
        for (int i = 0; i < Math.min(period, size); i++) {
            sum += values[i];
        }

        if (period <= size) {
            ema[period - 1] = sum / period;
        }

        // Calculate multiplier: (2 / (period + 1))
        double multiplier = 2.0 / (period + 1);

        // Calculate EMA: EMA = Value * multiplier + EMA(previous) * (1 - multiplier)
        for (int i = period; i < size; i++) {
            ema[i] = values[i] * multiplier +
                    ema[i - 1] * (1 - multiplier);
        }

        return ema;
    }

    /**
     * Implementation of the Indicator interface
     * Returns the histogram value at the given index
     */
    @Override
    public double calculate(int index, List<Quote> quotes) {
        if (macdLine == null || index < 0 || index >= macdLine.length) {
            calculate(quotes);
        }

        return histogram[index];
    }

    public double[] getMacdLine() {
        return macdLine;
    }

    public double[] getSignalLine() {
        return signalLine;
    }

    public double[] getHistogram() {
        return histogram;
    }
}
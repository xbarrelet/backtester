package ch.xavier.backtester.indicator.misc;

import ch.xavier.backtester.indicator.Indicator;
import ch.xavier.backtester.quote.Quote;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public class EMA implements Indicator {
    private int period;
    private double[] values;

    public EMA(int period) {
        this.period = period;
    }

    @Override
    public double calculate(int index, List<Quote> quotes) {
        if (values != null && index < values.length) {
            return values[index];
        }
        return calculateEMA(quotes, index, period);
    }

    public void calculate(List<Quote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return;
        }

        values = new double[quotes.size()];

        // Initialize with SMA for the first period
        double sum = 0;
        for (int i = 0; i < Math.min(period, quotes.size()); i++) {
            sum += quotes.get(i).getClose();
            if (i == period - 1) {
                values[i] = sum / period;
            } else {
                values[i] = 0; // Not enough data for EMA yet
            }
        }

        // Calculate remaining EMAs
        double multiplier = 2.0 / (period + 1);
        for (int i = period; i < quotes.size(); i++) {
            values[i] = (quotes.get(i).getClose() - values[i - 1]) * multiplier + values[i - 1];
        }
    }

    private double calculateEMA(List<Quote> quotes, int index, int period) {
        if (index < period - 1) {
            return 0; // Not enough data
        }

        // Calculate SMA for first 'period' elements
        double sum = 0;
        for (int i = index - period + 1; i <= index; i++) {
            sum += quotes.get(i).getClose();
        }
        double sma = sum / period;

        if (index == period - 1) {
            return sma;
        }

        // Calculate EMA
        double multiplier = 2.0 / (period + 1);
        double ema = sma;
        for (int i = period; i <= index; i++) {
            ema = (quotes.get(i).getClose() - ema) * multiplier + ema;
        }

        return ema;
    }

    public double getValue(int index) {
        if (values != null && index < values.length && index >= 0) {
            return values[index];
        }
        return 0;
    }
}
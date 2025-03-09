package ch.xavier.backtester.indicator;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class McGinleyDynamic implements Indicator {
    private final int length;

    @Override
    public double calculate(int index, List<Quote> quotes) {
        if (index < length) {
            return calculateEMA(quotes, index);
        }

        double prevMG = calculate(index - 1, quotes);
        double close = quotes.get(index).getClose().doubleValue();

        return prevMG + (close - prevMG) / (length * Math.pow(close / prevMG, 4));
    }

    private double calculateEMA(List<Quote> quotes, int index) {
        double sum = 0;
        for (int i = 0; i <= Math.min(index, length - 1); i++) {
            sum += quotes.get(i).getClose().doubleValue();
        }
        return sum / Math.min(index + 1, length);
    }
}
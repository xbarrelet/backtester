package ch.xavier.backtester.indicator;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class WhiteLine implements Indicator {
    private final int length;

    @Override
    public double calculate(int index, List<Quote> quotes) {
        if (index < length - 1) {
            return (quotes.get(index).getHigh().doubleValue() +
                    quotes.get(index).getLow().doubleValue()) / 2;
        }

        double highest = Double.MIN_VALUE;
        double lowest = Double.MAX_VALUE;

        for (int i = Math.max(0, index - length + 1); i <= index; i++) {
            highest = Math.max(highest, quotes.get(i).getHigh().doubleValue());
            lowest = Math.min(lowest, quotes.get(i).getLow().doubleValue());
        }

        return (highest + lowest) / 2;
    }
}
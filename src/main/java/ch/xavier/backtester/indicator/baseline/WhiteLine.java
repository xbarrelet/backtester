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
public class WhiteLine implements Indicator {
    private int length;

    @Override
    public double calculate(int index, List<Quote> quotes) {
        if (index < length - 1) {
            return (quotes.get(index).getHigh() +
                    quotes.get(index).getLow()) / 2;
        }

        double highest = Double.MIN_VALUE;
        double lowest = Double.MAX_VALUE;

        for (int i = Math.max(0, index - length + 1); i <= index; i++) {
            highest = Math.max(highest, quotes.get(i).getHigh());
            lowest = Math.min(lowest, quotes.get(i).getLow());
        }

        return (highest + lowest) / 2;
    }
}
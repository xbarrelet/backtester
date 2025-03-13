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
public class McGinley implements Indicator {
    private int length;

    @Override
    public double calculate(int index, List<Quote> quotes) {
        if (index < 0) return 0;

        // Initialize with simple average for first point
        double mg = calculateEMA(quotes, Math.min(index, length-1));

        // Iteratively calculate McGinley Dynamic values
        for (int i = length; i <= index; i++) {
            double close = quotes.get(i).getClose().doubleValue();
            mg = mg + (close - mg) / (length * Math.pow(close / mg, 4));
        }

        return mg;
    }

    private double calculateEMA(List<Quote> quotes, int index) {
        double sum = 0;
        for (int i = 0; i <= index; i++) {
            sum += quotes.get(i).getClose().doubleValue();
        }
        return sum / (index + 1);
    }
}
package ch.xavier.backtester.indicator;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
public class TetherLines implements Indicator {
    private final int fastLength;
    private final int slowLength;

    @Override
    public double calculate(int index, List<Quote> quotes) {
        // This returns the difference between fast and slow lines
        return calculateFastTether(index, quotes) - calculateSlowTether(index, quotes);
    }

    public TetherLinesResult calculateResult(int index, List<Quote> quotes) {
        double fastTether = calculateFastTether(index, quotes);
        double slowTether = calculateSlowTether(index, quotes);
        return new TetherLinesResult(fastTether, slowTether);
    }

    private double calculateFastTether(int index, List<Quote> quotes) {
        return calculateEMA(quotes, index, fastLength);
    }

    private double calculateSlowTether(int index, List<Quote> quotes) {
        return calculateEMA(quotes, index, slowLength);
    }

    private double calculateEMA(List<Quote> quotes, int index, int length) {
        if (index < length) {
            return calculateSMA(quotes, index, length);
        }

        double multiplier = 2.0 / (length + 1);
        double close = quotes.get(index).getClose().doubleValue();
        double prevEMA = calculateEMA(quotes, index - 1, length);

        return (close - prevEMA) * multiplier + prevEMA;
    }

    private double calculateSMA(List<Quote> quotes, int index, int length) {
        double sum = 0;
        int count = 0;
        for (int i = Math.max(0, index - length + 1); i <= index; i++) {
            sum += quotes.get(i).getClose().doubleValue();
            count++;
        }
        return sum / count;
    }

    @Getter
    public static class TetherLinesResult {
        private final double fastTether;
        private final double slowTether;

        public TetherLinesResult(double fastTether, double slowTether) {
            this.fastTether = fastTether;
            this.slowTether = slowTether;
        }

        public boolean isBullish() {
            return fastTether > slowTether;
        }

        public boolean isBearish() {
            return fastTether < slowTether;
        }
    }
}
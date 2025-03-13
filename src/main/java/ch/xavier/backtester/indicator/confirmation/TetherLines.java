package ch.xavier.backtester.indicator.confirmation;

import ch.xavier.backtester.indicator.Indicator;
import ch.xavier.backtester.quote.Quote;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@Setter
@Getter
public class TetherLines implements Indicator {
    private int fastLength;
    private int slowLength;

    @Override
    public double calculate(int index, List<Quote> quotes) {
        // Return the difference between fast and slow lines
        return calculateFastTether(index, quotes) - calculateSlowTether(index, quotes);
    }

    public TetherLinesResult calculateResult(int index, List<Quote> quotes) {
        double fastTether = calculateFastTether(index, quotes);
        double slowTether = calculateSlowTether(index, quotes);
        return new TetherLinesResult(fastTether, slowTether);
    }

    private double calculateFastTether(int index, List<Quote> quotes) {
        double highest = calculateHighest(quotes, index, fastLength);
        double lowest = calculateLowest(quotes, index, fastLength);
        return (highest + lowest) / 2;
    }

    private double calculateSlowTether(int index, List<Quote> quotes) {
        double highest = calculateHighest(quotes, index, slowLength);
        double lowest = calculateLowest(quotes, index, slowLength);
        return (highest + lowest) / 2;
    }

    private double calculateHighest(List<Quote> quotes, int index, int period) {
        double highest = Double.MIN_VALUE;
        for (int i = Math.max(0, index - period + 1); i <= index; i++) {
            highest = Math.max(highest, quotes.get(i).getHigh().doubleValue());
        }
        return highest;
    }

    private double calculateLowest(List<Quote> quotes, int index, int period) {
        double lowest = Double.MAX_VALUE;
        for (int i = Math.max(0, index - period + 1); i <= index; i++) {
            lowest = Math.min(lowest, quotes.get(i).getLow().doubleValue());
        }
        return lowest;
    }

    public int getTrend(int index, List<Quote> quotes) {
        double fastTether = calculateFastTether(index, quotes);
        double slowTether = calculateSlowTether(index, quotes);

        if (fastTether > slowTether) return 1;    // Bullish
        if (fastTether < slowTether) return -1;   // Bearish
        return 0;                                 // Neutral
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
package ch.xavier.backtester.indicator;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
public class TrailingStop {
    private final int length;
    private final double multiplier;
    private final String priceSource; // "hl2", "close", "open", etc.
    private final boolean useWicks;

    public TrailingStopResult calculate(int index, List<Quote> quotes, boolean inLongPosition) {
        if (index < length) {
            return new TrailingStopResult(0, 0, false, false);
        }

        double atr = calculateATR(quotes, index);
        double stopDistance = atr * multiplier;
        double sourcePrice = getPriceSource(quotes.get(index));

        double longStop = sourcePrice - stopDistance;
        double shortStop = sourcePrice + stopDistance;

        // Apply trailing logic
        if (index > 0 && index >= length) {
            TrailingStopResult prev = calculate(index - 1, quotes, inLongPosition);

            if (inLongPosition) {
                // For long positions, stop can only move higher
                longStop = Math.max(longStop, prev.getLongStop());
            } else {
                // For short positions, stop can only move lower
                shortStop = Math.min(shortStop, prev.getShortStop());
            }
        }

        // Check for stop hits
        boolean longStopHit = checkLongStopHit(quotes.get(index), longStop);
        boolean shortStopHit = checkShortStopHit(quotes.get(index), shortStop);

        return new TrailingStopResult(longStop, shortStop, longStopHit, shortStopHit);
    }

    private boolean checkLongStopHit(Quote quote, double longStop) {
        if (useWicks) {
            return quote.getLow().doubleValue() <= longStop;
        }
        return quote.getClose().doubleValue() <= longStop;
    }

    private boolean checkShortStopHit(Quote quote, double shortStop) {
        if (useWicks) {
            return quote.getHigh().doubleValue() >= shortStop;
        }
        return quote.getClose().doubleValue() >= shortStop;
    }

    private double calculateATR(List<Quote> quotes, int index) {
        double sum = 0;
        for (int i = Math.max(1, index - length + 1); i <= index; i++) {
            sum += calculateTR(quotes, i);
        }
        return sum / length;
    }

    private double calculateTR(List<Quote> quotes, int index) {
        double high = quotes.get(index).getHigh().doubleValue();
        double low = quotes.get(index).getLow().doubleValue();
        double prevClose = quotes.get(index-1).getClose().doubleValue();

        return Math.max(high - low,
                Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
    }

    private double getPriceSource(Quote quote) {
        switch (priceSource) {
            case "close": return quote.getClose().doubleValue();
            case "open": return quote.getOpen().doubleValue();
            case "high": return quote.getHigh().doubleValue();
            case "low": return quote.getLow().doubleValue();
            case "ohlc4": return (quote.getOpen().doubleValue() +
                    quote.getHigh().doubleValue() +
                    quote.getLow().doubleValue() +
                    quote.getClose().doubleValue()) / 4;
            case "hl2":
            default: return (quote.getHigh().doubleValue() + quote.getLow().doubleValue()) / 2;
        }
    }

    @Getter
    public static class TrailingStopResult {
        private final double longStop;
        private final double shortStop;
        private final boolean longStopHit;
        private final boolean shortStopHit;

        public TrailingStopResult(double longStop, double shortStop,
                                  boolean longStopHit, boolean shortStopHit) {
            this.longStop = longStop;
            this.shortStop = shortStop;
            this.longStopHit = longStopHit;
            this.shortStopHit = shortStopHit;
        }
    }
}
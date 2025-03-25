package ch.xavier.backtester.strategy;

import ch.xavier.backtester.backtesting.model.Position;
import ch.xavier.backtester.indicator.misc.EMA;
import ch.xavier.backtester.indicator.misc.Stochastic;
import ch.xavier.backtester.quote.Quote;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ExitStrategy {
    // EMA for trend determination
    private EMA ema200;
    private EMA ema20;
    private EMA ema50;

    // Stochastic indicators
    private Stochastic fastStoch; // 9-3 Stochastic
    private Stochastic fullStoch; // 60-10 Stochastic

    // Exit parameters
    @Builder.Default private double fastStochOverboughtLevel = 80.0;
    @Builder.Default private boolean useVwapConfirmation = false;
    @Builder.Default private double partialProfitPercentage = 50.0; // % of position to close
    @Builder.Default private double trailingStopMultiplier = 2.0; // For uptrend scenario
    @Builder.Default private boolean moveToBreakeven = true;
    @Builder.Default private boolean tightStopInDowntrend = true;
    @Builder.Default private boolean timeExitWithStochRotation = true;

    // State tracking
    private boolean partialProfitTaken = false;

    public void initialize(List<Quote> quotes) {
        if (ema200 != null) ema200.calculate(quotes);
        if (ema20 != null) ema20.calculate(quotes);
        if (ema50 != null) ema50.calculate(quotes);
        if (fastStoch != null) fastStoch.calculate(quotes);
        if (fullStoch != null) fullStoch.calculate(quotes);
    }

    public boolean shouldTakePartialProfit(Position position, List<Quote> quotes, int index) {
        if (partialProfitTaken || fastStoch == null) return false;

        // Take partial profits when 9-3 Stochastics reach overbought level
        return position.isLong() &&
                fastStoch.getD()[index] >= fastStochOverboughtLevel;
    }

    public boolean shouldExitPosition(Position position, List<Quote> quotes, int index) {
        if (ema200 == null || fastStoch == null || fullStoch == null) return false;

        double currentPrice = quotes.get(index).getClose();
        boolean isUptrend = currentPrice > ema200.getValue(index);

        if (isUptrend) {
            return shouldExitInUptrend(position, quotes, index);
        } else {
            return shouldExitInDowntrend(position, quotes, index);
        }
    }

    private boolean shouldExitInUptrend(Position position, List<Quote> quotes, int index) {
        // In uptrend, be more patient and use larger trailing stop
        if (!position.isLong()) return true; // Always exit short positions in uptrend

        // Check for 60-10 Stochastics rotation in uptrend if enabled
        if (timeExitWithStochRotation && isStochasticTurningDown(fullStoch, index)) {
            return true;
        }

        return false;
    }

    private boolean shouldExitInDowntrend(Position position, List<Quote> quotes, int index) {
        if (!position.isLong()) return false; // For short positions, use other criteria

        // Exit long positions in downtrend when:
        // 1. 9-3 Stochastics move back to 80 quickly
        boolean fastStochHigh = fastStoch.getD()[index] >= fastStochOverboughtLevel;

        // 2. 60-10 Stochastics heading down
        boolean fullStochHeadingDown = isStochasticTurningDown(fullStoch, index);

        // 3. Price under 20 EMA/50 EMA
        boolean priceUnderEmas = quotes.get(index).getClose() < ema20.getValue(index) ||
                quotes.get(index).getClose() < ema50.getValue(index);

        return fastStochHigh || (fullStochHeadingDown && priceUnderEmas);
    }

    public double updateStopLoss(Position position, List<Quote> quotes, int index, double atr, double currentStop) {
        double currentPrice = quotes.get(index).getClose();
        boolean isUptrend = currentPrice > ema200.getValue(index);

        // Move to breakeven after partial profit is taken
        if (partialProfitTaken && moveToBreakeven) {
            return Math.max(position.getEntryPrice(), currentStop);
        }

        // Use different trailing stop logic based on trend
        if (isUptrend) {
            // In uptrend, use larger trailing stop
            double newStop = position.isLong() ?
                    currentPrice - (atr * trailingStopMultiplier) :
                    currentPrice + (atr * trailingStopMultiplier);

            return position.isLong() ?
                    Math.max(currentStop, newStop) :
                    Math.min(currentStop, newStop);
        } else if (tightStopInDowntrend) {
            // In downtrend, use tighter trailing stop
            double newStop = position.isLong() ?
                    currentPrice - atr :
                    currentPrice + atr;

            return position.isLong() ?
                    Math.max(currentStop, newStop) :
                    Math.min(currentStop, newStop);
        }

        return currentStop;
    }

    private boolean isStochasticTurningDown(Stochastic stoch, int index) {
        if (index < 2 || stoch == null) return false;
        return stoch.getD()[index] < stoch.getD()[index - 1] &&
                stoch.getD()[index - 1] < stoch.getD()[index - 2];
    }

    public void resetState() {
        partialProfitTaken = false;
    }
}
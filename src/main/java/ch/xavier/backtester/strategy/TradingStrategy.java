package ch.xavier.backtester.strategy;

import ch.xavier.backtester.backtesting.model.Position;
import ch.xavier.backtester.quote.Quote;

import java.util.List;

public interface TradingStrategy {
    /**
     * Get strategy name for reporting
     */
    String getName();

    /**
     * Generate trading signal
     * @return 1 for long, -1 for short, 0 for no signal
     */
    int generateSignal(List<Quote> quotes, int index);

    /**
     * Calculate position size based on risk parameters
     */
    default double calculatePositionSize(double availableFunds, Quote quote, double stopPrice) {
        return 0.0; // Override in concrete strategies
    }

    /**
     * Calculate initial stop loss price
     */
    double calculateStopLossPrice(boolean isLong, List<Quote> quotes, int index);

    /**
     * Calculate take profit target price
     */
    double calculateTakeProfitPrice(boolean isLong, List<Quote> quotes, int index);

    /**
     * Update trailing stop or returns initial stop loss
     * @return new stop price
     */
    double updateStopLoss(Position position, List<Quote> quotes, int index);
}
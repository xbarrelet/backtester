package ch.xavier.backtester.strategy;

import ch.xavier.backtester.backtesting.model.Position;
import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.quote.Quote;

import java.util.List;
import java.util.Map;

public abstract class BaseStrategy implements TradingStrategy {
    protected boolean useTrailingSL = false;
    protected final TradingParameters parameters;

    public BaseStrategy(TradingParameters parameters) {
        this.parameters = parameters != null ? parameters : TradingParameters.builder().build();
    }

    @Override
    public double calculatePositionSize(double availableFunds, Quote quote, double stopLossPrice) {
        double close = quote.getClose().doubleValue();
        double riskAmount = availableFunds * parameters.getRiskPerTrade();
        double priceDifference = Math.abs(close - stopLossPrice);

        // Account for slippage
        double slippage = close * parameters.getSlippage();
        priceDifference += slippage;

        // Account for fees, limit orders
        double entryFee = close * parameters.getMakerFee();
        double exitFee = stopLossPrice * parameters.getMakerFee();
        double totalCost = priceDifference + entryFee + exitFee;

        // Ensure we don't divide by zero
        if (totalCost <= 0) {
            return 0;
        }

        return (riskAmount / totalCost) * parameters.getLeverage();
    }

    @Override
    public double calculateStopLossPrice(boolean isLong, List<Quote> quotes, int index) {
        double price = quotes.get(index).getClose().doubleValue();
        double atr = calculateATR(quotes, index, parameters.getAtrLength());
        double atrStop = isLong ?
                price - (atr * parameters.getAtrMultiplier()) :
                price + (atr * parameters.getAtrMultiplier());

        // Calculate maximum allowable stop loss distance
        double maxStopDistance = price * parameters.getMaxStopLoss();
        double maxStopPrice = isLong ?
                price - maxStopDistance :
                price + maxStopDistance;

        // Use the stop that's CLOSER to the entry price (more conservative)
        return isLong ?
                Math.max(atrStop, maxStopPrice) : // For longs, take the higher stop price (closer to entry)
                Math.min(atrStop, maxStopPrice);  // For shorts, take the lower stop price (closer to entry)
    }

    @Override
    public double calculateTakeProfitPrice(boolean isLong, List<Quote> quotes, int index) {
        double price = quotes.get(index).getClose().doubleValue();
        double riskAmount = Math.abs(price - calculateStopLossPrice(isLong, quotes, index));

        double defaultTP = isLong ?
                price + (riskAmount * parameters.getRiskRewardRatio()) :
                price - (riskAmount * parameters.getRiskRewardRatio());

        // Enforce minimum take profit
        double minTpPrice = isLong ?
                price * (1 + parameters.getMinTakeProfit()) :
                price * (1 - parameters.getMinTakeProfit());

        return isLong ?
                Math.max(defaultTP, minTpPrice) :
                Math.min(defaultTP, minTpPrice);
    }

    @Override
    public double updateStopLoss(Position position, List<Quote> quotes, int index) {
        if (useTrailingSL) {
            double atr = calculateATR(quotes, index, parameters.getAtrLength());
            double price = quotes.get(index).getClose().doubleValue();
            double newStopLoss = position.isLong() ?
                    price - (atr * parameters.getAtrMultiplier()) :
                    price + (atr * parameters.getAtrMultiplier());

            // Only update if the new stop is better than the current one
            if (position.isLong()) {
                return Math.max(position.getCurrentStopLossPrice(), newStopLoss);
            } else {
                return Math.min(position.getCurrentStopLossPrice(), newStopLoss);
            }
        } else {
            return position.getCurrentStopLossPrice();
        }
    }

    @Override
    public void setParameters(Map<String, Object> parameters) {
        if (parameters.containsKey("useTrailingSL")) {
            this.useTrailingSL = parameters.get("useTrailingSL").equals("true");
        }
    }

    // The ATR measures the average range between the high and low prices of an asset over a given period.
    // A higher ATR indicates more volatility, while a lower ATR indicates less volatility.
    protected double calculateATR(List<Quote> quotes, int index, int length) {
        if (index < length) return 0;

        double sum = 0;
        for (int i = index - length + 1; i <= index; i++) {
            Quote current = quotes.get(i);
            Quote previous = quotes.get(i - 1);

            double tr1 = current.getHigh().doubleValue() - current.getLow().doubleValue();
            double tr2 = Math.abs(current.getHigh().doubleValue() - previous.getClose().doubleValue());
            double tr3 = Math.abs(current.getLow().doubleValue() - previous.getClose().doubleValue());

            sum += Math.max(Math.max(tr1, tr2), tr3);
        }

        return sum / length;
    }
}

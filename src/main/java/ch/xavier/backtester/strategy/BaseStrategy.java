package ch.xavier.backtester.strategy;

import ch.xavier.backtester.backtesting.model.Position;
import ch.xavier.backtester.backtesting.model.TradingParameters;
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
    public double calculatePositionSize(double availableFunds, Quote quote, double stopPrice) {
        double riskAmount = availableFunds * parameters.getRiskPerTrade();
        double stopDistance = Math.abs(quote.getClose().doubleValue() - stopPrice);
        return (riskAmount / stopDistance) * parameters.getLeverage() / quote.getClose().doubleValue();
    }

    @Override
    public double calculateStopLossPrice(boolean isLong, List<Quote> quotes, int index) {
        double atr = calculateATR(quotes, index, parameters.getAtrLength());
        double price = quotes.get(index).getClose().doubleValue();
        return isLong ? price - (atr * parameters.getAtrMultiplier())
                : price + (atr * parameters.getAtrMultiplier());
    }

    @Override
    public double calculateTakeProfitPrice(boolean isLong, List<Quote> quotes, int index) {
        double stopDistance = Math.abs(quotes.get(index).getClose().doubleValue() -
                calculateStopLossPrice(isLong, quotes, index));
        double price = quotes.get(index).getClose().doubleValue();
        return isLong ? price + (stopDistance * parameters.getRiskRewardRatio())
                : price - (stopDistance * parameters.getRiskRewardRatio());
    }

    @Override
    public double updateStopLoss(Position position, List<Quote> quotes, int index) {
        if (useTrailingSL) {
            double atr = calculateATR(quotes, index, parameters.getAtrLength());
            double price = quotes.get(index).getClose().doubleValue();

            return position.isLong() ?
                    price - (atr * parameters.getAtrMultiplier()) :
                    price + (atr * parameters.getAtrMultiplier());
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

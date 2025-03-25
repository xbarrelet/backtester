package ch.xavier.backtester.strategy;

import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.backtesting.model.Position;
import ch.xavier.backtester.quote.Quote;

import java.util.List;
import java.util.Map;

public abstract class BaseStrategy implements TradingStrategy {
    protected int atrLength;
    protected final TradingParameters parameters;
    protected double riskRewardRatio;
    protected boolean useRiskBasedPositionSizing = false;
    protected boolean useTrailingSL = false;

    public BaseStrategy(TradingParameters parameters) {
        this.parameters = parameters != null ? parameters : TradingParameters.builder().build();

        this.riskRewardRatio = this.parameters.getRiskRewardRatio();
        this.atrLength = this.parameters.getAtrLength();
        this.useRiskBasedPositionSizing = this.parameters.isUseRiskedBasedPositionSizing();
    }

    @Override
    public double calculatePositionSize(double availableFunds, Quote quote, double stopLossPrice) {
        if (availableFunds < 10) {
            return 0;
        }

        double amountPerPosition = availableFunds * parameters.getRiskPerTrade();

        if (!useRiskBasedPositionSizing) {
            return amountPerPosition * parameters.getLeverage();
        }

        // Position sizing based on volatility/stop distance, you make sure regardless of your stoploss of pex 1% or 5%
        // that you can only lose 3% of your capital if you hit it.
        double close = quote.getClose();
        double priceDifference = Math.abs(close - stopLossPrice);

        double slippage = close * parameters.getSlippage();
        priceDifference += slippage;

        double entryFee = close * parameters.getMakerFee();
        double exitFee = stopLossPrice * parameters.getTakerFee();
        double totalCost = priceDifference + entryFee + exitFee;

        if (totalCost <= 0) {
            return 0;
        }

        return (amountPerPosition / totalCost) * parameters.getLeverage();
    }

    @Override
    public double calculateStopLossPrice(boolean isLong, List<Quote> quotes, int index) {
        double price = quotes.get(index).getClose();
        double atr = calculateATR(quotes, index, this.atrLength);
        double atrStop = isLong ?
                price - (atr * parameters.getAtrMultiplier()) :
                price + (atr * parameters.getAtrMultiplier());

        double maxStopDistance = price * parameters.getMaxStopLoss();
        double maxStopPrice = isLong ? price - maxStopDistance : price + maxStopDistance;

        return isLong ? Math.max(atrStop, maxStopPrice) : Math.min(atrStop, maxStopPrice);
    }

    @Override
    public double calculateTakeProfitPrice(boolean isLong, List<Quote> quotes, int index) {
        double price = quotes.get(index).getClose();
        double riskAmount = Math.abs(price - calculateStopLossPrice(isLong, quotes, index));

        double defaultTP = isLong ?
                price + (riskAmount * this.riskRewardRatio) :
                price - (riskAmount * this.riskRewardRatio);

        // Enforce minimum take profit
        double minTpPrice = isLong ?
                price * (1 + parameters.getMinTakeProfit()) :
                price * (1 - parameters.getMinTakeProfit());

        return isLong ? Math.max(defaultTP, minTpPrice) : Math.min(defaultTP, minTpPrice);
    }

    @Override
    public double updateStopLoss(Position position, List<Quote> quotes, int index) {
        if (useTrailingSL) {
            double atr = calculateATR(quotes, index, parameters.getAtrLength());
            double price = quotes.get(index).getClose();
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
        if (parameters.containsKey("riskRewardRatio")) {
            this.riskRewardRatio = (double) parameters.get("riskRewardRatio");
        }
        if (parameters.containsKey("atrLength")) {
            this.atrLength = (int) parameters.get("atrLength");
        }
        if (parameters.containsKey("useRiskBasedPositionSizing")) {
            this.useRiskBasedPositionSizing = parameters.get("useRiskBasedPositionSizing").equals("true");
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

            double tr1 = current.getHigh() - current.getLow();
            double tr2 = Math.abs(current.getHigh() - previous.getClose());
            double tr3 = Math.abs(current.getLow() - previous.getClose());

            sum += Math.max(Math.max(tr1, tr2), tr3);
        }

        return sum / length;
    }
}

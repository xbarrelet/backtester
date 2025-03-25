package ch.xavier.backtester.strategy.concrete;

import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.strategy.BaseStrategy;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class SMACrossoverStrategy extends BaseStrategy {
    private int fastPeriod;
    private int slowPeriod;

    public SMACrossoverStrategy(TradingParameters tradingParameters) {
        super(tradingParameters);
    }

    @Override
    public int generateSignal(List<Quote> quotes, int index) {
        if (index < slowPeriod) return 0; // Not enough data

        double fastSMA = calculateSMA(quotes, index, fastPeriod);
        double slowSMA = calculateSMA(quotes, index, slowPeriod);
        double prevFastSMA = calculateSMA(quotes, index - 1, fastPeriod);
        double prevSlowSMA = calculateSMA(quotes, index - 1, slowPeriod);

        // Fast SMA crosses above Slow SMA -> Long signal
        if (prevFastSMA <= prevSlowSMA && fastSMA > slowSMA) {
            return 1;
        }
        // Fast SMA crosses below Slow SMA -> Short signal
        else if (prevFastSMA >= prevSlowSMA && fastSMA < slowSMA) {
            return -1;
        }

        return 0; // No signal
    }

    private double calculateSMA(List<Quote> quotes, int index, int period) {
        if (index < period - 1) return 0;

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += quotes.get(index - i).getClose();
        }

        return sum / period;
    }

    @Override
    public void setParameters(Map<String, Object> parameters) {
        super.setParameters(parameters);

        if (parameters.containsKey("fastPeriod")) {
            this.fastPeriod = (int) parameters.get("fastPeriod");
        }
        if (parameters.containsKey("slowPeriod")) {
            this.slowPeriod = (int) parameters.get("slowPeriod");
        }
    }
}
package ch.xavier.backtester.strategy;

import ch.xavier.backtester.backtesting.model.TradingParameters;
import ch.xavier.backtester.quote.Quote;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SMACrossoverStrategy extends BaseStrategy {
    private final int fastPeriod;
    private final int slowPeriod;

    public SMACrossoverStrategy(TradingParameters parameters) {
        this("SMA Crossover (50,200)", parameters, 50, 200);
    }

    public SMACrossoverStrategy(String name, TradingParameters parameters, int fastPeriod, int slowPeriod) {
        super(name, parameters);
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
    }

    public SMACrossoverStrategy(String name, TradingParameters parameters, int fastPeriod, int slowPeriod,
                                boolean useTrailingSL) {
        super(name, parameters);
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.useTrailingSL = useTrailingSL;
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
            sum += quotes.get(index - i).getClose().doubleValue();
        }

        return sum / period;
    }
}
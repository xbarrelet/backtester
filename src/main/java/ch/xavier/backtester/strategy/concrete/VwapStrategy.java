package ch.xavier.backtester.strategy.concrete;

import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.indicator.misc.VWAP;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.strategy.BaseStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class VwapStrategy extends BaseStrategy {
    private VWAP vwap;
    private boolean initialized = false;

    // Strategy parameters
    private int lookbackPeriod = 3;
    private double distanceThreshold = 0.002; // 0.2% minimum distance from VWAP

    public VwapStrategy(TradingParameters parameters) {
        super(parameters);
        vwap = new VWAP();
    }

    @Override
    public int generateSignal(List<Quote> quotes, int index) {
        if (!initialized && quotes != null && !quotes.isEmpty()) {
            vwap.calculate(quotes);
            initialized = true;
        }

        if (!initialized || index < lookbackPeriod || index >= quotes.size()) {
            return 0;
        }

        Quote currentQuote = quotes.get(index);
        Quote previousQuote = quotes.get(index - 1);
        double currentVwap = vwap.getValues()[index];
        double previousVwap = vwap.getValues()[index - 1];

        // Calculate price relative to VWAP
        double currentPriceDistance = (currentQuote.getClose() - currentVwap) / currentVwap;

        // Check for VWAP crossovers
        boolean crossedAbove = previousQuote.getClose() < previousVwap &&
                currentQuote.getClose() > currentVwap;

        boolean crossedBelow = previousQuote.getClose() > previousVwap &&
                currentQuote.getClose() < currentVwap;

        // Long signal: price crosses above VWAP with minimum distance
        if (crossedAbove && Math.abs(currentPriceDistance) > distanceThreshold) {
            return 1;
        }

        // Short signal: price crosses below VWAP with minimum distance (if shorts enabled)
        if (crossedBelow && Math.abs(currentPriceDistance) > distanceThreshold) {
            return -1;
        }

        return 0;
    }

    @Override
    public void setParameters(Map<String, Object> parameters) {
        super.setParameters(parameters);

        if (parameters.containsKey("lookbackPeriod")) {
            this.lookbackPeriod = (int) parameters.get("lookbackPeriod");
        }
        if (parameters.containsKey("distanceThreshold")) {
            this.distanceThreshold = (double) parameters.get("distanceThreshold");
        }
    }
}
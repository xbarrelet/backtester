package ch.xavier.backtester.marketphase;

import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class VolatilityClassifier implements MarketPhaseClassifier {
    private final int period;
    private final double lowVolThreshold;
    private final double highVolThreshold;

    @Override
    public MarketPhase classify(List<Quote> quotes, int index) {
        if (index < period) return MarketPhase.UNKNOWN;

        double bBandWidth = calculateBollingerBandWidth(quotes, index);
        double priceChange = calculatePriceChange(quotes, index);

        if (bBandWidth < lowVolThreshold) {
            return MarketPhase.SIDEWAYS;
        } else if (priceChange > 0 && bBandWidth > highVolThreshold) {
            return MarketPhase.BULLISH;
        } else if (priceChange < 0 && bBandWidth > highVolThreshold) {
            return MarketPhase.BEARISH;
        } else {
            // If volatility is medium but no strong direction
            return MarketPhase.SIDEWAYS;
        }
    }

    private double calculateBollingerBandWidth(List<Quote> quotes, int index) {
        double sma = calculateSMA(quotes, index, period);
        double stdDev = calculateStdDev(quotes, index, period, sma);

        return (stdDev * 4) / sma; // Width as % of price
    }

    private double calculatePriceChange(List<Quote> quotes, int index) {
        if (index < period) return 0;

        double startPrice = quotes.get(index - period).getClose();
        double endPrice = quotes.get(index).getClose();

        return (endPrice - startPrice) / startPrice;
    }

    private double calculateSMA(List<Quote> quotes, int index, int period) {
        double sum = 0;
        for (int i = Math.max(0, index - period + 1); i <= index; i++) {
            sum += quotes.get(i).getClose();
        }
        return sum / period;
    }

    private double calculateStdDev(List<Quote> quotes, int index, int period, double mean) {
        double sum = 0;
        for (int i = Math.max(0, index - period + 1); i <= index; i++) {
            double diff = quotes.get(i).getClose() - mean;
            sum += diff * diff;
        }
        return Math.sqrt(sum / period);
    }
}
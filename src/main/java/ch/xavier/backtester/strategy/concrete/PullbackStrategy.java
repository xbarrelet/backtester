package ch.xavier.backtester.strategy.concrete;

import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.backtesting.model.Position;
import ch.xavier.backtester.indicator.misc.EMA;
import ch.xavier.backtester.indicator.misc.Stochastic;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.strategy.BaseStrategy;
import ch.xavier.backtester.strategy.ExitStrategy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Setter
public class PullbackStrategy extends BaseStrategy {
    // EMA parameters
    private int ema20Period = 20;
    private int ema50Period = 50;

    // Stochastic parameters
    private int fastStochK = 9;
    private int fastStochD = 3;
    private int fullStochK = 60;
    private int fullStochD = 10;
    private int fullStochSmooth = 1;

    // Strategy parameters
    private double stochLowThreshold = 20.0;
    private double stochHighThreshold = 85.0; // Preferably 90 for stronger trend
    private int lookbackPeriod = 10;
    private double minPriceChangePercent = 0.5; // Minimum aggressive move up requirement
    private boolean onlyLongTrades = true;

    // Indicators
    private EMA ema20;
    private EMA ema50;
    private Stochastic fastStoch; // 9-3 Stochastic
    private Stochastic fullStoch; // 60-10-1 Stochastic (full)

    // Tracking variables
    private double currentStopLoss;
    private double currentTarget;
    private boolean initialized = false;

    private ExitStrategy exitStrategy;
    private EMA ema200;
    private int ema200Period = 200;

    public PullbackStrategy(TradingParameters parameters) {
        super(parameters);
    }

    @Override
    public void setParameters(Map<String, Object> parameters) {
        super.setParameters(parameters);

        // Strategy specific parameters
        this.stochLowThreshold = (double) parameters.getOrDefault("stochLowThreshold", 20.0);
        this.stochHighThreshold = (double) parameters.getOrDefault("stochHighThreshold", 85.0);
        this.lookbackPeriod = (int) parameters.getOrDefault("lookbackPeriod", 10);
        this.minPriceChangePercent = (double) parameters.getOrDefault("minPriceChangePercent", 0.5);
        this.onlyLongTrades = (boolean) parameters.getOrDefault("onlyLongTrades", true);
        this.ema200Period = (int) parameters.getOrDefault("ema200Period", 200);


        // Create indicator objects
        try {
            ema20 = new EMA(ema20Period);
            ema50 = new EMA(ema50Period);
            fastStoch = new Stochastic(fastStochK, fastStochD);
            fullStoch = new Stochastic(fullStochK, fullStochD, fullStochSmooth, true);

            initialized = false; // Will be initialized with quotes later
        } catch (Exception e) {
            log.error("Failed to create indicators: {}", e.getMessage());
        }
    }

    public void initialize(List<Quote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            log.warn("Cannot initialize PullbackStrategy: quotes list is empty");
            return;
        }

        try {
            // Calculate all indicators
            ema20.calculate(quotes);
            ema50.calculate(quotes);
            fastStoch.calculate(quotes);
            fullStoch.calculate(quotes);

            initialized = true;

            ema200 = new EMA(ema200Period);
            exitStrategy = ExitStrategy.builder()
                    .ema200(ema200)
                    .ema20(ema20)
                    .ema50(ema50)
                    .fastStoch(fastStoch)
                    .fullStoch(fullStoch)
                    .build();
            exitStrategy.initialize(quotes);
            log.debug("PullbackStrategy initialized successfully");
        } catch (Exception e) {
            log.error("Error initializing indicators: {}", e.getMessage());
            initialized = false;
        }
    }

    @Override
    public int generateSignal(List<Quote> quotes, int currentIndex) {
        if (!initialized) {
            initialize(quotes);
            if (!initialized) return 0;
        }

        // Ensure we have enough data
        int minDataRequired = Math.max(Math.max(ema20Period, ema50Period),
                Math.max(fastStochK + fastStochD, fullStochK + fullStochD)) + lookbackPeriod;

        if (currentIndex < minDataRequired) return 0;

        // Check for valid long entry based on specified conditions
        if (isValidLongEntry(quotes, currentIndex)) {
            return 1; // Long signal
        }

        return 0; // No signal
    }

    private boolean isValidLongEntry(List<Quote> quotes, int currentIndex) {
        if (!initialized || currentIndex < lookbackPeriod) return false;

        Quote currentQuote = quotes.get(currentIndex);
        double currentPrice = currentQuote.getClose();

        // 1. Check if market is in uptrend (price above 50 EMA)
        if (currentPrice <= ema50.getValue(currentIndex)) {
            return false;
        }

        // 2. Check if 60-10 Stochastic is above threshold (KEY INDICATOR)
        if (fullStoch.getD()[currentIndex] < stochHighThreshold) {
            return false;
        }

        // 3. Check for aggressive move up followed by pullback
        if (!hasAggressiveMoveUpAndPullback(quotes, currentIndex)) {
            return false;
        }

        // 4. Check if 9-3 Stochastics are near 20 line
        if (fastStoch.getD()[currentIndex] > stochLowThreshold + 5) { // Allow small buffer
            return false;
        }

        // 5. Check for breakout of flag pattern
        if (!isBreakingOutOfFlag(quotes, currentIndex)) {
            return false;
        }

        // Setup stop loss and target prices
        setupStopLossAndTarget(quotes, currentIndex);

        return true;
    }

    private boolean hasAggressiveMoveUpAndPullback(List<Quote> quotes, int currentIndex) {
        if (currentIndex < lookbackPeriod) return false;

        // Find recent peak (aggressive move up)
        int peakIndex = findRecentPeak(quotes, currentIndex);
        if (peakIndex == -1) return false;

        // Check if price movement was steep enough
        double lowestBeforePeak = findLowestPriceBeforePeak(quotes, peakIndex);
        Quote peakQuote = quotes.get(peakIndex);
        double percentMove = (peakQuote.getHigh() - lowestBeforePeak) / lowestBeforePeak * 100;

        if (percentMove < minPriceChangePercent) return false;

        // Check for pullback to EMA zone
        double ema20Value = ema20.getValue(currentIndex);
        double ema50Value = ema50.getValue(currentIndex);
        double currentLow = quotes.get(currentIndex).getLow();

        // Price has pulled back to 20 EMA or 50 EMA zone
        return (Math.abs(currentLow - ema20Value) / ema20Value < 0.01) ||
                (Math.abs(currentLow - ema50Value) / ema50Value < 0.01);
    }

    private int findRecentPeak(List<Quote> quotes, int currentIndex) {
        int peakIndex = -1;
        double highestHigh = 0;

        // Look back to find recent peak
        for (int i = currentIndex - 1; i >= currentIndex - lookbackPeriod && i >= 0; i--) {
            if (quotes.get(i).getHigh() > highestHigh) {
                highestHigh = quotes.get(i).getHigh();
                peakIndex = i;
            }
        }

        return peakIndex;
    }

    private double findLowestPriceBeforePeak(List<Quote> quotes, int peakIndex) {
        double lowestLow = Double.MAX_VALUE;
        int lookbackStart = Math.max(0, peakIndex - lookbackPeriod);

        for (int i = lookbackStart; i < peakIndex; i++) {
            lowestLow = Math.min(lowestLow, quotes.get(i).getLow());
        }

        return lowestLow;
    }

    private boolean isBreakingOutOfFlag(List<Quote> quotes, int currentIndex) {
        if (currentIndex < 2) return false;

        Quote currentQuote = quotes.get(currentIndex);
        Quote previousQuote = quotes.get(currentIndex - 1);

        // Check for bullish candle breaking above previous candle's high
        return currentQuote.getClose() > currentQuote.getOpen() &&
                currentQuote.getHigh() > previousQuote.getHigh();
    }

    private void setupStopLossAndTarget(List<Quote> quotes, int currentIndex) {
        // Set stop loss below recent swing low or below the 50 EMA
        double swingLow = findRecentSwingLow(quotes, currentIndex);
        double ema50Value = ema50.getValue(currentIndex);
        currentStopLoss = Math.min(swingLow, ema50Value) * 0.99; // 1% buffer

        // Set target based on risk/reward ratio
        double entryPrice = quotes.get(currentIndex).getClose();
        double risk = entryPrice - currentStopLoss;
        currentTarget = entryPrice + (risk * riskRewardRatio);
    }

    private double findRecentSwingLow(List<Quote> quotes, int currentIndex) {
        double lowestLow = Double.MAX_VALUE;
        int lookbackRange = Math.min(lookbackPeriod, currentIndex);

        for (int i = currentIndex - 1; i >= currentIndex - lookbackRange; i--) {
            if (i <= 0) continue;

            Quote q = quotes.get(i);
            Quote qBefore = quotes.get(i-1);
            Quote qAfter = quotes.get(i+1);

            // A swing low is when the low is lower than adjacent candles' lows
            if (q.getLow() < qBefore.getLow() && q.getLow() < qAfter.getLow()) {
                lowestLow = Math.min(lowestLow, q.getLow());
            }
        }

        // If no swing low found, use the lowest low in lookback period
        if (lowestLow == Double.MAX_VALUE) {
            for (int i = currentIndex - lookbackRange; i < currentIndex; i++) {
                if (i >= 0) {
                    lowestLow = Math.min(lowestLow, quotes.get(i).getLow());
                }
            }
        }

        return lowestLow;
    }

    // Override methods from BaseStrategy with proper strategy-specific implementations
    @Override
    public double calculateStopLossPrice(boolean isLong, List<Quote> quotes, int index) {
        return currentStopLoss;
    }

    @Override
    public double calculateTakeProfitPrice(boolean isLong, List<Quote> quotes, int index) {
        return currentTarget;
    }

    @Override
    public double updateStopLoss(Position position, List<Quote> quotes, int index) {
        if (!initialized || index < 0 || index >= quotes.size()) {
            return position.getCurrentStopLossPrice();
        }

        // Check if we should take partial profits
        if (exitStrategy.shouldTakePartialProfit(position, quotes, index)) {
            // Logic to handle partial profit taking would go here
            // This would require extending your backtester to support partial closes
            log.debug("Taking partial profit at index {}", index);
            exitStrategy.setPartialProfitTaken(true);
        }

        // Check if we should exit the position
        if (exitStrategy.shouldExitPosition(position, quotes, index)) {
            // Force exit by returning very tight stop
            return position.isLong() ? quotes.get(index).getClose() * 0.999 :
                    quotes.get(index).getClose() * 1.001;
        }

        // Use exit strategy to update stop loss
        double atr = calculateATR(quotes, index, atrLength);
        return exitStrategy.updateStopLoss(position, quotes, index, atr,
                position.getCurrentStopLossPrice());
    }
}
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
public class DivergenceStrategy extends BaseStrategy {
    // Fixed stochastic parameters based on description
    private final int fastStoch1K = 9;
    private final int fastStoch1D = 3;
    private final int fastStoch2K = 14;
    private final int fastStoch2D = 3;
    private final int fastStoch3K = 40;
    private final int fastStoch3D = 4;
    private final int fullStochK = 60;
    private final int fullStochD = 10;
    private final int fullStochSmooth = 1;

    // Divergence parameters
    private double oversoldThreshold = 20.0;
    private double overboughtThreshold = 80.0;
    private int lookbackPeriod = 20;
    private double minPriceChange = 0.5;
    private boolean requireAllStochastics = true; // Set to true as per description
    private boolean requireReversalCandle = true;
    private boolean tradeLongOnly = true;

    // Indicators
    private Stochastic fastStoch1; // 9,3 - Primary indicator
    private Stochastic fastStoch2; // 14,3
    private Stochastic fastStoch3; // 40,4
    private Stochastic fullStoch;  // 60,10,1

    // Tracking variables
    private double currentStopLoss;
    private double currentTarget;
    private boolean initialized = false;

    private ExitStrategy exitStrategy;
    private EMA ema200;
    private EMA ema20;
    private EMA ema50;
    private int ema200Period = 200;

    public DivergenceStrategy(TradingParameters parameters) {
        super(parameters);
    }

    @Override
    public void setParameters(Map<String, Object> parameters) {
        super.setParameters(parameters);

        // Only allow customization of thresholds and risk parameters
        this.oversoldThreshold = (double) parameters.getOrDefault("oversoldThreshold", 20.0);
        this.overboughtThreshold = (double) parameters.getOrDefault("overboughtThreshold", 80.0);
        this.lookbackPeriod = (int) parameters.getOrDefault("lookbackPeriod", 20);
        this.minPriceChange = (double) parameters.getOrDefault("minPriceChange", 0.5);
        this.requireReversalCandle = (boolean) parameters.getOrDefault("requireReversalCandle", true);
        this.tradeLongOnly = (boolean) parameters.getOrDefault("tradeLongOnly", true);
        this.requireAllStochastics = (boolean) parameters.getOrDefault("requireAllStochastics", true);
        this.ema200Period = (int) parameters.getOrDefault("ema200Period", 200);

        // Create indicator objects
        try {
            fastStoch1 = new Stochastic(fastStoch1K, fastStoch1D);
            fastStoch2 = new Stochastic(fastStoch2K, fastStoch2D);
            fastStoch3 = new Stochastic(fastStoch3K, fastStoch3D);
            fullStoch = new Stochastic(fullStochK, fullStochD, fullStochSmooth, true);

            initialized = false; // Will be initialized with quotes later
        } catch (Exception e) {
            log.error("Failed to create stochastic indicators: {}", e.getMessage());
        }
    }

    public void initialize(List<Quote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            log.warn("Cannot initialize DivergenceStrategy: quotes list is empty");
            return;
        }

        try {
            // Calculate all stochastic indicators
            fastStoch1.calculate(quotes);
            fastStoch2.calculate(quotes);
            fastStoch3.calculate(quotes);
            fullStoch.calculate(quotes);

            ema200 = new EMA(ema200Period);
            ema20 = new EMA(20);
            ema50 = new EMA(50);
            exitStrategy = ExitStrategy.builder()
                    .ema200(ema200)
                    .ema20(ema20)
                    .ema50(ema50)
                    .fastStoch(fastStoch1)
                    .fullStoch(fullStoch)
                    .build();
            exitStrategy.initialize(quotes);

            initialized = true;
            log.debug("DivergenceStrategy initialized successfully");
        } catch (Exception e) {
            log.error("Error initializing stochastic indicators: {}", e.getMessage());
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
        int minDataRequired = Math.max(Math.max(fastStoch1K, fastStoch2K),
                Math.max(fastStoch3K, fullStochK)) + lookbackPeriod;

        if (currentIndex < minDataRequired) return 0;

        // Check for bullish divergence based on specified conditions
        if (isValidBullishDivergence(quotes, currentIndex)) {
            return 1; // Buy signal
        }

        // Check for bearish divergence if not trading long only
        if (!tradeLongOnly && isValidBearishDivergence(quotes, currentIndex)) {
            return -1; // Sell signal
        }

        return 0; // No signal
    }

    private boolean isValidBullishDivergence(List<Quote> quotes, int currentIndex) {
        if (!initialized || currentIndex < lookbackPeriod) return false;

        // First stage: Find where all four Stochastics were below 20
        int stageOneIndex = findStageOneIndex(quotes, currentIndex);
        if (stageOneIndex == -1) return false;

        // Second stage: Check if price made lower low but 9-3 Stoch made higher low
        int stageTwoIndex = findStageTwoIndex(quotes, currentIndex, stageOneIndex);
        if (stageTwoIndex == -1) return false;

        // Entry confirmation: 9-3 Stochastic turned back up and is above 20
        if (!isStochasticTurningUp(fastStoch1, currentIndex)) return false;
        if (fastStoch1.getD()[currentIndex] <= oversoldThreshold) return false;

        // Set stop loss just below the pattern's lowest candle
        Quote lowestCandle = quotes.get(stageTwoIndex);
        currentStopLoss = lowestCandle.getLow() - (lowestCandle.getHigh() - lowestCandle.getLow()) * 0.1;

        // Set target based on risk/reward ratio
        double entryPrice = quotes.get(currentIndex).getClose();
        double risk = entryPrice - currentStopLoss;
        currentTarget = entryPrice + (risk * riskRewardRatio);

        return true;
    }

    private boolean isValidBearishDivergence(List<Quote> quotes, int currentIndex) {
        if (!initialized || currentIndex < lookbackPeriod) return false;

        // First stage: Find where all four Stochastics were above 80
        int stageOneIndex = findBearishStageOneIndex(quotes, currentIndex);
        if (stageOneIndex == -1) return false;

        // Second stage: Check if price made higher high but 9-3 Stoch made lower high
        int stageTwoIndex = findBearishStageTwoIndex(quotes, currentIndex, stageOneIndex);
        if (stageTwoIndex == -1) return false;

        // Entry confirmation: 9-3 Stochastic turned back down and is below 80
        if (!isStochasticTurningDown(fastStoch1, currentIndex)) return false;
        if (fastStoch1.getD()[currentIndex] >= overboughtThreshold) return false;

        // Set stop loss just above the pattern's highest candle
        Quote highestCandle = quotes.get(stageTwoIndex);
        currentStopLoss = highestCandle.getHigh() + (highestCandle.getHigh() - highestCandle.getLow()) * 0.1;

        // Set target based on risk/reward ratio
        double entryPrice = quotes.get(currentIndex).getClose();
        double risk = currentStopLoss - entryPrice;
        currentTarget = entryPrice - (risk * riskRewardRatio);

        return true;
    }

    private int findStageOneIndex(List<Quote> quotes, int currentIndex) {
        // Look back to find where all stochastics went below oversold threshold
        for (int i = currentIndex - 1; i >= currentIndex - lookbackPeriod && i >= 0; i--) {
            if (allStochasticsOversold(i)) {
                return i;
            }
        }
        return -1;
    }

    private int findBearishStageOneIndex(List<Quote> quotes, int currentIndex) {
        // Look back to find where all stochastics went above overbought threshold
        for (int i = currentIndex - 1; i >= currentIndex - lookbackPeriod && i >= 0; i--) {
            if (allStochasticsOverbought(i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean allStochasticsOversold(int index) {
        if (!initialized || index < 0 ||
                index >= fastStoch1.getD().length ||
                index >= fastStoch2.getD().length ||
                index >= fastStoch3.getD().length ||
                index >= fullStoch.getD().length) return false;

        // Check if all four Stochastic D lines are below oversold threshold
        return fastStoch1.getD()[index] < oversoldThreshold &&
                fastStoch2.getD()[index] < oversoldThreshold &&
                fastStoch3.getD()[index] < oversoldThreshold &&
                fullStoch.getD()[index] < oversoldThreshold;
    }

    private boolean allStochasticsOverbought(int index) {
        if (!initialized || index < 0 ||
                index >= fastStoch1.getD().length ||
                index >= fastStoch2.getD().length ||
                index >= fastStoch3.getD().length ||
                index >= fullStoch.getD().length) return false;

        // Check if all four Stochastic D lines are above overbought threshold
        return fastStoch1.getD()[index] > overboughtThreshold &&
                fastStoch2.getD()[index] > overboughtThreshold &&
                fastStoch3.getD()[index] > overboughtThreshold &&
                fullStoch.getD()[index] > overboughtThreshold;
    }

    private int findStageTwoIndex(List<Quote> quotes, int currentIndex, int stageOneIndex) {
        if (!initialized || stageOneIndex == -1) return -1;

        // Find the lowest price point after stage one
        int lowestIndex = -1;
        double lowestPrice = Double.MAX_VALUE;

        for (int i = stageOneIndex; i <= currentIndex; i++) {
            double currentLow = quotes.get(i).getLow();
            if (currentLow < lowestPrice) {
                lowestPrice = currentLow;
                lowestIndex = i;
            }
        }

        // Ensure this is a valid low point (price lower, stochastic higher)
        if (lowestIndex > 0 && lowestIndex < fastStoch1.getD().length) {
            double newLowPrice = quotes.get(lowestIndex).getLow();
            double previousLowPrice = findPreviousLowPrice(quotes, lowestIndex);

            // Price made lower low
            if (newLowPrice < previousLowPrice) {
                // Check if stochastic made higher low (bullish divergence)
                double newStochLow = fastStoch1.getD()[lowestIndex];
                double previousStochLow = findPreviousStochLow(fastStoch1, lowestIndex);

                if (newStochLow > previousStochLow) {
                    return lowestIndex;
                }
            }
        }

        return -1;
    }

    private int findBearishStageTwoIndex(List<Quote> quotes, int currentIndex, int stageOneIndex) {
        if (!initialized || stageOneIndex == -1) return -1;

        // Find the highest price point after stage one
        int highestIndex = -1;
        double highestPrice = Double.MIN_VALUE;

        for (int i = stageOneIndex; i <= currentIndex; i++) {
            double currentHigh = quotes.get(i).getHigh();
            if (currentHigh > highestPrice) {
                highestPrice = currentHigh;
                highestIndex = i;
            }
        }

        // Ensure this is a valid high point (price higher, stochastic lower)
        if (highestIndex > 0 && highestIndex < fastStoch1.getD().length) {
            double newHighPrice = quotes.get(highestIndex).getHigh();
            double previousHighPrice = findPreviousHighPrice(quotes, highestIndex);

            // Price made higher high
            if (newHighPrice > previousHighPrice) {
                // Check if stochastic made lower high (bearish divergence)
                double newStochHigh = fastStoch1.getD()[highestIndex];
                double previousStochHigh = findPreviousStochHigh(fastStoch1, highestIndex);

                if (newStochHigh < previousStochHigh) {
                    return highestIndex;
                }
            }
        }

        return -1;
    }

    private double findPreviousLowPrice(List<Quote> quotes, int currentIndex) {
        if (currentIndex <= 0) return Double.MAX_VALUE;

        double lowestPrice = Double.MAX_VALUE;
        int lookbackStart = Math.max(0, currentIndex - lookbackPeriod);

        for (int i = lookbackStart; i < currentIndex; i++) {
            lowestPrice = Math.min(lowestPrice, quotes.get(i).getLow());
        }

        return lowestPrice;
    }

    private double findPreviousHighPrice(List<Quote> quotes, int currentIndex) {
        if (currentIndex <= 0) return Double.MIN_VALUE;

        double highestPrice = Double.MIN_VALUE;
        int lookbackStart = Math.max(0, currentIndex - lookbackPeriod);

        for (int i = lookbackStart; i < currentIndex; i++) {
            highestPrice = Math.max(highestPrice, quotes.get(i).getHigh());
        }

        return highestPrice;
    }

    private double findPreviousStochLow(Stochastic stoch, int currentIndex) {
        if (!initialized || currentIndex <= 0 || stoch == null || stoch.getD() == null) return 100.0;

        double lowestValue = 100.0;
        int lookbackStart = Math.max(0, currentIndex - lookbackPeriod);

        for (int i = lookbackStart; i < currentIndex && i < stoch.getD().length; i++) {
            lowestValue = Math.min(lowestValue, stoch.getD()[i]);
        }

        return lowestValue;
    }

    private double findPreviousStochHigh(Stochastic stoch, int currentIndex) {
        if (!initialized || currentIndex <= 0 || stoch == null || stoch.getD() == null) return 0.0;

        double highestValue = 0.0;
        int lookbackStart = Math.max(0, currentIndex - lookbackPeriod);

        for (int i = lookbackStart; i < currentIndex && i < stoch.getD().length; i++) {
            highestValue = Math.max(highestValue, stoch.getD()[i]);
        }

        return highestValue;
    }

    private boolean isStochasticTurningUp(Stochastic stoch, int index) {
        if (!initialized || index < 2 || stoch == null || stoch.getD() == null ||
                index >= stoch.getD().length) return false;

        // D-line is moving upward for at least 2 consecutive bars
        return stoch.getD()[index] > stoch.getD()[index - 1] &&
                stoch.getD()[index - 1] > stoch.getD()[index - 2];
    }

    private boolean isStochasticTurningDown(Stochastic stoch, int index) {
        if (!initialized || index < 2 || stoch == null || stoch.getD() == null ||
                index >= stoch.getD().length) return false;

        // D-line is moving downward for at least 2 consecutive bars
        return stoch.getD()[index] < stoch.getD()[index - 1] &&
                stoch.getD()[index - 1] < stoch.getD()[index - 2];
    }

    // Override methods from BaseStrategy with proper signatures
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
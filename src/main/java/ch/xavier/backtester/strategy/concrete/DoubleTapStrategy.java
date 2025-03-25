package ch.xavier.backtester.strategy.concrete;

import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.strategy.BaseStrategy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Setter
public class DoubleTapStrategy extends BaseStrategy {
    private TradingParameters parameters;
    private int pivotLength;
    private double pivotTolerance;
    private double targetFib;
    private double stopFib;
    private boolean detectBottoms;
    private boolean detectTops;
    private boolean useTimeFilter;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean useTrailStop;
    private int atrLength;
    private double atrMultiplier;
    private int swingLookback;

    // Track pivots and patterns
    private final List<Pivot> topPivots = new ArrayList<>();
    private final List<Pivot> bottomPivots = new ArrayList<>();
    private final Map<Integer, DoubleTapPattern> patterns = new HashMap<>();

    public DoubleTapStrategy(TradingParameters parameters) {
        super(parameters);
    }

    @Override
    public int generateSignal(List<Quote> quotes, int index) {
        if (index < pivotLength) {
            return 0; // Not enough data
        }

        Quote currentQuote = quotes.get(index);

        // Time filter check
        if (useTimeFilter) {
            LocalTime quoteTime = currentQuote.getTimestamp().toLocalDateTime().toLocalTime();
            if (quoteTime.isBefore(startTime) || quoteTime.isAfter(endTime)) {
                return 0;
            }
        }

        // Detect pivots
        detectPivots(quotes, index);

        // Check for double tap patterns
        DoubleTapPattern pattern = findDoubleTapPattern();
        if (pattern != null) {
            patterns.put(index, pattern);

            // Generate signal based on pattern
            if (pattern.isBottomPattern() && detectBottoms) {
                // Set stopLoss and target for risk management (can be accessed elsewhere)
                this.currentStopLoss = pattern.stopPrice();
                this.currentTarget = pattern.targetPrice();
                return 1; // Long signal
            } else if (pattern.isTopPattern() && detectTops) {
                // Set stopLoss and target for risk management (can be accessed elsewhere)
                this.currentStopLoss = pattern.stopPrice();
                this.currentTarget = pattern.targetPrice();
                return -1; // Short signal
            }
        }

        // Update trailing stops if needed
        if (useTrailStop) {
            updateTrailingStops(quotes, index);
        }

        return 0; // No signal
    }

    // Add these fields to store current stop/target values
    private double currentStopLoss;
    private double currentTarget;

    @Override
    public void setParameters(Map<String, Object> parameters) {
        this.pivotLength = (int) parameters.getOrDefault("pivotLength", 50);
        this.pivotTolerance = (double) parameters.getOrDefault("pivotTolerance", 15.0);
        this.targetFib = (double) parameters.getOrDefault("targetFib", 100.0);
        this.stopFib = (double) parameters.getOrDefault("stopFib", 0.0);
        this.detectBottoms = (boolean) parameters.getOrDefault("detectBottoms", true);
        this.detectTops = (boolean) parameters.getOrDefault("detectTops", true);
        this.useTimeFilter = (boolean) parameters.getOrDefault("useTimeFilter", false);
        this.startTime = (LocalTime) parameters.getOrDefault("startTime", LocalTime.of(0, 0));
        this.endTime = (LocalTime) parameters.getOrDefault("endTime", LocalTime.of(23, 59));
        this.useTrailStop = (boolean) parameters.getOrDefault("useTrailStop", false);
        this.atrLength = (int) parameters.getOrDefault("atrLength", 14);
        this.atrMultiplier = (double) parameters.getOrDefault("atrMultiplier", 1.0);
        this.swingLookback = (int) parameters.getOrDefault("swingLookback", 5);
    }

    private void detectPivots(List<Quote> quotes, int currentIndex) {
        if (currentIndex < pivotLength + 1) return;

        // Check if we have a new high pivot
        boolean isHighPivot = true;
        double highestValue = quotes.get(currentIndex - 1).getHigh();
        for (int i = 2; i <= pivotLength; i++) {
            if (currentIndex - i >= 0 && quotes.get(currentIndex - i).getHigh() > highestValue) {
                isHighPivot = false;
                break;
            }
        }

        if (isHighPivot) {
            topPivots.add(new Pivot(currentIndex - 1, highestValue));
            // Keep only recent pivots
            if (topPivots.size() > 5) {
                topPivots.removeFirst();
            }
        }

        // Check if we have a new low pivot
        boolean isLowPivot = true;
        double lowestValue = quotes.get(currentIndex - 1).getLow();
        for (int i = 2; i <= pivotLength; i++) {
            if (currentIndex - i >= 0 && quotes.get(currentIndex - i).getLow() < lowestValue) {
                isLowPivot = false;
                break;
            }
        }

        if (isLowPivot) {
            bottomPivots.add(new Pivot(currentIndex - 1, lowestValue));
            // Keep only recent pivots
            if (bottomPivots.size() > 5) {
                bottomPivots.removeFirst();
            }
        }
    }

    private DoubleTapPattern findDoubleTapPattern() {
        // Check for double bottom (W pattern)
        if (detectBottoms && bottomPivots.size() >= 4) {
            // We need at least 4 points for a W pattern: low, high, second low, second high
            Pivot firstLow = bottomPivots.get(bottomPivots.size() - 4);
            Pivot firstHigh = topPivots.size() > 2 ? topPivots.get(topPivots.size() - 3) : null;
            Pivot secondLow = bottomPivots.get(bottomPivots.size() - 2);
            Pivot currentHigh = !topPivots.isEmpty() ? topPivots.getLast() : null;

            if (firstHigh != null && currentHigh != null &&
                    secondLow.getIndex() > firstHigh.getIndex() &&
                    currentHigh.getIndex() > secondLow.getIndex()) {

                // Calculate tolerance band
                double height = Math.abs(firstHigh.getValue() - firstLow.getValue());
                double upperBand = firstLow.getValue() + height * (pivotTolerance / 100.0);
                double lowerBand = firstLow.getValue() - height * (pivotTolerance / 100.0);

                // Check if second low is within tolerance
                if (secondLow.getValue() >= lowerBand && secondLow.getValue() <= upperBand) {
                    double targetPrice = secondLow.getValue() + height * (targetFib / 100.0);
                    double stopPrice = secondLow.getValue() - (stopFib > 0 ? height * (stopFib / 100.0) : 0);

                    return new DoubleTapPattern(false, targetPrice, stopPrice);
                }
            }
        }

        // Check for double top (M pattern)
        if (detectTops && topPivots.size() >= 4) {
            // We need at least 4 points for an M pattern: high, low, second high, second low
            Pivot firstHigh = topPivots.get(topPivots.size() - 4);
            Pivot firstLow = bottomPivots.size() > 2 ? bottomPivots.get(bottomPivots.size() - 3) : null;
            Pivot secondHigh = topPivots.get(topPivots.size() - 2);
            Pivot currentLow = !bottomPivots.isEmpty() ? bottomPivots.getLast() : null;

            if (firstLow != null && currentLow != null &&
                    secondHigh.getIndex() > firstLow.getIndex() &&
                    currentLow.getIndex() > secondHigh.getIndex()) {

                // Calculate tolerance band
                double height = Math.abs(firstHigh.getValue() - firstLow.getValue());
                double upperBand = firstHigh.getValue() + height * (pivotTolerance / 100.0);
                double lowerBand = firstHigh.getValue() - height * (pivotTolerance / 100.0);

                // Check if second high is within tolerance
                if (secondHigh.getValue() >= lowerBand && secondHigh.getValue() <= upperBand) {
                    double targetPrice = secondHigh.getValue() - height * (targetFib / 100.0);
                    double stopPrice = secondHigh.getValue() + (stopFib > 0 ? height * (stopFib / 100.0) : 0);

                    return new DoubleTapPattern(true, targetPrice, stopPrice);
                }
            }
        }

        return null;
    }

    private void updateTrailingStops(List<Quote> quotes, int currentIndex) {
        if (currentIndex < atrLength) return;

        // Calculate ATR
        double atr = calculateATR(quotes, currentIndex, atrLength);

        // Calculate swing high/low for trailing stops
        double swingHigh = Double.MIN_VALUE;
        double swingLow = Double.MAX_VALUE;

        for (int i = 0; i < swingLookback; i++) {
            if (currentIndex - i >= 0) {
                Quote q = quotes.get(currentIndex - i);
                swingHigh = Math.max(swingHigh, q.getHigh());
                swingLow = Math.min(swingLow, q.getLow());
            }
        }

        // Set trailing stops
        double longTrailStop = swingLow - (atr * atrMultiplier);
        double shortTrailStop = swingHigh + (atr * atrMultiplier);

        // We'd update active position stops here if needed
    }

    // Helper classes
    @Getter
    private static class Pivot {
        private final int index;
        private final double value;

        public Pivot(int index, double value) {
            this.index = index;
            this.value = value;
        }
    }

    private record DoubleTapPattern(boolean isTop, @Getter double targetPrice, @Getter double stopPrice) {

        public boolean isTopPattern() {
            return isTop;
        }

        public boolean isBottomPattern() {
            return !isTop;
        }
    }
}
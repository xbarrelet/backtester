package ch.xavier.backtester.strategy.concrete;

import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.strategy.BaseStrategy;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ZScoreStrategy extends BaseStrategy {
    // Indicators settings
    private int maLength = 100;
    private int atrPeriod = 100;
    private double stdDevPercent = 0.3;

    // Entry settings
    private double entryThreshold = 1.5;
    private double exitThreshold = 1.0;
    private double reentryThreshold = 4.0;
    private int maxInventory = 1;
    private boolean shortAllowed = true;

    // Session settings
    private boolean useTimeFilter = true;
    private String sessionType = "Regular";
    private LocalTime regularSessionStart = LocalTime.of(13, 30);
    private LocalTime regularSessionEnd = LocalTime.of(20, 0);
    private LocalTime extendedSessionStart = LocalTime.of(4, 0);
    private LocalTime extendedSessionEnd = LocalTime.of(20, 0);

    // Strategy state variables
    private double lastEntry = 0.0;
    private double zscoreReentry = 0.0;
    private int inventory = 0;

    public ZScoreStrategy(TradingParameters tradingParameters) {
        super(tradingParameters);
    }

    @Override
    public int generateSignal(List<Quote> quotes, int index) {
        if (index < maLength) return 0; // Not enough data

        // Calculate SMA
        double sum = 0;
        for (int i = index - maLength + 1; i <= index; i++) {
            sum += quotes.get(i).getClose();
        }
        double sma = sum / maLength;

        // Calculate Standard Deviation
        double stdDev = (stdDevPercent * sma / 100);

        // Calculate Z-Score
        double currentClose = quotes.get(index).getClose();
        double zScore = (currentClose - sma) / stdDev;

        // Check if current time is within trading session
        boolean canTrade = !useTimeFilter || isWithinSession(quotes.get(index).getTimestamp().toLocalDateTime());

        // Generate trading signals
        if (inventory > 0) {
            // Update reentry z-score for long position
            zscoreReentry = (currentClose - lastEntry) / stdDev;

            // Check for exit condition
            if (zScore > exitThreshold && canTrade) {
                inventory = 0;
                return 0; // Exit long position
            }

            // Check for re-entry condition
            if (zscoreReentry < -reentryThreshold && inventory < maxInventory && canTrade) {
                inventory++;
                lastEntry = currentClose;
                return 1; // Add to long position
            }
        } else if (inventory < 0) {
            // Update reentry z-score for short position
            zscoreReentry = (lastEntry - currentClose) / stdDev;

            // Check for exit condition
            if (zScore < -exitThreshold && canTrade) {
                inventory = 0;
                return 0; // Exit short position
            }

            // Check for re-entry condition
            if (zscoreReentry < -reentryThreshold && inventory > -maxInventory && canTrade) {
                inventory--;
                lastEntry = currentClose;
                return -1; // Add to short position
            }
        } else {
            // No position, check for new entry
            zscoreReentry = 0;

            if (zScore < -entryThreshold && canTrade) {
                inventory = 1;
                lastEntry = currentClose;
                return 1; // Enter long position
            }

            if (zScore > entryThreshold && shortAllowed && canTrade) {
                inventory = -1;
                lastEntry = currentClose;
                return -1; // Enter short position
            }
        }

        return 0; // No signal
    }

    @Override
    protected double calculateATR(List<Quote> quotes, int index, int period) {
        if (index < period) return 0;

        double sum = 0;
        for (int i = index - period + 1; i <= index; i++) {
            Quote current = quotes.get(i);
            Quote previous = quotes.get(i - 1);

            double highLow = current.getHigh() - current.getLow();
            double highClosePrev = Math.abs(current.getHigh() - previous.getClose());
            double lowClosePrev = Math.abs(current.getLow() - previous.getClose());

            sum += Math.max(highLow, Math.max(highClosePrev, lowClosePrev));
        }

        return sum / period;
    }

    private boolean isWithinSession(LocalDateTime timestamp) {
        if (timestamp == null) return false;

        LocalTime time = timestamp.toLocalTime();
        int dayOfWeek = timestamp.getDayOfWeek().getValue(); // 1 = Monday, 7 = Sunday

        // Check if it's a weekday
        boolean isWeekday = (dayOfWeek >= 1 && dayOfWeek <= 5);
        if (!isWeekday) return false;

        // Check if within session hours
        LocalTime sessionStart = sessionType.equals("Regular") ? regularSessionStart : extendedSessionStart;
        LocalTime sessionEnd = sessionType.equals("Regular") ? regularSessionEnd : extendedSessionEnd;

        return !time.isBefore(sessionStart) && !time.isAfter(sessionEnd);
    }

    @Override
    public void setParameters(Map<String, Object> parameters) {
        super.setParameters(parameters);

        // Indicator settings
        if (parameters.containsKey("maLength")) {
            maLength = (int) parameters.get("maLength");
        }
        if (parameters.containsKey("atrPeriod")) {
            atrPeriod = (int) parameters.get("atrPeriod");
        }
        if (parameters.containsKey("stdDevPercent")) {
            stdDevPercent = (double) parameters.get("stdDevPercent");
        }

        // Entry settings
        if (parameters.containsKey("entryThreshold")) {
            entryThreshold = (double) parameters.get("entryThreshold");
        }
        if (parameters.containsKey("exitThreshold")) {
            exitThreshold = (double) parameters.get("exitThreshold");
        }
        if (parameters.containsKey("reentryThreshold")) {
            reentryThreshold = (double) parameters.get("reentryThreshold");
        }
        if (parameters.containsKey("maxInventory")) {
            maxInventory = (int) parameters.get("maxInventory");
        }
        if (parameters.containsKey("shortAllowed")) {
            shortAllowed = (boolean) parameters.get("shortAllowed");
        }

        // Session settings
        if (parameters.containsKey("useTimeFilter")) {
            useTimeFilter = (boolean) parameters.get("useTimeFilter");
        }
    }
}
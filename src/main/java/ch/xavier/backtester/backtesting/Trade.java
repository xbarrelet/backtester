package ch.xavier.backtester.backtesting;

import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class Trade {
    private final Date entryTime;
    private final Date exitTime;
    private final double entryPrice;
    private final double exitPrice;
    private final double size;
    private final double profit;
    private final double profitAmount;
    private final boolean isLong;
    private final double maxAdverseExcursion;
    private final double maxFavorableExcursion;
    private final MarketPhaseClassifier.MarketPhase marketPhase;
}
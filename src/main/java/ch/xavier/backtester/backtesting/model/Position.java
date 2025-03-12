package ch.xavier.backtester.backtesting.model;

import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Builder
@Getter
@Setter
public class Position {
    private double entryPrice;
    private double size;
    private double currentStopLossPrice;
    private double takeProfitPrice;
    private boolean isLong;
    private int entryIndex;
    private MarketPhaseClassifier.MarketPhase marketPhase;
    private List<Double> priceHistory;
}

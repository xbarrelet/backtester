package ch.xavier.backtester.backtesting;

import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class Position {
    private double entryPrice;
    private double size;
    private double currentStopPrice;
    private double targetPrice;
    private boolean isLong;
    private int entryIndex;
    private MarketPhaseClassifier.MarketPhase marketPhase;
    private List<Double> priceHistory = new ArrayList<>();
}

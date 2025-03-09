package ch.xavier.backtester.backtesting;

import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class WalkForwardResult {
    private final List<BacktestResult> windowResults;
    private final BacktestResult aggregatedResult;
    private final Map<MarketPhaseClassifier.MarketPhase, Integer> optimalParameters;
}
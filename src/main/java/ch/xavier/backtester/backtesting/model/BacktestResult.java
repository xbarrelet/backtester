package ch.xavier.backtester.backtesting.model;

import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import lombok.*;

import java.util.List;
import java.util.Map;

@Builder
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BacktestResult {
    private double finalFunds;
    private double totalReturn;
    private double annualizedReturn;
    private double sharpeRatio;
    private double sortinoRatio;
    private double profitFactor;
    private double winRate;
    private double maxDrawdown;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private double avgLossAmount;
    private double avgTradeAmount;
    private double avgTradeLength; // In days
    private double avgWinAmount;
    private String strategyName;
    private Map<MarketPhaseClassifier.MarketPhase, BacktestResult> phaseResults;
    private List<Trade> trades;
}

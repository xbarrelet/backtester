package ch.xavier.backtester.backtesting;

import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class BacktestResult {
    private double finalEquity;
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
    private double avgWinAmount;
    private double avgLossAmount;
    private double avgTradeAmount;
    private double avgTradeLength; // In days
    private Map<MarketPhaseClassifier.MarketPhase, BacktestResult> phaseResults;
    private List<Trade> trades;
}

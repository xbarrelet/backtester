package ch.xavier.backtester.backtesting;

import lombok.*;

@Builder
@Data
public class TradingParameters {
    // Exchange
    private final double makerFee = 0.02; // from Bybit
    private final double takerFee = 0.055; // from Bybit
    private final double slippage = 0.0005; // 0.05% average slippage

    // Position
    private final double initialCapital = 100000.0; // 100k USDT
    private final double riskPerTrade = 0.03; // 3% per trade
    private final double leverage = 10.0; // 10x leverage

    // Risks
    private final double maxDrawdown = 0.20; // Stop trading after 15% drawdown
    private final double dailyLossLimit = 0.05; // No more trades after 5% daily loss
    private final double riskRewardRatio = 2.0; // Target 2:1 reward to risk
    private final int atrLength = 14; // Period for ATR stop loss
    private final double atrMultiplier = 3.0; // Multiplier for ATR stop

    // Walk-forward parameters
    private final int walkForwardWindow = 60; // Days for training
    private final int testWindow = 30; // Days for testing
    private final int walkForwardStep = 30; // Step size for moving window
}

package ch.xavier.backtester.backtesting;

import lombok.*;

@Getter
@Setter
@Builder
public class TradingParameters {
    // Exchange
    private final double makerFee = 0.02; // from Bybit
    private final double takerFee = 0.055; // from Bybit
    private final double slippage = 0.0005; // 0.05% average slippage

    // Position
    private final double initialCapital = 100000.0;
    private final double riskPerTrade = 0.03; // 3% per trade
    private final double leverage = 10.0;
    private final boolean useRiskedBasedPositionSizing = false;

    // Risks
    private final double minTakeProfit = 0.01; // Minimum 1% take profit
    private final double maxStopLoss = 0.05; // Maximum 5% stop loss

    private final double dailyLossLimit = 0.05; // No more trades after 5% daily loss
    private final double riskRewardRatio = 2.0; // Target 2:1 reward to risk
    private final int atrLength = 14; // Period for ATR stop loss
    private final double atrMultiplier = 3.0; // Multiplier for ATR stop

    private final double maxDrawdown = 20; // Maximum 20% drawdown
    private int minNumberOfTrades = 200; // Minimum number of trades to consider strategy

    // Walk-forward parameters
    private final int walkForwardWindow = 60; // Days for training
    private final int testWindow = 30; // Days for testing
    private final int walkForwardStep = 30; // Step size for moving window

    // Possible additions
    // - Days of the week to exclude, trading crypto in weekend should be fine but could be used for attunement.
    // - Fixed percents for tp and sl
    // - Separate RR for long and short? Same with SL?
    // - Dynamic position size? Based on ATR?
    // - Maximum losing trades per side?
    // - Additional attunements like RSI big extended, Hurst exponent, etc?
}



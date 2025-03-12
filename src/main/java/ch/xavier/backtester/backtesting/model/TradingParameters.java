package ch.xavier.backtester.backtesting.model;

import lombok.*;

@Getter
@Setter
@Builder
public class TradingParameters {
    // Exchange
    // TODO: You only use limit orders, consider it
    private final double makerFee = 0.02; // from Bybit
    private final double takerFee = 0.055; // from Bybit
    private final double slippage = 0.0005; // 0.05% average slippage

    // Position
    private final double initialCapital = 100000.0;
    private final double riskPerTrade = 0.03; // 3% per trade
    private final double leverage = 10.0;

    // Risks
    private final double maxDrawdown = 0.20; // Stop trading after 20% drawdown
    private final double dailyLossLimit = 0.05; // No more trades after 5% daily loss
    private final double riskRewardRatio = 2.0; // Target 2:1 reward to risk
    private final int atrLength = 14; // Period for ATR stop loss
    private final double atrMultiplier = 3.0; // Multiplier for ATR stop

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
    // - Min TP and SL?
    // - Additional attunements like RSI big extended, Hurst exponent, etc?
}



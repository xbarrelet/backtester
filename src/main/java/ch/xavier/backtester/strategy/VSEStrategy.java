package ch.xavier.backtester.strategy;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
// Vortex Sniper Elite Strategy
public class VSEStrategy {
    
    // Baseline (McGinley Dynamic)
    private int baselineLength;
    private boolean showBaseline;

    // White Line
    private int whiteLineLength;
    private boolean showWhiteLine;

    // TTM Squeeze
    private boolean useTtmSqueeze;
    private boolean ttmGreenRedOnly;
    private boolean ttmCrossConfirmation;
    private boolean ttmInverseSignals;
    private boolean ttmHighlightGreenDots;
    private int ttmLength;
    private double ttmBollingerMultiplier;
    private double ttmKcMultiplier1;
    private double ttmKcMultiplier2;
    private double ttmKcMultiplier3;
    private boolean showSqueezeDots;

    // Tether Line
    private boolean showTetherLines;
    private int tetherFastLength;
    private int tetherSlowLength;
    private boolean showTetherCloud;
    private boolean showTetherLabels;

    // Vortex
    private int vortexLength;
    private double vortexThreshold;
    private boolean useVortexFilter;

    // Trading System
    private boolean useTrailStop;
    private double trailStopMultiplier;
    private int trailStopLength;
    private String trailStopSource;
    private boolean showTrailStopLabels;
    private boolean takeWicksIntoAccount;
    private boolean highlightState;

    public static VSEStrategy getDefaultConfig() {
        return VSEStrategy.builder()
                .baselineLength(14)
                .showBaseline(true)
                .whiteLineLength(20)
                .showWhiteLine(true)
                .useTtmSqueeze(true)
                .ttmGreenRedOnly(true)
                .ttmCrossConfirmation(true)
                .ttmInverseSignals(false)
                .ttmHighlightGreenDots(true)
                .ttmLength(20)
                .ttmBollingerMultiplier(2.0)
                .ttmKcMultiplier1(1.0)
                .ttmKcMultiplier2(1.5)
                .ttmKcMultiplier3(2.0)
                .showSqueezeDots(true)
                .showTetherLines(true)
                .tetherFastLength(13)
                .tetherSlowLength(55)
                .showTetherCloud(true)
                .showTetherLabels(false)
                .vortexLength(14)
                .vortexThreshold(0.05)
                .useVortexFilter(true)
                .useTrailStop(true)
                .trailStopMultiplier(3.0)
                .trailStopLength(22)
                .trailStopSource("hl2")
                .showTrailStopLabels(true)
                .takeWicksIntoAccount(true)
                .highlightState(true)
                .build();
    }
}
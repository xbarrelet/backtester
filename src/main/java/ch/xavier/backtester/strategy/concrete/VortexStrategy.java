package ch.xavier.backtester.strategy.concrete;

import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.indicator.baseline.McGinley;
import ch.xavier.backtester.indicator.baseline.WhiteLine;
import ch.xavier.backtester.indicator.confirmation.TTMSqueeze;
import ch.xavier.backtester.indicator.confirmation.TetherLines;
import ch.xavier.backtester.indicator.volatility.VortexIndicator;
import ch.xavier.backtester.quote.Quote;
import ch.xavier.backtester.strategy.BaseStrategy;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class VortexStrategy extends BaseStrategy {
    private McGinley macGinleyIndicator = new McGinley();
    private WhiteLine whiteLineIndicator = new WhiteLine();
    private TTMSqueeze ttmSqueezeIndicator = new TTMSqueeze();
    private TetherLines tetherLinesIndicator = new TetherLines();
    private VortexIndicator vortexIndicator = new VortexIndicator();

    public VortexStrategy(TradingParameters tradingParameters) {
        super(tradingParameters);

//        // Initialize indicators with default values
//        mcGinleyIndicator = new McGinley();
//        mcGinleyIndicator.setLength(14);
//
//        whiteLineIndicator = new WhiteLine();
//        whiteLineIndicator.setLength(20);
//
//        ttmSqueezeIndicator = new TTMSqueeze();
//
//        tetherLinesIndicator = new TetherLines();
//        tetherLinesIndicator.setFastLength(13);
//        tetherLinesIndicator.setSlowLength(55);
//
//        vortexIndicator = new VortexIndicator();
//        vortexIndicator.setLength(14);
//        vortexIndicator.setThreshold(0.05);
    }

    @Override
    public int generateSignal(List<Quote> quotes, int index) {
        if (index <= 0) return 0;

        // Calculate baseline signals
        double mcGinleyValue = macGinleyIndicator.calculate(index, quotes);
        double whiteLineValue = whiteLineIndicator.calculate(index, quotes);
        double currentPrice = quotes.get(index).getClose();

        // Determine trend based on baseline indicators
        int mcGinleyTrend = Double.compare(currentPrice, mcGinleyValue);
        int whiteLineTrend = Double.compare(currentPrice, whiteLineValue);

        // Check if baseline indicators agree on trend
        boolean baselineLong = mcGinleyTrend == 1 && whiteLineTrend == 1;
        boolean baselineShort = mcGinleyTrend == -1 && whiteLineTrend == -1;

        // Get confirmation signals
//        boolean ttmLongSignal = ttmSqueezeIndicator.isLongSignal(index, quotes);
//        boolean ttmShortSignal = ttmSqueezeIndicator.isShortSignal(index, quotes);
//        int tetherTrend = tetherLinesIndicator.getTrend(index, quotes);

        // Get Vortex signal and confirmation
//        int vortexSignal = vortexIndicator.getSignal(index, quotes);
//        boolean vortexConfirmed = vortexIndicator.isSignalConfirmed(index, quotes);
//        boolean vortexLong = vortexSignal == 1 && vortexConfirmed;
//        boolean vortexShort = vortexSignal == -1 && vortexConfirmed;

        // Long signal when all conditions met
//        if (baselineLong && ttmLongSignal && tetherTrend == 1 && vortexLong) {
        if (baselineLong) {
            return 1;
        }

        // Short signal when all conditions met
//        if (baselineShort && ttmShortSignal && tetherTrend == -1 && vortexShort) {
        if (baselineShort) {
            return -1;
        }

        return 0;  // No signal
    }

    @Override
    public void setParameters(Map<String, Object> parameters) {
        super.setParameters(parameters);
        // McGinley parameters
        if (parameters.containsKey("mcGinleyLength")) {
            macGinleyIndicator.setLength((int) parameters.get("mcGinleyLength"));
        }

        // White Line parameters
        if (parameters.containsKey("whiteLineLength")) {
            whiteLineIndicator.setLength((int) parameters.get("whiteLineLength"));
        }

        // TTM Squeeze parameters
        if (parameters.containsKey("ttmsLength")) {
            ttmSqueezeIndicator.setLength((int) parameters.get("ttmsLength"));
        }
        if (parameters.containsKey("ttmsBbMult")) {
            ttmSqueezeIndicator.setBbMult((double) parameters.get("ttmsBbMult"));
        }
        if (parameters.containsKey("ttmsKcMult")) {
            ttmSqueezeIndicator.setKcMult((double) parameters.get("ttmsKcMult"));
        }
        if (parameters.containsKey("ttmsUseGreenRedConfirmation")) {
            ttmSqueezeIndicator.setUseGreenRedConfirmation((boolean) parameters.get("ttmsUseGreenRedConfirmation"));
        }
        if (parameters.containsKey("ttmsRequireCrossing")) {
            ttmSqueezeIndicator.setRequireCrossing((boolean) parameters.get("ttmsRequireCrossing"));
        }
        if (parameters.containsKey("ttmsHighlightNoSqueeze")) {
            ttmSqueezeIndicator.setHighlightNoSqueeze((boolean) parameters.get("ttmsHighlightNoSqueeze"));
        }
        if (parameters.containsKey("ttmsInverseSignals")) {
            ttmSqueezeIndicator.setInverseSignals((boolean) parameters.get("ttmsInverseSignals"));
        }

        // Tether Lines parameters
        if (parameters.containsKey("tetherFastLength")) {
            tetherLinesIndicator.setFastLength((int) parameters.get("tetherFastLength"));
        }
        if (parameters.containsKey("tetherSlowLength")) {
            tetherLinesIndicator.setSlowLength((int) parameters.get("tetherSlowLength"));
        }

        // Vortex parameters
        if (parameters.containsKey("vortexLength")) {
            vortexIndicator.setLength((int) parameters.get("vortexLength"));
        }
        if (parameters.containsKey("vortexThreshold")) {
            vortexIndicator.setThreshold((double) parameters.get("vortexThreshold"));
        }
    }
}

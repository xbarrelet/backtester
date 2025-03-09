//package ch.xavier.backtester.strategy;
//
//import ch.xavier.backtester.model.Candle;
//import ch.xavier.backtester.model.TradingSignal;
//import ch.xavier.backtester.model.TradingSignal.SignalType;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//
//import java.util.*;
//
//@Service
//@RequiredArgsConstructor
//public class MultiIndicatorStrategyService {
//
//    public List<TradingSignal> backtest(List<Candle> candles, VSEStrategy config) {
//        if (candles.size() < config.getBaselineLength()) {
//            throw new IllegalArgumentException("Not enough candles for strategy calculation");
//        }
//
//        List<TradingSignal> signals = new ArrayList<>();
//
//        // Initialize state variables
//        int trailDirection = 1;
//        double trailLongStop = 0;
//        double trailShortStop = 0;
//
//        // Initialize indicator variables
//        double[] momentum = new double[candles.size()];
//        boolean[] noSqueeze = new boolean[candles.size()];
//        double[] baseline = new double[candles.size()];
//        double[] whiteLine = new double[candles.size()];
//        double[] tetherFast = new double[candles.size()];
//        double[] tetherSlow = new double[candles.size()];
//        double[] vortexPlus = new double[candles.size()];
//        double[] vortexMinus = new double[candles.size()];
//
//        // Calculate indicators for each candle
//        for (int i = 0; i < candles.size(); i++) {
//            int lookback = Math.max(config.getBaselineLength(),
//                    Math.max(config.getTtmLength(),
//                            Math.max(config.getWhiteLineLength(),
//                                    Math.max(config.getTetherSlowLength(),
//                                            Math.max(config.getVortexLength(), config.getTrailStopLength())))));
//
//            if (i < lookback) continue;
//
//            List<Candle> window = candles.subList(0, i + 1);
//            Candle currentCandle = candles.get(i);
//
//            // Calculate McGinley Dynamic (baseline)
//            baseline[i] = calculateMcGinleyDynamic(window, config.getBaselineLength());
//
//            // Calculate White Line
//            whiteLine[i] = calculateWhiteLine(window, config.getWhiteLineLength());
//
//            // Calculate TTM Squeeze
//            Map<String, Object> ttmResult = calculateTTMSqueeze(window, config.getTtmLength(),
//                    config.getTtmBollingerMultiplier(), config.getTtmKcMultiplier1(),
//                    config.getTtmKcMultiplier2(), config.getTtmKcMultiplier3());
//
//            noSqueeze[i] = (boolean) ttmResult.get("noSqueeze");
//            momentum[i] = (double) ttmResult.get("momentum");
//
//            // Calculate Tether Lines
//            tetherFast[i] = calculateTetherLine(window, config.getTetherFastLength());
//            tetherSlow[i] = calculateTetherLine(window, config.getTetherSlowLength());
//
//            // Calculate Vortex
//            Map<String, Double> vortexResult = calculateVortex(window, config.getVortexLength());
//            vortexPlus[i] = vortexResult.get("plus");
//            vortexMinus[i] = vortexResult.get("minus");
//
//            // Calculate Trail Stop
//            double atr = calculateATR(window, config.getTrailStopLength());
//            double trailStopAtr = config.getTrailStopMultiplier() * atr;
//
//            // Get previous values
//            double trailLongStopPrev = i > 0 ? trailLongStop : currentCandle.getLow() - trailStopAtr;
//            double trailShortStopPrev = i > 0 ? trailShortStop : currentCandle.getHigh() + trailStopAtr;
//            int prevDirection = i > 0 ? trailDirection : 1;
//
//            // Update trail stops
//            double trailStopPrice = getTrailStopPrice(currentCandle, config.getTrailStopSource());
//            trailLongStop = trailStopPrice - trailStopAtr;
//            trailShortStop = trailStopPrice + trailStopAtr;
//
//            // Adjust based on previous candle
//            if (i > 0) {
//                Candle prevCandle = candles.get(i-1);
//                boolean isDoji = isDoji(currentCandle);
//
//                if (!isDoji) {
//                    double trailLowPrice = config.isTakeWicksIntoAccount() ?
//                            currentCandle.getLow() : currentCandle.getClose();
//                    double trailHighPrice = config.isTakeWicksIntoAccount() ?
//                            currentCandle.getHigh() : currentCandle.getClose();
//
//                    double prevLowPrice = prevCandle.getLow();
//
//                    if (prevLowPrice > trailLongStopPrev) {
//                        trailLongStop = Math.max(trailLongStop, trailLongStopPrev);
//                    }
//
//                    double prevHighPrice = prevCandle.getHigh();
//
//                    if (prevHighPrice < trailShortStopPrev) {
//                        trailShortStop = Math.min(trailShortStop, trailShortStopPrev);
//                    }
//                } else {
//                    trailLongStop = trailLongStopPrev;
//                    trailShortStop = trailShortStopPrev;
//                }
//            }
//
//            // Update trail direction
//            double trailLowPrice = config.isTakeWicksIntoAccount() ?
//                    currentCandle.getLow() : currentCandle.getClose();
//            double trailHighPrice = config.isTakeWicksIntoAccount() ?
//                    currentCandle.getHigh() : currentCandle.getClose();
//
//            if (prevDirection == -1 && trailHighPrice > trailShortStopPrev) {
//                trailDirection = 1;
//            } else if (prevDirection == 1 && trailLowPrice < trailLongStopPrev) {
//                trailDirection = -1;
//            } else {
//                trailDirection = prevDirection;
//            }
//
//            // Calculate signals
//            boolean trailStopBuy = trailDirection == 1 && prevDirection == -1;
//            boolean trailStopSell = trailDirection == -1 && prevDirection == 1;
//
//            // Combined system signals
//            int baselineTrend = currentCandle.getClose() > baseline[i] ? 1 :
//                    currentCandle.getClose() < baseline[i] ? -1 : 0;
//
//            int whiteLineTrend = currentCandle.getClose() > whiteLine[i] ? 1 :
//                    currentCandle.getClose() < whiteLine[i] ? -1 : 0;
//
//            boolean ttmLongFinal = calculateTTMLongSignal(i, momentum, noSqueeze, config);
//            boolean ttmShortFinal = calculateTTMShortSignal(i, momentum, noSqueeze, config);
//
//            int tetherTrend = tetherFast[i] > tetherSlow[i] ? 1 :
//                    tetherFast[i] < tetherSlow[i] ? -1 : 0;
//
//            int vortexSignal = vortexPlus[i] > vortexMinus[i] ? 1 :
//                    vortexPlus[i] < vortexMinus[i] ? -1 : 0;
//
//            double vortexDiff = Math.abs(vortexPlus[i] - vortexMinus[i]);
//            boolean vortexConfirm = vortexDiff > config.getVortexThreshold();
//
//            // Final entry/exit signals
//            boolean systemLongSignal = baselineTrend == 1 && whiteLineTrend == 1 &&
//                    ttmLongFinal && tetherTrend == 1 &&
//                    (!config.isUseVortexFilter() ||
//                            (vortexSignal == 1 && vortexConfirm));
//
//            boolean systemShortSignal = baselineTrend == -1 && whiteLineTrend == -1 &&
//                    ttmShortFinal && tetherTrend == -1 &&
//                    (!config.isUseVortexFilter() ||
//                            (vortexSignal == -1 && vortexConfirm));
//
//            boolean systemEntryLong = systemLongSignal && trailStopBuy;
//            boolean systemExitLong = trailStopSell;
//            boolean systemEntryShort = systemShortSignal && trailStopSell;
//            boolean systemExitShort = trailStopBuy;
//
//            // Generate trading signals
//            if (systemEntryLong) {
//                signals.add(new TradingSignal(SignalType.ENTRY_LONG, currentCandle.getClose(), currentCandle));
//            } else if (systemExitLong) {
//                signals.add(new TradingSignal(SignalType.EXIT_LONG, currentCandle.getClose(), currentCandle));
//            } else if (systemEntryShort) {
//                signals.add(new TradingSignal(SignalType.ENTRY_SHORT, currentCandle.getClose(), currentCandle));
//            } else if (systemExitShort) {
//                signals.add(new TradingSignal(SignalType.EXIT_SHORT, currentCandle.getClose(), currentCandle));
//            }
//        }
//
//        return signals;
//    }
//
//    // Indicator calculation methods
//    private double calculateMcGinleyDynamic(List<Candle> candles, int length) {
//        if (candles.size() < length) {
//            return candles.get(candles.size() - 1).getClose();
//        }
//
//        double mg = 0;
//        boolean first = true;
//
//        for (int i = length; i < candles.size(); i++) {
//            double src = candles.get(i).getClose();
//
//            if (first) {
//                // Initialize with EMA
//                double sum = 0;
//                for (int j = i - length; j < i; j++) {
//                    sum += candles.get(j).getClose();
//                }
//                mg = sum / length;
//                first = false;
//            } else {
//                mg = mg + (src - mg) / (length * Math.pow(src / mg, 4));
//            }
//        }
//
//        return mg;
//    }
//
//    private double calculateWhiteLine(List<Candle> candles, int length) {
//        int startIdx = candles.size() - length;
//        if (startIdx < 0) startIdx = 0;
//
//        double highest = Double.MIN_VALUE;
//        double lowest = Double.MAX_VALUE;
//
//        for (int i = startIdx; i < candles.size(); i++) {
//            highest = Math.max(highest, candles.get(i).getHigh());
//            lowest = Math.min(lowest, candles.get(i).getLow());
//        }
//
//        return (highest + lowest) / 2;
//    }
//
//    private Map<String, Object> calculateTTMSqueeze(List<Candle> candles, int length,
//                                                    double bbMult, double kcMult1,
//                                                    double kcMult2, double kcMult3) {
//        Map<String, Object> result = new HashMap<>();
//        int size = candles.size();
//
//        if (size < length) {
//            result.put("noSqueeze", false);
//            result.put("momentum", 0.0);
//            result.put("basis", candles.get(size-1).getClose());
//            return result;
//        }
//
//        // Calculate basis (SMA)
//        double sum = 0;
//        for (int i = size - length; i < size; i++) {
//            sum += candles.get(i).getClose();
//        }
//        double basis = sum / length;
//
//        // Calculate standard deviation
//        double sumSquared = 0;
//        for (int i = size - length; i < size; i++) {
//            double diff = candles.get(i).getClose() - basis;
//            sumSquared += diff * diff;
//        }
//        double dev = Math.sqrt(sumSquared / length);
//
//        // Bollinger Bands
//        double bbUpper = basis + bbMult * dev;
//        double bbLower = basis - bbMult * dev;
//
//        // Calculate ATR for Keltner Channels
//        double atrSum = 0;
//        for (int i = size - length; i < size; i++) {
//            if (i > 0) {
//                double trueRange = Math.max(candles.get(i).getHigh() - candles.get(i).getLow(),
//                        Math.max(Math.abs(candles.get(i).getHigh() - candles.get(i-1).getClose()),
//                                Math.abs(candles.get(i).getLow() - candles.get(i-1).getClose())));
//                atrSum += trueRange;
//            }
//        }
//        double kcDev = atrSum / length;
//
//        // Keltner Channels
//        double kcUpper1 = basis + kcDev * kcMult1;
//        double kcLower1 = basis - kcDev * kcMult1;
//        double kcUpper2 = basis + kcDev * kcMult2;
//        double kcLower2 = basis - kcDev * kcMult2;
//        double kcUpper3 = basis + kcDev * kcMult3;
//        double kcLower3 = basis - kcDev * kcMult3;
//
//        // Squeeze conditions
//        boolean noSqueeze = bbLower < kcLower3 || bbUpper > kcUpper3;
//
//        // Calculate momentum
//        double highestHigh = Double.MIN_VALUE;
//        double lowestLow = Double.MAX_VALUE;
//        for (int i = size - length; i < size; i++) {
//            highestHigh = Math.max(highestHigh, candles.get(i).getHigh());
//            lowestLow = Math.min(lowestLow, candles.get(i).getLow());
//        }
//        double priceAvg = ((highestHigh + lowestLow) / 2 + basis) / 2;
//
//        // Linear regression (simplified)
//        double momentum = candles.get(size-1).getClose() - priceAvg;
//
//        result.put("noSqueeze", noSqueeze);
//        result.put("momentum", momentum);
//        result.put("basis", basis);
//
//        return result;
//    }
//
//    private double calculateTetherLine(List<Candle> candles, int length) {
//        int startIdx = candles.size() - length;
//        if (startIdx < 0) startIdx = 0;
//
//        double highest = Double.MIN_VALUE;
//        double lowest = Double.MAX_VALUE;
//
//        for (int i = startIdx; i < candles.size(); i++) {
//            highest = Math.max(highest, candles.get(i).getHigh());
//            lowest = Math.min(lowest, candles.get(i).getLow());
//        }
//
//        return (highest + lowest) / 2;
//    }
//
//    private Map<String, Double> calculateVortex(List<Candle> candles, int length) {
//        Map<String, Double> result = new HashMap<>();
//        int size = candles.size();
//
//        if (size <= length) {
//            result.put("plus", 0.0);
//            result.put("minus", 0.0);
//            return result;
//        }
//
//        double vortexPlus = 0;
//        double vortexMinus = 0;
//        double vortexSum = 0;
//
//        for (int i = size - length; i < size; i++) {
//            if (i > 0) {
//                vortexPlus += Math.abs(candles.get(i).getHigh() - candles.get(i-1).getLow());
//                vortexMinus += Math.abs(candles.get(i).getLow() - candles.get(i-1).getHigh());
//
//                double tr = Math.max(candles.get(i).getHigh() - candles.get(i).getLow(),
//                        Math.max(Math.abs(candles.get(i).getHigh() - candles.get(i-1).getClose()),
//                                Math.abs(candles.get(i).getLow() - candles.get(i-1).getClose())));
//                vortexSum += tr;
//            }
//        }
//
//        double vortexPos = vortexPlus / vortexSum;
//        double vortexNeg = vortexMinus / vortexSum;
//
//        result.put("plus", vortexPos);
//        result.put("minus", vortexNeg);
//
//        return result;
//    }
//
//    private double calculateATR(List<Candle> candles, int length) {
//        double atr = 0;
//        int size = candles.size();
//
//        if (size <= 1) return 0;
//
//        for (int i = size - length; i < size; i++) {
//            if (i > 0) {
//                double trueRange = Math.max(candles.get(i).getHigh() - candles.get(i).getLow(),
//                        Math.max(Math.abs(candles.get(i).getHigh() - candles.get(i-1).getClose()),
//                                Math.abs(candles.get(i).getLow() - candles.get(i-1).getClose())));
//                atr += trueRange;
//            }
//        }
//
//        return atr / length;
//    }
//
//    private double getTrailStopPrice(Candle candle, String source) {
//        switch (source) {
//            case "hl2": return (candle.getHigh() + candle.getLow()) / 2;
//            case "close": return candle.getClose();
//            case "open": return candle.getOpen();
//            case "high": return candle.getHigh();
//            case "low": return candle.getLow();
//            case "ohlc4": return (candle.getOpen() + candle.getHigh() + candle.getLow() + candle.getClose()) / 4;
//            default: return (candle.getHigh() + candle.getLow()) / 2;
//        }
//    }
//
//    private boolean isDoji(Candle candle) {
//        return candle.getOpen() == candle.getClose() &&
//                candle.getOpen() == candle.getLow() &&
//                candle.getOpen() == candle.getHigh();
//    }
//
//    private boolean calculateTTMLongSignal(int index, double[] momentum, boolean[] noSqueeze,
//                                           VSEStrategy config) {
//        if (!config.isUseTtmSqueeze()) return true;
//
//        boolean momentumRising = index > 0 && momentum[index] > momentum[index-1];
//        boolean isMomentumPositive = momentum[index] > 0;
//
//        boolean basicLong = config.isTtmGreenRedOnly() ?
//                momentumRising && isMomentumPositive : isMomentumPositive;
//
//        boolean longSignal = config.isTtmHighlightGreenDots() ?
//                noSqueeze[index] && basicLong : basicLong;
//
//        boolean longCross = config.isTtmCrossConfirmation() ?
//                (index > 0 && !longSignal && longSignal) : longSignal;
//
//        return config.isTtmInverseSignals() ? !longCross : longCross;
//    }
//
//    private boolean calculateTTMShortSignal(int index, double[] momentum, boolean[] noSqueeze,
//                                            VSEStrategy config) {
//        if (!config.isUseTtmSqueeze()) return true;
//
//        boolean momentumFalling = index > 0 && momentum[index] < momentum[index-1];
//        boolean isMomentumNegative = momentum[index] < 0;
//
//        boolean basicShort = config.isTtmGreenRedOnly() ?
//                momentumFalling && isMomentumNegative : isMomentumNegative;
//
//        boolean shortSignal = config.isTtmHighlightGreenDots() ?
//                noSqueeze[index] && basicShort : basicShort;
//
//        boolean shortCross = config.isTtmCrossConfirmation() ?
//                (index > 0 && !shortSignal && shortSignal) : shortSignal;
//
//        return config.isTtmInverseSignals() ? !shortCross : shortCross;
//    }
//}
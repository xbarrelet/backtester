package ch.xavier.backtester.strategy;

import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.strategy.concrete.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

@Component
@Slf4j
public class StrategiesFactory {

    public static TradingStrategy getStrategy(String strategyName, TradingParameters tradingParameters,
                                              Map<String, Object> strategyParameters) {
        TradingStrategy strategy = getStrategy(strategyName, tradingParameters);
        strategy.setParameters(strategyParameters);
        return strategy;
    }

    public static List<Map<String, Object>> generateAllStrategyParameters(Map<String, List<Object>> strategyParameters) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(new HashMap<>());

        for (Map.Entry<String, List<Object>> entry : strategyParameters.entrySet()) {
            String paramName = entry.getKey();
            List<Object> paramValues = entry.getValue();

            List<Map<String, Object>> newCombinations = new ArrayList<>();

            for (Map<String, Object> combination : result) {
                for (Object value : paramValues) {
                    Map<String, Object> newCombination = new HashMap<>(combination);
                    newCombination.put(paramName, value);
                    newCombinations.add(newCombination);
                }
            }

            result = newCombinations;
        }

        log.info("Backtesting {} strategy parameter combinations.", result.size());
        return result;
    }

    private static TradingStrategy getStrategy(String strategyName, TradingParameters tradingParameters) {
        return switch (strategyName) {
            case "SMACrossover" -> new SMACrossoverStrategy(tradingParameters);
            case "VortexStrategy" -> new VortexStrategy(tradingParameters);
            case "ZScoreStrategy" -> new ZScoreStrategy(tradingParameters);
            case "DoubleTapStrategy" -> new DoubleTapStrategy(tradingParameters);
            case "DivergenceStrategy" -> new DivergenceStrategy(tradingParameters);
            case "PullbackStrategy" -> new PullbackStrategy(tradingParameters);
            case "MacdDivergenceStrategy" -> new MacdDivergenceStrategy(tradingParameters);
            case "VwapStrategy" -> new VwapStrategy(tradingParameters);

            default -> {
                log.error("Unknown strategy name: {}", strategyName);
                throw new RuntimeException("Unknown strategy name: " + strategyName);
            }
        };
    }

    public static Map<String, List<Object>> getParametersGrid(String strategyName) {
        return switch (strategyName) {
            case "VwapStrategy" -> Map.ofEntries(
                    Map.entry("lookbackPeriod", generateIntRange(1, 30, 1)),
                    Map.entry("distanceThreshold", generateDoubleRange(0.001, 0.005, 0.001)),
                    Map.entry("riskRewardRatio", generateDoubleRange(1.5, 3.0, 0.1)),
                    Map.entry("useRiskBasedPositionSizing", List.of(true, false)),
                    Map.entry("useTrailingSL", List.of(false, true))
            );

            case "MacdDivergenceStrategy" -> Map.ofEntries(
                    Map.entry("fastLength", generateIntRange(8, 16, 4)),
                    Map.entry("slowLength", generateIntRange(20, 32, 6)),
                    Map.entry("signalLength", generateIntRange(7, 13, 3)),
                    Map.entry("pivotLookbackLeft", generateIntRange(3, 7, 2)),
                    Map.entry("pivotLookbackRight", generateIntRange(3, 7, 2)),
                    Map.entry("rangeLower", generateIntRange(3, 10, 3)),
                    Map.entry("rangeUpper", generateIntRange(40, 80, 20)),
                    Map.entry("dontTouchZero", List.of(true)),
                    Map.entry("riskRewardRatio", generateDoubleRange(1.5, 3.0, 0.1)),
                    Map.entry("useTrailingSL", List.of(false, true))
            );

            case "PullbackStrategy" -> Map.of(
                    // EMA parameters
//                    "ema20Period", generateIntRange(10, 30, 5),
//                    "ema50Period", generateIntRange(40, 60, 5),

                    // Stochastic parameters - using key values from strategy
                    "stochLowThreshold", generateDoubleRange(15.0, 25.0, 5.0),
                    "stochHighThreshold", generateDoubleRange(80.0, 95.0, 5.0),

                    // Strategy parameters
                    "lookbackPeriod", generateIntRange(5, 15, 5),
                    "minPriceChangePercent", generateDoubleRange(0.3, 0.8, 0.1),
                    "onlyLongTrades", List.of(true),

                    // Risk management
                    "riskRewardRatio", generateDoubleRange(1.5, 3.0, 0.25),
                    "useTrailingSL", List.of(false, true)
            );

            case "DivergenceStrategy" -> Map.of(
// Stochastic settings - using your default values
//                    "fastStoch1K", List.of(9),
//                    "fastStoch1D", List.of(3),
//                    "fastStoch2K", List.of(14),
//                    "fastStoch2D", List.of(3),
//                    "fastStoch3K", List.of(40),
//                    "fastStoch3D", List.of(4),
//                    "fullStochK", List.of(60),
//                    "fullStochD", List.of(10),
//                    "fullStochSmooth", List.of(1),
                    "tradeLongOnly", List.of(true, false),

                    // Divergence parameters
                    "oversoldThreshold", generateDoubleRange(15.0, 25.0, 5.0),
                    "lookbackPeriod", generateIntRange(15, 30, 5),
                    "minPriceChange", generateDoubleRange(0.3, 1.0, 0.2),
                    "requireAllStochastics", List.of(false, true),
                    "requireReversalCandle", List.of(true, false),

                    // Risk management
                    "riskRewardRatio", generateDoubleRange(1.5, 3.0, 0.25),
                    "useTrailingSL", List.of(false, true)
//                    "atrLength", List.of(14),
//                    "atrMultiplier", generateDoubleRange(1.5, 3.0, 0.5)
            );

            case "DoubleTapStrategy" -> Map.of(
                    // Core pattern recognition parameters
                    "pivotLength", generateIntRange(20, 80, 10),        // Pivot detection lookback
                    "pivotTolerance", generateDoubleRange(5.0, 20.0, 5.0), // % tolerance for second pivot
                    "targetFib", generateDoubleRange(50.0, 150.0, 25.0),   // Target projection %
                    "stopFib", generateDoubleRange(0.0, 50.0, 10.0),      // Stop loss projection %

                    // Direction settings
                    "detectBottoms", List.of(true, false),              // Detect W patterns
                    "detectTops", List.of(true, false),                // Detect M patterns

                    // Risk management
                    "riskRewardRatio", generateDoubleRange(1.0, 3.0, 0.5), // Risk:reward ratio
                    "useTrailingSL", List.of(false),             // Use trailing stops

                    // Trailing stop parameters (if enabled)
                    "atrLength", generateIntRange(7, 21, 7),           // ATR period
                    "atrMultiplier", generateDoubleRange(1.0, 3.0, 0.5)  // ATR multiplier
            );

            case "VortexStrategy" -> Map.of(
                    "mcGinleyLength", generateIntRange(7, 24, 3),
                    "whiteLineLength", generateIntRange(10, 30, 5),
//                "tetherFastLength", generateIntRange(8, 20, 2),
//                "tetherSlowLength", generateIntRange(35, 80, 5),
//                "vortexLength", generateIntRange(10, 20, 2),
//                "vortexThreshold", generateDoubleRange(0.03, 0.07, 0.01),
                    "riskRewardRatio", generateDoubleRange(1.0, 4.0, 0.5),
                    "atrLength", generateIntRange(7, 14, 1),
                    "useRiskBasedPositionSizing", List.of("false"),
//                "useRiskBasedPositionSizing", List.of("true", "false"),
                    "useTrailingSL", List.of("false")
//                "useTrailingSL", List.of("true", "false")
            );

            default -> throw new IllegalStateException("Unexpected value: " + strategyName);
        };
    }

    private static List<Object> generateIntRange(int from, int to, int interval) {
        return IntStream.iterate(from, n -> n <= to, n -> n + interval)
                .boxed()
                .map(i -> (Object) i)
                .toList();
    }

    private static List<Object> generateDoubleRange(double from, double to, double interval) {
        return DoubleStream.iterate(from, n -> n <= to, n -> n + interval)
                .boxed()
                .map(i -> (Object) i)
                .toList();
    }

}
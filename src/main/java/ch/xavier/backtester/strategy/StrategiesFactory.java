package ch.xavier.backtester.strategy;

import ch.xavier.backtester.backtesting.TradingParameters;
import ch.xavier.backtester.strategy.concrete.SMACrossoverStrategy;
import ch.xavier.backtester.strategy.concrete.VortexStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        switch (strategyName){
            case "SMACrossover": return new SMACrossoverStrategy(tradingParameters);
            case "VortexStrategy": return new VortexStrategy(tradingParameters);

            default: {
                log.error("Unknown strategy name: {}", strategyName);
                return null;
            }
        }
    }
}
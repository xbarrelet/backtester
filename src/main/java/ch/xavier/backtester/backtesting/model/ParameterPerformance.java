package ch.xavier.backtester.backtesting.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class ParameterPerformance {
    private final Map<String, Object> parameters;
    private final BacktestResult result;
    private final double performanceMetric;
}
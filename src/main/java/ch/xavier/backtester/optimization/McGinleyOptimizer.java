package ch.xavier.backtester.optimization;

import ch.xavier.backtester.indicator.McGinley;
import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import ch.xavier.backtester.quote.Quote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class McGinleyOptimizer {

    public OptimizationResult optimizeForPhase(List<Quote> quotes, MarketPhaseClassifier.MarketPhase phase) {
        log.info("Optimizing McGinley for {} market phase with {} quotes", phase, quotes.size());

        // Parameter ranges to test
        List<Integer> periodLengths = List.of(5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
                22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46,
                47, 48, 49, 50);

        List<ParameterResult> results = new ArrayList<>();

        // For each parameter combination, run a backtest
        for (Integer period : periodLengths) {
            McGinley mcginley = new McGinley(period);

            // Run backtest with this configuration
            PerformanceMetrics metrics = backtestMcGinley(quotes, mcginley);

            results.add(new ParameterResult(period, metrics));
            log.info("Period: {}, Win rate: {}, Profit Factor: {}",
                    period, metrics.getWinRate(), metrics.getProfitFactor());
        }

        // Sort results based on phase-specific criteria
        results.sort((r1, r2) -> compareResultsByPhase(r1, r2, phase));

        // Return best parameters and metrics
        ParameterResult best = results.get(0);
        log.info("Best McGinley period for {} market: {}", phase, best.getPeriod());

        return new OptimizationResult(best.getPeriod(), best.getMetrics());
    }

    private int compareResultsByPhase(ParameterResult r1, ParameterResult r2,
                                      MarketPhaseClassifier.MarketPhase phase) {
        PerformanceMetrics m1 = r1.getMetrics();
        PerformanceMetrics m2 = r2.getMetrics();

        switch (phase) {
            case BULLISH:
                // For bull markets, prioritize return metrics
                return Double.compare(m2.getProfitFactor() * m2.getTotalReturn(),
                        m1.getProfitFactor() * m1.getTotalReturn());

            case BEARISH:
                // For bear markets, prioritize drawdown control and win rate
                return Double.compare(m2.getWinRate() / m2.getMaxDrawdown(),
                        m1.getWinRate() / m1.getMaxDrawdown());

            case SIDEWAYS:
                // For sideways markets, prioritize consistency and reduced exposure
                return Double.compare(m2.getWinRate() * (1 - m2.getMarketExposure()),
                        m1.getWinRate() * (1 - m1.getMarketExposure()));

            default:
                // Default comparison by profit factor
                return Double.compare(m2.getProfitFactor(), m1.getProfitFactor());
        }
    }

    private PerformanceMetrics backtestMcGinley(List<Quote> quotes, McGinley mcginley) {
        int wins = 0;
        int losses = 0;
        double totalProfit = 0;
        double totalLoss = 0;
        double maxDrawdown = 0;
        double peak = 0;
        double equity = 1000; // Starting equity
        int inMarketDays = 0;

        boolean inPosition = false;
        double entryPrice = 0;

        // Backtest logic
        for (int i = 1; i < quotes.size(); i++) {
            double mgValue = mcginley.calculate(i - 1, quotes);
            double prevClose = quotes.get(i - 1).getClose().doubleValue();
            double currentClose = quotes.get(i).getClose().doubleValue();

            // Signal generation - same logic as your PineScript
            int baselineTrend = prevClose > mgValue ? 1 : prevClose < mgValue ? -1 : 0;

            // Simple trend following logic - enter when trend starts, exit when it ends
            if (!inPosition && baselineTrend == 1) {
                // Enter long
                entryPrice = currentClose;
                inPosition = true;
            } else if (inPosition && baselineTrend != 1) {
                // Exit long
                double pnl = (currentClose - entryPrice) / entryPrice;
                equity *= (1 + pnl);

                if (pnl > 0) wins++; else losses++;
                if (pnl > 0) totalProfit += pnl; else totalLoss -= pnl;

                inPosition = false;
            }

            // Track metrics
            if (inPosition) inMarketDays++;

            // Track drawdown
            if (equity > peak) {
                peak = equity;
            }
            double drawdown = (peak - equity) / peak;
            maxDrawdown = Math.max(maxDrawdown, drawdown);
        }

        // Calculate final metrics
        double winRate = wins / (double)(wins + losses);
        double profitFactor = totalLoss > 0 ? totalProfit / totalLoss : totalProfit;
        double totalReturn = (equity - 1000) / 1000;
        double marketExposure = inMarketDays / (double)quotes.size();

        return new PerformanceMetrics(winRate, profitFactor, totalReturn,
                maxDrawdown, marketExposure);
    }

    @Data
    public static class OptimizationResult {
        private final int bestPeriod;
        private final PerformanceMetrics metrics;
    }

    @Data
    @AllArgsConstructor
    public static class ParameterResult {
        private final int period;
        private final PerformanceMetrics metrics;
    }

    @Data
    @AllArgsConstructor
    public static class PerformanceMetrics {
        private final double winRate;
        private final double profitFactor;
        private final double totalReturn;
        private final double maxDrawdown;
        private final double marketExposure;
    }
}
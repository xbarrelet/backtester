package ch.xavier.backtester.backtesting;

import ch.xavier.backtester.indicator.McGinley;
import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import ch.xavier.backtester.quote.Quote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RealisticBacktesterService {

    public WalkForwardResult walkForwardTest(
            Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes) {

        // Prepare trading parameters
        TradingParameters params = TradingParameters.builder().build();

        // Create chronological list of all quotes for the whole period analysis
        List<Quote> allQuotes = combineAndSortQuotes(marketPhaseQuotes);

        // Map to track optimal periods found for each phase during walk-forward
        Map<MarketPhaseClassifier.MarketPhase, Integer> optimalPeriods = new HashMap<>();

        // Initialize list for storing results of each window
        List<BacktestResult> windowResults = new ArrayList<>();

        // Run walk-forward optimization
        for (int startIdx = 0; startIdx + params.getWalkForwardWindow() + params.getTestWindow() <= allQuotes.size();
             startIdx += params.getWalkForwardStep()) {

            // Define in-sample (training) and out-of-sample (testing) windows
            int trainEndIdx = startIdx + params.getWalkForwardWindow();
            int testEndIdx = Math.min(trainEndIdx + params.getTestWindow(), allQuotes.size());

            List<Quote> trainQuotes = allQuotes.subList(startIdx, trainEndIdx);
            List<Quote> testQuotes = allQuotes.subList(trainEndIdx, testEndIdx);

            log.info("Walk-forward window: Training from {} to {}, Testing from {} to {}",
                    trainQuotes.get(0).getTimestamp(),
                    trainQuotes.get(trainQuotes.size() - 1).getTimestamp(),
                    testQuotes.get(0).getTimestamp(),
                    testQuotes.get(testQuotes.size() - 1).getTimestamp());

            // Classify market phases for the training period
            Map<MarketPhaseClassifier.MarketPhase, List<Quote>> trainPhaseQuotes =
                    classifyQuotesInWindow(trainQuotes, marketPhaseQuotes);

            // Find optimal parameters for each phase
            for (Map.Entry<MarketPhaseClassifier.MarketPhase, List<Quote>> entry : trainPhaseQuotes.entrySet()) {
                MarketPhaseClassifier.MarketPhase phase = entry.getKey();
                List<Quote> phasedQuotes = entry.getValue();

                if (phase != MarketPhaseClassifier.MarketPhase.UNKNOWN && phasedQuotes.size() >= 30) {
                    int optimalPeriod = findOptimalPeriod(phasedQuotes, phase, params);
                    optimalPeriods.put(phase, optimalPeriod);
                    log.info("Found optimal period for {} phase: {}", phase, optimalPeriod);
                }
            }

            // Test on out-of-sample data with optimized parameters
            Map<MarketPhaseClassifier.MarketPhase, List<Quote>> testPhaseQuotes =
                    classifyQuotesInWindow(testQuotes, marketPhaseQuotes);

            BacktestResult windowResult = backtestWithOptimizedParameters(testPhaseQuotes, optimalPeriods, params);
            windowResults.add(windowResult);

            log.info("Window result: Final equity: ${}, Sharpe: {}, Win rate: {}%, Profit factor: {}",
                    String.format("%.2f", windowResult.getFinalEquity()),
                    String.format("%.2f", windowResult.getSharpeRatio()),
                    String.format("%.2f", windowResult.getWinRate() * 100),
                    String.format("%.2f", windowResult.getProfitFactor()));
        }

        // Aggregate results across all windows
        BacktestResult aggregatedResult = aggregateResults(windowResults);

        return new WalkForwardResult(windowResults, aggregatedResult, optimalPeriods);
    }

    private List<Quote> combineAndSortQuotes(Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes) {
        return marketPhaseQuotes.values().stream()
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(Quote::getTimestamp))
                .distinct() // Avoid duplicates
                .collect(Collectors.toList());
    }

    private Map<MarketPhaseClassifier.MarketPhase, List<Quote>> classifyQuotesInWindow(
            List<Quote> windowQuotes,
            Map<MarketPhaseClassifier.MarketPhase, List<Quote>> allPhasedQuotes) {

        // Map timestamps to phases using the full classification
        Map<Long, MarketPhaseClassifier.MarketPhase> timestampToPhase = new HashMap<>();

        for (Map.Entry<MarketPhaseClassifier.MarketPhase, List<Quote>> entry : allPhasedQuotes.entrySet()) {
            MarketPhaseClassifier.MarketPhase phase = entry.getKey();
            for (Quote quote : entry.getValue()) {
                timestampToPhase.put(quote.getTimestamp().getTime(), phase);
            }
        }

        // Classify window quotes based on the full classification
        Map<MarketPhaseClassifier.MarketPhase, List<Quote>> result = new HashMap<>();

        for (Quote quote : windowQuotes) {
            MarketPhaseClassifier.MarketPhase phase = timestampToPhase.getOrDefault(
                    quote.getTimestamp().getTime(),
                    MarketPhaseClassifier.MarketPhase.UNKNOWN);

            result.computeIfAbsent(phase, k -> new ArrayList<>()).add(quote);
        }

        return result;
    }

    private int findOptimalPeriod(List<Quote> quotes,
                                  MarketPhaseClassifier.MarketPhase phase,
                                  TradingParameters params) {

        // Parameter ranges to test
        List<Integer> periodLengths = List.of(8, 10, 12, 14, 16, 18, 20, 22, 24);

        int bestPeriod = 14; // Default
        double bestMetric = -Double.MAX_VALUE;

        for (Integer period : periodLengths) {
            McGinley mcginley = new McGinley(period);

            // Backtest with specific parameters
            BacktestResult result = backtest(quotes, mcginley, phase, params);

            // Calculate combined quality metric based on phase
            double metric = calculatePhaseSpecificMetric(result, phase);

            if (metric > bestMetric) {
                bestMetric = metric;
                bestPeriod = period;
            }
        }

        return bestPeriod;
    }

    private double calculatePhaseSpecificMetric(BacktestResult result,
                                                MarketPhaseClassifier.MarketPhase phase) {
        switch (phase) {
            case BULLISH:
                // For bull markets, prioritize return and Sharpe
                return result.getTotalReturn() * result.getSharpeRatio();

            case BEARISH:
                // For bear markets, prioritize preservation and Sortino
                return result.getSortinoRatio() * (1 - result.getMaxDrawdown());

            case SIDEWAYS:
                // For sideways markets, prioritize consistency and profit factor
                return result.getWinRate() * result.getProfitFactor();

            default:
                return result.getSharpeRatio();
        }
    }

    private BacktestResult backtestWithOptimizedParameters(
            Map<MarketPhaseClassifier.MarketPhase, List<Quote>> phaseQuotes,
            Map<MarketPhaseClassifier.MarketPhase, Integer> optimalPeriods,
            TradingParameters params) {

        Map<MarketPhaseClassifier.MarketPhase, BacktestResult> phaseResults = new HashMap<>();
        List<Trade> allTrades = new ArrayList<>();
        double initialCapital = params.getInitialCapital();
        double equity = initialCapital;

        // Backtest each phase separately with its optimal parameters
        for (Map.Entry<MarketPhaseClassifier.MarketPhase, List<Quote>> entry : phaseQuotes.entrySet()) {
            MarketPhaseClassifier.MarketPhase phase = entry.getKey();
            List<Quote> quotes = entry.getValue();

            if (phase == MarketPhaseClassifier.MarketPhase.UNKNOWN || quotes.size() < 30) {
                continue;
            }

            // Get optimal period for this phase, fall back to default if not found
            int period = optimalPeriods.getOrDefault(phase, 14);
            McGinley mcginley = new McGinley(period);

            // Backtest this phase with allocated capital
            BacktestResult phaseResult = backtest(quotes, mcginley, phase, params);

            // Scale phase equity based on overall equity
            double phaseEquityShare = equity * (quotes.size() /
                    (double) phaseQuotes.values().stream().mapToInt(List::size).sum());

            // Scale trades and results by current equity
            double equityScale = phaseEquityShare / params.getInitialCapital();
            List<Trade> scaledTrades = scaleTrades(phaseResult.getPhaseResults().get(phase).getTrades(), equityScale);
            allTrades.addAll(scaledTrades);

            // Update equity
            double phaseReturn = phaseResult.getTotalReturn();
            equity += phaseEquityShare * phaseReturn;

            // Store phase result
            phaseResults.put(phase, phaseResult);
        }

        // Calculate aggregated metrics
        return calculateResults(allTrades, equity, initialCapital);
    }

    private List<Trade> scaleTrades(List<Trade> trades, double scale) {
        // Implementation to scale trade sizes by a factor
        return trades.stream().map(t -> {
            Trade scaledTrade = new Trade(
                    t.getEntryTime(),
                    t.getExitTime(),
                    t.getEntryPrice(),
                    t.getExitPrice(),
                    t.getSize() * scale,
                    t.getProfit(),
                    t.getProfitAmount() * scale,
                    t.isLong(),
                    t.getMaxAdverseExcursion(),
                    t.getMaxFavorableExcursion(),
                    t.getMarketPhase()
            );
            return scaledTrade;
        }).collect(Collectors.toList());
    }

    public BacktestResult backtest(List<Quote> quotes, McGinley mcginley,
                                   MarketPhaseClassifier.MarketPhase phase,
                                   TradingParameters params) {

        double equity = params.getInitialCapital();
        double peak = equity;
        List<Position> openPositions = new ArrayList<>();
        List<Trade> completedTrades = new ArrayList<>();

        // Track daily returns for Sharpe & Sortino calculation
        Map<String, Double> dailyReturns = new HashMap<>();
        String currentDay = "";
        double dayStartEquity = equity;

        for (int i = Math.max(params.getAtrLength(), mcginley.getLength()); i < quotes.size(); i++) {
            Quote quote = quotes.get(i);

            // Track a new day for returns calculation
            String quoteDay = new java.text.SimpleDateFormat("yyyy-MM-dd")
                    .format(quote.getTimestamp());

            if (!quoteDay.equals(currentDay)) {
                if (!currentDay.isEmpty()) {
                    dailyReturns.put(currentDay, (equity - dayStartEquity) / dayStartEquity);
                }
                currentDay = quoteDay;
                dayStartEquity = equity;
            }

            // Update positions & check for exits
            updatePositions(openPositions, quote, quotes, i, completedTrades, params);

            // Check if we can open a new position when we have none open
            if (openPositions.isEmpty()) {
                int signal = generateSignal(mcginley, quotes, i);

                if (signal != 0) {
                    double atr = calculateATR(quotes, i, params.getAtrLength());
                    double stopDistance = atr * params.getAtrMultiplier();
                    double riskAmount = equity * params.getRiskPerTrade();
                    double unitSize = calculatePositionSize(
                            riskAmount,
                            stopDistance,
                            quote.getClose().doubleValue(),
                            params.getLeverage());

                    double entryPrice = quote.getClose().doubleValue();
                    double stopPrice = signal > 0 ?
                            entryPrice - stopDistance :
                            entryPrice + stopDistance;

                    double targetPrice = signal > 0 ?
                            entryPrice + (stopDistance * params.getRiskRewardRatio()) :
                            entryPrice - (stopDistance * params.getRiskRewardRatio());

                    // Create position
                    Position position = Position.builder()
                            .entryPrice(entryPrice)
                            .size(unitSize)
                            .currentStopPrice(stopPrice)
                            .targetPrice(targetPrice)
                            .isLong(signal > 0)
                            .entryIndex(i)
                            .marketPhase(phase)
                            .priceHistory(new ArrayList<>())
                            .build();

                    // Account for fees
                    double entryFee = entryPrice * unitSize * params.getTakerFee();
                    equity -= entryFee;

                    openPositions.add(position);
                }
            }

            // Update equity peak for drawdown calculation
            if (equity > peak) {
                peak = equity;
            }
        }

        // Close remaining open positions
        if (!openPositions.isEmpty() && !quotes.isEmpty()) {
            Quote lastQuote = quotes.get(quotes.size() - 1);
            closeAllPositions(openPositions, lastQuote, quotes, quotes.size() - 1, completedTrades, params);
        }

        // Calculate final day's return
        if (!currentDay.isEmpty()) {
            dailyReturns.put(currentDay, (equity - dayStartEquity) / dayStartEquity);
        }

        // Calculate metrics for this phase
        BacktestResult result = calculateResults(completedTrades, equity, params.getInitialCapital());

        // Calculate Sharpe & Sortino using daily returns
        if (!dailyReturns.isEmpty()) {
            double[] sharpeSortino = calculateSharpeAndSortino(dailyReturns);
            result.setSharpeRatio(sharpeSortino[0]);
            result.setSortinoRatio(sharpeSortino[1]);
        }

        // Track trades by phase for reporting
        Map<MarketPhaseClassifier.MarketPhase, BacktestResult> phaseResults = new HashMap<>();
        phaseResults.put(phase, result);
        result.setPhaseResults(phaseResults);

        return result;
    }

    private double[] calculateSharpeAndSortino(Map<String, Double> dailyReturns) {
        // Calculate mean return
        double sum = dailyReturns.values().stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / dailyReturns.size();

        // Calculate standard deviation for Sharpe
        double squaredSum = dailyReturns.values().stream()
                .mapToDouble(r -> Math.pow(r - mean, 2)).sum();
        double variance = squaredSum / dailyReturns.size();
        double stdDev = Math.sqrt(variance);

        // Calculate downside deviation for Sortino
        double downsideSquaredSum = dailyReturns.values().stream()
                .filter(r -> r < 0)
                .mapToDouble(r -> Math.pow(r, 2))
                .sum();
        double downsideDeviation = Math.sqrt(downsideSquaredSum / dailyReturns.size());

        // Risk-free rate (approx 2% annual -> daily)
        double dailyRiskFree = 0.02 / 365;

        // Annualize (approx 252 trading days)
        double sharpeRatio = stdDev > 0 ?
                (mean - dailyRiskFree) / stdDev * Math.sqrt(252) : 0;
        double sortinoRatio = downsideDeviation > 0 ?
                (mean - dailyRiskFree) / downsideDeviation * Math.sqrt(252) : 0;

        return new double[]{sharpeRatio, sortinoRatio};
    }

    private void updatePositions(List<Position> positions, Quote quote, List<Quote> quotes,
                                 int index, List<Trade> completedTrades, TradingParameters params) {

        double price = quote.getClose().doubleValue();
        Iterator<Position> iterator = positions.iterator();

        while (iterator.hasNext()) {
            Position position = iterator.next();
            position.getPriceHistory().add(price);

            boolean exitPosition = false;

            // Check for target hit
            if ((position.isLong() && price >= position.getTargetPrice()) ||
                    (!position.isLong() && price <= position.getTargetPrice())) {
                exitPosition = true;
            }

            // Check for stop hit
            if ((position.isLong() && price <= position.getCurrentStopPrice()) ||
                    (!position.isLong() && price >= position.getCurrentStopPrice())) {
                exitPosition = true;
            }

            if (exitPosition) {
                // Calculate profit
                double profit = position.isLong() ?
                        (price - position.getEntryPrice()) / position.getEntryPrice() :
                        (position.getEntryPrice() - price) / position.getEntryPrice();

                // Account for fees
                double exitFee = price * position.getSize() * params.getTakerFee();

                // Add trade
                completedTrades.add(new Trade(
                        quotes.get(position.getEntryIndex()).getTimestamp(),
                        quote.getTimestamp(),
                        position.getEntryPrice(),
                        price,
                        position.getSize(),
                        profit,
                        position.getEntryPrice() * position.getSize() * profit - exitFee, // Profit amount
                        position.isLong(),
                        calculateMAE(position.getPriceHistory(), position.isLong(), position.getEntryPrice()),
                        calculateMFE(position.getPriceHistory(), position.isLong(), position.getEntryPrice()),
                        position.getMarketPhase()
                ));

                // Remove position
                iterator.remove();
            } else {
                // Update trailing stop if price moved favorably
                updateTrailingStop(position, price, quotes, index, params.getAtrLength(), params.getAtrMultiplier());
            }
        }
    }

    private void closeAllPositions(List<Position> positions, Quote quote, List<Quote> allQuotes,
                                   int index, List<Trade> completedTrades, TradingParameters params) {
        double price = quote.getClose().doubleValue();

        for (Position position : positions) {
            // Calculate profit
            double profit = position.isLong() ?
                    (price - position.getEntryPrice()) / position.getEntryPrice() :
                    (position.getEntryPrice() - price) / position.getEntryPrice();

            // Account for fees
            double exitFee = price * position.getSize() * params.getTakerFee();

            // Add trade
            completedTrades.add(new Trade(
                    allQuotes.get(position.getEntryIndex()).getTimestamp(),
                    quote.getTimestamp(),
                    position.getEntryPrice(),
                    price,
                    position.getSize(),
                    profit,
                    position.getEntryPrice() * position.getSize() * profit - exitFee, // Profit amount
                    position.isLong(),
                    calculateMAE(position.getPriceHistory(), position.isLong(), position.getEntryPrice()),
                    calculateMFE(position.getPriceHistory(), position.isLong(), position.getEntryPrice()),
                    position.getMarketPhase()
            ));
        }

        positions.clear();
    }

    private void updateTrailingStop(Position position, double price, List<Quote> quotes,
                                    int index, int atrLength, double atrMultiplier) {
        // ATR-based trailing stop
        double atr = calculateATR(quotes, index, atrLength);
        double atrStop = position.isLong() ?
                price - (atr * atrMultiplier) :
                price + (atr * atrMultiplier);

        // Only move stop if it improves position
        if (position.isLong() && atrStop > position.getCurrentStopPrice()) {
            position.setCurrentStopPrice(atrStop);
        } else if (!position.isLong() && atrStop < position.getCurrentStopPrice()) {
            position.setCurrentStopPrice(atrStop);
        }
    }

    private double calculateMAE(List<Double> priceHistory, boolean isLong, double entryPrice) {
        // Maximum Adverse Excursion - worst point during trade
        double mae = 0;
        for (double price : priceHistory) {
            double excursion = isLong ?
                    (price - entryPrice) / entryPrice :
                    (entryPrice - price) / entryPrice;
            mae = Math.min(mae, excursion);
        }
        return mae;
    }

    private double calculateMFE(List<Double> priceHistory, boolean isLong, double entryPrice) {
        // Maximum Favorable Excursion - best point during trade
        double mfe = 0;
        for (double price : priceHistory) {
            double excursion = isLong ?
                    (price - entryPrice) / entryPrice :
                    (entryPrice - price) / entryPrice;
            mfe = Math.max(mfe, excursion);
        }
        return mfe;
    }

    private int generateSignal(McGinley mcginley, List<Quote> quotes, int index) {
        if (index <= 0) return 0;

        double currentPrice = quotes.get(index).getClose().doubleValue();
        double previousPrice = quotes.get(index - 1).getClose().doubleValue();

        double currentMcGinley = mcginley.calculate(index, quotes);
        double previousMcGinley = mcginley.calculate(index - 1, quotes);

        // McGinley baseline trend logic from user's code
        int currentTrend = currentPrice > currentMcGinley ? 1 : currentPrice < currentMcGinley ? -1 : 0;
        int previousTrend = previousPrice > previousMcGinley ? 1 : previousPrice < previousMcGinley ? -1 : 0;

        // Entry signal on trend change
        if (currentTrend == 1 && previousTrend <= 0) {
            return 1; // Long signal
        } else if (currentTrend == -1 && previousTrend >= 0) {
            return -1; // Short signal
        }

        return 0; // No signal
    }

    private double calculateATR(List<Quote> quotes, int index, int length) {
        if (index < length) return 0;

        double sum = 0;
        for (int i = index - length + 1; i <= index; i++) {
            Quote current = quotes.get(i);
            Quote previous = quotes.get(i - 1);

            double tr1 = current.getHigh().doubleValue() - current.getLow().doubleValue();
            double tr2 = Math.abs(current.getHigh().doubleValue() - previous.getClose().doubleValue());
            double tr3 = Math.abs(current.getLow().doubleValue() - previous.getClose().doubleValue());

            sum += Math.max(Math.max(tr1, tr2), tr3);
        }

        return sum / length;
    }

    private double calculatePositionSize(double riskAmount, double stopDistance,
                                         double price, double leverage) {
        double contractValue = price;
        double positionSize = (riskAmount / stopDistance) * leverage / contractValue;
        return positionSize;
    }

    private BacktestResult calculateResults(List<Trade> trades, double finalEquity, double initialCapital) {
        if (trades.isEmpty()) {
            return BacktestResult.builder()
                    .finalEquity(finalEquity)
                    .totalReturn((finalEquity - initialCapital) / initialCapital)
                    .winRate(0)
                    .profitFactor(0)
                    .totalTrades(0)
                    .build();
        }

        int winningCount = 0;
        int losingCount = 0;
        double totalProfit = 0;
        double totalLoss = 0;
        double maxDrawdown = 0;
        double peak = initialCapital;
        double equity = initialCapital;
        double sumWinAmount = 0;
        double sumLossAmount = 0;
        long totalDays = 0;

        for (Trade trade : trades) {
            // Update equity
            equity += trade.getProfitAmount();

            // Track max drawdown
            if (equity > peak) {
                peak = equity;
            }
            double drawdown = (peak - equity) / peak;
            maxDrawdown = Math.max(maxDrawdown, drawdown);

            // Count wins and losses
            if (trade.getProfit() > 0) {
                winningCount++;
                totalProfit += trade.getProfitAmount();
                sumWinAmount += trade.getProfitAmount();
            } else {
                losingCount++;
                totalLoss -= trade.getProfitAmount();
                sumLossAmount -= trade.getProfitAmount();
            }

            // Calculate trade duration
            long tradeDays = (trade.getExitTime().getTime() - trade.getEntryTime().getTime())
                    / (1000 * 60 * 60 * 24);
            totalDays += tradeDays;
        }

        double winRate = trades.isEmpty() ? 0 : (double) winningCount / trades.size();
        double profitFactor = totalLoss > 0 ? totalProfit / totalLoss : totalProfit > 0 ? Double.MAX_VALUE : 0;
        double avgWinAmount = winningCount > 0 ? sumWinAmount / winningCount : 0;
        double avgLossAmount = losingCount > 0 ? sumLossAmount / losingCount : 0;
        double avgTradeAmount = trades.isEmpty() ? 0 : (sumWinAmount - sumLossAmount) / trades.size();
        double avgTradeLength = trades.isEmpty() ? 0 : (double) totalDays / trades.size();

        // Annualized return calculation (assuming 252 trading days per year)
        double totalDaysDouble = (double) totalDays;
        double annualized = totalDaysDouble > 0 ?
                Math.pow((finalEquity / initialCapital), (252.0 / totalDaysDouble)) - 1.0 : 0;

        return BacktestResult.builder()
                .finalEquity(finalEquity)
                .totalReturn((finalEquity - initialCapital) / initialCapital)
                .annualizedReturn(annualized)
                .profitFactor(profitFactor)
                .winRate(winRate)
                .maxDrawdown(maxDrawdown)
                .totalTrades(trades.size())
                .winningTrades(winningCount)
                .losingTrades(losingCount)
                .avgWinAmount(avgWinAmount)
                .avgLossAmount(avgLossAmount)
                .avgTradeAmount(avgTradeAmount)
                .avgTradeLength(avgTradeLength)
                .trades(trades)
                .build();
    }

    private BacktestResult aggregateResults(List<BacktestResult> results) {
        if (results.isEmpty()) {
            return BacktestResult.builder().build();
        }

        // Calculate combined metrics
        double finalEquity = results.get(results.size() - 1).getFinalEquity();
        int totalTrades = results.stream().mapToInt(BacktestResult::getTotalTrades).sum();
        int winningTrades = results.stream().mapToInt(BacktestResult::getWinningTrades).sum();
        int losingTrades = results.stream().mapToInt(BacktestResult::getLosingTrades).sum();

        // Average metrics across all results
        double avgSharpe = results.stream().mapToDouble(BacktestResult::getSharpeRatio).average().orElse(0);
        double avgSortino = results.stream().mapToDouble(BacktestResult::getSortinoRatio).average().orElse(0);
        double avgDrawdown = results.stream().mapToDouble(BacktestResult::getMaxDrawdown).max().orElse(0);
        double avgProfitFactor = results.stream().mapToDouble(BacktestResult::getProfitFactor).average().orElse(0);

        // Calculate overall return
        double initialCapital = 100000.0; // Using default initial capital
        double totalReturn = (finalEquity - initialCapital) / initialCapital;

        return BacktestResult.builder()
                .finalEquity(finalEquity)
                .totalReturn(totalReturn)
                .annualizedReturn(results.stream().mapToDouble(BacktestResult::getAnnualizedReturn).average().orElse(0))
                .sharpeRatio(avgSharpe)
                .sortinoRatio(avgSortino)
                .profitFactor(avgProfitFactor)
                .winRate(totalTrades > 0 ? (double) winningTrades / totalTrades : 0)
                .maxDrawdown(avgDrawdown)
                .totalTrades(totalTrades)
                .winningTrades(winningTrades)
                .losingTrades(losingTrades)
                .avgWinAmount(results.stream().mapToDouble(BacktestResult::getAvgWinAmount).average().orElse(0))
                .avgLossAmount(results.stream().mapToDouble(BacktestResult::getAvgLossAmount).average().orElse(0))
                .avgTradeAmount(results.stream().mapToDouble(BacktestResult::getAvgTradeAmount).average().orElse(0))
                .avgTradeLength(results.stream().mapToDouble(BacktestResult::getAvgTradeLength).average().orElse(0))
                .build();
    }
}

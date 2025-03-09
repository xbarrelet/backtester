package ch.xavier.backtester.visualization;

import ch.xavier.backtester.backtesting.BacktestResult;
import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import ch.xavier.backtester.quote.Quote;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class XChartVisualizer {

    public void generateVisualizationOfMarketPhases(Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes) {
        createOHLCChart(marketPhaseQuotes.get(MarketPhaseClassifier.MarketPhase.BULLISH), "Bullish");
        createOHLCChart(marketPhaseQuotes.get(MarketPhaseClassifier.MarketPhase.BEARISH), "Bearish");
        createOHLCChart(marketPhaseQuotes.get(MarketPhaseClassifier.MarketPhase.SIDEWAYS), "Sideways");

        // Create a combined line chart to compare phases
        createCombinedLineChart(marketPhaseQuotes);
    }

    private void createOHLCChart(List<Quote> quotes, String phase) {
        if (quotes == null || quotes.isEmpty()) return;

        // Create Chart
        OHLCChart chart = new OHLCChartBuilder()
                .width(800)
                .height(500)
                .title(phase + " Market Phase")
                .xAxisTitle("Date")
                .yAxisTitle("Price")
                .build();

        // Customize Chart
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setDefaultSeriesRenderStyle(OHLCSeries.OHLCSeriesRenderStyle.Candle);
        chart.getStyler().setXAxisTickMarkSpacingHint(10);

        // Series
        List<Date> xData = new ArrayList<>();
        List<Double> openData = new ArrayList<>();
        List<Double> highData = new ArrayList<>();
        List<Double> lowData = new ArrayList<>();
        List<Double> closeData = new ArrayList<>();

        for (Quote quote : quotes) {
            xData.add(new Date(quote.getTimestamp().getTime()));
            openData.add(quote.getOpen().doubleValue());
            highData.add(quote.getHigh().doubleValue());
            lowData.add(quote.getLow().doubleValue());
            closeData.add(quote.getClose().doubleValue());
        }

        OHLCSeries series = chart.addSeries("Price", xData, openData, highData, lowData, closeData);

        // Set up and down colors
        series.setUpColor(Color.GREEN); // Bullish candles (up)
        series.setDownColor(Color.RED); // Bearish candles (down)

        // Save the chart
        try {
            BitmapEncoder.saveBitmap(chart, "./visualizations/market_phase_" + phase.toLowerCase(),
                    BitmapEncoder.BitmapFormat.PNG);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createCombinedLineChart(
            Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes) {

        // Create Chart
        XYChart chart = new XYChartBuilder()
                .width(800)
                .height(500)
                .title("Market Phase Comparison")
                .xAxisTitle("Time")
                .yAxisTitle("Closing Price")
                .build();

        // Customize Chart
        chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(3);
        chart.getStyler().setXAxisTickMarkSpacingHint(100);

        // Add series for each market phase
        addPhaseToChart(chart, marketPhaseQuotes.get(MarketPhaseClassifier.MarketPhase.BULLISH),
                "Bullish", Color.GREEN);
        addPhaseToChart(chart, marketPhaseQuotes.get(MarketPhaseClassifier.MarketPhase.BEARISH),
                "Bearish", Color.RED);
        addPhaseToChart(chart, marketPhaseQuotes.get(MarketPhaseClassifier.MarketPhase.SIDEWAYS),
                "Sideways", Color.BLUE);

        // Save the chart
        try {
            BitmapEncoder.saveBitmap(chart, "./visualizations/market_phase_comparison",
                    BitmapEncoder.BitmapFormat.PNG);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addPhaseToChart(XYChart chart, List<Quote> quotes, String phase, Color color) {
        if (quotes == null || quotes.isEmpty()) return;

        List<Date> xData = new ArrayList<>();
        List<Double> yData = new ArrayList<>();

        for (Quote quote : quotes) {
            xData.add(new Date(quote.getTimestamp().getTime()));
            yData.add(quote.getClose().doubleValue());
        }

        XYSeries series = chart.addSeries(phase, xData, yData);
        series.setMarker(SeriesMarkers.CIRCLE);
        series.setLineColor(color);
    }

    public void visualizeBacktestResults(Map<MarketPhaseClassifier.MarketPhase, BacktestResult> phaseResults,
                                         Map<MarketPhaseClassifier.MarketPhase, Integer> optimalPeriods) {

        if (phaseResults.isEmpty()) {
            log.warn("No backtest results to visualize");
            return;
        }

        // Create performance metrics chart
        CategoryChart performanceChart = new CategoryChartBuilder()
                .width(800)
                .height(600)
                .title("Backtest Performance by Market Phase")
                .xAxisTitle("Market Phase")
                .yAxisTitle("Value")
                .build();

        // Customize chart
        performanceChart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
        performanceChart.getStyler().setChartTitleVisible(true);
        performanceChart.getStyler().setDefaultSeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Bar);
        performanceChart.getStyler().setAvailableSpaceFill(.8);
        performanceChart.getStyler().setOverlapped(false);
        performanceChart.getStyler().setYAxisDecimalPattern("#.##%");

        // Add series data for win rate, profit factor, return, etc.
        List<String> phases = new ArrayList<>();
        List<Double> winRates = new ArrayList<>();
        List<Double> profitFactors = new ArrayList<>();
        List<Double> returns = new ArrayList<>();
        List<Double> drawdowns = new ArrayList<>();
        List<Double> periods = new ArrayList<>();

        for (MarketPhaseClassifier.MarketPhase phase : phaseResults.keySet()) {
            BacktestResult result = phaseResults.get(phase);
            if (result.getTotalTrades() > 0) {
                phases.add(phase.name());
                winRates.add(result.getWinRate());
                profitFactors.add(Math.min(result.getProfitFactor(), 5.0)); // Cap at 5 for better visualization
                returns.add(result.getTotalReturn());
                drawdowns.add(-result.getMaxDrawdown()); // Negative to show as downward bars
                periods.add(optimalPeriods.getOrDefault(phase, 0).doubleValue());
            }
        }

        performanceChart.addSeries("Win Rate", phases, winRates);
        performanceChart.addSeries("Total Return", phases, returns);
        performanceChart.addSeries("Max Drawdown", phases, drawdowns);

        // Create separate profit factor chart (different scale)
        CategoryChart profitFactorChart = new CategoryChartBuilder()
                .width(800)
                .height(400)
                .title("Profit Factor by Market Phase")
                .xAxisTitle("Market Phase")
                .yAxisTitle("Profit Factor")
                .build();

        profitFactorChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        profitFactorChart.getStyler().setDefaultSeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Bar);
        profitFactorChart.addSeries("Profit Factor", phases, profitFactors);

        // Create optimal periods chart
        CategoryChart parametersChart = new CategoryChartBuilder()
                .width(800)
                .height(400)
                .title("Optimal McGinley Period by Market Phase")
                .xAxisTitle("Market Phase")
                .yAxisTitle("Period")
                .build();

        parametersChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        parametersChart.getStyler().setDefaultSeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Bar);
        parametersChart.addSeries("McGinley Period", phases, periods);

        // Display charts
        new SwingWrapper<>(performanceChart).displayChart();
        new SwingWrapper<>(profitFactorChart).displayChart();
        new SwingWrapper<>(parametersChart).displayChart();

        // Create trades distribution chart if we have trades
        boolean hasTrades = phaseResults.values().stream()
                .anyMatch(result -> result.getTrades() != null && !result.getTrades().isEmpty());

        if (hasTrades) {
            // Create histogram of trade returns
            List<Double> allProfits = new ArrayList<>();
            phaseResults.values().stream()
                    .filter(result -> result.getTrades() != null)
                    .flatMap(result -> result.getTrades().stream())
                    .forEach(trade -> allProfits.add(trade.getProfit() * 100.0)); // Convert to percentage

            Histogram histogram = new Histogram(allProfits, 20);
            XYChart tradesChart = new XYChartBuilder()
                    .width(800)
                    .height(400)
                    .title("Distribution of Trade Returns")
                    .xAxisTitle("Return %")
                    .yAxisTitle("Frequency")
                    .build();

            tradesChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
            tradesChart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Area);

            tradesChart.addSeries("Trade Distribution", histogram.getxAxisData(), histogram.getyAxisData());
            new SwingWrapper<>(tradesChart).displayChart();
        }
    }
}
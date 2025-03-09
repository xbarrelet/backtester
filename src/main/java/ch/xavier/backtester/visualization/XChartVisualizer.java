package ch.xavier.backtester.visualization;

import ch.xavier.backtester.marketphase.MarketPhaseClassifier;
import ch.xavier.backtester.quote.Quote;
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
public class XChartVisualizer {

    public void visualizeMarketPhases(Map<MarketPhaseClassifier.MarketPhase, List<Quote>> marketPhaseQuotes) {
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
}
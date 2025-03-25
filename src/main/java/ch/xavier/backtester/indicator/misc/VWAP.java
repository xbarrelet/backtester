package ch.xavier.backtester.indicator.misc;

import ch.xavier.backtester.indicator.Indicator;
import ch.xavier.backtester.quote.Quote;

import java.time.LocalDate;
import java.util.List;

public class VWAP implements Indicator {
    private double[] vwap;
    private LocalDate currentDate;
    private double cumulativeTPV; // Total Price*Volume
    private double cumulativeVolume;


    public void calculate(List<Quote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return;
        }

        int size = quotes.size();
        vwap = new double[size];

        cumulativeTPV = 0;
        cumulativeVolume = 0;
        currentDate = quotes.get(0).getTimestamp().toLocalDateTime().toLocalDate();

        for (int i = 0; i < size; i++) {
            Quote quote = quotes.get(i);
            LocalDate quoteDate = quote.getTimestamp().toLocalDateTime().toLocalDate();

            // Reset calculations for a new day
            if (!quoteDate.equals(currentDate)) {
                cumulativeTPV = 0;
                cumulativeVolume = 0;
                currentDate = quoteDate;
            }

            // Calculate typical price: (high + low + close) / 3
            double typicalPrice = (quote.getHigh() + quote.getLow() + quote.getClose()) / 3.0;
            double volume = quote.getVolume();

            cumulativeTPV += typicalPrice * volume;
            cumulativeVolume += volume;

            // Calculate VWAP
            vwap[i] = cumulativeVolume > 0 ? cumulativeTPV / cumulativeVolume : quote.getClose();
        }
    }

    @Override
    public double calculate(int index, List<Quote> quotes) {
        if (vwap == null || index < 0 || index >= vwap.length) {
            calculate(quotes);
        }

        return index < vwap.length ? vwap[index] : 0;
    }

    public double[] getValues() {
        return vwap;
    }
}
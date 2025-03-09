package ch.xavier.backtester.indicator;

import ch.xavier.backtester.quote.Quote;
import java.util.List;

public interface Indicator {
    double calculate(int index, List<Quote> quotes);
}
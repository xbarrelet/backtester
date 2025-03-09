package ch.xavier.backtester.backtesting;


import ch.xavier.backtester.quote.QuoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BacktestingService {

    public BacktestingService(@Autowired QuoteService quoteService) throws InterruptedException {
        String symbol = "BTC";
        String timeframe = "1d";

        log.info("Starting backtesting!");

        quoteService.getUpToDateQuotes(symbol, timeframe).subscribe();
        Thread.sleep(100000);
    }
}

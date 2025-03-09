package ch.xavier.backtester.quote;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@Slf4j
public class QuoteService {
    @Autowired
    private QuoteRepository repository;

    private final BybitApiMarketRestClient marketClient =
            BybitApiClientFactory.newInstance(BybitApiConfig.MAINNET_DOMAIN, false).newMarketDataRestClient();

    @Transactional
    public Flux<Quote> getUpToDateQuotes(String symbol, String timeframe) {
        MarketInterval marketInterval = convertToMarketInterval(timeframe);

        if (marketInterval == null) {
            log.error("Invalid timeframe: {} for symbol:{}", timeframe, symbol);
            return Flux.empty();
        }

        return repository.findLatestBySymbolAndMarketInterval(symbol, marketInterval.name())
                .defaultIfEmpty(Quote.builder().timestamp(new Timestamp(0)).build())
                .flatMapMany(latestQuote -> {
                    log.info("Latest quote timestamp found in db for {}/{} is {}.",
                            symbol, marketInterval.name(), latestQuote.getTimestamp());

                    Timestamp latestTimestamp = getLatestTimestamp(symbol, marketInterval);
                    Timestamp startTime = new Timestamp(latestQuote.getTimestamp().getTime() + 1);

                    log.debug("Fetching quotes from {} to {}.", startTime, latestTimestamp);

                    if (startTime.after(latestTimestamp)) {
                        log.info("Database already has the latest quotes.");
                        return repository.findAllBySymbolAndMarketInterval(symbol, marketInterval.name());
                    }

                    return fetchMissingQuotes(symbol, marketInterval, startTime, latestTimestamp);
                });
    }

    private Timestamp getLatestTimestamp(String symbol, MarketInterval marketInterval) {
        MarketDataRequest request = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(symbol + "USDT")
                .marketInterval(marketInterval)
                .limit(1)
                .build();

        var marketLinesResponse = marketClient.getMarketLinesData(request);
        List<Quote> quotes = convertResponseToQuotes(marketLinesResponse, symbol, marketInterval.name());
        return quotes.getLast().getTimestamp();
    }

    private List<Quote> convertResponseToQuotes(Object response, String symbol, String marketInterval) {
        LinkedHashMap<String, LinkedHashMap<String, List<List<String>>>> typedResponse = (LinkedHashMap) response;

        List<List<String>> list = typedResponse.get("result").get("list");
        if (list == null) {
            return List.of();
        }

        return list
                .stream()
                .map(quote -> Quote.from(quote, symbol, marketInterval))
                .sorted(Comparator.comparing(Quote::getTimestamp))
                .toList();
    }

    private Flux<Quote> fetchMissingQuotes(String symbol, MarketInterval marketInterval, Timestamp startTime,
                                           Timestamp endTime) {
        List<Quote> allQuotes = new ArrayList<>();
        long start = startTime.getTime();
        long end = endTime.getTime();

        int limit = 1000; // Adjust based on Bybit's API limits

        long currentStart = end;
        while (start < currentStart) {
            MarketDataRequest request = MarketDataRequest.builder()
                    .category(CategoryType.LINEAR)
                    .symbol(symbol + "USDT")
                    .marketInterval(marketInterval)
                    .end(end)
                    .limit(limit)
                    .build();
            var response = marketClient.getMarketLinesData(request);

            List<Quote> quotes = convertResponseToQuotes(response, symbol, marketInterval.name());
            log.debug("Earliest quote timestamp:{}, latest quote timestamp:{}",
                    quotes.getFirst().getTimestamp(), quotes.getLast().getTimestamp());

            long earliestTimestamp = quotes.getFirst().getTimestamp().getTime();
            long latestTimestamp = quotes.getLast().getTimestamp().getTime();

            if (earliestTimestamp == latestTimestamp) {
                break;
            }

            for (Quote quote : quotes) {
                allQuotes.add(quote);
                end = earliestTimestamp + 1;
                currentStart = earliestTimestamp - (limit * getIntervalMillis(marketInterval)) + 1;
            }
        }

        repository.saveAll(allQuotes).subscribe();

        log.info("Fetched {} quotes for {}/{}", allQuotes.size(), symbol, marketInterval);
        return Flux.fromIterable(allQuotes);
    }

    private static MarketInterval convertToMarketInterval(String timeframe) {
        return switch (timeframe) {
            case "1m" -> MarketInterval.ONE_MINUTE;
            case "3m" -> MarketInterval.THREE_MINUTES;
            case "5m" -> MarketInterval.FIVE_MINUTES;
            case "15m" -> MarketInterval.FIFTEEN_MINUTES;
            case "30m" -> MarketInterval.HALF_HOURLY;
            case "1h" -> MarketInterval.HOURLY;
            case "2h" -> MarketInterval.TWO_HOURLY;
            case "4h" -> MarketInterval.FOUR_HOURLY;
            case "6h" -> MarketInterval.SIX_HOURLY;
            case "12h" -> MarketInterval.TWELVE_HOURLY;
            case "1d" -> MarketInterval.DAILY;
            case "1w" -> MarketInterval.WEEKLY;
            case "1M" -> MarketInterval.MONTHLY;
            default -> null;
        };
    }

    private long getIntervalMillis(MarketInterval interval) {
        // Approximate interval length in milliseconds
        switch (interval) {
            case ONE_MINUTE:
                return 60 * 1000;
            case THREE_MINUTES:
                return 3 * 60 * 1000;
            case FIVE_MINUTES:
                return 5 * 60 * 1000;
            case FIFTEEN_MINUTES:
                return 15 * 60 * 1000;
            case HALF_HOURLY:
                return 30 * 60 * 1000;
            case HOURLY:
                return 60 * 60 * 1000;
            case TWO_HOURLY:
                return 2 * 60 * 60 * 1000;
            case FOUR_HOURLY:
                return 4 * 60 * 60 * 1000;
            case SIX_HOURLY:
                return 6 * 60 * 60 * 1000;
            case TWELVE_HOURLY:
                return 12 * 60 * 60 * 1000;
            case DAILY:
                return 24 * 60 * 60 * 1000;
            case WEEKLY:
                return 7 * 24 * 60 * 60 * 1000;
            case MONTHLY:
                return 30L * 24 * 60 * 60 * 1000;
            default:
                return 24 * 60 * 60 * 1000;
        }
    }
}

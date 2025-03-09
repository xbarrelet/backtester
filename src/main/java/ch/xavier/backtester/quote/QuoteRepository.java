package ch.xavier.backtester.quote;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface QuoteRepository extends ReactiveCrudRepository<Quote, Long> {

    @Query("select * from quotes where symbol = :symbol and market_interval = :marketInterval order by timestamp desc limit 1")
    Mono<Quote> findLatestBySymbolAndMarketInterval(String symbol, String marketInterval);

    Flux<Quote> findAllBySymbolAndMarketInterval(String symbol, String marketInterval);
}

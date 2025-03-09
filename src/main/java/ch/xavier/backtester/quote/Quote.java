package ch.xavier.backtester.quote;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

@Builder
@Getter
@Table("quotes")
@ToString
public class Quote {
    @Id
    @Column("quote_id")
    private Long quoteId;

    @Column("close")
    private BigDecimal close;

    @Column("high")
    private BigDecimal high;

    @Column("low")
    private BigDecimal low;

    @Column("market_interval")
    private String marketInterval;

    @Column("open")
    private BigDecimal open;

    @Column("symbol")
    private String symbol;

    @Column("timestamp")
    private Timestamp timestamp;

    @Column("volume")
    private BigDecimal volume;

    public static Quote from(List<String> quoteString, String symbol, String marketInterval) {
        return Quote.builder()
                .timestamp(new Timestamp(Long.parseLong(String.valueOf(quoteString.get(0)))))
                .open(new BigDecimal(String.valueOf(quoteString.get(1))))
                .high(new BigDecimal(String.valueOf(quoteString.get(2))))
                .low(new BigDecimal(String.valueOf(quoteString.get(3))))
                .close(new BigDecimal(String.valueOf(quoteString.get(4))))
                .volume(new BigDecimal(String.valueOf(quoteString.get(5))))
                .symbol(symbol)
                .marketInterval(marketInterval)
                .build();
    }
}
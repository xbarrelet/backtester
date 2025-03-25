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
    private double close;

    @Column("high")
    private double high;

    @Column("low")
    private double low;

    @Column("market_interval")
    private String marketInterval;

    @Column("open")
    private double open;

    @Column("symbol")
    private String symbol;

    @Column("timestamp")
    private Timestamp timestamp;

    @Column("volume")
    private double volume;

    public static Quote from(List<String> quoteString, String symbol, String marketInterval) {
        return Quote.builder()
                .timestamp(new Timestamp(Long.parseLong(String.valueOf(quoteString.get(0)))))
                .open(Double.parseDouble(String.valueOf(quoteString.get(1))))
                .high(Double.parseDouble(String.valueOf(quoteString.get(2))))
                .low(Double.parseDouble(String.valueOf(quoteString.get(3))))
                .close(Double.parseDouble(String.valueOf(quoteString.get(4))))
                .volume(Double.parseDouble(String.valueOf(quoteString.get(5))))
                .symbol(symbol)
                .marketInterval(marketInterval)
                .build();
    }
}
package com.sharemarketbazaar.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** A single market data tick (domain model, also the Kafka message payload). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTick {

    @NotBlank(message = "symbol is required")
    private String symbol;

    @NotNull(message = "price is required")
    @Positive(message = "price must be positive")
    private BigDecimal price;

    @PositiveOrZero(message = "volume cannot be negative")
    private long volume;

    /** Exchange timestamp; defaulted to receive time when absent. */
    private Instant timestamp;
}

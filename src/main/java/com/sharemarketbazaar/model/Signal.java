package com.sharemarketbazaar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A generated trading signal. Only actionable (BUY/SELL) signals are persisted. */
@Entity
@Table(name = "signals", indexes = {
        @Index(name = "idx_signals_symbol_time", columnList = "symbol,generatedAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Signal {

    public enum SignalType { BUY, SELL, HOLD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 10)
    private SignalType type;

    /** 0.0 - 1.0 */
    @Column(nullable = false)
    private double confidence;

    @Column(precision = 19, scale = 4)
    private BigDecimal price;

    @Column(precision = 19, scale = 4)
    private BigDecimal targetPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal stopLoss;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private Instant generatedAt;
}

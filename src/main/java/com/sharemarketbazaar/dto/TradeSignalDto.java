package com.sharemarketbazaar.dto;

import com.sharemarketbazaar.model.Signal;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

/** Outbound signal representation (REST + WebSocket). */
@Value
@Builder
public class TradeSignalDto {
    String symbol;
    Signal.SignalType signal;
    double confidence;
    BigDecimal price;
    BigDecimal targetPrice;
    BigDecimal stopLoss;
    String reason;
    Instant generatedAt;

    public static TradeSignalDto from(Signal s) {
        return TradeSignalDto.builder()
                .symbol(s.getSymbol())
                .signal(s.getType())
                .confidence(s.getConfidence())
                .price(s.getPrice())
                .targetPrice(s.getTargetPrice())
                .stopLoss(s.getStopLoss())
                .reason(s.getReason())
                .generatedAt(s.getGeneratedAt())
                .build();
    }
}

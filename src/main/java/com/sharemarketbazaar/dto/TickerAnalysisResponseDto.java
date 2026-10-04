package com.sharemarketbazaar.dto;

import com.sharemarketbazaar.analytics.IndicatorResult;
import com.sharemarketbazaar.analytics.IndicatorType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Snapshot of all computed indicators and the current signal for one ticker. */
@Value
@Builder(toBuilder = true)
public class TickerAnalysisResponseDto {
    String symbol;
    BigDecimal lastPrice;
    Instant lastTickAt;
    int tickCount;
    Double ema12;
    Double ema26;
    Double rsi14;
    Double vwap;
    Map<IndicatorType, IndicatorResult> indicators;
    TradeSignalDto signal;
}

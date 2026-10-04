package com.sharemarketbazaar.analytics;

import com.sharemarketbazaar.model.StockTick;

import java.util.List;

/** Strategy Pattern contract: one implementation per technical indicator. */
public interface IndicatorStrategy {

    IndicatorType type();

    /** Minimum number of ticks before {@link #calculate} produces a meaningful value. */
    int minimumTicksRequired();

    /** @param ticks chronological ticks for the current trading day */
    IndicatorResult calculate(List<StockTick> ticks);

    default boolean canCalculate(List<StockTick> ticks) {
        return ticks.size() >= minimumTicksRequired();
    }
}

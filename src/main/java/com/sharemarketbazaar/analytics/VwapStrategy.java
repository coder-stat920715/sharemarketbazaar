package com.sharemarketbazaar.analytics;

import com.sharemarketbazaar.analytics.IndicatorResult.Bias;
import com.sharemarketbazaar.model.StockTick;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Intraday cumulative VWAP = sum(price * volume) / sum(volume).
 * The TickStore resets its window at each trading-day boundary, so the whole list is "today".
 */
@Component
public class VwapStrategy implements IndicatorStrategy {

    @Override
    public IndicatorType type() {
        return IndicatorType.VWAP;
    }

    @Override
    public int minimumTicksRequired() {
        return 1;
    }

    @Override
    public IndicatorResult calculate(List<StockTick> ticks) {
        if (!canCalculate(ticks)) {
            throw new IllegalArgumentException("VWAP needs at least one tick");
        }
        double priceVolume = 0;
        double volume = 0;
        for (StockTick t : ticks) {
            priceVolume += t.getPrice().doubleValue() * t.getVolume();
            volume += t.getVolume();
        }
        double last = ticks.get(ticks.size() - 1).getPrice().doubleValue();
        // With zero traded volume fall back to the last price to avoid division by zero.
        double vwap = volume > 0 ? priceVolume / volume : last;
        double deviationPct = vwap == 0 ? 0 : (last - vwap) / vwap * 100.0;

        Bias bias = last > vwap ? Bias.BULLISH : last < vwap ? Bias.BEARISH : Bias.NEUTRAL;

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("vwap", IndicatorMath.round(vwap));
        values.put("priceVsVwapPct", IndicatorMath.round(deviationPct));
        return new IndicatorResult(type(), values, bias);
    }
}

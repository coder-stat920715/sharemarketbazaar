package com.sharemarketbazaar.analytics;

import com.sharemarketbazaar.dto.TickerAnalysisResponseDto;
import com.sharemarketbazaar.exception.ResourceNotFoundException;
import com.sharemarketbazaar.model.StockTick;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Runs every configured indicator strategy for a ticker against its tick window. */
@Service
@RequiredArgsConstructor
public class TechnicalAnalysisEngineService {

    private final TickStore tickStore;
    private final IndicatorStrategyFactory strategyFactory;

    /** Indicators that have enough data; others are simply absent from the map (warm-up). */
    public Map<IndicatorType, IndicatorResult> computeIndicators(String symbol) {
        return compute(symbol, tickStore.snapshot(symbol));
    }

    public TickerAnalysisResponseDto analyze(String rawSymbol) {
        String symbol = rawSymbol.trim().toUpperCase();
        List<StockTick> ticks = tickStore.snapshot(symbol);
        if (ticks.isEmpty()) {
            throw new ResourceNotFoundException("No market data available for symbol " + symbol);
        }
        Map<IndicatorType, IndicatorResult> results = compute(symbol, ticks);
        StockTick last = ticks.get(ticks.size() - 1);

        return TickerAnalysisResponseDto.builder()
                .symbol(symbol)
                .lastPrice(last.getPrice())
                .lastTickAt(last.getTimestamp())
                .tickCount(ticks.size())
                .ema12(pick(results, IndicatorType.EMA_CROSSOVER, "ema12"))
                .ema26(pick(results, IndicatorType.EMA_CROSSOVER, "ema26"))
                .rsi14(pick(results, IndicatorType.RSI, "rsi"))
                .vwap(pick(results, IndicatorType.VWAP, "vwap"))
                .indicators(results)
                .build();
    }

    private Map<IndicatorType, IndicatorResult> compute(String symbol, List<StockTick> ticks) {
        Map<IndicatorType, IndicatorResult> results = new EnumMap<>(IndicatorType.class);
        for (IndicatorStrategy strategy : strategyFactory.createFor(symbol)) {
            if (strategy.canCalculate(ticks)) {
                results.put(strategy.type(), strategy.calculate(ticks));
            }
        }
        return results;
    }

    private Double pick(Map<IndicatorType, IndicatorResult> results, IndicatorType type, String key) {
        IndicatorResult result = results.get(type);
        return result == null ? null : result.values().get(key);
    }
}

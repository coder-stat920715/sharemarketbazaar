package com.sharemarketbazaar.analytics;

import com.sharemarketbazaar.config.SharemarketProperties;
import com.sharemarketbazaar.model.StockTick;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-symbol tick window for the current trading day.
 * A new trading day clears the symbol's window (this is what makes VWAP "intraday").
 */
@Component
@RequiredArgsConstructor
public class TickStore {

    private final SharemarketProperties properties;
    private final Map<String, Deque<StockTick>> store = new ConcurrentHashMap<>();

    /** @return false if the tick belongs to a previous trading day and was discarded */
    public boolean append(StockTick tick) {
        Deque<StockTick> window = store.computeIfAbsent(tick.getSymbol(), s -> new ArrayDeque<>());
        synchronized (window) {
            StockTick last = window.peekLast();
            if (last != null) {
                LocalDate lastDay = tradingDay(last);
                LocalDate newDay = tradingDay(tick);
                if (newDay.isBefore(lastDay)) {
                    return false;
                }
                if (newDay.isAfter(lastDay)) {
                    window.clear();
                }
            }
            window.addLast(tick);
            int max = properties.getAnalysis().getMaxTicksPerSymbol();
            while (window.size() > max) {
                window.pollFirst();
            }
            return true;
        }
    }

    /** Defensive copy, safe to iterate without holding locks. */
    public List<StockTick> snapshot(String symbol) {
        Deque<StockTick> window = store.get(symbol);
        if (window == null) {
            return List.of();
        }
        synchronized (window) {
            return new ArrayList<>(window);
        }
    }

    public Optional<StockTick> latest(String symbol) {
        Deque<StockTick> window = store.get(symbol);
        if (window == null) {
            return Optional.empty();
        }
        synchronized (window) {
            return Optional.ofNullable(window.peekLast());
        }
    }

    private LocalDate tradingDay(StockTick tick) {
        ZoneId zone = properties.getAnalysis().getMarketZone();
        return tick.getTimestamp().atZone(zone).toLocalDate();
    }
}

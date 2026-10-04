package com.sharemarketbazaar.event;

import com.sharemarketbazaar.model.StockTick;

/** Published after a validated tick is stored. Observers: signal engine, OMS, WebSocket feed. */
public record TickReceivedEvent(StockTick tick) {
}

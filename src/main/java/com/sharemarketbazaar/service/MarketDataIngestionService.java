package com.sharemarketbazaar.service;

import com.sharemarketbazaar.analytics.TickStore;
import com.sharemarketbazaar.event.TickReceivedEvent;
import com.sharemarketbazaar.model.StockTick;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

/**
 * Ingestion module. Kafka (key = symbol, so per-symbol ordering is preserved) and the REST
 * test endpoint both funnel into {@link #ingest(StockTick)}, which validates, stores and
 * publishes a {@link TickReceivedEvent} (Observer Pattern).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataIngestionService {

    private final TickStore tickStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Validator validator;

    @KafkaListener(topics = "${sharemarket.kafka.topic}", autoStartup = "${sharemarket.kafka.enabled:true}")
    public void onMessage(StockTick tick) {
        if (tick == null) {
            log.warn("Discarding tick that could not be deserialized");
            return;
        }
        try {
            ingest(tick);
        } catch (RuntimeException ex) {
            // Never let one bad tick block the partition.
            log.warn("Discarding tick {}: {}", tick, ex.getMessage());
        }
    }

    public StockTick ingest(StockTick tick) {
        Set<ConstraintViolation<StockTick>> violations = validator.validate(tick);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        tick.setSymbol(tick.getSymbol().trim().toUpperCase());
        if (tick.getTimestamp() == null) {
            tick.setTimestamp(Instant.now());
        }
        if (tickStore.append(tick)) {
            eventPublisher.publishEvent(new TickReceivedEvent(tick));
        } else {
            log.debug("Dropped stale tick for {} at {}", tick.getSymbol(), tick.getTimestamp());
        }
        return tick;
    }
}

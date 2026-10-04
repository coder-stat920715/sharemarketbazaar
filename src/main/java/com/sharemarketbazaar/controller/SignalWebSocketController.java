package com.sharemarketbazaar.controller;

import com.sharemarketbazaar.dto.TickerAnalysisResponseDto;
import com.sharemarketbazaar.dto.TradeSignalDto;
import com.sharemarketbazaar.repository.SignalRepository;
import com.sharemarketbazaar.service.SignalGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * STOMP endpoints.
 * Live stream : SUBSCRIBE /topic/signals or /topic/signals/{symbol} (pushed by SignalBroadcastListener)
 * Snapshot    : SUBSCRIBE /app/signals/latest/{symbol}  (immediate reply with the latest signal)
 * On-demand   : SEND /app/analysis/{symbol}, reply on /user/queue/analysis
 * Errors      : /user/queue/errors
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SignalWebSocketController {

    private final SignalGenerationService signalService;
    private final SignalRepository signalRepository;

    @SubscribeMapping("/signals/latest/{symbol}")
    public TradeSignalDto latestSignal(@DestinationVariable String symbol) {
        String normalized = symbol.trim().toUpperCase();
        return signalRepository.findFirstBySymbolOrderByGeneratedAtDesc(normalized)
                .map(TradeSignalDto::from)
                .orElseGet(() -> TradeSignalDto.from(signalService.evaluate(normalized)));
    }

    @MessageMapping("/analysis/{symbol}")
    @SendToUser("/queue/analysis")
    public TickerAnalysisResponseDto analyze(@DestinationVariable String symbol) {
        return signalService.analyzeWithSignal(symbol);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, String> handleError(Exception ex) {
        log.warn("WebSocket request failed: {}", ex.getMessage());
        return Map.of("error", String.valueOf(ex.getMessage()));
    }
}

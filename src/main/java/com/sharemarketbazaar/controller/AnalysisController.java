package com.sharemarketbazaar.controller;

import com.sharemarketbazaar.dto.TickerAnalysisResponseDto;
import com.sharemarketbazaar.dto.TradeSignalDto;
import com.sharemarketbazaar.model.StockTick;
import com.sharemarketbazaar.repository.SignalRepository;
import com.sharemarketbazaar.service.MarketDataIngestionService;
import com.sharemarketbazaar.service.SignalGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalysisController {

    private final SignalGenerationService signalService;
    private final SignalRepository signalRepository;
    private final MarketDataIngestionService ingestionService;

    /** Current indicators (EMA12/26, RSI14, VWAP) plus the live signal evaluation for a ticker. */
    @GetMapping("/analysis/{symbol}")
    public TickerAnalysisResponseDto analyze(@PathVariable String symbol) {
        return signalService.analyzeWithSignal(symbol);
    }

    /** The 20 most recent persisted BUY/SELL signals for a ticker. */
    @GetMapping("/analysis/{symbol}/signals")
    public List<TradeSignalDto> recentSignals(@PathVariable String symbol) {
        return signalRepository.findTop20BySymbolOrderByGeneratedAtDesc(symbol.trim().toUpperCase())
                .stream().map(TradeSignalDto::from).toList();
    }

    /** Dev/testing ingestion path, equivalent to publishing the tick on the Kafka topic. */
    @PostMapping("/ticks")
    public ResponseEntity<StockTick> ingestTick(@RequestBody StockTick tick) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.ingest(tick));
    }
}

package com.sharemarketbazaar.service;

import com.sharemarketbazaar.analytics.IndicatorResult;
import com.sharemarketbazaar.analytics.IndicatorResult.Bias;
import com.sharemarketbazaar.analytics.IndicatorType;
import com.sharemarketbazaar.analytics.TechnicalAnalysisEngineService;
import com.sharemarketbazaar.analytics.TickStore;
import com.sharemarketbazaar.config.SharemarketProperties;
import com.sharemarketbazaar.config.SharemarketProperties.SignalRules;
import com.sharemarketbazaar.dto.TickerAnalysisResponseDto;
import com.sharemarketbazaar.dto.TradeSignalDto;
import com.sharemarketbazaar.event.SignalGeneratedEvent;
import com.sharemarketbazaar.event.TickReceivedEvent;
import com.sharemarketbazaar.exception.ResourceNotFoundException;
import com.sharemarketbazaar.model.Signal;
import com.sharemarketbazaar.model.Signal.SignalType;
import com.sharemarketbazaar.model.StockTick;
import com.sharemarketbazaar.repository.SignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

/**
 * Signal engine: EMA crossover as the trigger, RSI as the guard.
 * BUY  = golden cross AND RSI below overbought.
 * SELL = death cross  AND RSI above oversold.
 * Anything else = HOLD. VWAP alignment adds confidence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalGenerationService {

    private final TechnicalAnalysisEngineService engine;
    private final TickStore tickStore;
    private final SignalRepository signalRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SharemarketProperties properties;

    /** Observer: evaluates signals on every incoming tick. */
    @EventListener
    public void onTick(TickReceivedEvent event) {
        try {
            Signal signal = evaluate(event.tick().getSymbol());
            boolean actionable = signal.getType() != SignalType.HOLD;
            if (actionable) {
                signal = signalRepository.save(signal);
                log.info("Signal {} {} @ {} (confidence {})",
                        signal.getType(), signal.getSymbol(), signal.getPrice(), signal.getConfidence());
            }
            if (actionable || properties.getSignal().isEmitHold()) {
                eventPublisher.publishEvent(new SignalGeneratedEvent(signal));
            }
        } catch (RuntimeException ex) {
            log.error("Signal generation failed for tick {}", event.tick(), ex);
        }
    }

    /** Pure evaluation of the current market state; does not persist or publish. */
    public Signal evaluate(String rawSymbol) {
        String symbol = rawSymbol.trim().toUpperCase();
        StockTick last = tickStore.latest(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("No market data available for symbol " + symbol));

        Map<IndicatorType, IndicatorResult> results = engine.computeIndicators(symbol);
        IndicatorResult ema = results.get(IndicatorType.EMA_CROSSOVER);
        IndicatorResult rsi = results.get(IndicatorType.RSI);
        IndicatorResult vwap = results.get(IndicatorType.VWAP);
        SignalRules rules = properties.getSignal();

        SignalType type = SignalType.HOLD;
        double confidence = 0.5;
        String reason;

        if (ema == null) {
            reason = "Warming up: not enough ticks for EMA crossover";
        } else {
            double crossover = ema.value("crossover");
            double rsiValue = rsi == null ? 50.0 : rsi.value("rsi");

            if (crossover > 0) {
                if (rsiValue < rules.getRsiOverbought()) {
                    type = SignalType.BUY;
                    reason = "EMA12 crossed above EMA26; RSI " + rsiValue + " below overbought "
                            + rules.getRsiOverbought();
                } else {
                    reason = "Bullish EMA cross blocked by RSI guard (RSI " + rsiValue + " overbought)";
                }
            } else if (crossover < 0) {
                if (rsiValue > rules.getRsiOversold()) {
                    type = SignalType.SELL;
                    reason = "EMA12 crossed below EMA26; RSI " + rsiValue + " above oversold "
                            + rules.getRsiOversold();
                } else {
                    reason = "Bearish EMA cross blocked by RSI guard (RSI " + rsiValue + " oversold)";
                }
            } else {
                reason = "No EMA crossover on latest tick (trend " + ema.bias() + ")";
            }

            if (type != SignalType.HOLD) {
                confidence = confidence(type, rsiValue, vwap, rules);
            }
        }

        BigDecimal price = last.getPrice();
        BigDecimal stopLoss = null;
        BigDecimal target = null;
        if (type == SignalType.BUY) {
            stopLoss = scale(price, 1 - rules.getStopLossPct() / 100.0);
            target = scale(price, 1 + rules.getTargetPct() / 100.0);
        } else if (type == SignalType.SELL) {
            stopLoss = scale(price, 1 + rules.getStopLossPct() / 100.0);
            target = scale(price, 1 - rules.getTargetPct() / 100.0);
        }

        return Signal.builder()
                .symbol(symbol)
                .type(type)
                .confidence(confidence)
                .price(price)
                .targetPrice(target)
                .stopLoss(stopLoss)
                .reason(reason)
                .generatedAt(Instant.now())
                .build();
    }

    /** Full analysis (indicators + current signal) for REST and WebSocket request/response. */
    public TickerAnalysisResponseDto analyzeWithSignal(String symbol) {
        TickerAnalysisResponseDto analysis = engine.analyze(symbol);
        return analysis.toBuilder().signal(TradeSignalDto.from(evaluate(symbol))).build();
    }

    private double confidence(SignalType type, double rsiValue, IndicatorResult vwap, SignalRules rules) {
        double c = 0.55;
        if (type == SignalType.BUY) {
            c += 0.25 * (rules.getRsiOverbought() - rsiValue) / rules.getRsiOverbought();
        } else {
            c += 0.25 * (rsiValue - rules.getRsiOversold()) / (100.0 - rules.getRsiOversold());
        }
        if (vwap != null) {
            boolean aligned = (type == SignalType.BUY && vwap.bias() == Bias.BULLISH)
                    || (type == SignalType.SELL && vwap.bias() == Bias.BEARISH);
            if (aligned) {
                c += 0.10;
            }
        }
        c = Math.min(1.0, Math.max(0.0, c));
        return Math.round(c * 100.0) / 100.0;
    }

    private BigDecimal scale(BigDecimal price, double factor) {
        return price.multiply(BigDecimal.valueOf(factor)).setScale(4, RoundingMode.HALF_UP);
    }
}

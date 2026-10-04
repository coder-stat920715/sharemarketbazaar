package com.sharemarketbazaar.config;

import com.sharemarketbazaar.analytics.IndicatorType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Strongly-typed binding for the {@code sharemarket.*} configuration tree. */
@Data
@ConfigurationProperties(prefix = "sharemarket")
public class SharemarketProperties {

    private Kafka kafka = new Kafka();
    private Analysis analysis = new Analysis();
    private SignalRules signal = new SignalRules();
    private RiskLimits risk = new RiskLimits();

    @Data
    public static class Kafka {
        private String topic = "market.ticks";
        private boolean enabled = true;
    }

    @Data
    public static class Analysis {
        private ZoneId marketZone = ZoneId.of("Asia/Kolkata");
        private int maxTicksPerSymbol = 50_000;
        private List<IndicatorType> defaultIndicators =
                new ArrayList<>(List.of(IndicatorType.EMA_CROSSOVER, IndicatorType.RSI, IndicatorType.VWAP));
        /** Per-ticker indicator overrides, keyed by upper-case symbol. */
        private Map<String, List<IndicatorType>> tickerIndicators = new HashMap<>();
    }

    @Data
    public static class SignalRules {
        private double stopLossPct = 1.5;
        private double targetPct = 3.0;
        private double rsiOverbought = 70;
        private double rsiOversold = 30;
        private boolean emitHold = false;
    }

    @Data
    public static class RiskLimits {
        private int maxPositionQuantity = 5000;
        private BigDecimal maxOrderValue = new BigDecimal("500000");
        private double minStopLossPct = 0.1;
        private double maxStopLossPct = 10.0;
    }
}

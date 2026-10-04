package com.sharemarketbazaar.analytics;

import com.sharemarketbazaar.config.SharemarketProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory Pattern: instantiates (resolves) the right set of indicator strategies for a ticker
 * based on {@code sharemarket.analysis.ticker-indicators}, falling back to the defaults.
 */
@Component
public class IndicatorStrategyFactory {

    private final Map<IndicatorType, IndicatorStrategy> registry = new EnumMap<>(IndicatorType.class);
    private final SharemarketProperties properties;

    public IndicatorStrategyFactory(List<IndicatorStrategy> strategies, SharemarketProperties properties) {
        this.properties = properties;
        for (IndicatorStrategy strategy : strategies) {
            registry.put(strategy.type(), strategy);
        }
    }

    public IndicatorStrategy create(IndicatorType type) {
        IndicatorStrategy strategy = registry.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy registered for indicator " + type);
        }
        return strategy;
    }

    public List<IndicatorStrategy> createFor(String symbol) {
        SharemarketProperties.Analysis cfg = properties.getAnalysis();
        List<IndicatorType> types = cfg.getTickerIndicators()
                .getOrDefault(symbol.toUpperCase(), cfg.getDefaultIndicators());
        List<IndicatorStrategy> result = new ArrayList<>(types.size());
        for (IndicatorType type : types) {
            result.add(create(type));
        }
        return result;
    }
}

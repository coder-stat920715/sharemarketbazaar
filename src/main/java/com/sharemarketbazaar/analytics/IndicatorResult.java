package com.sharemarketbazaar.analytics;

import java.util.Map;

/** Immutable output of an {@link IndicatorStrategy}. */
public record IndicatorResult(IndicatorType type, Map<String, Double> values, Bias bias) {

    public enum Bias { BULLISH, BEARISH, NEUTRAL }

    /** Returns a named value or fails fast if the strategy did not produce it. */
    public double value(String key) {
        Double v = values.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Indicator " + type + " has no value '" + key + "'");
        }
        return v;
    }
}

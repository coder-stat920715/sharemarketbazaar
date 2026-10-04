package com.sharemarketbazaar.analytics;

import com.sharemarketbazaar.model.StockTick;

import java.util.Arrays;
import java.util.List;

/**
 * Shared numeric helpers. EMA/RSI only look at the most recent {@link #LOOKBACK} ticks:
 * the influence of older data has decayed to ~0 by then, and it keeps per-tick cost bounded.
 */
final class IndicatorMath {

    static final int LOOKBACK = 250;

    private IndicatorMath() {
    }

    static double[] recentPrices(List<StockTick> ticks) {
        int from = Math.max(0, ticks.size() - LOOKBACK);
        double[] prices = new double[ticks.size() - from];
        for (int i = from; i < ticks.size(); i++) {
            prices[i - from] = ticks.get(i).getPrice().doubleValue();
        }
        return prices;
    }

    /** EMA seeded with the SMA of the first {@code period} values; NaN before the seed index. */
    static double[] ema(double[] prices, int period) {
        double[] out = new double[prices.length];
        Arrays.fill(out, Double.NaN);
        if (prices.length < period) {
            return out;
        }
        double k = 2.0 / (period + 1);
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += prices[i];
        }
        out[period - 1] = sum / period;
        for (int i = period; i < prices.length; i++) {
            out[i] = prices[i] * k + out[i - 1] * (1 - k);
        }
        return out;
    }

    static double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}

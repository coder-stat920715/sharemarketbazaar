package com.sharemarketbazaar.analytics;

import com.sharemarketbazaar.analytics.IndicatorResult.Bias;
import com.sharemarketbazaar.model.StockTick;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** EMA(12) vs EMA(26). {@code crossover}: +1 golden cross, -1 death cross, 0 none (on the latest tick). */
@Component
public class EmaCrossoverStrategy implements IndicatorStrategy {

    static final int FAST_PERIOD = 12;
    static final int SLOW_PERIOD = 26;

    @Override
    public IndicatorType type() {
        return IndicatorType.EMA_CROSSOVER;
    }

    @Override
    public int minimumTicksRequired() {
        return SLOW_PERIOD + 1; // one extra tick to compare against the previous spread
    }

    @Override
    public IndicatorResult calculate(List<StockTick> ticks) {
        if (!canCalculate(ticks)) {
            throw new IllegalArgumentException("EMA crossover needs at least " + minimumTicksRequired() + " ticks");
        }
        double[] prices = IndicatorMath.recentPrices(ticks);
        double[] fast = IndicatorMath.ema(prices, FAST_PERIOD);
        double[] slow = IndicatorMath.ema(prices, SLOW_PERIOD);

        int n = prices.length;
        double spread = fast[n - 1] - slow[n - 1];
        double prevSpread = fast[n - 2] - slow[n - 2];

        double crossover = 0;
        if (prevSpread <= 0 && spread > 0) {
            crossover = 1;
        } else if (prevSpread >= 0 && spread < 0) {
            crossover = -1;
        }

        Bias bias = spread > 0 ? Bias.BULLISH : spread < 0 ? Bias.BEARISH : Bias.NEUTRAL;

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("ema12", IndicatorMath.round(fast[n - 1]));
        values.put("ema26", IndicatorMath.round(slow[n - 1]));
        values.put("spread", IndicatorMath.round(spread));
        values.put("crossover", crossover);
        return new IndicatorResult(type(), values, bias);
    }
}

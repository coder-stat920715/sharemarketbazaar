package com.sharemarketbazaar.analytics;

import com.sharemarketbazaar.analytics.IndicatorResult.Bias;
import com.sharemarketbazaar.model.StockTick;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RSI(14) using Wilder's smoothing.
 * Bias is a mean-reversion reading: >=70 BEARISH (overbought), <=30 BULLISH (oversold).
 */
@Component
public class RsiStrategy implements IndicatorStrategy {

    static final int PERIOD = 14;

    @Override
    public IndicatorType type() {
        return IndicatorType.RSI;
    }

    @Override
    public int minimumTicksRequired() {
        return PERIOD + 1;
    }

    @Override
    public IndicatorResult calculate(List<StockTick> ticks) {
        if (!canCalculate(ticks)) {
            throw new IllegalArgumentException("RSI needs at least " + minimumTicksRequired() + " ticks");
        }
        double[] p = IndicatorMath.recentPrices(ticks);

        double gainSum = 0;
        double lossSum = 0;
        for (int i = 1; i <= PERIOD; i++) {
            double change = p[i] - p[i - 1];
            gainSum += Math.max(change, 0);
            lossSum += Math.max(-change, 0);
        }
        double avgGain = gainSum / PERIOD;
        double avgLoss = lossSum / PERIOD;

        for (int i = PERIOD + 1; i < p.length; i++) {
            double change = p[i] - p[i - 1];
            avgGain = (avgGain * (PERIOD - 1) + Math.max(change, 0)) / PERIOD;
            avgLoss = (avgLoss * (PERIOD - 1) + Math.max(-change, 0)) / PERIOD;
        }

        double rsi;
        if (avgLoss == 0) {
            rsi = avgGain == 0 ? 50.0 : 100.0;
        } else {
            rsi = 100.0 - (100.0 / (1.0 + avgGain / avgLoss));
        }

        Bias bias = rsi >= 70 ? Bias.BEARISH : rsi <= 30 ? Bias.BULLISH : Bias.NEUTRAL;

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("rsi", IndicatorMath.round(rsi));
        return new IndicatorResult(type(), values, bias);
    }
}

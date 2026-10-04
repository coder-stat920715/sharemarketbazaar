package com.sharemarketbazaar.service;

import com.sharemarketbazaar.config.SharemarketProperties;
import com.sharemarketbazaar.config.SharemarketProperties.RiskLimits;
import com.sharemarketbazaar.model.Account;
import com.sharemarketbazaar.model.Order;
import com.sharemarketbazaar.model.Position;
import com.sharemarketbazaar.repository.AccountRepository;
import com.sharemarketbazaar.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pre-trade risk checks: balance, position limits, order size and stop-loss bounds.
 * All violations are collected so the client sees every problem at once.
 */
@Service
@RequiredArgsConstructor
public class RiskEngineService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final SharemarketProperties properties;

    public record RiskCheckResult(boolean approved, List<String> violations) {
        static RiskCheckResult approve() {
            return new RiskCheckResult(true, List.of());
        }

        static RiskCheckResult reject(List<String> violations) {
            return new RiskCheckResult(false, List.copyOf(violations));
        }

        public String summary() {
            return String.join("; ", violations);
        }
    }

    /**
     * @param referencePrice price used to value the order (limit price if present, else market price)
     */
    public RiskCheckResult validate(Order order, BigDecimal referencePrice) {
        RiskLimits limits = properties.getRisk();
        List<String> violations = new ArrayList<>();

        Optional<Account> account = accountRepository.findByUserId(order.getUserId());
        if (account.isEmpty()) {
            return RiskCheckResult.reject(List.of("Unknown account: " + order.getUserId()));
        }

        BigDecimal notional = referencePrice.multiply(BigDecimal.valueOf(order.getQuantity()));
        int held = positionRepository.findByUserIdAndSymbol(order.getUserId(), order.getSymbol())
                .map(Position::getQuantity).orElse(0);

        if (notional.compareTo(limits.getMaxOrderValue()) > 0) {
            violations.add("Order value " + notional + " exceeds max order value " + limits.getMaxOrderValue());
        }

        if (order.getType() == Order.OrderType.BUY) {
            if (account.get().getBalance().compareTo(notional) < 0) {
                violations.add("Insufficient balance: need " + notional + ", available "
                        + account.get().getBalance());
            }
            if (held + order.getQuantity() > limits.getMaxPositionQuantity()) {
                violations.add("Position limit exceeded: holding " + held + " + " + order.getQuantity()
                        + " > max " + limits.getMaxPositionQuantity());
            }
            validateBuyStopLoss(order.getStopLoss(), referencePrice, limits, violations);
        } else {
            if (order.getQuantity() > held) {
                violations.add("Insufficient position: holding " + held + ", attempting to sell "
                        + order.getQuantity() + " (short selling not permitted)");
            }
            if (order.getStopLoss() != null && order.getStopLoss().compareTo(referencePrice) <= 0) {
                violations.add("Stop-loss for a SELL must be above the order price");
            }
        }

        return violations.isEmpty() ? RiskCheckResult.approve() : RiskCheckResult.reject(violations);
    }

    private void validateBuyStopLoss(BigDecimal stopLoss, BigDecimal price, RiskLimits limits,
                                     List<String> violations) {
        if (stopLoss == null) {
            violations.add("Stop-loss is mandatory for BUY orders");
            return;
        }
        if (stopLoss.compareTo(price) >= 0) {
            violations.add("Stop-loss must be below the order price for a BUY");
            return;
        }
        double distancePct = price.subtract(stopLoss).multiply(HUNDRED)
                .divide(price, 4, RoundingMode.HALF_UP).doubleValue();
        if (distancePct < limits.getMinStopLossPct() || distancePct > limits.getMaxStopLossPct()) {
            violations.add("Stop-loss distance " + distancePct + "% outside allowed range ["
                    + limits.getMinStopLossPct() + "%, " + limits.getMaxStopLossPct() + "%]");
        }
    }
}

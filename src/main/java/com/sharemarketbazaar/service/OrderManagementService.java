package com.sharemarketbazaar.service;

import com.sharemarketbazaar.analytics.TickStore;
import com.sharemarketbazaar.dto.OrderRequestDto;
import com.sharemarketbazaar.event.TickReceivedEvent;
import com.sharemarketbazaar.exception.ResourceNotFoundException;
import com.sharemarketbazaar.model.Account;
import com.sharemarketbazaar.model.Order;
import com.sharemarketbazaar.model.Order.OrderStatus;
import com.sharemarketbazaar.model.Order.OrderType;
import com.sharemarketbazaar.model.Position;
import com.sharemarketbazaar.model.StockTick;
import com.sharemarketbazaar.repository.AccountRepository;
import com.sharemarketbazaar.repository.OrderRepository;
import com.sharemarketbazaar.repository.PositionRepository;
import com.sharemarketbazaar.service.RiskEngineService.RiskCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Order Management System.
 * Market orders (no limit price) and marketable limit orders execute immediately at the last
 * price. Non-marketable limit orders stay PENDING and are matched on subsequent ticks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderManagementService {

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final RiskEngineService riskEngine;
    private final TickStore tickStore;
    private final TransactionTemplate transactionTemplate;

    /** Symbols that currently have PENDING orders, so ticks for other symbols never hit the DB. */
    private final Set<String> symbolsWithPending = ConcurrentHashMap.newKeySet();

    @EventListener(ApplicationReadyEvent.class)
    public void loadPendingSymbols() {
        symbolsWithPending.addAll(orderRepository.findDistinctSymbolsByStatus(OrderStatus.PENDING));
    }

    @Transactional
    public Order placeOrder(OrderRequestDto request) {
        String symbol = request.getSymbol().trim().toUpperCase();
        BigDecimal marketPrice = tickStore.latest(symbol)
                .map(StockTick::getPrice)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No market data available for symbol " + symbol + "; cannot price the order"));

        Order order = orderRepository.save(Order.builder()
                .userId(request.getUserId())
                .symbol(symbol)
                .type(request.getType())
                .quantity(request.getQuantity())
                .limitPrice(request.getLimitPrice())
                .stopLoss(request.getStopLoss())
                .status(OrderStatus.PENDING)
                .build());

        BigDecimal referencePrice = order.getLimitPrice() != null ? order.getLimitPrice() : marketPrice;
        RiskCheckResult check = riskEngine.validate(order, referencePrice);
        if (!check.approved()) {
            return reject(order, check.summary());
        }

        if (isMarketable(order, marketPrice)) {
            return execute(order, marketPrice);
        }
        symbolsWithPending.add(symbol);
        log.info("Order {} left PENDING (limit {} vs market {})", order.getId(), order.getLimitPrice(), marketPrice);
        return order;
    }

    @Transactional
    public Order cancelOrder(Long orderId, String userId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING orders can be cancelled; order " + orderId + " is " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Order> listOrders(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Observer: try to fill resting limit orders on every tick of their symbol. */
    @EventListener
    public void onTick(TickReceivedEvent event) {
        StockTick tick = event.tick();
        if (!symbolsWithPending.contains(tick.getSymbol())) {
            return;
        }
        List<Order> pending = orderRepository.findBySymbolAndStatus(tick.getSymbol(), OrderStatus.PENDING);
        boolean stillPending = false;
        for (Order candidate : pending) {
            if (!isMarketable(candidate, tick.getPrice())) {
                stillPending = true;
                continue;
            }
            try {
                // Each order in its own transaction so one failure cannot affect the others.
                transactionTemplate.executeWithoutResult(status -> fillPending(candidate.getId(), tick.getPrice()));
            } catch (RuntimeException ex) {
                stillPending = true;
                log.error("Failed to fill pending order {}", candidate.getId(), ex);
            }
        }
        if (!stillPending) {
            symbolsWithPending.remove(tick.getSymbol());
        }
    }

    private void fillPending(Long orderId, BigDecimal marketPrice) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return; // cancelled or filled concurrently
        }
        // Re-run risk at fill time: balance/position may have changed since placement.
        RiskCheckResult check = riskEngine.validate(order, marketPrice);
        if (!check.approved()) {
            reject(order, check.summary());
            return;
        }
        execute(order, marketPrice);
    }

    private boolean isMarketable(Order order, BigDecimal marketPrice) {
        if (order.getLimitPrice() == null) {
            return true;
        }
        return order.getType() == OrderType.BUY
                ? marketPrice.compareTo(order.getLimitPrice()) <= 0
                : marketPrice.compareTo(order.getLimitPrice()) >= 0;
    }

    private Order reject(Order order, String reason) {
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectionReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        log.info("Order {} REJECTED: {}", order.getId(), reason);
        return orderRepository.save(order);
    }

    /** Settles the order: moves cash, updates the position (weighted average cost), marks EXECUTED. */
    private Order execute(Order order, BigDecimal price) {
        Account account = accountRepository.findByUserIdForUpdate(order.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Account " + order.getUserId() + " not found"));
        Position position = positionRepository.findByUserIdAndSymbol(order.getUserId(), order.getSymbol())
                .orElseGet(() -> Position.builder()
                        .userId(order.getUserId())
                        .symbol(order.getSymbol())
                        .quantity(0)
                        .averageCost(BigDecimal.ZERO)
                        .build());

        BigDecimal notional = price.multiply(BigDecimal.valueOf(order.getQuantity()));

        if (order.getType() == OrderType.BUY) {
            BigDecimal existingCost = position.getAverageCost().multiply(BigDecimal.valueOf(position.getQuantity()));
            int newQty = position.getQuantity() + order.getQuantity();
            position.setAverageCost(existingCost.add(notional)
                    .divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP));
            position.setQuantity(newQty);
            account.setBalance(account.getBalance().subtract(notional));
        } else {
            int newQty = position.getQuantity() - order.getQuantity();
            position.setQuantity(newQty);
            if (newQty == 0) {
                position.setAverageCost(BigDecimal.ZERO);
            }
            account.setBalance(account.getBalance().add(notional));
        }

        positionRepository.save(position);
        accountRepository.save(account);

        order.setStatus(OrderStatus.EXECUTED);
        order.setExecutionPrice(price);
        log.info("Order {} EXECUTED: {} {} {} @ {}", order.getId(), order.getType(), order.getQuantity(),
                order.getSymbol(), price);
        return orderRepository.save(order);
    }
}

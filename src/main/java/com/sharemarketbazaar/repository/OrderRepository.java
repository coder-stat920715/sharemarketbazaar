package com.sharemarketbazaar.repository;

import com.sharemarketbazaar.model.Order;
import com.sharemarketbazaar.model.Order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Order> findBySymbolAndStatus(String symbol, OrderStatus status);

    @Query("select distinct o.symbol from TradeOrder o where o.status = :status")
    List<String> findDistinctSymbolsByStatus(@Param("status") OrderStatus status);
}

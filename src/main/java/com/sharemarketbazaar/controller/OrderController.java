package com.sharemarketbazaar.controller;

import com.sharemarketbazaar.dto.OrderRequestDto;
import com.sharemarketbazaar.model.Order;
import com.sharemarketbazaar.model.Order.OrderStatus;
import com.sharemarketbazaar.service.OrderManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderManagementService orderService;

    /** 201 when accepted (EXECUTED or resting PENDING); 422 with the persisted order when risk-rejected. */
    @PostMapping
    public ResponseEntity<Order> placeOrder(@Valid @RequestBody OrderRequestDto request) {
        Order order = orderService.placeOrder(request);
        HttpStatus status = order.getStatus() == OrderStatus.REJECTED
                ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(order);
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @GetMapping
    public List<Order> listOrders(@RequestParam String userId) {
        return orderService.listOrders(userId);
    }

    @DeleteMapping("/{id}")
    public Order cancelOrder(@PathVariable Long id, @RequestParam String userId) {
        return orderService.cancelOrder(id, userId);
    }
}

package com.sharemarketbazaar.dto;

import com.sharemarketbazaar.model.Order;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Inbound manual order request. Omit {@code limitPrice} for a market order. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequestDto {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotBlank(message = "symbol is required")
    @Pattern(regexp = "^[A-Za-z0-9.&\\-]{1,20}$", message = "symbol contains invalid characters")
    private String symbol;

    @NotNull(message = "type (BUY/SELL) is required")
    private Order.OrderType type;

    @Positive(message = "quantity must be at least 1")
    private int quantity;

    @Positive(message = "limitPrice must be positive")
    private BigDecimal limitPrice;

    @Positive(message = "stopLoss must be positive")
    private BigDecimal stopLoss;
}

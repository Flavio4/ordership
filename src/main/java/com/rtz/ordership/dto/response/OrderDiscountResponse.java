package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.OrderDiscount;

import java.math.BigDecimal;

public record OrderDiscountResponse(String label, BigDecimal amount) {
    public static OrderDiscountResponse fromEntity(OrderDiscount discount) {
        return new OrderDiscountResponse(discount.getLabel(), discount.getAmount());
    }
}

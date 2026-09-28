package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.OrderItem;

import java.math.BigDecimal;

/**
 * Ganancia neta = lo que paga el cliente - costo de los productos - costo del delivery.
 *
 * @param productCost   suma de cantidad × costo de los productos que tienen costo
 * @param deliveryCost  lo que se le pagó al repartidor; null si no se cargó (cuenta como 0)
 * @param complete      false si algún producto no tiene precio de compra: la ganancia está sobreestimada
 */
public record OrderProfitResponse(
        BigDecimal revenue,
        BigDecimal productCost,
        BigDecimal deliveryCost,
        BigDecimal netProfit,
        boolean complete) {

    public static OrderProfitResponse fromEntity(Order order) {
        BigDecimal productCost = BigDecimal.ZERO;
        boolean complete = true;
        for (OrderItem item : order.getItems()) {
            if (item.getUnitCost() == null) {
                complete = false;
            } else {
                productCost = productCost.add(item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        BigDecimal revenue = order.getAmountToCollect() != null ? order.getAmountToCollect() : BigDecimal.ZERO;
        BigDecimal deliveryCost = order.getDeliveryCost();
        BigDecimal netProfit = revenue.subtract(productCost)
                .subtract(deliveryCost != null ? deliveryCost : BigDecimal.ZERO);
        return new OrderProfitResponse(revenue, productCost, deliveryCost, netProfit, complete);
    }
}

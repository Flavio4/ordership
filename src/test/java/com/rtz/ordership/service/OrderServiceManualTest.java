package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.OrderItemRequest;
import com.rtz.ordership.dto.request.OrderItemsUpdateRequest;
import com.rtz.ordership.dto.request.OrderRequest;
import com.rtz.ordership.dto.response.OrderResponse;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.OrderItem;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.entity.enums.Currency;
import com.rtz.ordership.entity.enums.OrderSource;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.entity.enums.PaymentStatus;
import com.rtz.ordership.repository.CustomerAddressRepository;
import com.rtz.ordership.repository.CustomerRepository;
import com.rtz.ordership.repository.OrderRepository;
import com.rtz.ordership.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceManualTest {

    private static final LocalDate DELIVERY = LocalDate.of(2026, 9, 28);

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private OrderService orderService;
    private Customer customer;
    private Product curcuma;
    private Product miel;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        orderService = new OrderService(orderRepository, customerRepository, mock(CustomerAddressRepository.class),
                productRepository);

        customer = Customer.builder().id(UUID.randomUUID()).fullName("Ana Rojas").phone("+595981000111").build();
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        curcuma = product("Cúrcuma", "179000", 5);
        miel = product("Miel", "50000", 0);

        User operator = User.builder().id(UUID.randomUUID()).fullName("Operador").email("op@ordership.com").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aManualOrderIsBornConfirmedWithItsDateAndTheTotalIncludesDeliveryAndDiscount() {
        OrderResponse order = orderService.createOrder(new OrderRequest(customer.getId(), null, DELIVERY, "  De tarde ",
                new BigDecimal("15000"), new BigDecimal("9000"), PaymentStatus.PAID,
                List.of(new OrderItemRequest(curcuma.getId(), 2), new OrderItemRequest(miel.getId(), 1))));

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.confirmedAt()).isNotNull();
        assertThat(order.source()).isEqualTo(OrderSource.MANUAL);
        assertThat(order.deliveryDate()).isEqualTo(DELIVERY);
        assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.notes()).isEqualTo("De tarde");
        assertThat(order.createdByName()).isEqualTo("Operador");
        assertThat(order.totalAmount()).isEqualByComparingTo("408000");
        assertThat(order.deliveryFee()).isEqualByComparingTo("15000");
        assertThat(order.discount()).isEqualByComparingTo("9000");
        assertThat(order.amountToCollect()).isEqualByComparingTo("414000");
    }

    @Test
    void withoutStockTheOrderIsNotBlockedAndTheStockGoesNegative() {
        orderService.createOrder(request(new OrderItemRequest(miel.getId(), 3)));

        assertThat(miel.getStock()).isEqualTo(-3);
    }

    @Test
    void theSameProductTwiceIsOneLine() {
        OrderResponse order = orderService.createOrder(request(
                new OrderItemRequest(curcuma.getId(), 1), new OrderItemRequest(curcuma.getId(), 2)));

        assertThat(order.items()).singleElement().satisfies(item -> assertThat(item.quantity()).isEqualTo(3));
        assertThat(curcuma.getStock()).isEqualTo(2);
    }

    @Test
    void productsInDollarsOrDeactivatedAreRejected() {
        Product imported = product("Importado", "20", 5);
        imported.setCurrency(Currency.USD);
        Product old = product("Viejo", "1000", 5);
        old.setActive(false);

        assertThatThrownBy(() -> orderService.createOrder(request(new OrderItemRequest(imported.getId(), 1))))
                .hasMessageContaining("dólares");
        assertThatThrownBy(() -> orderService.createOrder(request(new OrderItemRequest(old.getId(), 1))))
                .hasMessageContaining("desactivado");
    }

    @Test
    void theDiscountCannotBeBiggerThanTheTotal() {
        assertThatThrownBy(() -> orderService.createOrder(new OrderRequest(customer.getId(), null, DELIVERY, null,
                null, new BigDecimal("200000"), null, List.of(new OrderItemRequest(curcuma.getId(), 1)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editingItemsReturnsTheOldStockKeepsSoldPricesAndRecalculatesTheTotal() {
        Order order = existingOrder(OrderSource.MANUAL, OrderStatus.ASSIGNED);
        curcuma.setSalePrice(new BigDecimal("199000"));

        OrderResponse edited = orderService.updateItems(order.getId(), new OrderItemsUpdateRequest(
                List.of(new OrderItemRequest(curcuma.getId(), 1), new OrderItemRequest(miel.getId(), 2)),
                new BigDecimal("10000"), null));

        // 3 + 2 devueltas - 1 nueva
        assertThat(curcuma.getStock()).isEqualTo(4);
        assertThat(miel.getStock()).isEqualTo(-2);
        assertThat(edited.items()).hasSize(2);
        assertThat(edited.items().get(0).unitPrice()).isEqualByComparingTo("179000");
        assertThat(edited.totalAmount()).isEqualByComparingTo("279000");
        assertThat(edited.amountToCollect()).isEqualByComparingTo("289000");
        assertThat(edited.discount()).isEqualByComparingTo("0");
    }

    @Test
    void theCostIsFrozenAtSaleAndTheProfitSubtractsProductsAndDelivery() {
        curcuma.setPurchasePrice(new BigDecimal("100000"));
        Product unreviewed = product("Nuevo de Shopify", "60000", 5);
        unreviewed.setNeedsReview(true);

        OrderResponse order = orderService.createOrder(new OrderRequest(customer.getId(), null, DELIVERY, null,
                new BigDecimal("15000"), null, null, List.of(new OrderItemRequest(curcuma.getId(), 2))));
        assertThat(order.items().get(0).unitCost()).isEqualByComparingTo("100000");
        // 373.000 cobrados - 200.000 de productos, sin costo de delivery cargado
        assertThat(order.profit().productCost()).isEqualByComparingTo("200000");
        assertThat(order.profit().deliveryCost()).isNull();
        assertThat(order.profit().netProfit()).isEqualByComparingTo("173000");
        assertThat(order.profit().complete()).isTrue();

        OrderResponse incomplete = orderService.createOrder(request(new OrderItemRequest(unreviewed.getId(), 1)));
        assertThat(incomplete.items().get(0).unitCost()).isNull();
        assertThat(incomplete.profit().complete()).isFalse();
    }

    @Test
    void theDeliveryCostIsOptionalAndCannotBeNegative() {
        Order order = existingOrder(OrderSource.SHOPIFY, OrderStatus.DELIVERED);
        order.setAmountToCollect(new BigDecimal("380000"));

        OrderResponse withCost = orderService.updateDeliveryCost(order.getId(), new BigDecimal("20000"));
        // 380.000 - 2 × 150.000 - 20.000
        assertThat(withCost.profit().netProfit()).isEqualByComparingTo("60000");

        assertThat(orderService.updateDeliveryCost(order.getId(), null).profit().netProfit())
                .isEqualByComparingTo("80000");
        assertThatThrownBy(() -> orderService.updateDeliveryCost(order.getId(), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editingItemsKeepsTheCostOfTheProductsAlreadySold() {
        Order order = existingOrder(OrderSource.MANUAL, OrderStatus.CONFIRMED);
        curcuma.setPurchasePrice(new BigDecimal("170000"));

        OrderResponse edited = orderService.updateItems(order.getId(), new OrderItemsUpdateRequest(
                List.of(new OrderItemRequest(curcuma.getId(), 3)), null, null));

        assertThat(edited.items().get(0).unitCost()).isEqualByComparingTo("150000");
    }

    @Test
    void shopifyDeliveredOrCancelledOrdersCannotBeEdited() {
        OrderItemsUpdateRequest request = new OrderItemsUpdateRequest(
                List.of(new OrderItemRequest(curcuma.getId(), 1)), null, null);

        Order shopify = existingOrder(OrderSource.SHOPIFY, OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> orderService.updateItems(shopify.getId(), request)).hasMessageContaining("Shopify");

        Order delivered = existingOrder(OrderSource.MANUAL, OrderStatus.DELIVERED);
        assertThatThrownBy(() -> orderService.updateItems(delivered.getId(), request))
                .isInstanceOf(IllegalStateException.class);
        assertThat(curcuma.getStock()).isEqualTo(3);
    }

    private OrderRequest request(OrderItemRequest... items) {
        return new OrderRequest(customer.getId(), null, DELIVERY, null, null, null, null, List.of(items));
    }

    // Pedido con 2 cúrcumas vendidas a 179.000, con costo 150.000 (stock ya descontado: 5 → 3)
    private Order existingOrder(OrderSource source, OrderStatus status) {
        curcuma.setStock(3);
        Order order = Order.builder()
                .id(UUID.randomUUID()).customer(customer).source(source).status(status)
                .totalAmount(new BigDecimal("358000")).amountToCollect(new BigDecimal("358000"))
                .build();
        order.getItems().add(OrderItem.builder().order(order).product(curcuma).quantity(2)
                .unitPrice(new BigDecimal("179000")).subtotal(new BigDecimal("358000"))
                .unitCost(new BigDecimal("150000")).build());
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        return order;
    }

    private Product product(String name, String price, int stock) {
        Product product = Product.builder()
                .id(UUID.randomUUID()).name(name).salePrice(new BigDecimal(price)).purchasePrice(BigDecimal.ONE)
                .currency(Currency.PYG).stock(stock).active(true)
                .build();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        return product;
    }
}

package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.DeliveryAssignmentRequest;
import com.rtz.ordership.dto.request.DeliveryStatusUpdateRequest;
import com.rtz.ordership.entity.Carrier;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.CustomerAddress;
import com.rtz.ordership.entity.DeliveryAssignment;
import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.Zone;
import com.rtz.ordership.entity.enums.CarrierType;
import com.rtz.ordership.entity.enums.DeliveryStatus;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.entity.enums.ShippingMethod;
import com.rtz.ordership.repository.CarrierRepository;
import com.rtz.ordership.repository.DeliveryAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeliveryAssignmentServiceTest {

    private DeliveryAssignmentRepository deliveryRepository;
    private OrderService orderService;
    private CarrierService carrierService;
    private DeliveryAssignmentService service;

    private final Zone zone = Zone.builder().id(UUID.randomUUID()).name("Asunción").build();
    private final Carrier uncle = carrier("Tío Juan", CarrierType.OWN);
    private final Carrier courier = carrier("AEX", CarrierType.COURIER);

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(DeliveryAssignmentRepository.class);
        orderService = mock(OrderService.class);
        carrierService = mock(CarrierService.class);
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(carrierService.findCarrierOrThrow(uncle.getId())).thenReturn(uncle);
        when(carrierService.findCarrierOrThrow(courier.getId())).thenReturn(courier);
        service = new DeliveryAssignmentService(deliveryRepository, orderService, carrierService,
                mock(CarrierRepository.class));
    }

    @Test
    void ownCarrierNeedsAnAddressWithZone() {
        Order order = order(OrderStatus.CONFIRMED, false);

        assertThatThrownBy(() -> service.assign(request(order, uncle, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dirección con zona");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void courierDoesNotNeedAZoneAndKeepsTheTrackingCode() {
        Order order = order(OrderStatus.CONFIRMED, false);

        DeliveryAssignment delivery = service.assign(request(order, courier, " AEX-123 "));

        assertThat(delivery.getZone()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(order.getShippingMethod()).isEqualTo(ShippingMethod.COURIER);
        assertThat(order.getCourierName()).isEqualTo("AEX");
        assertThat(order.getTrackingCode()).isEqualTo("AEX-123");
        assertThat(order.getDeliveryAssignments()).containsExactly(delivery);
    }

    @Test
    void ownCarrierCopiesTheZoneAndClearsCourierData() {
        Order order = order(OrderStatus.CONFIRMED, true);
        order.setCourierName("AEX");
        order.setTrackingCode("viejo");

        DeliveryAssignment delivery = service.assign(request(order, uncle, "ignorado"));

        assertThat(delivery.getZone()).isEqualTo(zone);
        assertThat(order.getShippingMethod()).isEqualTo(ShippingMethod.OWN_DELIVERY);
        assertThat(order.getCourierName()).isNull();
        assertThat(order.getTrackingCode()).isNull();
    }

    @Test
    void onlyConfirmedOrdersWithoutAnActiveDeliveryCanBeAssigned() {
        Order pending = order(OrderStatus.PENDING, true);
        assertThatThrownBy(() -> service.assign(request(pending, uncle, null)))
                .hasMessageContaining("confirmados");

        Order confirmed = order(OrderStatus.CONFIRMED, true);
        when(deliveryRepository.existsByOrderIdAndStatusIn(eq(confirmed.getId()), anyCollection())).thenReturn(true);
        assertThatThrownBy(() -> service.assign(request(confirmed, uncle, null)))
                .hasMessageContaining("entrega en curso");
    }

    @Test
    void inactiveCarrierCannotTakeOrders() {
        uncle.setActive(false);
        Order order = order(OrderStatus.CONFIRMED, true);

        assertThatThrownBy(() -> service.assign(request(order, uncle, null)))
                .hasMessageContaining("desactivado");
    }

    @Test
    void assignedDeliveryCanBeMarkedDeliveredDirectly() {
        Order order = order(OrderStatus.ASSIGNED, true);
        DeliveryAssignment delivery = activeDelivery(order);

        service.updateActiveDeliveryOfOrder(order.getId(), new DeliveryStatusUpdateRequest(DeliveryStatus.DELIVERED, null));

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getCompletedAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void failedDeliveryNeedsAReasonAndLeavesTheOrderConfirmedForAnotherTry() {
        Order order = order(OrderStatus.IN_TRANSIT, true);
        DeliveryAssignment delivery = activeDelivery(order);
        delivery.setStatus(DeliveryStatus.IN_TRANSIT);

        assertThatThrownBy(() -> service.updateActiveDeliveryOfOrder(order.getId(),
                new DeliveryStatusUpdateRequest(DeliveryStatus.FAILED, "  ")))
                .isInstanceOf(IllegalArgumentException.class);

        service.updateActiveDeliveryOfOrder(order.getId(),
                new DeliveryStatusUpdateRequest(DeliveryStatus.FAILED, "No atendió el teléfono"));

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getFailureReason()).isEqualTo("No atendió el teléfono");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void orderWithoutActiveDeliveryCannotAdvance() {
        Order order = order(OrderStatus.CONFIRMED, true);
        when(deliveryRepository.findByOrderIdAndStatusIn(eq(order.getId()), anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateActiveDeliveryOfOrder(order.getId(),
                new DeliveryStatusUpdateRequest(DeliveryStatus.DELIVERED, null)))
                .hasMessageContaining("no tiene una entrega en curso");
    }

    private DeliveryAssignment activeDelivery(Order order) {
        DeliveryAssignment delivery = DeliveryAssignment.builder()
                .id(UUID.randomUUID()).order(order).carrier(uncle).zone(zone).status(DeliveryStatus.ASSIGNED).build();
        when(deliveryRepository.findByOrderIdAndStatusIn(eq(order.getId()), anyCollection())).thenReturn(List.of(delivery));
        return delivery;
    }

    private DeliveryAssignmentRequest request(Order order, Carrier carrier, String trackingCode) {
        return new DeliveryAssignmentRequest(order.getId(), carrier.getId(), trackingCode, null);
    }

    private Order order(OrderStatus status, boolean withAddress) {
        Customer customer = Customer.builder().id(UUID.randomUUID()).fullName("Cliente").phone("+595981000999").build();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .status(status)
                .customerAddress(withAddress
                        ? CustomerAddress.builder().id(UUID.randomUUID()).customer(customer).zone(zone).build()
                        : null)
                .build();
        when(orderService.findOrderOrThrow(order.getId())).thenReturn(order);
        return order;
    }

    private static Carrier carrier(String name, CarrierType type) {
        return Carrier.builder().id(UUID.randomUUID()).name(name).type(type).active(true).build();
    }
}

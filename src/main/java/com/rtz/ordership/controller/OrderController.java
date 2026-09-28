package com.rtz.ordership.controller;

import com.rtz.ordership.dto.request.DeliveryAssignmentRequest;
import com.rtz.ordership.dto.request.DeliveryStatusUpdateRequest;
import com.rtz.ordership.dto.request.OrderAddressUpdateRequest;
import com.rtz.ordership.dto.request.OrderDeliveryCostRequest;
import com.rtz.ordership.dto.request.OrderDeliveryDateRequest;
import com.rtz.ordership.dto.request.OrderDeliveryRequest;
import com.rtz.ordership.dto.request.OrderItemsUpdateRequest;
import com.rtz.ordership.dto.request.OrderRequest;
import com.rtz.ordership.dto.request.OrderStatusUpdateRequest;
import com.rtz.ordership.dto.request.PaymentStatusUpdateRequest;
import com.rtz.ordership.dto.response.AgendaSummaryResponse;
import com.rtz.ordership.dto.response.OrderResponse;
import com.rtz.ordership.entity.enums.OrderSource;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.service.DeliveryAssignmentService;
import com.rtz.ordership.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Pedidos", description = "Gestión de pedidos y sus ítems")
public class OrderController {

    private final OrderService orderService;
    private final DeliveryAssignmentService deliveryService;

    public OrderController(OrderService orderService, DeliveryAssignmentService deliveryService) {
        this.orderService = orderService;
        this.deliveryService = deliveryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Listar pedidos", description = "Lista pedidos paginados con filtros opcionales por estado, fecha de entrega, cliente y origen (MANUAL/SHOPIFY). "
            + "query busca por nombre del cliente, número propio (P-1024), número de Shopify (#1488) o teléfono (desde 6 dígitos). "
            + "scheduled=true: agenda, solo los que tienen fecha de entrega y todavía no se entregaron ni cancelaron")
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) OrderSource source,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean scheduled,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(status, deliveryDate, customerId, source, query, scheduled, pageable));
    }

    @GetMapping("/agenda-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Resumen de la agenda", description = "Cuántos pedidos programados están atrasados y cuántos son para hoy. "
            + "today lo manda la app (fecha del celular) para no depender de la zona horaria del servidor")
    public ResponseEntity<AgendaSummaryResponse> getAgendaSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today,
            @RequestParam(required = false) OrderSource source) {
        return ResponseEntity.ok(orderService.getAgendaSummary(today, source));
    }

    @PatchMapping("/{id}/delivery-date")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Programar entrega", description = "Fecha en que el cliente quiere recibir el pedido; null la quita")
    public ResponseEntity<OrderResponse> updateDeliveryDate(@PathVariable UUID id,
            @RequestBody OrderDeliveryDateRequest request) {
        return ResponseEntity.ok(orderService.updateDeliveryDate(id, request.deliveryDate()));
    }

    @PatchMapping("/{id}/delivery-cost")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Costo del delivery", description = "Lo que se le pagó al repartidor o courier, para la ganancia "
            + "neta del pedido (profit); null lo quita")
    public ResponseEntity<OrderResponse> updateDeliveryCost(@PathVariable UUID id,
            @RequestBody OrderDeliveryCostRequest request) {
        return ResponseEntity.ok(orderService.updateDeliveryCost(id, request.deliveryCost()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Obtener pedido", description = "Obtiene el detalle de un pedido incluyendo sus ítems")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Crear pedido manual",
            description = "Pedido que el cliente hizo directamente: nace CONFIRMED con su fecha de entrega. A cobrar = "
                    + "productos (precio de catálogo) + envío - descuento. Descuenta stock sin bloquear (puede quedar "
                    + "negativo). La dirección es opcional; los productos tienen que ser en guaraníes y estar activos")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @PutMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Editar productos de un pedido manual",
            description = "Reemplaza los productos, el envío y el descuento. Devuelve el stock de los anteriores y "
                    + "descuenta el de los nuevos; los que ya estaban conservan su precio. Solo pedidos MANUAL que no "
                    + "estén entregados ni cancelados")
    public ResponseEntity<OrderResponse> updateItems(@PathVariable UUID id,
            @Valid @RequestBody OrderItemsUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateItems(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Cambiar estado", description = "Cambia el estado del pedido (solo transiciones válidas)")
    public ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable UUID id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request));
    }

    @PatchMapping("/{id}/payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Actualizar pago", description = "Cambia el estado de pago del pedido (UNPAID, PAID, PARTIAL)")
    public ResponseEntity<OrderResponse> updatePaymentStatus(@PathVariable UUID id,
            @Valid @RequestBody PaymentStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updatePaymentStatus(id, request));
    }

    @PatchMapping("/{id}/address")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Asignar dirección", description = "Vincula una dirección con zona a un pedido que no tiene una todavía (por ejemplo, pedidos de Shopify)")
    public ResponseEntity<OrderResponse> updateOrderAddress(@PathVariable UUID id,
            @Valid @RequestBody OrderAddressUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateOrderAddress(id, request));
    }

    @PostMapping("/{id}/delivery")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Asignar reparto", description = "Asigna un pedido CONFIRMED a un repartidor propio (requiere dirección con zona) "
            + "o a un courier (trackingCode opcional). El pedido pasa a ASSIGNED")
    public ResponseEntity<OrderResponse> assignDelivery(@PathVariable UUID id,
            @Valid @RequestBody OrderDeliveryRequest request) {
        deliveryService.assign(new DeliveryAssignmentRequest(id, request.carrierId(), request.trackingCode(), request.notes()));
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @PatchMapping("/{id}/delivery")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Avanzar la entrega", description = "Cambia el estado de la entrega en curso: IN_TRANSIT, DELIVERED o FAILED "
            + "(notes obligatorio con el motivo; el pedido vuelve a CONFIRMED para otro intento)")
    public ResponseEntity<OrderResponse> updateDelivery(@PathVariable UUID id,
            @Valid @RequestBody DeliveryStatusUpdateRequest request) {
        deliveryService.updateActiveDeliveryOfOrder(id, request);
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Cancelar pedido", description = "Cancela un pedido (cambia el estado a CANCELLED y devuelve stock)")
    public ResponseEntity<Void> cancelOrder(@PathVariable UUID id) {
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }
}

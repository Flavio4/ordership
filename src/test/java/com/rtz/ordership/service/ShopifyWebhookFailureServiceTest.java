package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.ShopifyWebhookFailureResponse;
import com.rtz.ordership.entity.ShopifyWebhookFailure;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.ShopifyWebhookFailureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShopifyWebhookFailureServiceTest {

    private static final String PAYLOAD = "{\"id\":555}";
    private static final String ADMIN_URL = "https://admin.shopify.com/store/mitienda/orders/555";

    private ShopifyWebhookFailureRepository failureRepository;
    private ShopifyWebhookFailureService service;

    @BeforeEach
    void setUp() {
        failureRepository = mock(ShopifyWebhookFailureRepository.class);
        when(failureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new ShopifyWebhookFailureService(failureRepository, JsonMapper.builder().build());
    }

    @Test
    void theResponseSummarizesTheOrderFromThePayload() {
        String payload = """
                {"id":555,"name":"#1490","total_price":"329000.00","currency":"PYG",
                 "note_attributes":[{"name":"Nombre","value":"Ana"},{"name":"Apellido","value":"Rojas"},
                                    {"name":"Dirección","value":"Palma 123"},{"name":"Ciudad","value":"Asunción"}],
                 "line_items":[{"title":"Cúrcuma","variant_title":"Default Title","quantity":2},
                               {"title":"Miel","variant_title":"500 g","quantity":1}]}""";
        ShopifyWebhookFailure failure = ShopifyWebhookFailure.builder()
                .id(UUID.randomUUID()).shopifyOrderId("555").payload(payload).reason("Sin teléfono")
                .adminUrl(ADMIN_URL).build();
        when(failureRepository.findByResolvedAtIsNull(any())).thenReturn(new PageImpl<>(List.of(failure)));

        ShopifyWebhookFailureResponse response = service.getFailures(false, PageRequest.of(0, 20)).getContent().get(0);

        assertThat(response.adminUrl()).isEqualTo(ADMIN_URL);
        assertThat(response.order().orderName()).isEqualTo("#1490");
        assertThat(response.order().customerName()).isEqualTo("Ana Rojas");
        assertThat(response.order().phone()).isNull();
        assertThat(response.order().totalPrice()).isEqualByComparingTo("329000");
        assertThat(response.order().items()).containsExactly("2 × Cúrcuma", "1 × Miel - 500 g");
        assertThat(response.order().shippingAddress()).isEqualTo("Palma 123, Asunción");
    }

    @Test
    void anUnreadablePayloadHasNoSummary() {
        ShopifyWebhookFailure failure = ShopifyWebhookFailure.builder()
                .id(UUID.randomUUID()).payload("esto no es json").reason("Payload inválido").build();
        when(failureRepository.findByResolvedAtIsNull(any())).thenReturn(new PageImpl<>(List.of(failure)));

        assertThat(service.getFailures(false, PageRequest.of(0, 20)).getContent().get(0).order()).isNull();
    }

    @Test
    void newFailureIsRecorded() {
        when(failureRepository.findByShopifyOrderId("555")).thenReturn(Optional.empty());

        service.recordFailure("555", PAYLOAD, "Sin teléfono", ADMIN_URL);

        ArgumentCaptor<ShopifyWebhookFailure> saved = ArgumentCaptor.forClass(ShopifyWebhookFailure.class);
        verify(failureRepository).save(saved.capture());
        assertThat(saved.getValue().getShopifyOrderId()).isEqualTo("555");
        assertThat(saved.getValue().getAdminUrl()).isEqualTo(ADMIN_URL);
        assertThat(saved.getValue().getPayload()).isEqualTo(PAYLOAD);
        assertThat(saved.getValue().getReason()).isEqualTo("Sin teléfono");
        assertThat(saved.getValue().getAttempts()).isEqualTo(1);
    }

    @Test
    void redeliveryUpdatesExistingFailure() {
        ShopifyWebhookFailure existing = ShopifyWebhookFailure.builder()
                .id(UUID.randomUUID())
                .shopifyOrderId("555")
                .payload(PAYLOAD)
                .reason("Sin teléfono")
                .build();
        when(failureRepository.findByShopifyOrderId("555")).thenReturn(Optional.of(existing));

        service.recordFailure("555", PAYLOAD, "Ítem sin SKU", null);

        verify(failureRepository).save(existing);
        assertThat(existing.getAttempts()).isEqualTo(2);
        assertThat(existing.getReason()).isEqualTo("Ítem sin SKU");
        assertThat(existing.getLastAttemptAt()).isNotNull();
    }

    @Test
    void getFailuresMapsAllFieldsIncludingPayload() {
        ShopifyWebhookFailure failure = ShopifyWebhookFailure.builder()
                .id(UUID.randomUUID())
                .shopifyOrderId("555")
                .payload(PAYLOAD)
                .reason("Sin teléfono")
                .build();
        when(failureRepository.findByResolvedAtIsNull(any())).thenReturn(new PageImpl<>(List.of(failure)));

        Page<ShopifyWebhookFailureResponse> failures = service.getFailures(false, PageRequest.of(0, 20));

        assertThat(failures.getContent()).singleElement().satisfies(f -> {
            assertThat(f.shopifyOrderId()).isEqualTo("555");
            assertThat(f.reason()).isEqualTo("Sin teléfono");
            assertThat(f.payload()).isEqualTo(PAYLOAD);
            assertThat(f.attempts()).isEqualTo(1);
            assertThat(f.resolvedAt()).isNull();
        });
        verify(failureRepository, never()).findByResolvedAtIsNotNull(any());
    }

    @Test
    void resolveStoresTheNoteAndWhoResolvedIt() {
        ShopifyWebhookFailure failure = ShopifyWebhookFailure.builder()
                .id(UUID.randomUUID())
                .shopifyOrderId("555")
                .payload(PAYLOAD)
                .reason("Sin teléfono")
                .build();
        when(failureRepository.findById(failure.getId())).thenReturn(Optional.of(failure));
        User operator = User.builder().email("op@ordership.com").fullName("Operador").build();

        ShopifyWebhookFailureResponse response = service.resolve(failure.getId(), "  Cargado a mano  ", operator);

        assertThat(failure.getResolvedAt()).isNotNull();
        assertThat(failure.getResolvedBy()).isSameAs(operator);
        assertThat(response.resolutionNote()).isEqualTo("Cargado a mano");
        assertThat(response.resolvedByName()).isEqualTo("Operador");
    }

    @Test
    void alreadyResolvedFailureCannotBeResolvedAgain() {
        ShopifyWebhookFailure failure = ShopifyWebhookFailure.builder()
                .id(UUID.randomUUID())
                .payload(PAYLOAD)
                .reason("Sin teléfono")
                .resolvedAt(Instant.now())
                .resolutionNote("Cargado a mano")
                .build();
        when(failureRepository.findById(failure.getId())).thenReturn(Optional.of(failure));

        assertThatThrownBy(() -> service.resolve(failure.getId(), "Otra nota", User.builder().build()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(failure.getResolutionNote()).isEqualTo("Cargado a mano");
        verify(failureRepository, never()).save(any());
    }

    @Test
    void resolvingAnUnknownFailureIsNotFound() {
        UUID id = UUID.randomUUID();
        when(failureRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(id, "nota", User.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void failureWithoutOrderIdIsNeverDeduplicated() {
        service.recordFailure(null, "esto no es json", "Payload inválido", null);

        verify(failureRepository, never()).findByShopifyOrderId(any());
        verify(failureRepository).save(any());
    }
}

package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.ShopifyWebhookFailureResponse;
import com.rtz.ordership.entity.ShopifyWebhookFailure;
import com.rtz.ordership.repository.ShopifyWebhookFailureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShopifyWebhookFailureServiceTest {

    private static final String PAYLOAD = "{\"id\":555}";

    private ShopifyWebhookFailureRepository failureRepository;
    private ShopifyWebhookFailureService service;

    @BeforeEach
    void setUp() {
        failureRepository = mock(ShopifyWebhookFailureRepository.class);
        when(failureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new ShopifyWebhookFailureService(failureRepository);
    }

    @Test
    void newFailureIsRecorded() {
        when(failureRepository.findByShopifyOrderId("555")).thenReturn(Optional.empty());

        service.recordFailure("555", PAYLOAD, "Sin teléfono");

        ArgumentCaptor<ShopifyWebhookFailure> saved = ArgumentCaptor.forClass(ShopifyWebhookFailure.class);
        verify(failureRepository).save(saved.capture());
        assertThat(saved.getValue().getShopifyOrderId()).isEqualTo("555");
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

        service.recordFailure("555", PAYLOAD, "Ítem sin SKU");

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
        when(failureRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(failure));

        List<ShopifyWebhookFailureResponse> failures = service.getFailures();

        assertThat(failures).singleElement().satisfies(f -> {
            assertThat(f.shopifyOrderId()).isEqualTo("555");
            assertThat(f.reason()).isEqualTo("Sin teléfono");
            assertThat(f.payload()).isEqualTo(PAYLOAD);
            assertThat(f.attempts()).isEqualTo(1);
        });
    }

    @Test
    void failureWithoutOrderIdIsNeverDeduplicated() {
        service.recordFailure(null, "esto no es json", "Payload inválido");

        verify(failureRepository, never()).findByShopifyOrderId(any());
        verify(failureRepository).save(any());
    }
}

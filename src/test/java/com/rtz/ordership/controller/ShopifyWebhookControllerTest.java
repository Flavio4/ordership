package com.rtz.ordership.controller;

import com.rtz.ordership.repository.UserRepository;
import com.rtz.ordership.security.JwtProvider;
import com.rtz.ordership.service.ShopifyWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShopifyWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.shopify.webhook-secret=" + ShopifyWebhookControllerTest.SECRET)
class ShopifyWebhookControllerTest {

    static final String SECRET = "test-shopify-secret";
    private static final String BODY = "{\"id\":123,\"note\":\"Envío a Córdoba\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShopifyWebhookController controller;

    @MockitoBean
    private ShopifyWebhookService shopifyWebhookService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void acceptsValidSignature() throws Exception {
        mockMvc.perform(post("/api/webhooks/shopify/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.getBytes(StandardCharsets.UTF_8))
                        .header("X-Shopify-Hmac-Sha256", sign(BODY, SECRET)))
                .andExpect(status().isOk());

        verify(shopifyWebhookService).receiveOrderCreated(BODY);
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        mockMvc.perform(post("/api/webhooks/shopify/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.getBytes(StandardCharsets.UTF_8))
                        .header("X-Shopify-Hmac-Sha256", sign(BODY, "otro-secret")))
                .andExpect(status().isUnauthorized());

        verify(shopifyWebhookService, never()).receiveOrderCreated(any());
    }

    @Test
    void rejectsHexSignature() throws Exception {
        // Mismo HMAC pero en hexadecimal: Shopify firma los webhooks en Base64, así que no debe aceptarse
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hex = java.util.HexFormat.of().formatHex(mac.doFinal(BODY.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/webhooks/shopify/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.getBytes(StandardCharsets.UTF_8))
                        .header("X-Shopify-Hmac-Sha256", hex))
                .andExpect(status().isUnauthorized());

        verify(shopifyWebhookService, never()).receiveOrderCreated(any());
    }

    @Test
    void rejectsBlankSignature() throws Exception {
        mockMvc.perform(post("/api/webhooks/shopify/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.getBytes(StandardCharsets.UTF_8))
                        .header("X-Shopify-Hmac-Sha256", "   "))
                .andExpect(status().isUnauthorized());

        verify(shopifyWebhookService, never()).receiveOrderCreated(any());
    }

    @Test
    void rejectsMissingSignature() throws Exception {
        mockMvc.perform(post("/api/webhooks/shopify/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized());

        verify(shopifyWebhookService, never()).receiveOrderCreated(any());
    }

    @Test
    void rejectsWhenSecretNotConfiguredAndUnsignedNotAllowed() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");
        try {
            mockMvc.perform(post("/api/webhooks/shopify/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY.getBytes(StandardCharsets.UTF_8)))
                    .andExpect(status().isServiceUnavailable());

            verify(shopifyWebhookService, never()).receiveOrderCreated(any());
        } finally {
            ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
        }
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}

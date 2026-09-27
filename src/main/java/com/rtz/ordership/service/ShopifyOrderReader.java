package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.ShopifyOrderSummary;
import com.rtz.ordership.dto.webhook.ShopifyOrderWebhookPayload;
import com.rtz.ordership.dto.webhook.ShopifyOrderWebhookPayload.NoteAttribute;
import com.rtz.ordership.dto.webhook.ShopifyOrderWebhookPayload.ShopifyAddress;
import com.rtz.ordership.dto.webhook.ShopifyOrderWebhookPayload.ShopifyLineItem;
import tools.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lee el payload de un pedido de Shopify: los datos del comprador salen primero del formulario de Releasit
 * ("Información adicional") y, si no están, de los campos estándar de Shopify.
 */
public class ShopifyOrderReader {

    private final ObjectMapper objectMapper;

    public ShopifyOrderReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ShopifyOrderWebhookPayload parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, ShopifyOrderWebhookPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Payload de Shopify inválido: " + e.getMessage(), e);
        }
    }

    /** Resumen para mostrar un pedido que no se pudo crear; con un payload ilegible devuelve null. */
    public ShopifyOrderSummary summarize(String rawBody) {
        ShopifyOrderWebhookPayload payload;
        try {
            payload = parse(rawBody);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Map<String, String> fields = formFields(payload);
        List<String> items = payload.lineItems() == null ? List.of()
                : payload.lineItems().stream()
                        .map(li -> (li.quantity() == null ? 1 : li.quantity()) + " × " + productName(li))
                        .toList();
        return new ShopifyOrderSummary(
                payload.name(),
                customerName(payload, fields),
                phone(payload, fields),
                payload.totalPrice(),
                payload.currency(),
                items,
                shippingAddress(payload.shippingAddress(), fields));
    }

    /**
     * Campos del formulario de Releasit, con la clave en minúsculas y sin tildes ("Dirección" → "direccion")
     * para no depender de cómo esté escrita la etiqueta en el formulario.
     */
    public Map<String, String> formFields(ShopifyOrderWebhookPayload payload) {
        Map<String, String> fields = new HashMap<>();
        if (payload.noteAttributes() == null) {
            return fields;
        }
        for (NoteAttribute attribute : payload.noteAttributes()) {
            if (attribute.name() != null && notBlank(attribute.value())) {
                fields.put(normalizeKey(attribute.name()), attribute.value().trim());
            }
        }
        return fields;
    }

    public static String normalizeKey(String key) {
        return Normalizer.normalize(key.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    // Primero lo que escribió el comprador en el formulario (su WhatsApp); después los campos estándar de Shopify
    public String phone(ShopifyOrderWebhookPayload payload, Map<String, String> formFields) {
        String formPhone = firstField(formFields, "whatsapp", "telefono", "celular", "phone");
        if (formPhone != null) {
            return formPhone;
        }
        if (payload.shippingAddress() != null && notBlank(payload.shippingAddress().phone())) {
            return payload.shippingAddress().phone();
        }
        if (payload.customer() != null && notBlank(payload.customer().phone())) {
            return payload.customer().phone();
        }
        return payload.phone();
    }

    public String customerName(ShopifyOrderWebhookPayload payload, Map<String, String> formFields) {
        String formFirstName = firstField(formFields, "nombre");
        String formLastName = firstField(formFields, "apellido");
        if (formFirstName != null || formLastName != null) {
            return joinNames(formFirstName, formLastName);
        }
        if (payload.customer() != null
                && (notBlank(payload.customer().firstName()) || notBlank(payload.customer().lastName()))) {
            return joinNames(payload.customer().firstName(), payload.customer().lastName());
        }
        if (payload.shippingAddress() != null
                && (notBlank(payload.shippingAddress().firstName()) || notBlank(payload.shippingAddress().lastName()))) {
            return joinNames(payload.shippingAddress().firstName(), payload.shippingAddress().lastName());
        }
        return "Cliente Shopify #" + payload.id();
    }

    public String email(ShopifyOrderWebhookPayload payload) {
        if (payload.customer() != null && notBlank(payload.customer().email())) {
            return payload.customer().email();
        }
        return payload.email();
    }

    // Dirección del formulario si la hay (la dirección de envío estándar suele venir vacía en los pedidos de Releasit),
    // más la referencia cercana, que le sirve al repartidor
    public String shippingAddress(ShopifyAddress address, Map<String, String> formFields) {
        String formAddress = joinNonBlank(
                firstField(formFields, "direccion"),
                firstField(formFields, "ciudad"),
                firstField(formFields, "departamento"));

        String baseAddress = notBlank(formAddress) ? formAddress : standardAddress(address);
        String reference = firstField(formFields, "referencia cercana", "referencia");

        if (reference == null) {
            return notBlank(baseAddress) ? baseAddress : null;
        }
        return notBlank(baseAddress) ? baseAddress + ". Referencia: " + reference : "Referencia: " + reference;
    }

    public String productName(ShopifyLineItem lineItem) {
        String title = notBlank(lineItem.title()) ? lineItem.title().trim() : "Producto Shopify " + lineItem.sku();
        // Shopify manda "Default Title" como variante en los productos sin variantes
        if (notBlank(lineItem.variantTitle()) && !"Default Title".equals(lineItem.variantTitle())) {
            return title + " - " + lineItem.variantTitle().trim();
        }
        return title;
    }

    private String standardAddress(ShopifyAddress address) {
        if (address == null) {
            return null;
        }
        return joinNonBlank(
                address.address1(),
                address.address2(),
                address.city(),
                address.province(),
                address.zip(),
                address.country());
    }

    private String firstField(Map<String, String> formFields, String... keys) {
        for (String key : keys) {
            if (notBlank(formFields.get(key))) {
                return formFields.get(key);
            }
        }
        return null;
    }

    private String joinNonBlank(String... parts) {
        return Stream.of(parts)
                .filter(ShopifyOrderReader::notBlank)
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    private String joinNames(String first, String last) {
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }

    static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

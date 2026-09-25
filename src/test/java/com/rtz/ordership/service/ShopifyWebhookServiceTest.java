package com.rtz.ordership.service;

import com.rtz.ordership.dto.webhook.ShopifyOrderDetails;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.entity.enums.Currency;
import com.rtz.ordership.entity.enums.Unit;
import com.rtz.ordership.repository.CustomerRepository;
import com.rtz.ordership.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopifyWebhookServiceTest {

    private static final String PAYLOAD = """
            {"id":555,"phone":"+595981000999","currency":"PYG",
             "line_items":[{"id":1,"title":"Remera","variant_title":"Azul / M","sku":"REM-01",
                            "quantity":2,"price":"150000.00"}]}""";

    private OrderService orderService;
    private CustomerRepository customerRepository;
    private ProductRepository productRepository;
    private ShopifyWebhookFailureService failureService;
    private PlatformTransactionManager transactionManager;
    private ShopifyWebhookService service;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        customerRepository = mock(CustomerRepository.class);
        productRepository = mock(ProductRepository.class);
        failureService = mock(ShopifyWebhookFailureService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        when(customerRepository.findByPhone(anyString())).thenReturn(Optional.empty());
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findByShopifySku(anyString())).thenReturn(Optional.empty());
        when(productRepository.findFirstByNameIgnoreCaseAndShopifySkuIsNull(anyString())).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        service = new ShopifyWebhookService(orderService, customerRepository, productRepository, failureService,
                JsonMapper.builder().build(), new TransactionTemplate(transactionManager),
                List.of("Envio Prioritario y Garantia Extendida"));
    }

    @Test
    void knownSkuUsesExistingProduct() {
        givenProductWithSku("REM-01");

        service.receiveOrderCreated(PAYLOAD, null);

        verify(orderService).createOrderFromShopify(any(Customer.class), withId("555"), anyList());
        verify(productRepository, never()).save(any());
        verify(transactionManager).commit(any());
        verifyNoInteractions(failureService);
    }

    @Test
    void unknownSkuCreatesProductMarkedForReview() {
        service.receiveOrderCreated(PAYLOAD, null);

        ArgumentCaptor<Product> created = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(created.capture());
        Product product = created.getValue();
        assertThat(product.getName()).isEqualTo("Remera - Azul / M");
        assertThat(product.getShopifySku()).isEqualTo("REM-01");
        assertThat(product.getSalePrice()).isEqualByComparingTo("150000.00");
        assertThat(product.getPurchasePrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(product.getCurrency()).isEqualTo(Currency.PYG);
        assertThat(product.getUnit()).isEqualTo(Unit.UNID);
        assertThat(product.getStock()).isZero();
        assertThat(product.getNeedsReview()).isTrue();

        verify(orderService).createOrderFromShopify(any(Customer.class), withId("555"), anyList());
        verifyNoInteractions(failureService);
    }

    @Test
    void unknownSkuIsLinkedToExistingProductWithSameNameAndNoSku() {
        Product existing = Product.builder().id(UUID.randomUUID()).name("Remera - Azul / M")
                .purchasePrice(new BigDecimal("80000")).build();
        when(productRepository.findFirstByNameIgnoreCaseAndShopifySkuIsNull("Remera - Azul / M"))
                .thenReturn(Optional.of(existing));

        service.receiveOrderCreated(PAYLOAD, null);

        verify(productRepository).save(existing);
        assertThat(existing.getShopifySku()).isEqualTo("REM-01");
        assertThat(existing.getNeedsReview()).isFalse();
        assertThat(existing.getPurchasePrice()).isEqualByComparingTo("80000");
        verify(orderService).createOrderFromShopify(any(Customer.class), withId("555"), anyList());
    }

    @Test
    void nameTakenByProductWithOtherSkuGetsSkuSuffix() {
        when(productRepository.existsByName("Remera - Azul / M")).thenReturn(true);

        service.receiveOrderCreated(PAYLOAD, null);

        ArgumentCaptor<Product> created = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(created.capture());
        assertThat(created.getValue().getName()).isEqualTo("Remera - Azul / M (REM-01)");
    }

    @Test
    void defaultVariantTitleIsNotAddedToName() {
        String payload = PAYLOAD.replace("Azul / M", "Default Title");

        service.receiveOrderCreated(payload, null);

        ArgumentCaptor<Product> created = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(created.capture());
        assertThat(created.getValue().getName()).isEqualTo("Remera");
    }

    // Pedido como los del formulario de Releasit (ej. #1440): sin cliente ni teléfono estándar,
    // los datos del comprador vienen en "Información adicional" (note_attributes)
    private static final String RELEASIT_PAYLOAD = """
            {"id":1440,"currency":"PYG","customer":null,"shipping_address":null,
             "line_items":[{"id":1,"title":"Magnesium 60 caps","sku":"8364749557","quantity":1,"price":"179000.00"}],
             "note_attributes":[
               {"name":"quantityOffer","value":"1783556215762_0"},
               {"name":"Nombre","value":"Diego"},
               {"name":"Apellido","value":"Navarro"},
               {"name":"whatsapp","value":"0986871042"},
               {"name":"Dirección","value":"Avda 23 de octubre con amambay"},
               {"name":"Referencia cercana","value":"A Dos cuadras de fabrica de cartón yaguarete"},
               {"name":"Departamento","value":"Alto Paraná"},
               {"name":"Ciudad","value":"Ciudad del este"},
               {"name":"UTM source","value":"facebook"}]}""";

    @Test
    void releasitFormFieldsAreUsedForCustomerAndAddress() {
        givenProductWithSku("8364749557");

        service.receiveOrderCreated(RELEASIT_PAYLOAD, null);

        ArgumentCaptor<Customer> customer = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customer.capture());
        assertThat(customer.getValue().getFullName()).isEqualTo("Diego Navarro");
        assertThat(customer.getValue().getPhone()).isEqualTo("+595986871042");

        verify(customerRepository).findByPhone("+595986871042");
        assertThat(capturedDetails().shippingAddressRaw()).isEqualTo(
                "Avda 23 de octubre con amambay, Ciudad del este, Alto Paraná."
                        + " Referencia: A Dos cuadras de fabrica de cartón yaguarete");
        verifyNoInteractions(failureService);
    }

    @Test
    void releasitWhatsappTakesPrecedenceOverStandardPhone() {
        givenProductWithSku("REM-01");
        String payload = PAYLOAD.replace("\"line_items\"",
                "\"note_attributes\":[{\"name\":\"WhatsApp\",\"value\":\"0986 871 042\"}],\"line_items\"");

        service.receiveOrderCreated(payload, null);

        verify(customerRepository).findByPhone("+595986871042");
    }

    @Test
    void standardShopifyFieldsAreUsedWhenThereIsNoForm() {
        givenProductWithSku("REM-01");
        String payload = PAYLOAD
                .replace("\"phone\":\"+595981000999\",", "\"phone\":\"0981000999\",")
                .replace("\"line_items\"", "\"customer\":{\"first_name\":\"Ana\",\"last_name\":\"Gómez\"},"
                        + "\"shipping_address\":{\"address1\":\"Calle 1\",\"city\":\"Asunción\"},\"line_items\"");

        service.receiveOrderCreated(payload, null);

        ArgumentCaptor<Customer> customer = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customer.capture());
        assertThat(customer.getValue().getFullName()).isEqualTo("Ana Gómez");
        assertThat(customer.getValue().getPhone()).isEqualTo("+595981000999");
        assertThat(capturedDetails().shippingAddressRaw()).isEqualTo("Calle 1, Asunción");
    }

    @Test
    void existingCustomerIsFoundRegardlessOfPhoneFormat() {
        givenProductWithSku("8364749557");
        Customer existing = Customer.builder().id(UUID.randomUUID()).fullName("Diego Navarro")
                .phone("+595986871042").build();
        when(customerRepository.findByPhone("+595986871042")).thenReturn(Optional.of(existing));

        service.receiveOrderCreated(RELEASIT_PAYLOAD, null);

        verify(customerRepository, never()).save(any());
        verify(orderService).createOrderFromShopify(eq(existing), withId("1440"), anyList());
    }

    // Pedido #1440 real: Magnesium (179.000) + extra "Envio Prioritario y Garantia Extendida" (10.000, sin SKU)
    private static final String ORDER_WITH_EXTRA = """
            {"id":1440,"currency":"PYG","total_price":"189000.00","phone":"0986871042",
             "line_items":[
               {"id":1,"title":"Magnesium 60 caps","sku":"8364749557","quantity":1,"price":"179000.00"},
               {"id":2,"title":"Envio Prioritario y Garantia Extendida","sku":null,"quantity":1,"price":"10000.00"}]}""";

    @Test
    void extraItemIsIgnoredButItsAmountIsCollected() {
        givenProductWithSku("8364749557");

        service.receiveOrderCreated(ORDER_WITH_EXTRA, null);

        ArgumentCaptor<List<com.rtz.ordership.dto.request.OrderItemRequest>> items = ArgumentCaptor.captor();
        verify(orderService).createOrderFromShopify(any(Customer.class),
                argThat(d -> d.amountToCollect().compareTo(new BigDecimal("189000")) == 0), items.capture());
        assertThat(items.getValue()).hasSize(1);
        verify(productRepository, never()).save(any());
        verifyNoInteractions(failureService);
    }

    @Test
    void extraItemIsRecognizedIgnoringCaseAndAccents() {
        givenProductWithSku("8364749557");
        String payload = ORDER_WITH_EXTRA.replace("Envio Prioritario y Garantia Extendida",
                "ENVÍO PRIORITARIO Y GARANTÍA EXTENDIDA");

        service.receiveOrderCreated(payload, null);

        verify(orderService).createOrderFromShopify(any(Customer.class), withId("1440"),
                argThat(list -> list.size() == 1));
        verifyNoInteractions(failureService);
    }

    @Test
    void quantityOfferTotalFromShopifyIsTheAmountToCollect() {
        // 2 x Magnesium con 15% de descuento: el cliente paga 304.300, no 358.000
        givenProductWithSku("8364749557");
        String payload = """
                {"id":1478,"currency":"PYG","total_price":"304300.00","phone":"0981000111",
                 "line_items":[{"id":1,"title":"Magnesium 60 caps","sku":"8364749557","quantity":2,"price":"179000.00"}]}""";

        service.receiveOrderCreated(payload, null);

        assertThat(capturedDetails().amountToCollect()).isEqualByComparingTo("304300");
    }

    @Test
    void orderWithOnlyIgnoredItemsIsRecorded() {
        String onlyExtra = """
                {"id":1500,"currency":"PYG","total_price":"10000.00","phone":"0981000111",
                 "line_items":[{"id":2,"title":"Envio Prioritario y Garantia Extendida","quantity":1,"price":"10000.00"}]}""";

        service.receiveOrderCreated(onlyExtra, null);

        verify(failureService).recordFailure(eq("1500"), eq(onlyExtra), contains("solo extras ignorados"));
    }

    @Test
    void itemWithoutSkuIsRecordedAndTransactionRolledBack() {
        String withoutSku = PAYLOAD.replace("\"sku\":\"REM-01\",", "");

        service.receiveOrderCreated(withoutSku, null);

        verify(failureService).recordFailure(eq("555"), eq(withoutSku), contains("no tiene SKU"));
        verify(transactionManager).rollback(any());
        verify(orderService, never()).createOrderFromShopify(any(), any(), any());
    }

    @Test
    void unsupportedCurrencyIsRecorded() {
        String inPesos = PAYLOAD.replace("\"PYG\"", "\"ARS\"");

        service.receiveOrderCreated(inPesos, null);

        verify(failureService).recordFailure(eq("555"), eq(inPesos), contains("moneda no soportada: ARS"));
    }

    @Test
    void orderWithoutPhoneIsRecorded() {
        String withoutPhone = PAYLOAD.replace("\"phone\":\"+595981000999\",", "");

        service.receiveOrderCreated(withoutPhone, null);

        verify(failureService).recordFailure(eq("555"), eq(withoutPhone), contains("teléfono"));
    }

    @Test
    void unreadablePayloadIsRecordedWithoutOrderId() {
        service.receiveOrderCreated("esto no es json", null);

        verify(failureService).recordFailure(isNull(), eq("esto no es json"), contains("Payload de Shopify inválido"));
    }

    @Test
    void technicalErrorPropagatesSoShopifyRetries() {
        when(customerRepository.findByPhone(anyString())).thenThrow(new DataAccessResourceFailureException("base caída"));

        assertThatThrownBy(() -> service.receiveOrderCreated(PAYLOAD, null))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(failureService);
    }

    @Test
    void shopifyOrderNumberAndAdminLinkAreSaved() {
        givenProductWithSku("8364749557");
        String payload = RELEASIT_PAYLOAD.replace("\"id\":1440,", "\"id\":7576227119276,\"name\":\"#1487\",");

        service.receiveOrderCreated(payload, "fqyja1-t8.myshopify.com");

        ShopifyOrderDetails details = capturedDetails();
        assertThat(details.orderName()).isEqualTo("#1487");
        assertThat(details.adminUrl()).isEqualTo("https://admin.shopify.com/store/fqyja1-t8/orders/7576227119276");
    }

    @Test
    void adminLinkIsNotBuiltFromAnUnexpectedDomain() {
        givenProductWithSku("8364749557");

        service.receiveOrderCreated(RELEASIT_PAYLOAD, "evil.example.com/\"><script>");

        assertThat(capturedDetails().adminUrl()).isNull();
    }

    @Test
    void adminLinkIsNullWithoutShopDomain() {
        givenProductWithSku("8364749557");

        service.receiveOrderCreated(RELEASIT_PAYLOAD, null);

        assertThat(capturedDetails().adminUrl()).isNull();
    }

    private ShopifyOrderDetails capturedDetails() {
        ArgumentCaptor<ShopifyOrderDetails> details = ArgumentCaptor.forClass(ShopifyOrderDetails.class);
        verify(orderService).createOrderFromShopify(any(Customer.class), details.capture(), anyList());
        return details.getValue();
    }

    private static ShopifyOrderDetails withId(String shopifyOrderId) {
        return argThat(d -> shopifyOrderId.equals(d.shopifyOrderId()));
    }

    private void givenProductWithSku(String sku) {
        Product product = Product.builder().id(UUID.randomUUID()).shopifySku(sku).build();
        when(productRepository.findByShopifySku(sku)).thenReturn(Optional.of(product));
    }
}

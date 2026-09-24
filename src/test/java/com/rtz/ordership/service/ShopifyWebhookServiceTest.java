package com.rtz.ordership.service;

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
                JsonMapper.builder().build(), new TransactionTemplate(transactionManager));
    }

    @Test
    void knownSkuUsesExistingProduct() {
        givenProductWithSku("REM-01");

        service.receiveOrderCreated(PAYLOAD);

        verify(orderService).createOrderFromShopify(any(Customer.class), any(), eq("555"), isNull(), anyList());
        verify(productRepository, never()).save(any());
        verify(transactionManager).commit(any());
        verifyNoInteractions(failureService);
    }

    @Test
    void unknownSkuCreatesProductMarkedForReview() {
        service.receiveOrderCreated(PAYLOAD);

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

        verify(orderService).createOrderFromShopify(any(Customer.class), any(), eq("555"), isNull(), anyList());
        verifyNoInteractions(failureService);
    }

    @Test
    void unknownSkuIsLinkedToExistingProductWithSameNameAndNoSku() {
        Product existing = Product.builder().id(UUID.randomUUID()).name("Remera - Azul / M")
                .purchasePrice(new BigDecimal("80000")).build();
        when(productRepository.findFirstByNameIgnoreCaseAndShopifySkuIsNull("Remera - Azul / M"))
                .thenReturn(Optional.of(existing));

        service.receiveOrderCreated(PAYLOAD);

        verify(productRepository).save(existing);
        assertThat(existing.getShopifySku()).isEqualTo("REM-01");
        assertThat(existing.getNeedsReview()).isFalse();
        assertThat(existing.getPurchasePrice()).isEqualByComparingTo("80000");
        verify(orderService).createOrderFromShopify(any(Customer.class), any(), eq("555"), isNull(), anyList());
    }

    @Test
    void nameTakenByProductWithOtherSkuGetsSkuSuffix() {
        when(productRepository.existsByName("Remera - Azul / M")).thenReturn(true);

        service.receiveOrderCreated(PAYLOAD);

        ArgumentCaptor<Product> created = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(created.capture());
        assertThat(created.getValue().getName()).isEqualTo("Remera - Azul / M (REM-01)");
    }

    @Test
    void defaultVariantTitleIsNotAddedToName() {
        String payload = PAYLOAD.replace("Azul / M", "Default Title");

        service.receiveOrderCreated(payload);

        ArgumentCaptor<Product> created = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(created.capture());
        assertThat(created.getValue().getName()).isEqualTo("Remera");
    }

    @Test
    void itemWithoutSkuIsRecordedAndTransactionRolledBack() {
        String withoutSku = PAYLOAD.replace("\"sku\":\"REM-01\",", "");

        service.receiveOrderCreated(withoutSku);

        verify(failureService).recordFailure(eq("555"), eq(withoutSku), contains("no tiene SKU"));
        verify(transactionManager).rollback(any());
        verify(orderService, never()).createOrderFromShopify(any(), any(), any(), any(), any());
    }

    @Test
    void unsupportedCurrencyIsRecorded() {
        String inPesos = PAYLOAD.replace("\"PYG\"", "\"ARS\"");

        service.receiveOrderCreated(inPesos);

        verify(failureService).recordFailure(eq("555"), eq(inPesos), contains("moneda no soportada: ARS"));
    }

    @Test
    void orderWithoutPhoneIsRecorded() {
        String withoutPhone = PAYLOAD.replace("\"phone\":\"+595981000999\",", "");

        service.receiveOrderCreated(withoutPhone);

        verify(failureService).recordFailure(eq("555"), eq(withoutPhone), contains("teléfono"));
    }

    @Test
    void unreadablePayloadIsRecordedWithoutOrderId() {
        service.receiveOrderCreated("esto no es json");

        verify(failureService).recordFailure(isNull(), eq("esto no es json"), contains("Payload de Shopify inválido"));
    }

    @Test
    void technicalErrorPropagatesSoShopifyRetries() {
        when(customerRepository.findByPhone(anyString())).thenThrow(new DataAccessResourceFailureException("base caída"));

        assertThatThrownBy(() -> service.receiveOrderCreated(PAYLOAD))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(failureService);
    }

    private void givenProductWithSku(String sku) {
        Product product = Product.builder().id(UUID.randomUUID()).shopifySku(sku).build();
        when(productRepository.findByShopifySku(sku)).thenReturn(Optional.of(product));
    }
}

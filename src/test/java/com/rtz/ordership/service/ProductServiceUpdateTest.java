package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.ProductUpdateRequest;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.repository.ProductRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceUpdateTest {

    private ProductRepository productRepository;
    private ProductService productService;
    private Product product;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        productService = new ProductService(productRepository);
        product = Product.builder()
                .id(UUID.randomUUID())
                .name("Remera")
                .description("Algodón")
                .purchasePrice(BigDecimal.ZERO)
                .salePrice(new BigDecimal("150000"))
                .shopifySku("REM-1")
                .needsReview(true)
                .build();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
    }

    @Test
    void emptyTextsClearDescriptionAndSkuWhileNullKeepsThem() {
        productService.updateProduct(product.getId(), request(null, null, null));
        assertThat(product.getDescription()).isEqualTo("Algodón");
        assertThat(product.getShopifySku()).isEqualTo("REM-1");

        productService.updateProduct(product.getId(), request(null, "  ", ""));
        assertThat(product.getDescription()).isNull();
        assertThat(product.getShopifySku()).isNull();
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> productService.updateProduct(product.getId(), request("  ", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(product.getName()).isEqualTo("Remera");
    }

    @Test
    void purchasePriceMustBePositiveToMarkTheProductAsReviewed() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ProductUpdateRequest zero = new ProductUpdateRequest(null, null, BigDecimal.ZERO, null, null, null, null, null);
        assertThat(validator.validate(zero)).extracting(v -> v.getMessage())
                .containsExactly("El precio de compra debe ser mayor a 0");

        productService.updateProduct(product.getId(),
                new ProductUpdateRequest(null, null, new BigDecimal("90000"), null, null, null, null, null));
        assertThat(product.getNeedsReview()).isFalse();
    }

    private ProductUpdateRequest request(String name, String description, String shopifySku) {
        return new ProductUpdateRequest(name, description, null, null, null, null, null, shopifySku);
    }
}

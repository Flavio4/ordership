package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.ProductRequest;
import com.rtz.ordership.dto.request.ProductUpdateRequest;
import com.rtz.ordership.dto.response.ProductResponse;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.exception.DuplicateResourceException;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductResponse> getAllProducts() {
        log.info("Listando todos los productos activos");
        List<ProductResponse> products = productRepository.findByActiveTrue().stream()
                .map(ProductResponse::fromEntity)
                .toList();
        log.info("Se encontraron {} productos activos", products.size());
        return products;
    }

    public ProductResponse getProductById(UUID id) {
        log.info("Buscando producto por ID: {}", id);
        Product product = findProductOrThrow(id);
        return ProductResponse.fromEntity(product);
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        log.info("Creando producto: {}", request.name());

        if (productRepository.existsByName(request.name())) {
            log.warn("Producto duplicado: {}", request.name());
            throw new DuplicateResourceException("Ya existe un producto con el nombre: " + request.name());
        }

        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .purchasePrice(request.purchasePrice())
                .salePrice(request.salePrice())
                .unit(request.unit())
                .active(true)
                .build();

        product = productRepository.save(product);
        log.info("Producto creado - id: {}, nombre: {}, precio venta: {}",
                product.getId(), product.getName(), product.getSalePrice());
        return ProductResponse.fromEntity(product);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductUpdateRequest request) {
        log.info("Actualizando producto ID: {}", id);
        Product product = findProductOrThrow(id);

        if (request.name() != null) {
            if (!request.name().equals(product.getName()) && productRepository.existsByName(request.name())) {
                log.warn("Producto duplicado al actualizar: {}", request.name());
                throw new DuplicateResourceException("Ya existe un producto con el nombre: " + request.name());
            }
            product.setName(request.name());
        }
        if (request.description() != null)
            product.setDescription(request.description());
        if (request.purchasePrice() != null)
            product.setPurchasePrice(request.purchasePrice());
        if (request.salePrice() != null)
            product.setSalePrice(request.salePrice());
        if (request.unit() != null)
            product.setUnit(request.unit());
        if (request.active() != null)
            product.setActive(request.active());

        product = productRepository.save(product);
        log.info("Producto actualizado - id: {}, nombre: {}", product.getId(), product.getName());
        return ProductResponse.fromEntity(product);
    }

    @Transactional
    public void deactivateProduct(UUID id) {
        log.info("Desactivando producto ID: {}", id);
        Product product = findProductOrThrow(id);
        product.setActive(false);
        productRepository.save(product);
        log.info("Producto desactivado - id: {}, nombre: {}", product.getId(), product.getName());
    }

    private Product findProductOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Producto no encontrado con ID: {}", id);
                    return new ResourceNotFoundException("Producto no encontrado con ID: " + id);
                });
    }
}

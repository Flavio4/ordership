package com.rtz.ordership.security;

import com.rtz.ordership.entity.Store;
import com.rtz.ordership.repository.StoreRepository;
import com.rtz.ordership.tenant.StoreContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/** Los webhooks de Shopify no traen usuario: la tienda sale del header X-Shopify-Shop-Domain. */
@Slf4j
@Component
public class ShopifyWebhookStoreFilter extends OncePerRequestFilter {

    private final StoreRepository storeRepository;

    public ShopifyWebhookStoreFilter(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/webhooks/shopify/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String shopDomain = request.getHeader("X-Shopify-Shop-Domain");
        Optional<Store> store = StringUtils.hasText(shopDomain)
                ? storeRepository.findByShopifyShopDomainAndActiveTrue(shopDomain.trim().toLowerCase(Locale.ROOT))
                : Optional.empty();

        if (store.isEmpty()) {
            log.warn("Webhook de Shopify rechazado: tienda desconocida ({})", shopDomain);
            JsonErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "Tienda de Shopify desconocida");
            return;
        }

        StoreContext.set(store.get().getId(), null);
        try {
            filterChain.doFilter(request, response);
        } finally {
            StoreContext.clear();
        }
    }
}

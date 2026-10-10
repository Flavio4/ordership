package com.rtz.ordership.tenant;

import com.rtz.ordership.entity.enums.Role;

import java.util.Optional;
import java.util.UUID;

/**
 * Tienda con la que se trabaja en el request actual. La fijan los filtros (header X-Store-Id o dominio del webhook de
 * Shopify) antes de que se abra la sesión de Hibernate, que filtra con ella todas las entidades con @TenantId.
 */
public final class StoreContext {

    /** {@code role} es el del usuario en esa tienda; null en el webhook de Shopify. */
    public record CurrentStore(UUID storeId, Role role) {
    }

    private static final ThreadLocal<CurrentStore> CURRENT = new ThreadLocal<>();

    private StoreContext() {
    }

    public static Optional<CurrentStore> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID requireStoreId() {
        return current()
                .map(CurrentStore::storeId)
                .orElseThrow(() -> new IllegalStateException("No hay una tienda seleccionada en este request"));
    }

    public static void set(UUID storeId, Role role) {
        CURRENT.set(new CurrentStore(storeId, role));
    }

    public static void clear() {
        CURRENT.remove();
    }
}

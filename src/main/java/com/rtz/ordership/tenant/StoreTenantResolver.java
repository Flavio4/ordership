package com.rtz.ordership.tenant;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Le dice a Hibernate qué tienda usar para filtrar y completar el store_id de las entidades con @TenantId. */
@Component
public class StoreTenantResolver implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    // Sin tienda en el request: un id que no es de ninguna, así nunca se ven ni se guardan datos de todas las tiendas
    static final UUID NO_STORE = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return StoreContext.current().map(StoreContext.CurrentStore::storeId).orElse(NO_STORE);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}

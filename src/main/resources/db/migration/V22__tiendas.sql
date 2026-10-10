-- Varias tiendas en la misma base: cada dato de negocio lleva store_id y Hibernate (@TenantId) filtra por la tienda
-- del request. Un usuario puede pertenecer a varias tiendas, con un rol en cada una (store_members).
CREATE TABLE stores (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    shopify_shop_domain character varying(255),
    active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT stores_pkey PRIMARY KEY (id),
    CONSTRAINT uk_stores_shopify_shop_domain UNIQUE (shopify_shop_domain)
);

CREATE TABLE store_members (
    id uuid NOT NULL,
    store_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT store_members_pkey PRIMARY KEY (id),
    CONSTRAINT uk_store_members_store_user UNIQUE (store_id, user_id),
    CONSTRAINT store_members_role_check CHECK (role IN ('ADMIN', 'OPERATOR', 'DELIVERY')),
    CONSTRAINT fk_store_members_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_store_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_store_members_user ON store_members (user_id);

-- La tienda que ya usaba OrderShip se queda con todos los datos y usuarios existentes
INSERT INTO stores (id, name, shopify_shop_domain, active, created_at)
VALUES (gen_random_uuid(), 'Al natural Py', 'fqyja1-t8.myshopify.com', true, now());

INSERT INTO store_members (id, store_id, user_id, role, created_at)
SELECT gen_random_uuid(), (SELECT id FROM stores), u.id, u.role, now()
FROM users u;

-- El rol pasa a ser por tienda
ALTER TABLE users DROP COLUMN role;

DO $$
DECLARE
    current_store uuid := (SELECT id FROM stores);
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['zones', 'customers', 'customer_addresses', 'products', 'orders', 'order_items',
                             'order_discounts', 'delivery_assignments', 'carriers', 'shopify_webhook_failures',
                             'stock_movements'] LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN store_id uuid', t);
        EXECUTE format('UPDATE %I SET store_id = %L', t, current_store);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN store_id SET NOT NULL', t);
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (store_id) REFERENCES stores (id)',
                       t, 'fk_' || t || '_store');
        EXECUTE format('CREATE INDEX %I ON %I (store_id)', 'idx_' || t || '_store', t);
    END LOOP;
END $$;

-- Lo que era único en toda la base pasa a ser único dentro de cada tienda
ALTER TABLE customers DROP CONSTRAINT ukm3iom37efaxd5eucmxjqqcbe9;
ALTER TABLE customers ADD CONSTRAINT uk_customers_store_phone UNIQUE (store_id, phone);

ALTER TABLE products DROP CONSTRAINT ukb30wn1e2lyfyeix1lw7hu5xow;
ALTER TABLE products ADD CONSTRAINT uk_products_store_shopify_sku UNIQUE (store_id, shopify_sku);

ALTER TABLE orders DROP CONSTRAINT ukjiv6wo8wqmb71olgcj1c4kw85;
ALTER TABLE orders ADD CONSTRAINT uk_orders_store_shopify_order_id UNIQUE (store_id, shopify_order_id);

ALTER TABLE zones DROP CONSTRAINT uk9vf2c47kjchldfq92cptovfts;
ALTER TABLE zones ADD CONSTRAINT uk_zones_store_name UNIQUE (store_id, name);

ALTER TABLE shopify_webhook_failures DROP CONSTRAINT uk_shopify_webhook_failures_order;
ALTER TABLE shopify_webhook_failures
    ADD CONSTRAINT uk_shopify_webhook_failures_store_order UNIQUE (store_id, shopify_order_id);

-- Un usuario DELIVERY puede ser repartidor de varias tiendas, pero de una sola vez en cada una
ALTER TABLE carriers DROP CONSTRAINT carriers_user_id_key;
ALTER TABLE carriers ADD CONSTRAINT uk_carriers_store_user UNIQUE (store_id, user_id);

-- Los listados de pedidos por fecha ahora siempre filtran por tienda
DROP INDEX idx_orders_created_at;
CREATE INDEX idx_orders_store_created_at ON orders (store_id, created_at);

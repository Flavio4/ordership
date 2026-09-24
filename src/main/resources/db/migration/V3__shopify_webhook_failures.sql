-- Pedidos de Shopify que no se pudieron crear por datos que Shopify mandó incompletos
-- (sin teléfono, ítem sin SKU, moneda no soportada...). El webhook responde 200 igual, para que Shopify
-- no reintente en vano ni desactive el webhook, y el payload queda guardado para reconstruir el pedido a mano.

CREATE TABLE shopify_webhook_failures (
    id uuid NOT NULL,
    shopify_order_id character varying(255),
    payload text NOT NULL,
    reason text NOT NULL,
    attempts integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    last_attempt_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT shopify_webhook_failures_pkey PRIMARY KEY (id),
    -- Si Shopify reenvía el mismo pedido, se actualiza el registro existente (varios NULL están permitidos)
    CONSTRAINT uk_shopify_webhook_failures_order UNIQUE (shopify_order_id)
);

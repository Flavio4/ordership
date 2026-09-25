-- Referencias al pedido en Shopify, para encontrarlo y ver su desglose (descuentos, extras, envío):
-- el número que ve la tienda (ej. "#1488") y el link directo al pedido en Shopify Admin.
ALTER TABLE orders ADD COLUMN shopify_order_name character varying(255);
ALTER TABLE orders ADD COLUMN shopify_admin_url text;

-- Ganancia del pedido: costo de cada producto al momento de la venta y costo del delivery (opcional, a mano).
-- unit_cost null = el producto no tenía precio de compra (creado desde Shopify o en dólares): ganancia incompleta.
ALTER TABLE order_items ADD COLUMN unit_cost NUMERIC(12, 2);
ALTER TABLE orders ADD COLUMN delivery_cost NUMERIC(12, 2);

-- Pedidos anteriores: se toma el precio de compra actual
UPDATE order_items oi
SET unit_cost = p.purchase_price
FROM products p
WHERE p.id = oi.product_id
  AND p.needs_review = false
  AND p.currency = 'PYG'
  AND p.purchase_price > 0;

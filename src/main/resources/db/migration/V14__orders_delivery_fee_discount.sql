-- Pedidos manuales: costo de envío y descuento sobre la suma de los productos.
-- A cobrar = total de productos + envío - descuento. En los de Shopify quedan en 0 (el total viene de Shopify).
ALTER TABLE orders ADD COLUMN delivery_fee NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN discount NUMERIC(12, 2) NOT NULL DEFAULT 0;

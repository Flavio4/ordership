-- Número de pedido propio de OrderShip (se muestra como P-1024), independiente del número de Shopify.
-- Los pedidos existentes se numeran por fecha de creación.
CREATE SEQUENCE orders_order_number_seq START WITH 1;

ALTER TABLE orders ADD COLUMN order_number bigint;

UPDATE orders o SET order_number = numbered.n
FROM (SELECT id, row_number() OVER (ORDER BY created_at, id) AS n FROM orders) numbered
WHERE o.id = numbered.id;

SELECT setval('orders_order_number_seq', COALESCE((SELECT MAX(order_number) FROM orders), 0) + 1, false);

ALTER TABLE orders ALTER COLUMN order_number SET DEFAULT nextval('orders_order_number_seq');
ALTER TABLE orders ALTER COLUMN order_number SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT orders_order_number_key UNIQUE (order_number);
ALTER SEQUENCE orders_order_number_seq OWNED BY orders.order_number;

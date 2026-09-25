-- Monto que el cliente debe pagar (lo que cobra el repartidor en pago contra entrega).
-- En pedidos de Shopify es el total de Shopify (incluye ofertas por cantidad y extras como "Envío prioritario"),
-- que puede diferir de total_amount, calculado con los precios del catálogo de OrderShip.
-- En pedidos manuales es igual a total_amount.
ALTER TABLE orders ADD COLUMN amount_to_collect numeric(12, 2);

UPDATE orders SET amount_to_collect = total_amount;

ALTER TABLE orders ALTER COLUMN amount_to_collect SET NOT NULL;

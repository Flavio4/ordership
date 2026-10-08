-- Filtro de pedidos por zona: de la zona a sus direcciones y de ahí a sus pedidos.
-- La FK no crea índice en Postgres: estos también evitan recorrer la tabla entera al borrar una dirección (para ver
-- si algún pedido la usa) o una zona.
CREATE INDEX IF NOT EXISTS idx_orders_customer_address ON orders (customer_address_id);
CREATE INDEX IF NOT EXISTS idx_customer_addresses_zone ON customer_addresses (zone_id);

-- Para que listas, filtros por fecha, dashboard y resumen no recorran todos los pedidos cuando haya muchos.
-- created_at: lista de pedidos (ordenada por fecha), filtro from/to, dashboard y resumen por período.
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders (created_at);

-- order_id: los ítems de cada pedido (detalle, ganancia, resumen). La FK no crea índice en Postgres.
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items (order_id);

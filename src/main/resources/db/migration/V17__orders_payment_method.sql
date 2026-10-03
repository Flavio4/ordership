-- Cómo pagó el cliente (efectivo o transferencia). Null mientras el pedido está sin pagar
-- y en los pedidos marcados como pagados antes de que existiera.
ALTER TABLE orders
    ADD COLUMN payment_method character varying(255),
    ADD CONSTRAINT orders_payment_method_check CHECK (payment_method IN ('CASH', 'TRANSFER'));

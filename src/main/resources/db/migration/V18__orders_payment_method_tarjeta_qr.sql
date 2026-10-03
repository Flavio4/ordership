-- Formas de pago: se suman tarjeta y QR.
ALTER TABLE orders
    DROP CONSTRAINT orders_payment_method_check,
    ADD CONSTRAINT orders_payment_method_check CHECK (payment_method IN ('CASH', 'TRANSFER', 'CARD', 'QR'));

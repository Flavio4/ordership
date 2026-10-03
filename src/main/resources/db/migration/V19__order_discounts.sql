-- Un pedido puede tener varios descuentos, cada uno con su motivo opcional ("Cliente frecuente", "Producto golpeado").
-- orders.discount queda como la suma de las líneas: ganancia, a cobrar y dashboard siguen leyendo ese campo.
CREATE TABLE order_discounts (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    label character varying(255),
    amount numeric(12, 2) NOT NULL,
    position integer NOT NULL,
    CONSTRAINT order_discounts_pkey PRIMARY KEY (id),
    CONSTRAINT order_discounts_amount_check CHECK (amount > 0),
    CONSTRAINT fk_order_discounts_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_order_discounts_order ON order_discounts (order_id);

-- Los descuentos que ya existían pasan a ser una línea sin motivo
INSERT INTO order_discounts (id, order_id, label, amount, position)
SELECT gen_random_uuid(), o.id, NULL, o.discount, 0
FROM orders o
WHERE o.discount > 0;

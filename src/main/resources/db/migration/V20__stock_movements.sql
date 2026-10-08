-- Historial de stock: cada cambio queda con su cantidad (+/-), el stock que quedó, el motivo y quién lo hizo.
-- Lo cargan las ventas, ediciones y cancelaciones de pedidos, el alta del producto y los ajustes a mano.
-- Empieza vacío: los cambios anteriores a esta versión no se guardaban.
CREATE TABLE stock_movements (
    id uuid NOT NULL,
    product_id uuid NOT NULL,
    type character varying(30) NOT NULL,
    quantity integer NOT NULL,
    stock_after integer NOT NULL,
    reason character varying(255),
    order_id uuid,
    user_id uuid,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT stock_movements_pkey PRIMARY KEY (id),
    CONSTRAINT stock_movements_quantity_check CHECK (quantity <> 0),
    CONSTRAINT stock_movements_type_check
        CHECK (type IN ('INITIAL', 'SALE', 'ORDER_EDIT', 'CANCELLATION', 'ADJUSTMENT')),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_movements_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL,
    CONSTRAINT fk_stock_movements_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_stock_movements_product ON stock_movements (product_id, created_at DESC);

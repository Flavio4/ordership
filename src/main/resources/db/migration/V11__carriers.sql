-- Repartidores: los propios (ej. un familiar que reparte, sin usuario en la app) y los couriers tercerizados.
-- user_id vincula opcionalmente un repartidor propio con un usuario DELIVERY que use la app.
CREATE TABLE carriers (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    phone character varying(255),
    user_id uuid,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT carriers_pkey PRIMARY KEY (id),
    CONSTRAINT carriers_type_check CHECK (type IN ('OWN', 'COURIER')),
    CONSTRAINT carriers_user_id_key UNIQUE (user_id),
    CONSTRAINT fk_carriers_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Los usuarios DELIVERY que ya tenían entregas pasan a ser repartidores propios vinculados a su usuario
INSERT INTO carriers (id, name, type, phone, user_id, active, created_at)
SELECT gen_random_uuid(), u.full_name, 'OWN', u.phone, u.id, u.active, now()
FROM users u
WHERE u.id IN (SELECT DISTINCT delivery_user_id FROM delivery_assignments);

ALTER TABLE delivery_assignments ADD COLUMN carrier_id uuid;
UPDATE delivery_assignments d SET carrier_id = c.id FROM carriers c WHERE c.user_id = d.delivery_user_id;
ALTER TABLE delivery_assignments ALTER COLUMN carrier_id SET NOT NULL;
ALTER TABLE delivery_assignments
    ADD CONSTRAINT fk_delivery_assignments_carrier FOREIGN KEY (carrier_id) REFERENCES carriers (id);
ALTER TABLE delivery_assignments DROP COLUMN delivery_user_id;

-- Los envíos por courier (ej. al interior) no necesitan una zona de reparto
ALTER TABLE delivery_assignments ALTER COLUMN zone_id DROP NOT NULL;

ALTER TABLE delivery_assignments ADD COLUMN failure_reason text;

-- Un pedido puede tener varios intentos de entrega, pero solo uno en curso a la vez
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
    WHERE con.conrelid = 'delivery_assignments'::regclass
      AND con.contype = 'u'
      AND att.attname = 'order_id'
      AND array_length(con.conkey, 1) = 1;
    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE delivery_assignments DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

CREATE INDEX idx_delivery_assignments_order ON delivery_assignments (order_id);
CREATE UNIQUE INDEX uq_delivery_assignments_active_order
    ON delivery_assignments (order_id) WHERE status IN ('ASSIGNED', 'IN_TRANSIT');

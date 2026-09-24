-- Estructura inicial: refleja el schema que existía al adoptar Flyway (generado por ddl-auto=update).
-- Las bases ya existentes se marcan como V1 sin ejecutar este script (baseline).

CREATE TABLE users (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(255) NOT NULL,
    full_name character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    phone character varying(255),
    role character varying(255) NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email),
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'OPERATOR', 'DELIVERY'))
);

CREATE TABLE zones (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    CONSTRAINT zones_pkey PRIMARY KEY (id),
    CONSTRAINT uk9vf2c47kjchldfq92cptovfts UNIQUE (name)
);

CREATE TABLE customers (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(255),
    full_name character varying(255) NOT NULL,
    phone character varying(255) NOT NULL,
    notes text,
    CONSTRAINT customers_pkey PRIMARY KEY (id),
    CONSTRAINT ukm3iom37efaxd5eucmxjqqcbe9 UNIQUE (phone)
);

CREATE TABLE customer_addresses (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(255),
    latitude double precision,
    longitude double precision,
    customer_id uuid NOT NULL,
    zone_id uuid NOT NULL,
    city character varying(255),
    is_default boolean NOT NULL,
    label character varying(255),
    street character varying(255),
    map_url text NOT NULL,
    CONSTRAINT customer_addresses_pkey PRIMARY KEY (id),
    CONSTRAINT fkrvr6wl9gll7u98cda18smugp4 FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fkh4977e8p2d9hbae6pircskbpq FOREIGN KEY (zone_id) REFERENCES zones (id)
);

CREATE TABLE products (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    purchase_price numeric(12, 2) NOT NULL,
    sale_price numeric(12, 2) NOT NULL,
    unit character varying(255) NOT NULL,
    currency character varying(255) NOT NULL,
    stock integer NOT NULL,
    created_by_user_id uuid,
    shopify_sku character varying(255),
    CONSTRAINT products_pkey PRIMARY KEY (id),
    CONSTRAINT ukb30wn1e2lyfyeix1lw7hu5xow UNIQUE (shopify_sku),
    CONSTRAINT products_currency_check CHECK (currency IN ('PYG', 'USD')),
    CONSTRAINT fkan9lghvq8mcmqx9uejsxc8nbj FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);

CREATE TABLE orders (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    delivery_date date,
    notes text,
    status character varying(255) NOT NULL,
    total_amount numeric(12, 2) NOT NULL,
    updated_at timestamp(6) with time zone,
    created_by_user_id uuid,
    customer_id uuid NOT NULL,
    customer_address_id uuid,
    payment_status character varying(255) NOT NULL,
    confirmed_at timestamp(6) with time zone,
    courier_name character varying(255),
    shipping_method character varying(255) NOT NULL,
    source character varying(255) NOT NULL,
    tracking_code character varying(255),
    shipping_address_raw text,
    shopify_order_id character varying(255),
    CONSTRAINT orders_pkey PRIMARY KEY (id),
    CONSTRAINT ukjiv6wo8wqmb71olgcj1c4kw85 UNIQUE (shopify_order_id),
    CONSTRAINT orders_status_check CHECK (status IN ('PENDING', 'CONFIRMED', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT orders_payment_status_check CHECK (payment_status IN ('UNPAID', 'PAID', 'PARTIAL')),
    CONSTRAINT orders_shipping_method_check CHECK (shipping_method IN ('OWN_DELIVERY', 'COURIER')),
    CONSTRAINT orders_source_check CHECK (source IN ('SHOPIFY', 'MANUAL')),
    CONSTRAINT fkdmgt52mybm8qr38knw7r12eml FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fkpxtb8awmi0dk6smoh2vp1litg FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fkpx4ixbgxbou9py6suv5a4wku6 FOREIGN KEY (customer_address_id) REFERENCES customer_addresses (id)
);

CREATE TABLE order_items (
    id uuid NOT NULL,
    quantity integer NOT NULL,
    subtotal numeric(12, 2) NOT NULL,
    unit_price numeric(12, 2) NOT NULL,
    order_id uuid NOT NULL,
    product_id uuid NOT NULL,
    CONSTRAINT order_items_pkey PRIMARY KEY (id),
    CONSTRAINT fkbioxgbv59vetrxe0ejfubep1w FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fkocimc7dtr037rh4ls4l95nlfi FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE delivery_assignments (
    id uuid NOT NULL,
    assigned_at timestamp(6) with time zone NOT NULL,
    completed_at timestamp(6) with time zone,
    notes text,
    status character varying(255) NOT NULL,
    delivery_user_id uuid NOT NULL,
    order_id uuid NOT NULL,
    zone_id uuid NOT NULL,
    CONSTRAINT delivery_assignments_pkey PRIMARY KEY (id),
    CONSTRAINT uk1wlwhcjxiawu7puc2ly5440vj UNIQUE (order_id),
    CONSTRAINT delivery_assignments_status_check CHECK (status IN ('ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'FAILED')),
    CONSTRAINT fkjg9fbpbp5pvo2j3p4onijeccc FOREIGN KEY (delivery_user_id) REFERENCES users (id),
    CONSTRAINT fkal6lp5gq27djtgpdsn2907uq5 FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fktc3ggspo91mfxm4h0ddrhxt6j FOREIGN KEY (zone_id) REFERENCES zones (id)
);

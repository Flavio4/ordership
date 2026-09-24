-- Productos creados automáticamente desde un pedido de Shopify (SKU desconocido).
-- Quedan marcados hasta que alguien complete los datos que Shopify no manda (precio de compra, etc.).
ALTER TABLE products ADD COLUMN needs_review boolean NOT NULL DEFAULT false;

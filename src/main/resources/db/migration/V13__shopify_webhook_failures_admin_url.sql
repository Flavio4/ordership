-- Link al pedido en Shopify Admin, para abrirlo desde la app. Las fallas anteriores quedan sin link.
ALTER TABLE shopify_webhook_failures ADD COLUMN admin_url VARCHAR(500);

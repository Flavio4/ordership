ALTER TABLE shopify_webhook_failures ADD COLUMN resolved_at timestamp(6) with time zone;
ALTER TABLE shopify_webhook_failures ADD COLUMN resolved_by_user_id uuid;
ALTER TABLE shopify_webhook_failures ADD COLUMN resolution_note text;
ALTER TABLE shopify_webhook_failures
    ADD CONSTRAINT fk_shopify_webhook_failures_resolved_by FOREIGN KEY (resolved_by_user_id) REFERENCES users (id);

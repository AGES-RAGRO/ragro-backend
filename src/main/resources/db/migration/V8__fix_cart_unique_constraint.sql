DROP INDEX IF EXISTS carts_customer_id_idx;
CREATE UNIQUE INDEX IF NOT EXISTS carts_customer_id_active_idx ON carts (customer_id) WHERE active = true;

DROP INDEX IF EXISTS cart_items_cart_id_product_id_idx;
CREATE UNIQUE INDEX IF NOT EXISTS cart_items_cart_id_product_id_active_idx ON cart_items (cart_id, product_id) WHERE active = true;

DROP INDEX IF EXISTS farmer_availability_farmer_id_weekday_idx;
CREATE UNIQUE INDEX IF NOT EXISTS farmer_availability_farmer_id_weekday_active_idx ON farmer_availability (farmer_id, weekday) WHERE active = true;

DROP INDEX IF EXISTS payment_methods_farmer_id_type_pix_key_idx;
CREATE UNIQUE INDEX IF NOT EXISTS payment_methods_farmer_id_type_pix_key_active_idx ON payment_methods (farmer_id, type, pix_key) WHERE active = true;

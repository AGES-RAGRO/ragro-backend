CREATE TABLE "users" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "name" varchar(120) NOT NULL,
  "email" varchar(254) UNIQUE NOT NULL,
  "phone" varchar(20),
  "type" varchar(20) NOT NULL,
  "active" boolean NOT NULL DEFAULT true,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now(),
  "auth_sub" text NOT NULL UNIQUE
);

CREATE TABLE "addresses" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "user_id" uuid NOT NULL,
  "street" varchar(200) NOT NULL,
  "number" varchar(10) NOT NULL,
  "complement" varchar(100),
  "neighborhood" varchar(100),
  "city" varchar(100) NOT NULL,
  "state" varchar(2) NOT NULL,
  "zip_code" varchar(8) NOT NULL,
  "latitude" decimal(10,7),
  "longitude" decimal(10,7),
  "is_primary" boolean NOT NULL DEFAULT true,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "farmers" (
  "id" uuid PRIMARY KEY,
  "fiscal_number" varchar(14) NOT NULL,
  "fiscal_number_type" varchar(5) NOT NULL,
  "farm_name" varchar(150) NOT NULL,
  "description" text,
  "avatar_s3" text,
  "display_photo_s3" text,
  "total_reviews" integer NOT NULL DEFAULT 0,
  "average_rating" decimal(3,2) NOT NULL DEFAULT 0,
  "total_orders" integer NOT NULL DEFAULT 0,
  "total_sales_amount" decimal(14,2) NOT NULL DEFAULT 0,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "farmer_availability" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "farmer_id" uuid NOT NULL,
  "weekday" smallint NOT NULL,
  "opens_at" time NOT NULL,
  "closes_at" time NOT NULL,
  "active" boolean NOT NULL DEFAULT true
);

CREATE TABLE "product_photos" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "product_id" uuid NOT NULL,
  "url" text NOT NULL,
  "display_order" smallint NOT NULL DEFAULT 0,
  "created_at" timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "customers" (
  "id" uuid PRIMARY KEY,
  "fiscal_number" varchar(11) UNIQUE NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "product_categories" (
  "id" serial PRIMARY KEY,
  "name" varchar(80) UNIQUE NOT NULL,
  "description" text
);

CREATE TABLE "products" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "farmer_id" uuid NOT NULL,
  "name" varchar(150) NOT NULL,
  "description" text,
  "price" decimal(10,2) NOT NULL,
  "unity_type" varchar(20) NOT NULL DEFAULT 'unit',
  "stock_quantity" decimal(12,3) NOT NULL DEFAULT 0,
  "image_s3" text,
  "active" boolean NOT NULL DEFAULT true,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "stock_movements" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "product_id" uuid NOT NULL,
  "type" varchar(10) NOT NULL,
  "reason" varchar(20) NOT NULL,
  "quantity" decimal(12,3) NOT NULL,
  "notes" text,
  "created_at" timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "carts" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "customer_id" uuid NOT NULL,
  "farmer_id" uuid NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now(),
  "active" boolean NOT NULL DEFAULT true
);

CREATE TABLE "cart_items" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "cart_id" uuid NOT NULL,
  "product_id" uuid NOT NULL,
  "quantity" decimal(12,3) NOT NULL,
  "active" boolean NOT NULL DEFAULT true
);

CREATE TABLE "orders" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "customer_id" uuid NOT NULL,
  "farmer_id" uuid NOT NULL,
  "delivery_address_id" uuid NOT NULL,
  "delivery_address_snapshot" jsonb NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'pending',
  "payment_method_id" uuid NOT NULL,
  "payment_status" varchar(20) NOT NULL DEFAULT 'pending',
  "scheduled_for" timestamptz,
  "delivered_at" timestamptz,
  "notes" text,
  "cancellation_reason" text,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "order_items" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "order_id" uuid NOT NULL,
  "product_id" uuid NOT NULL,
  "product_name_snapshot" varchar(150) NOT NULL,
  "unit_price_snapshot" decimal(10,2) NOT NULL,
  "unity_type_snapshot" varchar(20) NOT NULL,
  "quantity" decimal(12,3) NOT NULL,
  "subtotal" decimal(12,2) NOT NULL
);

CREATE TABLE "review" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "order_id" uuid UNIQUE NOT NULL,
  "farmer_id" uuid NOT NULL,
  "customer_id" uuid NOT NULL,
  "rating" smallint NOT NULL,
  "comment" text,
  "created_at" timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "delivery_routes" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "farmer_id" uuid NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'planned',
  "planned_date" date NOT NULL,
  "started_at" timestamptz,
  "completed_at" timestamptz,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "delivery_route_stops" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "route_id" uuid NOT NULL,
  "order_id" uuid NOT NULL,
  "stop_order" smallint NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'pending',
  "delivered_at" timestamptz,
  "notes" text
);

CREATE TABLE "visual_route_information" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "delivery_route_id" uuid NOT NULL,
  "encoded_polyline" text,
  "total_distance_meters" integer,
  "total_duration" text,
  "optimized_stop_order" smallint[],
  "travel_mode" varchar(20),
  "origin_latitude" decimal(10,7),
  "origin_longitude" decimal(10,7)
);

CREATE TABLE "product_category_assignments" (
  "product_id" uuid NOT NULL,
  "category_id" integer NOT NULL,
  PRIMARY KEY ("product_id", "category_id")
);

CREATE TABLE "order_status_history" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "order_id" uuid NOT NULL,
  "status" varchar(20) NOT NULL,
  "changed_at" timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "favorites" (
  "customer_id" uuid NOT NULL,
  "farmer_id" uuid NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY ("customer_id", "farmer_id")
);

CREATE TABLE "payment_methods" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "farmer_id" uuid NOT NULL,
  "type" varchar(20) NOT NULL,
  "pix_key_type" varchar(20),
  "pix_key" varchar(100),
  "bank_code" char(3),
  "bank_name" varchar(100),
  "agency" varchar(10),
  "account_number" varchar(20),
  "account_type" varchar(20),
  "holder_name" varchar(120),
  "fiscal_number" varchar(14),
  "active" boolean NOT NULL DEFAULT true,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ON "farmer_availability" ("farmer_id", "weekday");

CREATE UNIQUE INDEX ON "carts" ("customer_id");

CREATE UNIQUE INDEX ON "cart_items" ("cart_id", "product_id");

CREATE UNIQUE INDEX ON "order_items" ("order_id", "product_id");

CREATE UNIQUE INDEX ON "delivery_route_stops" ("route_id", "stop_order");

CREATE UNIQUE INDEX ON "delivery_route_stops" ("order_id");

CREATE UNIQUE INDEX ON "payment_methods" ("farmer_id", "type", "pix_key");

COMMENT ON COLUMN "users"."type" IS 'farmer | customer | admin';

COMMENT ON COLUMN "farmers"."fiscal_number_type" IS 'cpf | cnpj';

COMMENT ON COLUMN "farmer_availability"."weekday" IS '0=sunday | 1=monday | 2=tuesday | 3=wednesday | 4= thursday | 5=friday | 6=saturday';

COMMENT ON COLUMN "products"."unity_type" IS 'kg | g | unit | box | liter | ml | dozen';

COMMENT ON COLUMN "stock_movements"."type" IS 'entry | exit';

COMMENT ON COLUMN "stock_movements"."reason" IS 'sale | loss | disposal | manual_entry';

COMMENT ON COLUMN "orders"."status" IS 'pending | confirmed | delivering | delivered | cancelled';

COMMENT ON COLUMN "orders"."payment_status" IS 'pending | paid | refunded';

COMMENT ON COLUMN "order_items"."subtotal" IS 'quantity * unit_price';

COMMENT ON COLUMN "review"."rating" IS '1 to 5';

COMMENT ON COLUMN "delivery_routes"."status" IS 'planned | in_progress | completed | cancelled';

COMMENT ON COLUMN "delivery_route_stops"."status" IS 'pending | arrived | delivered | failed';

COMMENT ON COLUMN "payment_methods"."type" IS 'pix | bank_account';

COMMENT ON COLUMN "payment_methods"."pix_key_type" IS 'cpf | cnpj | email | phone | random';

COMMENT ON COLUMN "payment_methods"."account_type" IS 'checking | savings';

ALTER TABLE "addresses" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "farmers" ADD FOREIGN KEY ("id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "farmer_availability" ADD FOREIGN KEY ("farmer_id") REFERENCES "farmers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "product_photos" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "customers" ADD FOREIGN KEY ("id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "products" ADD FOREIGN KEY ("farmer_id") REFERENCES "farmers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "stock_movements" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "carts" ADD FOREIGN KEY ("customer_id") REFERENCES "customers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "carts" ADD FOREIGN KEY ("farmer_id") REFERENCES "farmers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "cart_items" ADD FOREIGN KEY ("cart_id") REFERENCES "carts" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "cart_items" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "orders" ADD FOREIGN KEY ("customer_id") REFERENCES "customers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "orders" ADD FOREIGN KEY ("farmer_id") REFERENCES "farmers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "orders" ADD FOREIGN KEY ("delivery_address_id") REFERENCES "addresses" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "orders" ADD FOREIGN KEY ("payment_method_id") REFERENCES "payment_methods" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "order_items" ADD FOREIGN KEY ("order_id") REFERENCES "orders" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "order_items" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "review" ADD FOREIGN KEY ("order_id") REFERENCES "orders" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "review" ADD FOREIGN KEY ("farmer_id") REFERENCES "farmers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "review" ADD FOREIGN KEY ("customer_id") REFERENCES "customers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "delivery_routes" ADD FOREIGN KEY ("farmer_id") REFERENCES "farmers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "delivery_route_stops" ADD FOREIGN KEY ("route_id") REFERENCES "delivery_routes" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "delivery_route_stops" ADD FOREIGN KEY ("order_id") REFERENCES "orders" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "visual_route_information" ADD FOREIGN KEY ("delivery_route_id") REFERENCES "delivery_routes" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "product_category_assignments" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "product_category_assignments" ADD FOREIGN KEY ("category_id") REFERENCES "product_categories" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "order_status_history" ADD FOREIGN KEY ("order_id") REFERENCES "orders" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "favorites" ADD FOREIGN KEY ("customer_id") REFERENCES "customers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "favorites" ADD FOREIGN KEY ("farmer_id") REFERENCES "farmers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "payment_methods" ADD FOREIGN KEY ("farmer_id") REFERENCES "farmers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

-- ============================================================
-- TRIGGERS: desativação em cascata para carrinhos
-- ============================================================

-- Produto desativado → desativa cart_items desse produto
-- Se o carrinho ficar sem itens ativos, desativa o carrinho também
CREATE OR REPLACE FUNCTION fn_deactivate_cart_items_on_product()
RETURNS trigger AS $$
BEGIN
  IF OLD.active = true AND NEW.active = false THEN
    UPDATE cart_items
    SET active = false
    WHERE product_id = NEW.id;

    -- desativa carrinhos que ficaram sem nenhum item ativo
    UPDATE carts
    SET active = false
    WHERE id IN (
      SELECT c.id
      FROM carts c
      WHERE c.active = true
        AND NOT EXISTS (
          SELECT 1 FROM cart_items ci
          WHERE ci.cart_id = c.id AND ci.active = true
        )
    );
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_product_deactivated
AFTER UPDATE OF active ON products
FOR EACH ROW
EXECUTE FUNCTION fn_deactivate_cart_items_on_product();

-- Produtor desativado → desativa carrinhos e itens dele
CREATE OR REPLACE FUNCTION fn_deactivate_carts_on_farmer()
RETURNS trigger AS $$
BEGIN
  IF OLD.active = true AND NEW.active = false THEN
    UPDATE cart_items
    SET active = false
    WHERE cart_id IN (
      SELECT id FROM carts WHERE farmer_id = NEW.id
    );

    UPDATE carts
    SET active = false
    WHERE farmer_id = NEW.id;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_farmer_deactivated
AFTER UPDATE OF active ON users
FOR EACH ROW
WHEN (OLD.type = 'farmer')
EXECUTE FUNCTION fn_deactivate_carts_on_farmer();
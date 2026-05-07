
ALTER TABLE "stock_movements"
ADD CONSTRAINT check_stock_movement_type
CHECK (type IN ('ENTRY', 'EXIT'));

ALTER TABLE "stock_movements"
ADD CONSTRAINT check_stock_movement_reason
CHECK (reason IN ('SALE', 'LOSS', 'DISPOSAL', 'MANUAL_ENTRY'));

ALTER TABLE "stock_movements"
ALTER COLUMN type SET NOT NULL;

ALTER TABLE "stock_movements"
ALTER COLUMN reason SET NOT NULL;

ALTER TABLE "stock_movements"
ALTER COLUMN quantity SET NOT NULL;

ALTER TABLE "stock_movements"
ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE "stock_movements"
ADD COLUMN IF NOT EXISTS "updated_at" timestamptz NOT NULL DEFAULT now();

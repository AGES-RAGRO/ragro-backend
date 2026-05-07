ALTER TABLE "stock_movements"
DROP CONSTRAINT IF EXISTS check_stock_movement_reason;

ALTER TABLE "stock_movements"
ADD CONSTRAINT check_stock_movement_reason
CHECK (reason IN ('SALE', 'LOSS', 'DISPOSAL', 'MANUAL_ENTRY', 'CANCELED_SALE'));

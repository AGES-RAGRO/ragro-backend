-- Add cancellation_details column to orders so cancellations can carry both a
-- reason code (free text or enum-like keyword such as REFUSED_BY_FARMER) and a
-- longer details/justification text supplied by the user.
ALTER TABLE "orders" ADD COLUMN "cancellation_details" text DEFAULT NULL;

COMMENT ON COLUMN "orders"."cancellation_reason" IS 'Reason or category for the cancellation (e.g. CUSTOMER_GIVE_UP, REFUSED_BY_FARMER, free text).';
COMMENT ON COLUMN "orders"."cancellation_details" IS 'Longer justification/details optionally provided by the actor that cancelled the order.';

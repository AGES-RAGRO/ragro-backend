-- Adds seen_by_farmer flag to orders so Hibernate schema validation passes
ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS seen_by_farmer boolean NOT NULL DEFAULT false;

-- Lock otimista em products: registerSale/registerCancelledSale fazem read-modify-write em
-- stock_quantity; sem versão, duas confirmações concorrentes do mesmo produto perdem update
-- silenciosamente (auditoria Fase 0, achado A4).
ALTER TABLE products
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- As listagens de pedidos filtram por customer_id/farmer_id e o Postgres não indexa FKs
-- automaticamente (as demais listagens do sistema já têm índice — pedidos era a exceção).
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_farmer_id ON orders (farmer_id);

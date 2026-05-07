-- =============================================================================
-- Seed: test products for Cart functionality testing
-- =============================================================================

-- Product 1: From Farmer 1 (Sítio Boa Vista)
INSERT INTO products (id, farmer_id, name, description, price, unity_type, stock_quantity, active)
VALUES (
  'b0000000-0000-0000-0000-000000000001',
  'a0000000-0000-0000-0000-000000000003',
  'Morango Orgânico',
  'Morangos fresquinhos colhidos no dia. Sem agrotóxicos.',
  15.50,
  'box',
  50.000,
  true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO product_category_assignments (product_id, category_id)
VALUES ('b0000000-0000-0000-0000-000000000001', 1) -- Frutas
ON CONFLICT DO NOTHING;

-- Product 2: From Farmer 1 (Sítio Boa Vista)
INSERT INTO products (id, farmer_id, name, description, price, unity_type, stock_quantity, active)
VALUES (
  'b0000000-0000-0000-0000-000000000002',
  'a0000000-0000-0000-0000-000000000003',
  'Alface Crespa',
  'Alface hidropônica bem crocante.',
  4.50,
  'unit',
  100.000,
  true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO product_category_assignments (product_id, category_id)
VALUES ('b0000000-0000-0000-0000-000000000002', 2) -- Verduras
ON CONFLICT DO NOTHING;

-- Product 3: From Farmer 2 (Gustavo)
INSERT INTO products (id, farmer_id, name, description, price, unity_type, stock_quantity, active)
VALUES (
  'b0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004',
  'Tomate Italiano',
  'Tomates maduros ideais para molho.',
  8.90,
  'kg',
  30.000,
  true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO product_category_assignments (product_id, category_id)
VALUES ('b0000000-0000-0000-0000-000000000003', 3) -- Legumes
ON CONFLICT DO NOTHING;

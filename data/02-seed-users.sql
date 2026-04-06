-- =============================================================================
-- Seed: test users synchronized with Keycloak (ragro-realm.json)
--
-- These UUIDs in auth_sub MUST match the "id" field of each user in
-- keycloak/ragro-realm.json so that JWT sub claims resolve correctly.
--
-- Credentials (managed by Keycloak):
--   admin@ragro.com.br    / Admin@123   (group: ADMIN)
--   customer@ragro.com.br / Test@123    (group: CUSTOMER)
--   farmer@ragro.com.br   / Test@123    (group: FARMER)
-- =============================================================================

-- Admin user (no extra table needed)
INSERT INTO users (id, name, email, phone, type, active, auth_sub)
VALUES (
  'a0000000-0000-0000-0000-000000000001',
  'Admin RAGRO',
  'admin@ragro.com.br',
  '(51) 99999-0000',
  'ADMIN',
  true,
  '10000000-0000-0000-0000-000000000001'
) ON CONFLICT (email) DO NOTHING;

-- Customer user
INSERT INTO users (id, name, email, phone, type, active, auth_sub)
VALUES (
  'a0000000-0000-0000-0000-000000000002',
  'Customer Test',
  'customer@ragro.com.br',
  '(51) 99999-0001',
  'CUSTOMER',
  true,
  '10000000-0000-0000-0000-000000000002'
) ON CONFLICT (email) DO NOTHING;

INSERT INTO customers (id, fiscal_number)
VALUES (
  'a0000000-0000-0000-0000-000000000002',
  '00000000000'
) ON CONFLICT (id) DO NOTHING;

-- Farmer user
INSERT INTO users (id, name, email, phone, type, active, auth_sub)
VALUES (
  'a0000000-0000-0000-0000-000000000003',
  'Farmer Test',
  'farmer@ragro.com.br',
  '(51) 99999-0002',
  'FARMER',
  true,
  '10000000-0000-0000-0000-000000000003'
) ON CONFLICT (email) DO NOTHING;

INSERT INTO farmers (id, fiscal_number, fiscal_number_type, farm_name, description)
VALUES (
  'a0000000-0000-0000-0000-000000000003',
  '00000000000000',
  'cnpj',
  'Sítio Boa Vista',
  'Produção familiar de hortaliças orgânicas'
) ON CONFLICT (id) DO NOTHING;

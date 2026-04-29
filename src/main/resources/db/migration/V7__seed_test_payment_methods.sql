-- =============================================================================
-- Seed: test payment methods for farmers
-- =============================================================================

INSERT INTO payment_methods (id, farmer_id, type, pix_key_type, pix_key, active, created_at, updated_at)
VALUES (
  'c0000000-0000-0000-0000-000000000001',
  'a0000000-0000-0000-0000-000000000003',
  'PIX',
  'CPF',
  '12345678909',
  true,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

INSERT INTO payment_methods (id, farmer_id, type, pix_key_type, pix_key, active, created_at, updated_at)
VALUES (
  'c0000000-0000-0000-0000-000000000002',
  'a0000000-0000-0000-0000-000000000004',
  'PIX',
  'EMAIL',
  'farmer2@ragro.com.br',
  true,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

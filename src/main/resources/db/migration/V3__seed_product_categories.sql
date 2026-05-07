-- =============================================================================
-- Seed: product categories
-- =============================================================================

INSERT INTO product_categories (name, description) VALUES
  ('Frutas', 'Frutas frescas e secas'),
  ('Verduras', 'Folhosas e ervas frescas'),
  ('Legumes', 'Legumes e tubérculos'),
  ('Laticínios', 'Leite, queijos e derivados'),
  ('Ovos', 'Ovos caipiras e de granja'),
  ('Grãos e Cereais', 'Feijão, milho, arroz e similares'),
  ('Carnes', 'Carnes frescas e embutidos artesanais'),
  ('Mel e Derivados', 'Mel, própolis e cera de abelha'),
  ('Processados Artesanais', 'Conservas, geleias, farinhas e outros processados'),
  ('Plantas e Mudas', 'Mudas de hortaliças, ervas e ornamentais')
ON CONFLICT (name) DO NOTHING;

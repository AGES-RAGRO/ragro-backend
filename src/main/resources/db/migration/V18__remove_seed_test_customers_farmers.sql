-- =============================================================================
-- Remove os usuários de teste CONSUMIDOR e PRODUTOR da seed, mantendo só o ADMIN.
--
-- A V2 semeia 4 usuários: admin (a0…001), customer (a0…002), farmer (a0…003) e
-- farmer "Gustavo" (a0…004). Consumidor e produtor passam a ser criados pelo
-- fluxo real de cadastro do app — não pré-semeados. Esta migration remove os
-- usuários de teste 002/003/004 e TODA a cadeia de dados que os referencia.
--
-- Nenhuma FK do schema tem ON DELETE CASCADE (todas RESTRICT), então os DELETEs
-- vão de baixo para cima (tabelas filhas antes das pais). É defensiva e
-- idempotente: roda tanto em banco "limpo" (onde a V11 já removeu products e
-- payment_methods de teste) quanto em banco onde houve uso real via API
-- (carrinhos, pedidos, reviews etc. criados por esses usuários).
--
-- IDs-alvo:
--   users/customers/farmers: a0000000-…-002 (customer), -003 e -004 (farmers)
-- =============================================================================

-- Histórico/derivados de pedidos e rotas (netos) ----------------------------
DELETE FROM order_status_history
WHERE order_id IN (
  SELECT id FROM orders
  WHERE customer_id = 'a0000000-0000-0000-0000-000000000002'
     OR farmer_id IN (
       'a0000000-0000-0000-0000-000000000003',
       'a0000000-0000-0000-0000-000000000004'
     )
);

DELETE FROM delivery_route_stops
WHERE order_id IN (
  SELECT id FROM orders
  WHERE customer_id = 'a0000000-0000-0000-0000-000000000002'
     OR farmer_id IN (
       'a0000000-0000-0000-0000-000000000003',
       'a0000000-0000-0000-0000-000000000004'
     )
)
OR route_id IN (
  SELECT id FROM delivery_routes
  WHERE farmer_id IN (
    'a0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000004'
  )
);

DELETE FROM visual_route_information
WHERE delivery_route_id IN (
  SELECT id FROM delivery_routes
  WHERE farmer_id IN (
    'a0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000004'
  )
);

DELETE FROM delivery_routes
WHERE farmer_id IN (
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

-- Reviews (dependem de orders/customer/farmer) ------------------------------
DELETE FROM review
WHERE customer_id = 'a0000000-0000-0000-0000-000000000002'
   OR farmer_id IN (
     'a0000000-0000-0000-0000-000000000003',
     'a0000000-0000-0000-0000-000000000004'
   )
   OR order_id IN (
     SELECT id FROM orders
     WHERE customer_id = 'a0000000-0000-0000-0000-000000000002'
        OR farmer_id IN (
          'a0000000-0000-0000-0000-000000000003',
          'a0000000-0000-0000-0000-000000000004'
        )
   );

-- Pedidos -------------------------------------------------------------------
DELETE FROM order_items
WHERE order_id IN (
  SELECT id FROM orders
  WHERE customer_id = 'a0000000-0000-0000-0000-000000000002'
     OR farmer_id IN (
       'a0000000-0000-0000-0000-000000000003',
       'a0000000-0000-0000-0000-000000000004'
     )
);

DELETE FROM orders
WHERE customer_id = 'a0000000-0000-0000-0000-000000000002'
   OR farmer_id IN (
     'a0000000-0000-0000-0000-000000000003',
     'a0000000-0000-0000-0000-000000000004'
   );

-- Carrinhos -----------------------------------------------------------------
DELETE FROM cart_items
WHERE cart_id IN (
  SELECT id FROM carts
  WHERE customer_id = 'a0000000-0000-0000-0000-000000000002'
     OR farmer_id IN (
       'a0000000-0000-0000-0000-000000000003',
       'a0000000-0000-0000-0000-000000000004'
     )
);

DELETE FROM carts
WHERE customer_id = 'a0000000-0000-0000-0000-000000000002'
   OR farmer_id IN (
     'a0000000-0000-0000-0000-000000000003',
     'a0000000-0000-0000-0000-000000000004'
   );

-- Favoritos -----------------------------------------------------------------
DELETE FROM favorites
WHERE customer_id = 'a0000000-0000-0000-0000-000000000002'
   OR farmer_id IN (
     'a0000000-0000-0000-0000-000000000003',
     'a0000000-0000-0000-0000-000000000004'
   );

-- Disponibilidade do produtor -----------------------------------------------
DELETE FROM farmer_availability
WHERE farmer_id IN (
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

-- Produtos dos farmers e seus dependentes -----------------------------------
DELETE FROM stock_movements
WHERE product_id IN (
  SELECT id FROM products
  WHERE farmer_id IN (
    'a0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000004'
  )
);

DELETE FROM cart_items
WHERE product_id IN (
  SELECT id FROM products
  WHERE farmer_id IN (
    'a0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000004'
  )
);

DELETE FROM order_items
WHERE product_id IN (
  SELECT id FROM products
  WHERE farmer_id IN (
    'a0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000004'
  )
);

DELETE FROM product_category_assignments
WHERE product_id IN (
  SELECT id FROM products
  WHERE farmer_id IN (
    'a0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000004'
  )
);

DELETE FROM product_photos
WHERE product_id IN (
  SELECT id FROM products
  WHERE farmer_id IN (
    'a0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000004'
  )
);

DELETE FROM products
WHERE farmer_id IN (
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

-- Métodos de pagamento dos farmers ------------------------------------------
DELETE FROM payment_methods
WHERE farmer_id IN (
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

-- Dados ligados ao user (notificações, CO2, endereços) ----------------------
DELETE FROM notifications
WHERE user_id IN (
  'a0000000-0000-0000-0000-000000000002',
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

DELETE FROM co2_emissions
WHERE vehicle_preference_user_id IN (
  'a0000000-0000-0000-0000-000000000002',
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

DELETE FROM co2_savings
WHERE user_id IN (
  'a0000000-0000-0000-0000-000000000002',
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

DELETE FROM vehicle_preferences
WHERE user_id IN (
  'a0000000-0000-0000-0000-000000000002',
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

DELETE FROM addresses
WHERE user_id IN (
  'a0000000-0000-0000-0000-000000000002',
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

-- Perfis específicos (customer / farmer / producer_profile) -----------------
DELETE FROM customers
WHERE id = 'a0000000-0000-0000-0000-000000000002';

DELETE FROM farmers
WHERE id IN (
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

DELETE FROM producer_profiles
WHERE id IN (
  'a0000000-0000-0000-0000-000000000002',
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

-- Por fim, os próprios usuários ---------------------------------------------
DELETE FROM users
WHERE id IN (
  'a0000000-0000-0000-0000-000000000002',
  'a0000000-0000-0000-0000-000000000003',
  'a0000000-0000-0000-0000-000000000004'
);

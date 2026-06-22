-- Rotas de entrega persistidas (plano Etapa 1, Fase 2). As tabelas da V1 nunca foram mapeadas
-- por entidade nem escritas pela aplicação (rota era 100% efêmera) — recriadas no formato novo.
DROP TABLE IF EXISTS "visual_route_information";
DROP TABLE IF EXISTS "delivery_route_stops";
DROP TABLE IF EXISTS "delivery_routes";

CREATE TABLE "delivery_routes" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "farmer_id" uuid NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'ACTIVE',
  "origin_latitude" decimal(10,7) NOT NULL,
  "origin_longitude" decimal(10,7) NOT NULL,
  "total_distance_km" decimal(10,2),
  "total_duration_seconds" integer,
  -- Baseline para o cálculo de CO2: ida-e-volta individual a cada parada (Route Matrix);
  -- antes era haversine no app, incomparável com a distância rodoviária da rota.
  "baseline_distance_km" decimal(10,2),
  "overview_polyline" text,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "completed_at" timestamptz
);

COMMENT ON COLUMN "delivery_routes"."status" IS 'ACTIVE | COMPLETED | CANCELLED';

CREATE TABLE "route_stops" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "route_id" uuid NOT NULL,
  "order_id" uuid NOT NULL,
  "sequence" integer NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'PENDING',
  "latitude" decimal(10,7) NOT NULL,
  "longitude" decimal(10,7) NOT NULL,
  "address_text" text,
  "leg_distance_km" decimal(10,2),
  "leg_duration_seconds" integer,
  -- ETA absoluto calculado na criação (created_at + somatório das legs até esta parada);
  -- o recálculo dinâmico (Fase 3) usa as legs + posição em tempo real.
  "eta" timestamptz,
  "completed_at" timestamptz
);

COMMENT ON COLUMN "route_stops"."status" IS 'PENDING | ARRIVED | DELIVERED | FAILED';

ALTER TABLE "delivery_routes"
  ADD FOREIGN KEY ("farmer_id") REFERENCES "farmers" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "route_stops"
  ADD FOREIGN KEY ("route_id") REFERENCES "delivery_routes" ("id") ON DELETE CASCADE;
ALTER TABLE "route_stops"
  ADD FOREIGN KEY ("order_id") REFERENCES "orders" ("id") DEFERRABLE INITIALLY IMMEDIATE;

CREATE INDEX "idx_delivery_routes_farmer_id" ON "delivery_routes" ("farmer_id");
-- No máximo UMA rota ativa por produtor (POST /routes substitui a anterior).
CREATE UNIQUE INDEX "uq_delivery_routes_farmer_active"
  ON "delivery_routes" ("farmer_id") WHERE "status" = 'ACTIVE';
CREATE UNIQUE INDEX "uq_route_stops_route_sequence" ON "route_stops" ("route_id", "sequence");
CREATE UNIQUE INDEX "uq_route_stops_route_order" ON "route_stops" ("route_id", "order_id");

-- Bookkeeping de geocodificação: acaba com o retry infinito de endereços não-geocodáveis
-- (self-heal re-tentava a cada GET /producers/locations) e dá feedback no cadastro.
ALTER TABLE "addresses"
  ADD COLUMN "geocode_status" varchar(12),
  ADD COLUMN "geocoded_at" timestamptz;

COMMENT ON COLUMN "addresses"."geocode_status"
  IS 'OK | AMBIGUOUS | FAILED — null = nunca tentado (re-tenta no próximo uso)';

-- Trilha de posições do produtor durante a rota (rastreamento em tempo real, Fase 3).
-- Retenção: 7 dias (decisão de produto — base LGPD: suporte/disputas), purgada por job diário.
CREATE TABLE "route_positions" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "route_id" uuid NOT NULL,
  "latitude" decimal(10,7) NOT NULL,
  "longitude" decimal(10,7) NOT NULL,
  "accuracy_meters" decimal(8,2),
  "speed_kmh" decimal(6,2),
  "recorded_at" timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE "route_positions"
  ADD FOREIGN KEY ("route_id") REFERENCES "delivery_routes" ("id") ON DELETE CASCADE;

CREATE INDEX "idx_route_positions_route_recorded"
  ON "route_positions" ("route_id", "recorded_at" DESC);
-- O purge varre por idade.
CREATE INDEX "idx_route_positions_recorded_at" ON "route_positions" ("recorded_at");

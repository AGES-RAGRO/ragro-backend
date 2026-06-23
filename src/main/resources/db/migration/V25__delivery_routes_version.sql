-- Optimistic-lock column for DeliveryRoute (@Version): guards against two concurrent route
-- mutations (parallel POST /routes / PATCH /routes/{id}/add-stops) corrupting the single active route.
ALTER TABLE delivery_routes
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

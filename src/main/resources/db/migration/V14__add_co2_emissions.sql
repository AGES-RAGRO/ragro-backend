CREATE TABLE co2_emissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_preference_user_id UUID NOT NULL,
    route_distance_km DOUBLE PRECISION NOT NULL,
    co2_emission DOUBLE PRECISION NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL,
    fuel_type VARCHAR(50) NOT NULL,
    average_consumption DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_co2_emissions_vehicle_preference
        FOREIGN KEY (vehicle_preference_user_id) REFERENCES vehicle_preferences(user_id)
);

CREATE INDEX idx_co2_emissions_vehicle_preference ON co2_emissions(vehicle_preference_user_id);

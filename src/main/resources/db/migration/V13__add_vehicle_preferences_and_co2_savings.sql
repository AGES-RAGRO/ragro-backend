CREATE TABLE vehicle_preferences (
    user_id UUID PRIMARY KEY,
    vehicle_type VARCHAR(50) NOT NULL,
    fuel_type VARCHAR(50) NOT NULL,
    average_consumption DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicle_preferences_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE co2_savings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    distance_optimized DOUBLE PRECISION NOT NULL,
    distance_non_optimized DOUBLE PRECISION NOT NULL,
    co2_saved DOUBLE PRECISION NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL,
    fuel_type VARCHAR(50) NOT NULL,
    average_consumption DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_co2_savings_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE favorite_producers (
    id UUID PRIMARY KEY,

    customer_id UUID NOT NULL,
    producer_id UUID NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_favorite_customer
        FOREIGN KEY (customer_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_favorite_producer
        FOREIGN KEY (producer_id)
        REFERENCES farmers(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_customer_producer_favorite
        UNIQUE (customer_id, producer_id)
);

-- Indexes

CREATE INDEX idx_favorite_customer
    ON favorite_producers(customer_id);

CREATE INDEX idx_favorite_producer
    ON favorite_producers(producer_id);
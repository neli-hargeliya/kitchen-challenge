-- actions
CREATE TABLE IF NOT EXISTS actions (
    id BIGSERIAL PRIMARY KEY,
    ts TIMESTAMP NULL,
    order_id VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL,
    target VARCHAR(32) NOT NULL
    );

-- orders
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(128) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    temp VARCHAR(16) NOT NULL,
    storage VARCHAR(16),
    freshness INTEGER,
    placed_at TIMESTAMP
    );

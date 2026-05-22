CREATE TABLE IF NOT EXISTS product (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(19, 2) NOT NULL,
    selling BOOLEAN NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS inventory (
    product_id VARCHAR(36) NOT NULL,
    available_quantity INT NOT NULL,
    held_quantity INT NOT NULL,
    PRIMARY KEY (product_id),
    CONSTRAINT fk_inventory_product
        FOREIGN KEY (product_id)
        REFERENCES product (id),
    CONSTRAINT chk_inventory_available_quantity
        CHECK (available_quantity >= 0),
    CONSTRAINT chk_inventory_held_quantity
        CHECK (held_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_orders_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    unit_price DECIMAL(19, 2) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_order_item_order_id (order_id),
    CONSTRAINT fk_order_item_order
        FOREIGN KEY (order_id)
        REFERENCES orders (id),
    CONSTRAINT chk_order_item_quantity
        CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS inventory_reservation (
    id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    quantity INT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_inventory_reservation_order_id (order_id),
    INDEX idx_inventory_reservation_product_id (product_id),
    INDEX idx_inventory_reservation_status_expires_at (status, expires_at),
    CONSTRAINT fk_inventory_reservation_order
        FOREIGN KEY (order_id)
        REFERENCES orders (id),
    CONSTRAINT fk_inventory_reservation_product
        FOREIGN KEY (product_id)
        REFERENCES product (id),
    CONSTRAINT chk_inventory_reservation_quantity
        CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payment (
    id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    approval_requested_at DATETIME(6) NULL,
    pg_transaction_id VARCHAR(100) NULL,
    PRIMARY KEY (id),
    INDEX idx_payment_order_id (order_id),
    INDEX idx_payment_status_approval_requested_at (status, approval_requested_at),
    CONSTRAINT fk_payment_order
        FOREIGN KEY (order_id)
        REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS idempotency_record (
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_body JSON NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (idempotency_key),
    INDEX idx_idempotency_record_status_expires_at (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS outbox_event (
    id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    retry_count INT NOT NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_event_status_occurred_at (status, occurred_at),
    INDEX idx_outbox_event_aggregate_id (aggregate_id),
    CONSTRAINT chk_outbox_event_retry_count
        CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

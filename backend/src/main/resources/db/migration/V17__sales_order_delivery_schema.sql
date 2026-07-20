CREATE TABLE IF NOT EXISTS sal_order (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    order_no VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    delivery_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    approval_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'NOT_DELIVERED',
    total_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    total_tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sal_order_line (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    product_id BIGINT NOT NULL,
    qty DECIMAL(18, 4) NOT NULL,
    price DECIMAL(18, 2) NOT NULL,
    tax_rate DECIMAL(8, 4) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    tax_amount DECIMAL(18, 2) NOT NULL,
    delivered_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sal_delivery (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    delivery_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    delivery_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    total_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    total_tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sal_delivery_line (
    id BIGINT PRIMARY KEY,
    delivery_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    order_line_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    qty DECIMAL(18, 4) NOT NULL,
    price DECIMAL(18, 2) NOT NULL,
    tax_rate DECIMAL(8, 4) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    tax_amount DECIMAL(18, 2) NOT NULL,
    returned_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sal_return (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    return_no VARCHAR(64) NOT NULL,
    delivery_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    return_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    total_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    total_tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sal_return_line (
    id BIGINT PRIMARY KEY,
    return_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    delivery_line_id BIGINT NOT NULL,
    order_line_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    qty DECIMAL(18, 4) NOT NULL,
    price DECIMAL(18, 2) NOT NULL,
    tax_rate DECIMAL(8, 4) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    tax_amount DECIMAL(18, 2) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sal_order_order_no ON sal_order (order_no);
CREATE INDEX idx_sal_order_customer_id ON sal_order (customer_id);
CREATE INDEX idx_sal_order_order_date ON sal_order (order_date);
CREATE INDEX idx_sal_order_status ON sal_order (status);
CREATE INDEX idx_sal_order_line_order_id ON sal_order_line (order_id);

CREATE UNIQUE INDEX uk_sal_delivery_delivery_no ON sal_delivery (delivery_no);
CREATE INDEX idx_sal_delivery_order_id ON sal_delivery (order_id);
CREATE INDEX idx_sal_delivery_warehouse_id ON sal_delivery (warehouse_id);
CREATE INDEX idx_sal_delivery_line_delivery_id ON sal_delivery_line (delivery_id);
CREATE INDEX idx_sal_delivery_line_order_line_id ON sal_delivery_line (order_line_id);

CREATE UNIQUE INDEX uk_sal_return_return_no ON sal_return (return_no);
CREATE INDEX idx_sal_return_delivery_id ON sal_return (delivery_id);
CREATE INDEX idx_sal_return_warehouse_id ON sal_return (warehouse_id);
CREATE INDEX idx_sal_return_line_return_id ON sal_return_line (return_id);
CREATE INDEX idx_sal_return_line_delivery_line_id ON sal_return_line (delivery_line_id);

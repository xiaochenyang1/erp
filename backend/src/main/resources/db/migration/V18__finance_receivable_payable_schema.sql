CREATE TABLE IF NOT EXISTS fin_payable (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    payable_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id BIGINT NOT NULL,
    source_no VARCHAR(64) NOT NULL,
    direction VARCHAR(32) NOT NULL CHECK (direction IN ('INCREASE', 'DECREASE')),
    supplier_id BIGINT NOT NULL,
    biz_date DATE NOT NULL,
    original_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    settled_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'UNSETTLED' CHECK (status IN ('UNSETTLED', 'PARTIALLY_SETTLED', 'SETTLED', 'OFFSET')),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fin_payment (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    payment_no VARCHAR(64) NOT NULL,
    supplier_id BIGINT NOT NULL,
    payment_date DATE NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    allocated_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'CANCELLED')),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fin_payment_allocation (
    id BIGINT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    payable_id BIGINT NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fin_receivable (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    receivable_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id BIGINT NOT NULL,
    source_no VARCHAR(64) NOT NULL,
    direction VARCHAR(32) NOT NULL CHECK (direction IN ('INCREASE', 'DECREASE')),
    customer_id BIGINT NOT NULL,
    biz_date DATE NOT NULL,
    original_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    settled_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'UNSETTLED' CHECK (status IN ('UNSETTLED', 'PARTIALLY_SETTLED', 'SETTLED', 'OFFSET')),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fin_receipt (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    receipt_no VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    receipt_date DATE NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    allocated_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'CANCELLED')),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fin_receipt_allocation (
    id BIGINT PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    receivable_id BIGINT NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fin_voucher (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    voucher_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id BIGINT NOT NULL,
    source_no VARCHAR(64) NOT NULL,
    biz_date DATE NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'CANCELLED')),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_fin_payable_payable_no ON fin_payable (payable_no);
CREATE UNIQUE INDEX uk_fin_payable_source ON fin_payable (source_type, source_id);
CREATE INDEX idx_fin_payable_supplier_id ON fin_payable (supplier_id);
CREATE INDEX idx_fin_payable_status ON fin_payable (status);

CREATE UNIQUE INDEX uk_fin_payment_payment_no ON fin_payment (payment_no);
CREATE INDEX idx_fin_payment_supplier_id ON fin_payment (supplier_id);
CREATE INDEX idx_fin_payment_allocation_payment_id ON fin_payment_allocation (payment_id);
CREATE INDEX idx_fin_payment_allocation_payable_id ON fin_payment_allocation (payable_id);

CREATE UNIQUE INDEX uk_fin_receivable_receivable_no ON fin_receivable (receivable_no);
CREATE UNIQUE INDEX uk_fin_receivable_source ON fin_receivable (source_type, source_id);
CREATE INDEX idx_fin_receivable_customer_id ON fin_receivable (customer_id);
CREATE INDEX idx_fin_receivable_status ON fin_receivable (status);

CREATE UNIQUE INDEX uk_fin_receipt_receipt_no ON fin_receipt (receipt_no);
CREATE INDEX idx_fin_receipt_customer_id ON fin_receipt (customer_id);
CREATE INDEX idx_fin_receipt_allocation_receipt_id ON fin_receipt_allocation (receipt_id);
CREATE INDEX idx_fin_receipt_allocation_receivable_id ON fin_receipt_allocation (receivable_id);

CREATE UNIQUE INDEX uk_fin_voucher_voucher_no ON fin_voucher (voucher_no);
CREATE UNIQUE INDEX uk_fin_voucher_source ON fin_voucher (source_type, source_id);

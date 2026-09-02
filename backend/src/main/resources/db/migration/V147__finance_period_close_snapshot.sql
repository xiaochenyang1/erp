-- V147: 固化锁定/结账时的月结检查证据，并支持按期间追溯。
CREATE TABLE IF NOT EXISTS fin_period_close_snapshot (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    period_id BIGINT NOT NULL,
    action_type VARCHAR(16) NOT NULL CHECK (action_type IN ('LOCK', 'CLOSE')),
    passed_flag TINYINT NOT NULL DEFAULT 1,
    issue_count INT NOT NULL DEFAULT 0,
    checked_by BIGINT NOT NULL DEFAULT 0,
    checked_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fin_period_close_snapshot_period
    ON fin_period_close_snapshot (company_id, account_book_id, period_id, checked_time);

CREATE TABLE IF NOT EXISTS fin_period_close_snapshot_item (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    snapshot_id BIGINT NOT NULL,
    check_code VARCHAR(64) NOT NULL,
    check_title VARCHAR(128) NOT NULL,
    check_category VARCHAR(64) NOT NULL,
    passed_flag TINYINT NOT NULL DEFAULT 0,
    check_message VARCHAR(512) NOT NULL,
    metric DECIMAL(18, 2) NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fin_period_close_snapshot_item_snapshot
    ON fin_period_close_snapshot_item (company_id, account_book_id, snapshot_id, id);

CREATE TABLE IF NOT EXISTS sys_idempotency_request (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_method VARCHAR(16) NOT NULL,
    request_path VARCHAR(512) NOT NULL,
    request_body_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    response_status INT,
    response_content_type VARCHAR(128),
    response_body TEXT,
    expires_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sys_idempotency_scope_key
    ON sys_idempotency_request (company_id, request_method, request_path, idempotency_key);

CREATE INDEX idx_sys_idempotency_expires_at
    ON sys_idempotency_request (company_id, expires_at);

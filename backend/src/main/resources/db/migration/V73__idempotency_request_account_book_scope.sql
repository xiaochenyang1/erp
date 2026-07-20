ALTER TABLE sys_idempotency_request
    ADD COLUMN account_book_id BIGINT NOT NULL DEFAULT 1;

DROP INDEX uk_sys_idempotency_user_scope_key ON sys_idempotency_request;

CREATE UNIQUE INDEX uk_sys_idempotency_book_user_scope_key
    ON sys_idempotency_request (company_id, account_book_id, user_id, request_method, request_path, idempotency_key);

CREATE INDEX idx_sys_idempotency_company_book_expires_at
    ON sys_idempotency_request (company_id, account_book_id, expires_at);

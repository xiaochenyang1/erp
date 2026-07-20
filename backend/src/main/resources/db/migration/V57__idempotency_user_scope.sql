DROP INDEX uk_sys_idempotency_scope_key ON sys_idempotency_request;

CREATE UNIQUE INDEX uk_sys_idempotency_user_scope_key
    ON sys_idempotency_request (company_id, user_id, request_method, request_path, idempotency_key);

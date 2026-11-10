-- V156: exception-rule automation scheduler lease.
-- The scheduler is a process-wide job, so this table intentionally has no
-- company/account-book columns.  A single seeded row also avoids a first-use
-- insert race between application instances.
CREATE TABLE IF NOT EXISTS sys_scheduler_lease (
    lease_key VARCHAR(128) PRIMARY KEY,
    owner_token VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sys_scheduler_lease_expires_at
    ON sys_scheduler_lease (expires_at);

INSERT INTO sys_scheduler_lease
(lease_key, owner_token, expires_at, created_time, updated_time, version)
VALUES
    ('EXCEPTION_RULE_AUTOMATION', '', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0) AS new
ON DUPLICATE KEY UPDATE
    lease_key = new.lease_key;

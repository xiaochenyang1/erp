CREATE TABLE IF NOT EXISTS sys_sequence_counter (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    biz_type VARCHAR(64) NOT NULL,
    period_key VARCHAR(32) NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sys_sequence_counter_company_biz_period
    ON sys_sequence_counter (company_id, biz_type, period_key);
CREATE INDEX idx_sys_sequence_counter_company_biz
    ON sys_sequence_counter (company_id, biz_type);

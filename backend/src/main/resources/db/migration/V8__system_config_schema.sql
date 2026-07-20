CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT PRIMARY KEY,
    config_code VARCHAR(64) NOT NULL,
    config_name VARCHAR(64) NOT NULL,
    config_value VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_sequence_rule (
    id BIGINT PRIMARY KEY,
    biz_type VARCHAR(64) NOT NULL,
    prefix VARCHAR(32) NOT NULL,
    date_pattern VARCHAR(32) NOT NULL,
    seq_length INT NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sys_config_config_code ON sys_config (config_code);
CREATE UNIQUE INDEX uk_sys_sequence_rule_biz_type ON sys_sequence_rule (biz_type);

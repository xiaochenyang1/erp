CREATE TABLE md_currency (
    id BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    currency_name VARCHAR(64) NOT NULL,
    currency_symbol VARCHAR(8),
    decimal_places INT NOT NULL DEFAULT 2,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_md_currency_code (currency_code)
);

CREATE TABLE md_exchange_rate (
    id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    from_currency_code VARCHAR(3) NOT NULL,
    to_currency_code VARCHAR(3) NOT NULL,
    rate DECIMAL(24,12) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_md_exchange_rate_positive CHECK (rate > 0),
    UNIQUE KEY uk_md_exchange_rate_scope (company_id, account_book_id, from_currency_code, to_currency_code, effective_from),
    KEY idx_md_exchange_rate_lookup (company_id, account_book_id, from_currency_code, to_currency_code, effective_from, effective_to)
);

INSERT INTO md_currency (id, currency_code, currency_name, currency_symbol, decimal_places)
VALUES (1, 'CNY', 'Chinese Yuan', '¥', 2)
ON DUPLICATE KEY UPDATE currency_name = VALUES(currency_name), currency_symbol = VALUES(currency_symbol);

INSERT INTO sys_config (id, config_code, config_name, config_value, status, deleted_flag, remark)
VALUES (910020, 'finance.base.currency', 'Base currency', 'CNY', 'ENABLED', 0, 'Default base currency for legacy documents')
ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), config_value = VALUES(config_value);

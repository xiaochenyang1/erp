-- V166: period-end unrealized FX revaluation runs.
--
-- The subledger keeps its booking-rate snapshot. This table records one GL
-- revaluation per open period: a period-end adjustment and the next-period
-- opening reversal. A POSTED row occupies generation 0. Cancelling sets
-- generation to the row id so the same period can be revalued again.
-- Pure DDL, compatible with MySQL and H2 migration checks.

CREATE TABLE IF NOT EXISTS fin_fx_revaluation (
    id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    open_original_total DECIMAL(20,2) NOT NULL DEFAULT 0,
    ar_adjustment DECIMAL(20,2) NOT NULL DEFAULT 0,
    ap_adjustment DECIMAL(20,2) NOT NULL DEFAULT 0,
    revaluation_date DATE NOT NULL,
    reversal_date DATE NOT NULL,
    generation BIGINT NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_fin_fx_revaluation_generation
    ON fin_fx_revaluation (company_id, account_book_id, period_id, generation);

CREATE INDEX idx_fin_fx_revaluation_period
    ON fin_fx_revaluation (company_id, account_book_id, period_id, status, deleted_flag);

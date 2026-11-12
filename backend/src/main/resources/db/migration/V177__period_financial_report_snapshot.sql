-- V177: immutable financial-report payloads captured with an account-period close snapshot.

CREATE TABLE IF NOT EXISTS fin_period_financial_report_snapshot (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    close_snapshot_id BIGINT NOT NULL,
    report_type VARCHAR(32) NOT NULL CHECK (report_type IN (
        'TRIAL_BALANCE', 'BALANCE_SHEET', 'INCOME_STATEMENT', 'CASH_FLOW_DIRECT'
    )),
    formula_version VARCHAR(64) NOT NULL,
    configuration_digest CHAR(64) NOT NULL,
    configuration_payload LONGTEXT NOT NULL,
    date_from DATE NOT NULL,
    date_to DATE NOT NULL,
    verified_flag TINYINT NOT NULL DEFAULT 0,
    report_payload LONGTEXT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    captured_by BIGINT NOT NULL,
    captured_time TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_period_fin_report_snapshot_type (close_snapshot_id, report_type),
    KEY idx_period_fin_report_snapshot_period
        (company_id, account_book_id, period_id, captured_time)
);

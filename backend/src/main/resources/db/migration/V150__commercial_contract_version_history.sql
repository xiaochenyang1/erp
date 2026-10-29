-- V150: 商务合同版本历史（头/明细快照、差异与恢复草稿）
CREATE TABLE IF NOT EXISTS biz_contract_version (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    contract_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    contract_snapshot_json LONGTEXT NOT NULL,
    line_snapshot_json LONGTEXT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_biz_contract_version_scope ON biz_contract_version (company_id, account_book_id, contract_id, version_no);
CREATE INDEX idx_biz_contract_version_contract ON biz_contract_version (company_id, account_book_id, contract_id, created_time, id);

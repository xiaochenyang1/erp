CREATE TABLE IF NOT EXISTS sys_readiness_run (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    run_no VARCHAR(64) NOT NULL,
    release_commit VARCHAR(128) NOT NULL,
    release_version VARCHAR(128),
    environment VARCHAR(64) NOT NULL,
    database_instance VARCHAR(256),
    redis_instance VARCHAR(256),
    docker_profile VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    decision VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    decision_comment VARCHAR(512),
    remark VARCHAR(512),
    started_by BIGINT NOT NULL,
    started_time TIMESTAMP NOT NULL,
    decided_by BIGINT,
    decided_time TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_sys_readiness_run_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'PASSED', 'FAILED', 'BLOCKED', 'NO_GO')),
    CONSTRAINT chk_sys_readiness_run_decision CHECK (decision IN ('PENDING', 'GO', 'NO_GO'))
);

CREATE TABLE IF NOT EXISTS sys_readiness_item (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expected_result VARCHAR(512),
    actual_result VARCHAR(512),
    failure_reason VARCHAR(512),
    executed_by BIGINT,
    executed_time TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_sys_readiness_item_priority CHECK (priority IN ('P0', 'P1', 'P2')),
    CONSTRAINT chk_sys_readiness_item_status CHECK (status IN ('PENDING', 'PASSED', 'FAILED', 'BLOCKED', 'SKIPPED'))
);

CREATE TABLE IF NOT EXISTS sys_readiness_evidence (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    request_method VARCHAR(16),
    request_uri VARCHAR(512),
    http_status INT,
    business_type VARCHAR(64),
    business_id BIGINT,
    business_no VARCHAR(128),
    summary VARCHAR(256) NOT NULL,
    detail VARCHAR(2048),
    attachment_business_type VARCHAR(64),
    attachment_business_id BIGINT,
    recorded_by BIGINT NOT NULL,
    recorded_time TIMESTAMP NOT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_sys_readiness_evidence_type CHECK (evidence_type IN ('API', 'BUSINESS_NO', 'LOG', 'SCREENSHOT', 'NOTE', 'ATTACHMENT'))
);

CREATE UNIQUE INDEX uk_sys_readiness_run_no
    ON sys_readiness_run (company_id, account_book_id, run_no);
CREATE INDEX idx_sys_readiness_run_commit
    ON sys_readiness_run (company_id, account_book_id, release_commit);
CREATE INDEX idx_sys_readiness_run_status_time
    ON sys_readiness_run (company_id, account_book_id, status, created_time);
CREATE INDEX idx_sys_readiness_item_run_status
    ON sys_readiness_item (company_id, account_book_id, run_id, priority, status);
CREATE INDEX idx_sys_readiness_item_code_time
    ON sys_readiness_item (company_id, account_book_id, item_code, created_time);
CREATE INDEX idx_sys_readiness_evidence_item_time
    ON sys_readiness_evidence (company_id, account_book_id, run_id, item_id, recorded_time);
CREATE INDEX idx_sys_readiness_evidence_business
    ON sys_readiness_evidence (company_id, account_book_id, business_type, business_id);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
VALUES
    (5091, 5001, 'MENU', 'SYSTEM_READINESS', '预生产验收', '/system/readiness', 'system/readiness/index', 'system:readiness:view', 11, 1, 'ACTIVE', 0, 0, 0, 0),
    (5092, 5091, 'BUTTON', 'SYSTEM_READINESS_MANAGE', '维护验收记录', NULL, NULL, 'system:readiness:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5093, 5091, 'BUTTON', 'SYSTEM_READINESS_DECIDE', '发布决策', NULL, NULL, 'system:readiness:decide', 2, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    visible_flag = VALUES(visible_flag),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7126, 3002, 5091, 0),
    (7127, 3002, 5092, 0),
    (7128, 3002, 5093, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);

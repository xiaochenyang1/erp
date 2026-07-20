CREATE TABLE IF NOT EXISTS biz_business_timeline (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    business_no VARCHAR(128),
    event_type VARCHAR(32) NOT NULL,
    content VARCHAR(1024) NOT NULL,
    attachment_id BIGINT,
    operator_user_id BIGINT NOT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_biz_business_timeline_event_type
        CHECK (event_type IN ('COMMENT', 'ATTACHMENT_UPLOADED', 'ATTACHMENT_DELETED', 'SYSTEM'))
);

CREATE INDEX idx_biz_business_timeline_business_time
    ON biz_business_timeline (company_id, account_book_id, business_type, business_id, deleted_flag, created_time);

CREATE INDEX idx_biz_business_timeline_business_no
    ON biz_business_timeline (company_id, account_book_id, business_type, business_no, deleted_flag, created_time);

CREATE INDEX idx_biz_business_timeline_attachment
    ON biz_business_timeline (company_id, account_book_id, attachment_id);

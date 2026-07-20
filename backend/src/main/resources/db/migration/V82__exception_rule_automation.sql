ALTER TABLE biz_exception_rule ADD COLUMN schedule_interval_minutes INT NOT NULL DEFAULT 60;
ALTER TABLE biz_exception_rule ADD COLUMN next_scan_time TIMESTAMP NULL;
ALTER TABLE biz_exception_rule
    ADD CONSTRAINT chk_biz_exception_rule_schedule_interval
    CHECK (schedule_interval_minutes BETWEEN 5 AND 10080);

CREATE INDEX idx_biz_exception_rule_due
    ON biz_exception_rule (enabled, deleted_flag, next_scan_time);

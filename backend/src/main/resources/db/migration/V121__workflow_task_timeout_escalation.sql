ALTER TABLE wf_approval_task
    ADD COLUMN due_time TIMESTAMP NULL,
    ADD COLUMN escalated_time TIMESTAMP NULL,
    ADD COLUMN escalation_count INT NOT NULL DEFAULT 0;

UPDATE wf_approval_task
SET due_time = DATE_ADD(created_time, INTERVAL 24 HOUR)
WHERE due_time IS NULL;

CREATE INDEX idx_wf_task_company_book_status_due
    ON wf_approval_task (company_id, account_book_id, status, due_time);

ALTER TABLE wf_approval_record DROP CHECK ck_wf_approval_record_action;
ALTER TABLE wf_approval_record ADD CONSTRAINT ck_wf_approval_record_action
    CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'CANCEL', 'WITHDRAW', 'TRANSFER', 'ESCALATE'));

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5412, 5011, 'BUTTON', 'WORKFLOW_ESCALATE', '审批超时升级', NULL, NULL, 'workflow:escalate', 4, 1,
     'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), permission = VALUES(permission),
    sort_no = VALUES(sort_no), status = VALUES(status), deleted_flag = VALUES(deleted_flag), updated_by = VALUES(updated_by);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
VALUES (7422, 3002, 5412, 0)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), menu_id = VALUES(menu_id);

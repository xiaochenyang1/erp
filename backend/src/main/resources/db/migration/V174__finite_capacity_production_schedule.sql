-- V174: finite-capacity production scheduling.
-- Work centers expose a deterministic weekday capacity window and released-order
-- operation snapshots retain both standard load and the committed schedule.

ALTER TABLE prd_work_center
    ADD COLUMN daily_capacity_minutes INT NOT NULL DEFAULT 480 AFTER work_center_name,
    ADD COLUMN workday_start_time TIME NOT NULL DEFAULT '08:00:00' AFTER daily_capacity_minutes;

ALTER TABLE prd_work_center
    ADD CONSTRAINT chk_prd_work_center_daily_capacity
        CHECK (daily_capacity_minutes BETWEEN 1 AND 1440);

ALTER TABLE prd_order_operation
    ADD COLUMN standard_minutes DECIMAL(18, 2) NOT NULL DEFAULT 0 AFTER work_center_id,
    ADD COLUMN scheduled_minutes DECIMAL(18, 2) NULL AFTER status,
    ADD COLUMN scheduled_start_time TIMESTAMP NULL AFTER scheduled_minutes,
    ADD COLUMN scheduled_end_time TIMESTAMP NULL AFTER scheduled_start_time,
    ADD COLUMN scheduled_by BIGINT NULL AFTER scheduled_end_time,
    ADD COLUMN scheduled_time TIMESTAMP NULL AFTER scheduled_by;

UPDATE prd_order_operation operation_snapshot
JOIN prd_routing_operation routing_operation
  ON routing_operation.id = operation_snapshot.routing_operation_id
 AND routing_operation.company_id = operation_snapshot.company_id
 AND routing_operation.account_book_id = operation_snapshot.account_book_id
SET operation_snapshot.standard_minutes = routing_operation.standard_minutes
WHERE operation_snapshot.standard_minutes = 0;

CREATE INDEX idx_prd_order_operation_schedule
    ON prd_order_operation (company_id, account_book_id, work_center_id, scheduled_start_time, scheduled_end_time);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5520, 5080, 'MENU', 'PRODUCTION_SCHEDULE', '产能排程', '/production/schedules',
     'production/schedules/index', 'production:schedule:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5521, 5520, 'BUTTON', 'PRODUCTION_SCHEDULE_PREVIEW', '预览产能排程', NULL, NULL,
     'production:schedule:view', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5522, 5520, 'BUTTON', 'PRODUCTION_SCHEDULE_RUN', '执行产能排程', NULL, NULL,
     'production:schedule:run', 2, 1, 'ACTIVE', 0, 0, 0, 0)
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
    deleted_flag = VALUES(deleted_flag),
    updated_by = VALUES(updated_by);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7520, 3002, 5520, 0),
    (7521, 3002, 5521, 0),
    (7522, 3002, 5522, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);

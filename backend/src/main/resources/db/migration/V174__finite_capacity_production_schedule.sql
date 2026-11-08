-- V174: finite-capacity production scheduling.
-- Work centers expose a deterministic weekday capacity window and released-order
-- operation snapshots retain both standard load and the committed schedule.

ALTER TABLE prd_work_center
    ADD COLUMN daily_capacity_minutes INT NOT NULL DEFAULT 480;

ALTER TABLE prd_work_center
    ADD COLUMN workday_start_time TIME NOT NULL DEFAULT '08:00:00';

ALTER TABLE prd_work_center
    ADD CONSTRAINT chk_prd_work_center_daily_capacity
        CHECK (daily_capacity_minutes BETWEEN 1 AND 1440);

ALTER TABLE prd_order_operation
    ADD COLUMN standard_minutes DECIMAL(18, 2) NOT NULL DEFAULT 0;

ALTER TABLE prd_order_operation
    ADD COLUMN scheduled_minutes DECIMAL(18, 2) NULL;

ALTER TABLE prd_order_operation
    ADD COLUMN scheduled_start_time TIMESTAMP NULL;

ALTER TABLE prd_order_operation
    ADD COLUMN scheduled_end_time TIMESTAMP NULL;

ALTER TABLE prd_order_operation
    ADD COLUMN scheduled_by BIGINT NULL;

ALTER TABLE prd_order_operation
    ADD COLUMN scheduled_time TIMESTAMP NULL;

UPDATE prd_order_operation operation_snapshot
SET operation_snapshot.standard_minutes = (
    SELECT routing_operation.standard_minutes
    FROM prd_routing_operation routing_operation
    WHERE routing_operation.id = operation_snapshot.routing_operation_id
      AND routing_operation.company_id = operation_snapshot.company_id
      AND routing_operation.account_book_id = operation_snapshot.account_book_id
)
WHERE operation_snapshot.standard_minutes = 0
  AND EXISTS (
      SELECT 1
      FROM prd_routing_operation routing_operation
      WHERE routing_operation.id = operation_snapshot.routing_operation_id
        AND routing_operation.company_id = operation_snapshot.company_id
        AND routing_operation.account_book_id = operation_snapshot.account_book_id
  );

CREATE INDEX idx_prd_order_operation_schedule
    ON prd_order_operation (company_id, account_book_id, work_center_id, scheduled_start_time, scheduled_end_time);

INSERT INTO sys_menu
    (id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
     status, deleted_flag, created_by, updated_by, version)
SELECT allocated.base_id + 1, parent.id, 'MENU', 'PRODUCTION_SCHEDULE', '产能排程',
       '/production/schedules', 'production/schedules/index', 'production:schedule:view', 5, 1,
       'ACTIVE', 0, 0, 0, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_menu) allocated
JOIN sys_menu parent ON parent.menu_code = 'PRODUCTION'
    AND parent.menu_type = 'CATALOG' AND parent.deleted_flag = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing WHERE existing.menu_code = 'PRODUCTION_SCHEDULE'
);

INSERT INTO sys_menu
    (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag,
     status, deleted_flag, created_by, updated_by, version)
SELECT allocated.base_id + 1, parent.id, 'BUTTON', 'PRODUCTION_SCHEDULE_PREVIEW', '预览产能排程',
       'production:schedule:view', 1, 1, 'ACTIVE', 0, 0, 0, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_menu) allocated
JOIN sys_menu parent ON parent.menu_code = 'PRODUCTION_SCHEDULE'
    AND parent.menu_type = 'MENU' AND parent.deleted_flag = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing WHERE existing.menu_code = 'PRODUCTION_SCHEDULE_PREVIEW'
);

INSERT INTO sys_menu
    (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag,
     status, deleted_flag, created_by, updated_by, version)
SELECT allocated.base_id + 1, parent.id, 'BUTTON', 'PRODUCTION_SCHEDULE_RUN', '执行产能排程',
       'production:schedule:run', 2, 1, 'ACTIVE', 0, 0, 0, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_menu) allocated
JOIN sys_menu parent ON parent.menu_code = 'PRODUCTION_SCHEDULE'
    AND parent.menu_type = 'MENU' AND parent.deleted_flag = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing WHERE existing.menu_code = 'PRODUCTION_SCHEDULE_RUN'
);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
SELECT allocated.base_id + ROW_NUMBER() OVER (ORDER BY roles.id, menus.id), roles.id, menus.id, 0
FROM (SELECT COALESCE(MAX(id), 0) AS base_id FROM sys_role_menu) allocated
CROSS JOIN sys_role roles
CROSS JOIN sys_menu menus
WHERE roles.role_code = 'ERP_ADMIN' AND roles.deleted_flag = 0
  AND menus.menu_code IN (
      'PRODUCTION_SCHEDULE',
      'PRODUCTION_SCHEDULE_PREVIEW',
      'PRODUCTION_SCHEDULE_RUN'
  )
  AND menus.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = roles.id AND existing.menu_id = menus.id
  );

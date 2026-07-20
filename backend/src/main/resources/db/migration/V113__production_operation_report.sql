-- V113: 生产工序报工。
-- 工单释放时按 BOM 关联工艺路线生成工序快照；完工前若有工序则要求全部报工完成。
-- 按钮挂生产工单 MENU 5083；menu 5380；role_menu 7390。

CREATE TABLE IF NOT EXISTS prd_order_operation (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    routing_id BIGINT,
    routing_operation_id BIGINT,
    line_no INT NOT NULL,
    operation_code VARCHAR(64) NOT NULL,
    operation_name VARCHAR(128) NOT NULL,
    work_center_id BIGINT,
    planned_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    reported_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    qualified_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    scrap_qty DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_prd_order_operation_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DONE'))
);

CREATE UNIQUE INDEX uk_prd_order_operation_order_line
    ON prd_order_operation (company_id, account_book_id, order_id, line_no);
CREATE INDEX idx_prd_order_operation_order_status
    ON prd_order_operation (company_id, account_book_id, order_id, status);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5380, 5083, 'BUTTON', 'PRODUCTION_ORDER_REPORT', '工序报工', NULL, NULL,
     'production:order:report', 8, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_name = VALUES(menu_name),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    visible_flag = VALUES(visible_flag),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    updated_by = VALUES(updated_by);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7390, 3002, 5380, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);

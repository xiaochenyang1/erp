-- 库存预警处置记录：低库存命中本身是按规则+当前库存动态派生的，不落库。
-- 本表记录对某仓某商品当前低库存命中的处置动作（忽略/已处理），使派生出的预警可以被闭环。
-- 处置时快照当时缺口 snapshot_shortage_qty；若之后缺口进一步恶化（当前缺口 > 快照缺口），
-- 处置在查询侧自动失效、该项重新变回 ACTIVE，避免"忽略一次即永久静默"。
CREATE TABLE IF NOT EXISTS inv_alert_disposition (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    snapshot_shortage_qty DECIMAL(20, 4) NOT NULL DEFAULT 0,
    handle_remark VARCHAR(512),
    handled_by BIGINT NOT NULL,
    handled_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_inv_alert_disposition_status CHECK (status IN ('IGNORED', 'RESOLVED'))
);

-- 每个（租户 + 仓库 + 商品）最多一条有效处置记录，处置以商品在仓维度收敛，不与具体规则 id 强绑
CREATE UNIQUE INDEX uk_inv_alert_disposition_wh_product
    ON inv_alert_disposition (company_id, account_book_id, warehouse_id, product_id, deleted_flag);

CREATE INDEX idx_inv_alert_disposition_rule
    ON inv_alert_disposition (company_id, account_book_id, rule_id, deleted_flag);

-- 库存预警处置按钮权限种子。库存预警菜单在 scripts/init-menu-data.sql 中 id=505。
-- SUPER_ADMIN 通过 PermissionCodes.allPermissions() 自动获得该权限，此处种子用于普通角色的菜单树按钮下发。
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5103, 505, 'BUTTON', 'INVENTORY_ALERT_HANDLE', '处置库存预警', NULL, NULL,
     'inventory:alert:handle', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7147, 3002, 5103, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);

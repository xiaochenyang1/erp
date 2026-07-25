-- V130: 仓库库位轻模型（主数据）。
-- 每个仓库默认生成 MAIN 库位；后续库存过账可逐步接入 location_id。
-- 号段：menu 5450-5451；role_menu 7460-7461。

CREATE TABLE IF NOT EXISTS md_location (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    location_code VARCHAR(64) NOT NULL,
    location_name VARCHAR(128) NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_md_location_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uk_md_location_company_book_warehouse_code
    ON md_location (company_id, account_book_id, warehouse_id, location_code);
CREATE INDEX idx_md_location_company_book_warehouse_status
    ON md_location (company_id, account_book_id, warehouse_id, status, deleted_flag);
CREATE INDEX idx_md_location_company_book_warehouse_default
    ON md_location (company_id, account_book_id, warehouse_id, is_default, deleted_flag);

-- 为已有仓库补默认 MAIN 库位（幂等：按仓库+编码唯一）
INSERT INTO md_location (
    id, company_id, account_book_id, warehouse_id, location_code, location_name,
    is_default, status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version
)
SELECT
    (w.id + 500000000000000000) AS id,
    w.company_id,
    w.account_book_id,
    w.id AS warehouse_id,
    'MAIN' AS location_code,
    '默认库位' AS location_name,
    1 AS is_default,
    'ACTIVE' AS status,
    0 AS deleted_flag,
    '系统默认库位' AS remark,
    0 AS created_by,
    CURRENT_TIMESTAMP AS created_time,
    0 AS updated_by,
    CURRENT_TIMESTAMP AS updated_time,
    0 AS version
FROM md_warehouse w
WHERE w.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM md_location l
      WHERE l.company_id = w.company_id
        AND l.account_book_id = w.account_book_id
        AND l.warehouse_id = w.id
        AND l.location_code = 'MAIN'
  );

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5450, 5130, 'MENU', 'MASTERDATA_LOCATION', '库位管理', '/masterdata/locations',
     'masterdata/locations/index', 'masterdata:location:view', 5, 1, 'ACTIVE', 0, 0, 0, 0),
    (5451, 5450, 'BUTTON', 'MASTERDATA_LOCATION_MANAGE', '维护库位', NULL, NULL,
     'masterdata:location:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0)
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
    (7460, 3002, 5450, 0),
    (7461, 3002, 5451, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);

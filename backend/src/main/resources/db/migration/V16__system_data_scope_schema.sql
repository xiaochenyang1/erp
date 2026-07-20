CREATE TABLE IF NOT EXISTS sys_role_data_scope (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    warehouse_id BIGINT,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sys_role_data_scope_type
        CHECK (scope_type IN ('ALL', 'DEPT', 'POST', 'WAREHOUSE', 'SELF')),
    CONSTRAINT chk_sys_role_data_scope_warehouse
        CHECK (
            (scope_type = 'WAREHOUSE' AND warehouse_id IS NOT NULL)
            OR (scope_type <> 'WAREHOUSE' AND warehouse_id IS NULL)
        )
);

CREATE TABLE IF NOT EXISTS sys_user_data_scope (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    warehouse_id BIGINT,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sys_user_data_scope_type
        CHECK (scope_type IN ('ALL', 'DEPT', 'POST', 'WAREHOUSE', 'SELF')),
    CONSTRAINT chk_sys_user_data_scope_warehouse
        CHECK (
            (scope_type = 'WAREHOUSE' AND warehouse_id IS NOT NULL)
            OR (scope_type <> 'WAREHOUSE' AND warehouse_id IS NULL)
        )
);

CREATE UNIQUE INDEX uk_sys_role_data_scope_unique
    ON sys_role_data_scope (role_id, scope_type, warehouse_id);

CREATE UNIQUE INDEX uk_sys_user_data_scope_unique
    ON sys_user_data_scope (user_id, scope_type, warehouse_id);

INSERT INTO sys_role_data_scope (id, role_id, scope_type, warehouse_id, created_by)
SELECT 16001, r.id, 'ALL', NULL, 0
FROM sys_role r
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_data_scope s
      WHERE s.role_id = r.id
        AND s.scope_type = 'ALL'
  );

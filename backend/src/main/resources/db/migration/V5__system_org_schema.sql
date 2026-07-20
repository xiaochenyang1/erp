CREATE TABLE IF NOT EXISTS sys_dept (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    dept_code VARCHAR(64) NOT NULL,
    dept_name VARCHAR(64) NOT NULL,
    leader_user_id BIGINT,
    sort_no INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_post (
    id BIGINT PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    post_code VARCHAR(64) NOT NULL,
    post_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sys_dept_dept_code ON sys_dept (dept_code);
CREATE UNIQUE INDEX uk_sys_post_post_code ON sys_post (post_code);
CREATE INDEX idx_sys_dept_parent_id ON sys_dept (parent_id);
CREATE INDEX idx_sys_post_dept_id ON sys_post (dept_id);

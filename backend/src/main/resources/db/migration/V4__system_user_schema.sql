CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    mobile VARCHAR(32),
    dept_id BIGINT,
    post_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username);
CREATE INDEX idx_sys_user_dept_id ON sys_user (dept_id);
CREATE INDEX idx_sys_user_role_user_id ON sys_user_role (user_id);
CREATE INDEX idx_sys_user_role_role_id ON sys_user_role (role_id);

CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE')),
    message VARCHAR(512),
    login_ip VARCHAR(64),
    user_agent VARCHAR(512),
    login_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    module VARCHAR(64) NOT NULL,
    operation VARCHAR(128) NOT NULL,
    biz_no VARCHAR(128),
    result VARCHAR(32) NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE')),
    message VARCHAR(512),
    request_method VARCHAR(16),
    request_uri VARCHAR(512),
    operation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sys_login_log_username ON sys_login_log (username);
CREATE INDEX idx_sys_login_log_login_time ON sys_login_log (login_time);
CREATE INDEX idx_sys_operation_log_module ON sys_operation_log (module);
CREATE INDEX idx_sys_operation_log_biz_no ON sys_operation_log (biz_no);
CREATE INDEX idx_sys_operation_log_operation_time ON sys_operation_log (operation_time);

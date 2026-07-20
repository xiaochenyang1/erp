USE erp_server;

CREATE TABLE IF NOT EXISTS fin_account_subject (
    id BIGINT NOT NULL PRIMARY KEY,
    subject_code VARCHAR(64) NOT NULL,
    subject_name VARCHAR(128) NOT NULL,
    subject_level INT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    enabled_flag TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO fin_account_subject (id, subject_code, subject_name, subject_level, direction, parent_id)
VALUES
    (9001, '1001', '库存现金', 1, 'DEBIT', 0),
    (9002, '1122', '应收账款', 1, 'DEBIT', 0),
    (9003, '2202', '应付账款', 1, 'CREDIT', 0),
    (9004, '6001', '主营业务收入', 1, 'CREDIT', 0)
ON DUPLICATE KEY UPDATE
    subject_code = VALUES(subject_code),
    subject_name = VALUES(subject_name),
    subject_level = VALUES(subject_level),
    direction = VALUES(direction),
    parent_id = VALUES(parent_id),
    enabled_flag = 1;

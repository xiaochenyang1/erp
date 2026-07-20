USE erp_server;

CREATE TABLE IF NOT EXISTS wf_approval_definition (
    id BIGINT NOT NULL PRIMARY KEY,
    biz_type VARCHAR(64) NOT NULL,
    flow_name VARCHAR(64) NOT NULL,
    enabled_flag TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_approval_node_definition (
    id BIGINT NOT NULL PRIMARY KEY,
    definition_id BIGINT NOT NULL,
    node_no INT NOT NULL,
    approver_role_code VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO wf_approval_definition (id, biz_type, flow_name, enabled_flag)
VALUES
    (8001, 'PURCHASE_ORDER', '采购订单审批', 1),
    (8002, 'SALES_ORDER', '销售订单审批', 1)
ON DUPLICATE KEY UPDATE
    biz_type = VALUES(biz_type),
    flow_name = VALUES(flow_name),
    enabled_flag = VALUES(enabled_flag);

INSERT INTO wf_approval_node_definition (id, definition_id, node_no, approver_role_code)
VALUES
    (8101, 8001, 1, 'ERP_ADMIN'),
    (8102, 8002, 1, 'ERP_ADMIN')
ON DUPLICATE KEY UPDATE
    definition_id = VALUES(definition_id),
    node_no = VALUES(node_no),
    approver_role_code = VALUES(approver_role_code);

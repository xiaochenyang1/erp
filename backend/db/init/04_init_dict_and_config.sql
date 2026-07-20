USE erp_server;

INSERT INTO sys_config (id, config_code, config_name, config_value)
VALUES
    (1002, 'erp.stock.allow-negative', '是否允许负库存', 'false'),
    (1003, 'erp.approval.enabled', '审批开关', 'true')
ON DUPLICATE KEY UPDATE
    config_name = VALUES(config_name),
    config_value = VALUES(config_value);

INSERT INTO sys_sequence_rule (id, biz_type, prefix, date_pattern, seq_length, current_value)
VALUES
    (2001, 'PURCHASE_ORDER', 'PO', 'yyyyMMdd', 4, 1),
    (2002, 'SALES_ORDER', 'SO', 'yyyyMMdd', 4, 0),
    (2003, 'STOCK_ADJUST', 'IA', 'yyyyMMdd', 4, 0),
    (2004, 'PURCHASE_RECEIPT', 'PR', 'yyyyMMdd', 4, 1),
    (2005, 'PURCHASE_RETURN', 'PRT', 'yyyyMMdd', 4, 1),
    (2006, 'FIN_PAYMENT', 'FP', 'yyyyMMdd', 4, 0),
    (2007, 'FIN_RECEIPT', 'FR', 'yyyyMMdd', 4, 0),
    (2008, 'INVENTORY_TRANSFER', 'IT', 'yyyyMMdd', 4, 0)
ON DUPLICATE KEY UPDATE
    prefix = VALUES(prefix),
    date_pattern = VALUES(date_pattern),
    seq_length = VALUES(seq_length),
    current_value = VALUES(current_value),
    status = 'ACTIVE';

INSERT INTO sys_dict_type
(id, dict_type, dict_name, status, deleted_flag, remark)
VALUES
    (25001, 'product_type', '商品类型', 'ACTIVE', 0, '主数据商品类型枚举'),
    (25002, 'settlement_method', '结算方式', 'ACTIVE', 0, '客户供应商结算方式枚举')
ON DUPLICATE KEY UPDATE
    dict_name = VALUES(dict_name),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark);

INSERT INTO sys_dict_item
(id, type_id, dict_type, item_label, item_value, sort_no, status, deleted_flag, remark)
VALUES
    (25011, 25001, 'product_type', '实物商品', 'PHYSICAL', 10, 'ACTIVE', 0, '需要库存流转的实物商品'),
    (25012, 25001, 'product_type', '库存商品', 'GOODS', 20, 'ACTIVE', 0, '库存业务兼容商品类型'),
    (25013, 25001, 'product_type', '服务', 'SERVICE', 30, 'ACTIVE', 0, '不参与库存流转的服务项目'),
    (25021, 25002, 'settlement_method', '月结30天', 'MONTH_END_30', 10, 'ACTIVE', 0, '月结30天'),
    (25022, 25002, 'settlement_method', '月结45天', 'MONTH_END_45', 20, 'ACTIVE', 0, '月结45天'),
    (25023, 25002, 'settlement_method', '预付款', 'PREPAID', 30, 'ACTIVE', 0, '先款后货'),
    (25024, 25002, 'settlement_method', '货到付款', 'CASH_ON_DELIVERY', 40, 'ACTIVE', 0, '货到付款')
ON DUPLICATE KEY UPDATE
    type_id = VALUES(type_id),
    item_label = VALUES(item_label),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    remark = VALUES(remark);

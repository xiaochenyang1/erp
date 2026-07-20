INSERT INTO sys_dict_type
(id, dict_type, dict_name, status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (25001, 'product_type', '商品类型', 'ACTIVE', 0, '主数据商品类型枚举', 0, 0, 0),
    (25002, 'settlement_method', '结算方式', 'ACTIVE', 0, '客户供应商结算方式枚举', 0, 0, 0);

INSERT INTO sys_dict_item
(id, type_id, dict_type, item_label, item_value, sort_no, status, deleted_flag, remark, created_by, updated_by, version)
VALUES
    (25011, 25001, 'product_type', '实物商品', 'PHYSICAL', 10, 'ACTIVE', 0, '需要库存流转的实物商品', 0, 0, 0),
    (25012, 25001, 'product_type', '库存商品', 'GOODS', 20, 'ACTIVE', 0, '库存业务兼容商品类型', 0, 0, 0),
    (25013, 25001, 'product_type', '服务', 'SERVICE', 30, 'ACTIVE', 0, '不参与库存流转的服务项目', 0, 0, 0),
    (25021, 25002, 'settlement_method', '月结30天', 'MONTH_END_30', 10, 'ACTIVE', 0, '月结30天', 0, 0, 0),
    (25022, 25002, 'settlement_method', '月结45天', 'MONTH_END_45', 20, 'ACTIVE', 0, '月结45天', 0, 0, 0),
    (25023, 25002, 'settlement_method', '预付款', 'PREPAID', 30, 'ACTIVE', 0, '先款后货', 0, 0, 0),
    (25024, 25002, 'settlement_method', '货到付款', 'CASH_ON_DELIVERY', 40, 'ACTIVE', 0, '货到付款', 0, 0, 0);

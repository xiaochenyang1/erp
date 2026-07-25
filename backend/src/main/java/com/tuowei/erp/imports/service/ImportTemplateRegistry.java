package com.tuowei.erp.imports.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ImportTemplateRegistry {

    private static final Map<String, List<String>> HEADERS = Map.of(
            ImportConstants.PRODUCT, List.of(
                    "product_code", "product_name", "product_type", "category_name", "specification",
                    "unit_name", "aux_unit_name", "conversion_factor", "barcode",
                    "purchase_price", "sale_price", "tax_rate", "status",
                    "lot_controlled", "shelf_life_controlled", "inspection_required", "serial_controlled",
                    "remark"
            ),
            ImportConstants.CUSTOMER, List.of("customer_code", "customer_name", "customer_type", "contact_name", "contact_phone", "email", "settlement_method", "credit_limit", "credit_period", "address", "status", "remark"),
            ImportConstants.SUPPLIER, List.of("supplier_code", "supplier_name", "contact_name", "contact_phone", "email", "settlement_method", "credit_period", "address", "status", "remark"),
            ImportConstants.WAREHOUSE, List.of("warehouse_code", "warehouse_name", "dept_id", "manager_user_id", "address", "status", "remark"),
            ImportConstants.LOCATION, List.of("warehouse_code", "location_code", "location_name", "is_default", "status", "remark"),
            ImportConstants.OPENING_INVENTORY, List.of("warehouse_code", "product_code", "location_code", "qty_on_hand", "amount_on_hand", "opening_date", "lot_no", "production_date", "expiry_date", "remark"),
            ImportConstants.OPENING_RECEIVABLE, List.of("customer_code", "receivable_no", "biz_date", "original_amount", "settled_amount", "remark"),
            ImportConstants.OPENING_PAYABLE, List.of("supplier_code", "payable_no", "biz_date", "original_amount", "settled_amount", "remark"),
            ImportConstants.OPENING_ACCOUNT_BALANCE, List.of("subject_code", "biz_date", "debit_amount", "credit_amount", "summary")
    );

    private static final Map<String, List<String>> SAMPLES = Map.of(
            ImportConstants.PRODUCT, List.of(
                    "P001", "标准商品", "STANDARD", "默认分类", "规格A", "件", "箱", "12", "6901234567890",
                    "10.00", "15.00", "13.00", "ACTIVE", "0", "0", "0", "0", "商品期初导入示例"
            ),
            ImportConstants.CUSTOMER, List.of("C001", "示例客户", "COMPANY", "张三", "13800000000", "customer@example.com", "MONTH_END", "0", "30", "北京市", "ACTIVE", "客户期初导入示例"),
            ImportConstants.SUPPLIER, List.of("S001", "示例供应商", "李四", "13900000000", "supplier@example.com", "MONTH_END", "30", "上海市", "ACTIVE", "供应商期初导入示例"),
            ImportConstants.WAREHOUSE, List.of("W001", "主仓库", "1", "1", "北京市", "ACTIVE", "仓库期初导入示例"),
            ImportConstants.LOCATION, List.of("W001", "A-01", "A区01货架", "0", "ACTIVE", "库位导入示例"),
            ImportConstants.OPENING_INVENTORY, List.of("W001", "P001", "MAIN", "100.0000", "1000.00", "2026-01-01", "LOT-001", "2026-01-01", "2026-12-31", "期初库存示例"),
            ImportConstants.OPENING_RECEIVABLE, List.of("C001", "AR-OPEN-001", "2026-01-01", "500.00", "0", "期初应收示例"),
            ImportConstants.OPENING_PAYABLE, List.of("S001", "AP-OPEN-001", "2026-01-01", "800.00", "0", "期初应付示例"),
            ImportConstants.OPENING_ACCOUNT_BALANCE, List.of("1001", "2026-01-01", "1000.00", "0", "期初科目余额示例")
    );

    public List<String> headers(String importType) {
        List<String> headers = HEADERS.get(importType);
        if (headers == null) {
            throw new IllegalArgumentException("不支持的导入类型: " + importType);
        }
        return headers;
    }

    public String csvTemplate(String importType) {
        List<String> headers = headers(importType);
        List<String> sample = SAMPLES.get(importType);
        if (sample == null) {
            throw new IllegalArgumentException("不支持的导入类型: " + importType);
        }
        return String.join(",", headers) + "\n" + String.join(",", sample) + "\n";
    }

    public Set<String> supportedTypes() {
        return HEADERS.keySet();
    }
}

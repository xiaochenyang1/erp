-- =====================================================
-- ERP系统测试数据初始化脚本
-- 版本: v1.0
-- 日期: 2026-06-16
-- 说明: 用于快速搭建测试环境
-- =====================================================

-- 设置字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. 基础数据：公司、账套、用户
-- =====================================================

-- 插入测试公司
INSERT INTO sys_company (id, company_code, company_name, status, created_by, created_time, updated_by, updated_time, version)
VALUES
(1, 'TEST001', '测试公司', 'ACTIVE', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE company_name = '测试公司';

-- 插入测试账套
INSERT INTO sys_account_book (id, company_id, book_code, book_name, fiscal_year, status, created_by, created_time, updated_by, updated_time, version)
VALUES
(1, 1, 'BOOK2026', '2026年账套', 2026, 'ACTIVE', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE book_name = '2026年账套';

-- 插入测试用户
INSERT INTO sys_user (id, username, password, real_name, email, mobile, status, created_by, created_time, updated_by, updated_time, version)
VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', 'admin@test.com', '13800138000', 'ACTIVE', 1, NOW(), 1, NOW(), 0),
(100, 'testuser', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '测试用户', 'test@test.com', '13800138001', 'ACTIVE', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name);

-- =====================================================
-- 2. 主数据：产品、仓库、供应商、客户
-- =====================================================

-- 插入测试产品
INSERT INTO md_product (id, company_id, product_code, product_name, specification, unit, category, status, created_by, created_time, updated_by, updated_time, version)
VALUES
(1, 1, 'P001', '测试产品A', '规格A', '个', '原材料', 'ACTIVE', 1, NOW(), 1, NOW(), 0),
(2, 1, 'P002', '测试产品B', '规格B', '个', '原材料', 'ACTIVE', 1, NOW(), 1, NOW(), 0),
(10, 1, 'P010', '测试成品A', '成品规格', '个', '成品', 'ACTIVE', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE product_name = VALUES(product_name);

-- 插入测试仓库
INSERT INTO md_warehouse (id, company_id, warehouse_code, warehouse_name, warehouse_type, status, created_by, created_time, updated_by, updated_time, version)
VALUES
(1, 1, 'WH001', '主仓库', 'NORMAL', 'ACTIVE', 1, NOW(), 1, NOW(), 0),
(2, 1, 'WH002', '分仓库', 'NORMAL', 'ACTIVE', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE warehouse_name = VALUES(warehouse_name);

-- 插入测试供应商
INSERT INTO md_supplier (id, company_id, supplier_code, supplier_name, contact_person, contact_phone, address, status, created_by, created_time, updated_by, updated_time, version)
VALUES
(1, 1, 'SUP001', 'ABC供应商', '张三', '13900139000', '测试地址1', 'ACTIVE', 1, NOW(), 1, NOW(), 0),
(2, 1, 'SUP002', 'XYZ供应商', '李四', '13900139001', '测试地址2', 'ACTIVE', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE supplier_name = VALUES(supplier_name);

-- 插入测试客户
INSERT INTO md_customer (id, company_id, customer_code, customer_name, contact_person, contact_phone, address, status, created_by, created_time, updated_by, updated_time, version)
VALUES
(1, 1, 'CUS001', '测试客户A', '王五', '13900139002', '客户地址1', 'ACTIVE', 1, NOW(), 1, NOW(), 0),
(2, 1, 'CUS002', '测试客户B', '赵六', '13900139003', '客户地址2', 'ACTIVE', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE customer_name = VALUES(customer_name);

-- =====================================================
-- 3. 财务数据：会计科目
-- =====================================================

-- 插入测试会计科目
INSERT INTO fin_account_subject (id, company_id, account_book_id, subject_code, subject_name, subject_type, parent_id, level_num, is_leaf, status, created_by, created_time, updated_by, updated_time, version)
VALUES
(100, 1, 1, '1001', '库存现金', 'ASSET', NULL, 1, 1, 'ACTIVE', 1, NOW(), 1, NOW(), 0),
(101, 1, 1, '1002', '银行存款', 'ASSET', NULL, 1, 1, 'ACTIVE', 1, NOW(), 1, NOW(), 0),
(200, 1, 1, '5001', '生产成本', 'COST', NULL, 1, 1, 'ACTIVE', 1, NOW(), 1, NOW(), 0),
(201, 1, 1, '6001', '管理费用', 'EXPENSE', NULL, 1, 1, 'ACTIVE', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE subject_name = VALUES(subject_name);

-- =====================================================
-- 4. 业务数据：示例订单（用于测试）
-- =====================================================

-- 插入测试采购订单
INSERT INTO pur_purchase_order (id, company_id, account_book_id, order_no, supplier_id, order_date, expected_date, total_amount, total_tax_amount, status, remark, created_by, created_time, updated_by, updated_time, version, deleted_flag)
VALUES
(1, 1, 1, 'PO20260616001', 1, '2026-06-16', '2026-06-30', 10000.00, 1300.00, 'APPROVED', '测试采购订单1', 1, NOW(), 1, NOW(), 0, 0),
(2, 1, 1, 'PO20260616002', 2, '2026-06-16', '2026-07-01', 15000.00, 1950.00, 'PENDING', '测试采购订单2', 1, NOW(), 1, NOW(), 0, 0)
ON DUPLICATE KEY UPDATE order_no = VALUES(order_no);

-- 插入采购订单明细
INSERT INTO pur_purchase_order_line (id, company_id, account_book_id, order_id, line_no, product_id, qty, price, tax_rate, amount, tax_amount, received_qty, remark, created_by, created_time, updated_by, updated_time, version)
VALUES
(1, 1, 1, 1, 1, 1, 100, 100.00, 0.13, 10000.00, 1300.00, 0, '采购产品A', 1, NOW(), 1, NOW(), 0),
(2, 1, 1, 2, 1, 2, 150, 100.00, 0.13, 15000.00, 1950.00, 0, '采购产品B', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE qty = VALUES(qty);

-- 插入测试生产订单
INSERT INTO prd_production_order (id, company_id, account_book_id, order_no, product_id, bom_id, plan_quantity, warehouse_id, plan_start_date, plan_end_date, priority, status, remark, created_by, created_time, updated_by, updated_time, version, deleted_flag)
VALUES
(1, 1, 1, 'MO20260616001', 10, 1, 100, 1, '2026-06-17', '2026-06-20', 'NORMAL', 'RELEASED', '测试生产订单1', 1, NOW(), 1, NOW(), 0, 0)
ON DUPLICATE KEY UPDATE order_no = VALUES(order_no);

-- 插入测试库存余额（初始库存）
INSERT INTO inv_stock_balance (id, company_id, account_book_id, warehouse_id, product_id, lot_no, quantity, unit_cost, created_by, created_time, updated_by, updated_time, version)
VALUES
(1, 1, 1, 1, 1, NULL, 500, 10.50, 1, NOW(), 1, NOW(), 0),
(2, 1, 1, 1, 2, NULL, 300, 12.00, 1, NOW(), 1, NOW(), 0),
(3, 1, 1, 2, 1, NULL, 200, 10.50, 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

-- =====================================================
-- 5. 权限数据：角色和权限
-- =====================================================

-- 插入测试角色
INSERT INTO sys_role (id, role_code, role_name, description, status, created_by, created_time, updated_by, updated_time, version)
VALUES
(1, 'ADMIN', '系统管理员', '拥有所有权限', 'ACTIVE', 1, NOW(), 1, NOW(), 0),
(2, 'USER', '普通用户', '基本权限', 'ACTIVE', 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- 用户角色关联
INSERT INTO sys_user_role (user_id, role_id, created_by, created_time)
VALUES
(1, 1, 1, NOW()),
(100, 2, 1, NOW())
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id);

-- =====================================================
-- 数据统计
-- =====================================================

SELECT '测试数据初始化完成！' AS message;

SELECT
    '公司' AS data_type,
    COUNT(*) AS count
FROM sys_company WHERE id = 1

UNION ALL

SELECT
    '用户',
    COUNT(*)
FROM sys_user WHERE id IN (1, 100)

UNION ALL

SELECT
    '产品',
    COUNT(*)
FROM md_product WHERE company_id = 1

UNION ALL

SELECT
    '仓库',
    COUNT(*)
FROM md_warehouse WHERE company_id = 1

UNION ALL

SELECT
    '供应商',
    COUNT(*)
FROM md_supplier WHERE company_id = 1

UNION ALL

SELECT
    '采购订单',
    COUNT(*)
FROM pur_purchase_order WHERE company_id = 1

UNION ALL

SELECT
    '生产订单',
    COUNT(*)
FROM prd_production_order WHERE company_id = 1

UNION ALL

SELECT
    '库存余额',
    COUNT(*)
FROM inv_stock_balance WHERE company_id = 1;

-- =====================================================
-- 使用说明
-- =====================================================

/*
使用方法：

1. 执行脚本：
   mysql -u root -p erp_db < test-data-init.sql

2. 测试账号：
   用户名: admin
   密码: password (需要BCrypt加密)

   用户名: testuser
   密码: password (需要BCrypt加密)

3. 测试数据说明：
   - 公司ID: 1
   - 账套ID: 1
   - 仓库: 主仓库(1), 分仓库(2)
   - 产品: 产品A(1), 产品B(2), 成品A(10)
   - 供应商: ABC供应商(1), XYZ供应商(2)
   - 采购订单: PO20260616001, PO20260616002
   - 生产订单: MO20260616001

4. 清理测试数据：
   执行 test-data-cleanup.sql

注意：
- 本脚本使用 ON DUPLICATE KEY UPDATE，可以重复执行
- 密码字段需要使用BCrypt加密，示例密码为占位符
- 实际使用时请修改密码和敏感信息
*/

SET FOREIGN_KEY_CHECKS = 1;

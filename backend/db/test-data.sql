-- =============================================
-- ERP测试数据脚本
-- 创建日期: 2026-06-12
-- 说明: 插入测试数据，用于开发和演示
-- =============================================

-- 清空现有测试数据
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE sys_user_role;
DELETE FROM sys_user WHERE id > 0;
DELETE FROM sys_role WHERE id > 0;
DELETE FROM md_warehouse WHERE id > 0;
DELETE FROM md_product WHERE id > 0;
DELETE FROM md_customer WHERE id > 0;
DELETE FROM md_supplier WHERE id > 0;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================
-- 1. 系统用户数据
-- =============================================
-- 密码都是: 123456 (BCrypt加密后的值)
INSERT INTO sys_user (id, company_id, account_book_id, username, password, real_name, mobile, status, created_by, created_time, updated_by, updated_time)
VALUES
(1, 1, 1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '13800138000', 'ACTIVE', 0, NOW(), 0, NOW()),
(2, 1, 1, 'zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张三', '13800138001', 'ACTIVE', 1, NOW(), 1, NOW()),
(3, 1, 1, 'lisi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李四', '13800138002', 'ACTIVE', 1, NOW(), 1, NOW()),
(4, 1, 1, 'wangwu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王五', '13800138003', 'ACTIVE', 1, NOW(), 1, NOW()),
(5, 1, 1, 'zhaoliu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵六', '13800138004', 'ACTIVE', 1, NOW(), 1, NOW());

-- =============================================
-- 2. 角色数据
-- =============================================
INSERT INTO sys_role (id, company_id, account_book_id, role_code, role_name, remark, status, created_by, created_time, updated_by, updated_time)
VALUES
(1, 1, 1, 'SUPER_ADMIN', '超级管理员', '拥有所有权限', 'ACTIVE', 1, NOW(), 1, NOW()),
(2, 1, 1, 'WAREHOUSE_MANAGER', '仓库管理员', '管理库存和出入库', 'ACTIVE', 1, NOW(), 1, NOW()),
(3, 1, 1, 'SALES_MANAGER', '销售经理', '管理销售订单和客户', 'ACTIVE', 1, NOW(), 1, NOW()),
(4, 1, 1, 'PURCHASE_MANAGER', '采购经理', '管理采购订单和供应商', 'ACTIVE', 1, NOW(), 1, NOW()),
(5, 1, 1, 'FINANCE_MANAGER', '财务经理', '管理财务数据', 'ACTIVE', 1, NOW(), 1, NOW());

-- =============================================
-- 3. 用户角色关联
-- =============================================
INSERT INTO sys_user_role (id, user_id, role_id)
VALUES
(1, 1, 1),  -- admin -> 超级管理员
(2, 2, 2),  -- zhangsan -> 仓库管理员
(3, 3, 3),  -- lisi -> 销售经理
(4, 4, 4),  -- wangwu -> 采购经理
(5, 5, 5);  -- zhaoliu -> 财务经理

-- =============================================
-- 4. 仓库数据
-- =============================================
INSERT INTO md_warehouse (id, company_id, account_book_id, warehouse_code, warehouse_name, dept_id, manager_user_id, address, status, created_by, created_time, updated_by, updated_time)
VALUES
(1, 1, 1, 'WH001', '主仓库', 1, 2, '北京市朝阳区xxx路xxx号', 'ACTIVE', 1, NOW(), 1, NOW()),
(2, 1, 1, 'WH002', '分仓库A', 1, 2, '上海市浦东新区xxx路xxx号', 'ACTIVE', 1, NOW(), 1, NOW()),
(3, 1, 1, 'WH003', '分仓库B', 1, 2, '广州市天河区xxx路xxx号', 'ACTIVE', 1, NOW(), 1, NOW());

-- =============================================
-- 5. 产品数据
-- =============================================
INSERT INTO md_product (id, company_id, account_book_id, product_code, product_name, product_type, category_name, specification, unit_name, purchase_price, sale_price, tax_rate, status, created_by, created_time, updated_by, updated_time)
VALUES
(1, 1, 1, 'PROD001', 'iPhone 15 Pro', 'FINISHED', '手机', '256GB 深空黑', '台', 7999.00, 8999.00, 0.13, 'ACTIVE', 1, NOW(), 1, NOW()),
(2, 1, 1, 'PROD002', 'MacBook Pro', 'FINISHED', '电脑', '14寸 M3芯片', '台', 14999.00, 16999.00, 0.13, 'ACTIVE', 1, NOW(), 1, NOW()),
(3, 1, 1, 'PROD003', 'AirPods Pro', 'FINISHED', '耳机', '第二代', '副', 1699.00, 1999.00, 0.13, 'ACTIVE', 1, NOW(), 1, NOW()),
(4, 1, 1, 'PROD004', 'iPad Air', 'FINISHED', '平板', '10.9寸 64GB', '台', 4399.00, 4999.00, 0.13, 'ACTIVE', 1, NOW(), 1, NOW()),
(5, 1, 1, 'PROD005', 'Apple Watch', 'FINISHED', '手表', 'Series 9 GPS', '块', 2699.00, 2999.00, 0.13, 'ACTIVE', 1, NOW(), 1, NOW()),
(6, 1, 1, 'PROD006', '小米13 Ultra', 'FINISHED', '手机', '12GB+256GB', '台', 4999.00, 5999.00, 0.13, 'ACTIVE', 1, NOW(), 1, NOW()),
(7, 1, 1, 'PROD007', '华为Mate 60 Pro', 'FINISHED', '手机', '12GB+512GB', '台', 6499.00, 6999.00, 0.13, 'ACTIVE', 1, NOW(), 1, NOW()),
(8, 1, 1, 'PROD008', '戴尔XPS 13', 'FINISHED', '电脑', 'i7 16GB 512GB', '台', 8999.00, 9999.00, 0.13, 'ACTIVE', 1, NOW(), 1, NOW()),
(9, 1, 1, 'PROD009', '索尼WH-1000XM5', 'FINISHED', '耳机', '降噪耳机', '副', 2299.00, 2699.00, 0.13, 'ACTIVE', 1, NOW(), 1, NOW()),
(10, 1, 1, 'PROD010', '罗技MX Master 3S', 'FINISHED', '配件', '无线鼠标', '个', 699.00, 899.00, 0.13, 'ACTIVE', 1, NOW(), 1, NOW());

-- =============================================
-- 6. 客户数据
-- =============================================
INSERT INTO md_customer (id, company_id, account_book_id, customer_code, customer_name, contact_name, contact_phone, settlement_method, credit_limit, address, status, created_by, created_time, updated_by, updated_time)
VALUES
(1, 1, 1, 'CUST001', '北京科技有限公司', '李明', '13900139000', 'MONTHLY', 1000000.00, '北京市海淀区中关村大街1号', 'ACTIVE', 1, NOW(), 1, NOW()),
(2, 1, 1, 'CUST002', '上海贸易有限公司', '王芳', '13900139001', 'MONTHLY', 800000.00, '上海市黄浦区南京东路100号', 'ACTIVE', 1, NOW(), 1, NOW()),
(3, 1, 1, 'CUST003', '广州电子有限公司', '张伟', '13900139002', 'MONTHLY', 500000.00, '广州市天河区体育西路200号', 'ACTIVE', 1, NOW(), 1, NOW()),
(4, 1, 1, 'CUST004', '深圳创新有限公司', '刘强', '13900139003', 'MONTHLY', 1200000.00, '深圳市南山区科技园300号', 'ACTIVE', 1, NOW(), 1, NOW()),
(5, 1, 1, 'CUST005', '杭州网络有限公司', '陈静', '13900139004', 'MONTHLY', 600000.00, '杭州市西湖区文三路400号', 'ACTIVE', 1, NOW(), 1, NOW());

-- =============================================
-- 7. 供应商数据
-- =============================================
INSERT INTO md_supplier (id, company_id, account_book_id, supplier_code, supplier_name, contact_name, contact_phone, settlement_method, address, status, created_by, created_time, updated_by, updated_time)
VALUES
(1, 1, 1, 'SUPP001', '苹果电子产品供应商', '张经理', '13800139000', 'MONTHLY', '深圳市福田区华强北路1号', 'ACTIVE', 1, NOW(), 1, NOW()),
(2, 1, 1, 'SUPP002', '小米产品代理商', '李经理', '13800139001', 'MONTHLY', '北京市海淀区小米科技园', 'ACTIVE', 1, NOW(), 1, NOW()),
(3, 1, 1, 'SUPP003', '华为产品经销商', '王经理', '13800139002', 'MONTHLY', '深圳市龙岗区华为基地', 'ACTIVE', 1, NOW(), 1, NOW()),
(4, 1, 1, 'SUPP004', '戴尔电脑总代理', '赵经理', '13800139003', 'MONTHLY', '上海市浦东新区张江高科', 'ACTIVE', 1, NOW(), 1, NOW()),
(5, 1, 1, 'SUPP005', '配件综合供应商', '孙经理', '13800139004', 'MONTHLY', '广州市番禺区电子城', 'ACTIVE', 1, NOW(), 1, NOW());

-- =============================================
-- 完成
-- =============================================
SELECT '✅ 测试数据插入完成！' AS message;
SELECT '👤 用户: admin, zhangsan, lisi, wangwu, zhaoliu' AS users;
SELECT '🔑 密码: 123456' AS password;
SELECT '📦 产品: 10个' AS products;
SELECT '👥 客户: 5个' AS customers;
SELECT '🏭 供应商: 5个' AS suppliers;
SELECT '🏢 仓库: 3个' AS warehouses;

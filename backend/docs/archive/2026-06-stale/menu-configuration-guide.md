# 系统菜单配置指南

**文档版本**: v1.0  
**更新日期**: 2026-06-16  
**适用系统**: ERP管理系统

---

## 📋 目录

1. [菜单配置概述](#菜单配置概述)
2. [数据库表结构](#数据库表结构)
3. [SQL脚本使用](#sql脚本使用)
4. [菜单结构说明](#菜单结构说明)
5. [权限码对照表](#权限码对照表)
6. [常见问题](#常见问题)

---

## 📖 菜单配置概述

### 配置目的
- ✅ 让前端所有页面在系统菜单中可见
- ✅ 建立完整的系统导航结构
- ✅ 配置菜单与权限的关联关系
- ✅ 支持角色基于菜单的权限控制

### 菜单层级
```
系统共3级菜单结构：
├─ 一级菜单（9个）：首页、主数据、采购、销售、库存、财务、生产、系统、报表
├─ 二级菜单（36个）：各模块下的具体功能页面
└─ 三级菜单（可选）：按钮级权限（通过menu_type='BUTTON'实现）
```

### 菜单总数
- **一级菜单**: 9个
- **二级菜单**: 36个
- **合计**: 45个菜单项

---

## 🗄️ 数据库表结构

### 菜单表（sys_menu）

假设的表结构（根据实际情况调整）：

```sql
CREATE TABLE sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT COMMENT '父菜单ID，NULL表示一级菜单',
    name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    path VARCHAR(200) COMMENT '路由路径',
    component VARCHAR(200) COMMENT '组件路径',
    icon VARCHAR(50) COMMENT '菜单图标',
    order_num INT DEFAULT 0 COMMENT '排序号',
    menu_type VARCHAR(20) DEFAULT 'MENU' COMMENT '菜单类型：MENU-菜单，BUTTON-按钮',
    permission VARCHAR(100) COMMENT '权限标识',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用，INACTIVE-停用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_parent_id (parent_id),
    INDEX idx_status (status)
) COMMENT='系统菜单表';
```

### 字段说明

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| id | BIGINT | 主键ID | 1, 2, 3... |
| parent_id | BIGINT | 父菜单ID | NULL（一级）, 6（二级） |
| name | VARCHAR(50) | 菜单名称 | "财务管理" |
| path | VARCHAR(200) | 前端路由路径 | "/finance/subjects" |
| component | VARCHAR(200) | 前端组件路径 | "finance/subjects/index" |
| icon | VARCHAR(50) | Element Plus图标名 | "Money", "Document" |
| order_num | INT | 排序号（越小越靠前） | 1, 2, 3... |
| menu_type | VARCHAR(20) | 菜单类型 | "MENU" 或 "BUTTON" |
| permission | VARCHAR(100) | 权限标识 | "finance:subject:view" |
| status | VARCHAR(20) | 状态 | "ACTIVE" 或 "INACTIVE" |

---

## 🚀 SQL脚本使用

### 方法1：使用MySQL客户端

```bash
# 1. 连接到数据库
mysql -u root -p your_database_name

# 2. 执行SQL脚本
source /path/to/init-menu-data.sql;

# 或者直接执行
mysql -u root -p your_database_name < init-menu-data.sql
```

### 方法2：使用数据库管理工具

**推荐工具**：
- MySQL Workbench
- DBeaver
- Navicat
- phpMyAdmin

**操作步骤**：
1. 打开工具连接到数据库
2. 新建查询窗口
3. 复制 `init-menu-data.sql` 内容
4. 执行SQL脚本

### 方法3：通过后端Flyway迁移

```sql
-- 创建文件：src/main/resources/db/migration/V999__init_menu_data.sql
-- 将 init-menu-data.sql 内容复制到该文件
-- 启动应用，Flyway会自动执行
```

### 执行前检查

```sql
-- 1. 检查表是否存在
SHOW TABLES LIKE 'sys_menu';

-- 2. 检查表结构
DESC sys_menu;

-- 3. 检查现有菜单数量
SELECT COUNT(*) FROM sys_menu;

-- 4. 备份现有数据（重要！）
CREATE TABLE sys_menu_backup AS SELECT * FROM sys_menu;
```

---

## 🗂️ 菜单结构说明

### 完整菜单树

```
ERP系统
├─ 🏠 首页 (/dashboard)
├─ 📁 主数据管理 (/masterdata)
│   ├─ 📦 产品管理 (/masterdata/products)
│   ├─ 👤 客户管理 (/masterdata/customers)
│   ├─ 🏢 供应商管理 (/masterdata/suppliers)
│   └─ 🏠 仓库管理 (/masterdata/warehouses)
├─ 🛒 采购管理 (/purchase)
│   ├─ 📄 采购订单 (/purchase/orders)
│   ├─ 📦 采购收货 (/purchase/receipts)
│   └─ ↩️ 采购退货 (/purchase/returns)
├─ 💰 销售管理 (/sales)
│   ├─ 📄 销售订单 (/sales/orders)
│   ├─ 📦 销售发货 (/sales/deliveries)
│   └─ ↩️ 销售退货 (/sales/returns)
├─ 📦 库存管理 (/inventory)
│   ├─ 📋 库存查询 (/inventory/stocks)
│   ├─ ✏️ 库存调整 (/inventory/adjustments)
│   ├─ 📄 库存盘点 (/inventory/checks)
│   ├─ 🔄 库存调拨 (/inventory/transfers)
│   └─ ⚠️ 库存预警 (/inventory/alerts)
├─ 💵 财务管理 (/finance)
│   ├─ 📓 会计科目 (/finance/subjects) ← 新增
│   ├─ 🎫 凭证管理 (/finance/vouchers)
│   ├─ 📊 总账查询 (/finance/ledger) ← 新增
│   ├─ 📄 应收账款 (/finance/receivables)
│   ├─ 📄 应付账款 (/finance/payables)
│   ├─ 💵 收付款管理 (/finance/payments)
│   └─ 💼 费用管理 (/finance/expenses) ← 新增
├─ 🏭 生产管理 (/production)
│   ├─ 📋 BOM管理 (/production/boms) ← 新增
│   └─ 📄 生产订单 (/production/orders) ← 新增
├─ ⚙️ 系统管理 (/system)
│   ├─ 👤 用户管理 (/system/users)
│   ├─ 👥 角色管理 (/system/roles)
│   ├─ 📋 菜单管理 (/system/menus)
│   ├─ 🏢 部门管理 (/system/depts)
│   ├─ 💼 岗位管理 (/system/posts) ← 新增
│   ├─ 📚 字典管理 (/system/dicts) ← 新增
│   ├─ 🔧 系统配置 (/system/configs) ← 新增
│   └─ 📝 操作日志 (/system/logs) ← 新增
└─ 📊 报表中心 (/reports)
```

### 本次新增菜单（9个）

| 序号 | 菜单名称 | 路径 | 所属模块 |
|------|---------|------|----------|
| 1 | 会计科目 | /finance/subjects | 财务管理 |
| 2 | 总账查询 | /finance/ledger | 财务管理 |
| 3 | 费用管理 | /finance/expenses | 财务管理 |
| 4 | BOM管理 | /production/boms | 生产管理 |
| 5 | 生产订单 | /production/orders | 生产管理 |
| 6 | 岗位管理 | /system/posts | 系统管理 |
| 7 | 字典管理 | /system/dicts | 系统管理 |
| 8 | 系统配置 | /system/configs | 系统管理 |
| 9 | 操作日志 | /system/logs | 系统管理 |

---

## 🔐 权限码对照表

### 权限命名规范
```
格式：{模块}:{功能}:{操作}
示例：finance:subject:view
```

### 完整权限码清单

#### 主数据管理
| 菜单 | 权限码 |
|------|--------|
| 产品管理 | product:view |
| 客户管理 | customer:view |
| 供应商管理 | supplier:view |
| 仓库管理 | warehouse:view |

#### 采购管理
| 菜单 | 权限码 |
|------|--------|
| 采购订单 | purchase:order:view |
| 采购收货 | purchase:receipt:view |
| 采购退货 | purchase:return:view |

#### 销售管理
| 菜单 | 权限码 |
|------|--------|
| 销售订单 | sales:order:view |
| 销售发货 | sales:delivery:view |
| 销售退货 | sales:return:view |

#### 库存管理
| 菜单 | 权限码 |
|------|--------|
| 库存查询 | inventory:stock:view |
| 库存调整 | inventory:adjust:view |
| 库存盘点 | inventory:check:view |
| 库存调拨 | inventory:transfer:view |
| 库存预警 | inventory:alert:view |

#### 财务管理（包含新增）
| 菜单 | 权限码 |
|------|--------|
| 会计科目 ⭐ | finance:subject:view |
| 凭证管理 | finance:voucher:view |
| 总账查询 ⭐ | finance:ledger:view |
| 应收账款 | finance:receivable:view |
| 应付账款 | finance:payable:view |
| 收付款管理 | finance:payment:view |
| 费用管理 ⭐ | finance:expense:view |

#### 生产管理（新增模块）
| 菜单 | 权限码 |
|------|--------|
| BOM管理 ⭐ | production:bom:view |
| 生产订单 ⭐ | production:order:view |

#### 系统管理（包含新增）
| 菜单 | 权限码 |
|------|--------|
| 用户管理 | system:user:view |
| 角色管理 | system:role:view |
| 菜单管理 | system:menu:view |
| 部门管理 | system:dept:view |
| 岗位管理 ⭐ | system:post:view |
| 字典管理 ⭐ | system:dict:view |
| 系统配置 ⭐ | system:config:view |
| 操作日志 ⭐ | system:log:view |

---

## ✅ 验证步骤

### 1. 验证SQL执行结果

```sql
-- 检查菜单总数（应该是45个）
SELECT COUNT(*) as total FROM sys_menu;

-- 检查一级菜单（应该是9个）
SELECT id, name, path, order_num
FROM sys_menu
WHERE parent_id IS NULL
ORDER BY order_num;

-- 检查财务模块菜单（应该是7个）
SELECT id, name, path
FROM sys_menu
WHERE parent_id = 6
ORDER BY order_num;

-- 检查生产模块菜单（应该是2个）
SELECT id, name, path
FROM sys_menu
WHERE parent_id = 7
ORDER BY order_num;

-- 检查系统管理菜单（应该是8个）
SELECT id, name, path
FROM sys_menu
WHERE parent_id = 8
ORDER BY order_num;
```

### 2. 验证前端显示

**步骤**：
1. 启动后端服务
2. 启动前端服务
3. 登录系统
4. 检查侧边栏菜单是否完整显示

**预期结果**：
- ✅ 侧边栏显示9个一级菜单
- ✅ 点击展开可以看到各模块下的二级菜单
- ✅ 新增的9个菜单都能看到
- ✅ 点击菜单可以正常跳转到对应页面

### 3. 验证权限控制

```sql
-- 给管理员角色分配所有菜单权限
-- 假设管理员角色ID为1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 或者使用角色权限码
UPDATE sys_role
SET permissions = 'dashboard:view,product:view,customer:view,supplier:view,warehouse:view,purchase:order:view,purchase:receipt:view,purchase:return:view,sales:order:view,sales:delivery:view,sales:return:view,inventory:stock:view,inventory:adjust:view,inventory:check:view,inventory:transfer:view,inventory:alert:view,finance:subject:view,finance:voucher:view,finance:ledger:view,finance:receivable:view,finance:payable:view,finance:payment:view,finance:expense:view,production:bom:view,production:order:view,system:user:view,system:role:view,system:menu:view,system:dept:view,system:post:view,system:dict:view,system:config:view,system:log:view,report:view'
WHERE id = 1;
```

---

## ❓ 常见问题

### Q1: 执行SQL时报错"表不存在"
**A**: 检查表名是否正确，可能是 `sys_menu` 或 `system_menu`，根据实际情况调整SQL脚本。

### Q2: 菜单插入后前端看不到
**A**: 可能原因：
1. 前端缓存问题，清除浏览器缓存重新登录
2. 用户角色没有对应菜单权限
3. 前端菜单组件没有正确读取后端数据

### Q3: 菜单ID冲突
**A**: 如果数据库中已有菜单数据，调整SQL中的ID值，确保不冲突。

### Q4: 图标不显示
**A**: 检查图标名称是否与Element Plus图标库匹配，参考：https://element-plus.org/zh-CN/component/icon.html

### Q5: 菜单顺序不对
**A**: 调整 `order_num` 字段值，数字越小越靠前。

### Q6: 如何添加按钮级权限
**A**: 在对应菜单下插入 `menu_type='BUTTON'` 的记录，示例：
```sql
INSERT INTO sys_menu (parent_id, name, permission, menu_type, status)
VALUES (601, '新增科目', 'finance:subject:create', 'BUTTON', 'ACTIVE');
```

---

## 🔧 自定义配置

### 修改菜单图标

```sql
-- 更新图标
UPDATE sys_menu SET icon = 'NewIcon' WHERE id = 601;
```

### 调整菜单顺序

```sql
-- 调整排序
UPDATE sys_menu SET order_num = 10 WHERE id = 607;
```

### 禁用某个菜单

```sql
-- 停用菜单
UPDATE sys_menu SET status = 'INACTIVE' WHERE id = 607;
```

### 批量修改权限码

```sql
-- 统一权限码格式
UPDATE sys_menu
SET permission = CONCAT('finance:', LOWER(REPLACE(name, '管理', '')), ':view')
WHERE parent_id = 6;
```

---

## 📞 技术支持

如有问题，请联系：
- 📧 技术支持邮箱
- 💬 内部技术群
- 📚 查看更多文档

---

**文档版本**: v1.0  
**最后更新**: 2026-06-16  
**维护人**: 系统管理员

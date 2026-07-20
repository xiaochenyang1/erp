# 菜单配置快速执行指南

**⏱️ 预计时间**: 5-10分钟  
**📅 日期**: 2026-06-16

---

## 🎯 目标

让所有新开发的页面在系统菜单中可见，用户可以通过侧边栏导航访问。

---

## 📋 执行清单

### ✅ 准备工作

- [ ] 确认后端服务正在运行
- [ ] 确认数据库连接正常
- [ ] 备份现有菜单数据（如果有）

### ✅ 执行步骤

#### 步骤1：检查数据库表

```bash
# 进入后端项目目录
cd /e/tuowei/python/erpServer

# 连接数据库（根据实际情况调整）
mysql -u root -p erp_database
```

```sql
-- 检查表是否存在
SHOW TABLES LIKE 'sys_menu';

-- 查看表结构
DESC sys_menu;

-- 查看现有数据
SELECT COUNT(*) FROM sys_menu;
```

#### 步骤2：备份现有数据（可选但推荐）

```sql
-- 创建备份表
CREATE TABLE sys_menu_backup_20260616 AS SELECT * FROM sys_menu;

-- 验证备份
SELECT COUNT(*) FROM sys_menu_backup_20260616;
```

#### 步骤3：执行菜单配置SQL

**方式A：使用命令行**
```bash
mysql -u root -p erp_database < scripts/init-menu-data.sql
```

**方式B：使用MySQL客户端**
```sql
source /e/tuowei/python/erpServer/scripts/init-menu-data.sql;
```

**方式C：复制粘贴**
1. 打开文件 `scripts/init-menu-data.sql`
2. 复制全部内容
3. 在数据库管理工具中执行

#### 步骤4：验证执行结果

```sql
-- 1. 检查菜单总数
SELECT COUNT(*) as total_menus FROM sys_menu;
-- 预期结果：45个（可能更多如果之前有数据）

-- 2. 查看一级菜单
SELECT id, name, path, icon, order_num
FROM sys_menu
WHERE parent_id IS NULL
ORDER BY order_num;
-- 预期结果：9个一级菜单

-- 3. 查看新增的财务菜单
SELECT id, parent_id, name, path
FROM sys_menu
WHERE parent_id = 6
ORDER BY order_num;
-- 预期结果：7个菜单，包括会计科目、总账查询、费用管理

-- 4. 查看生产管理菜单
SELECT id, parent_id, name, path
FROM sys_menu
WHERE parent_id = 7
ORDER BY order_num;
-- 预期结果：2个菜单（BOM管理、生产订单）

-- 5. 查看系统管理菜单
SELECT id, parent_id, name, path
FROM sys_menu
WHERE parent_id = 8
ORDER BY order_num;
-- 预期结果：8个菜单，包括岗位、字典、配置、日志
```

#### 步骤5：配置角色权限（重要！）

```sql
-- 给超级管理员角色分配所有菜单权限
-- 假设管理员角色ID为1，根据实际情况调整

-- 方式A：通过角色菜单关联表
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu
WHERE id NOT IN (
    SELECT menu_id FROM sys_role_menu WHERE role_id = 1
);

-- 方式B：如果使用权限码字符串
-- 需要根据实际的角色表结构调整
UPDATE sys_role
SET permissions = (
    SELECT GROUP_CONCAT(permission SEPARATOR ',')
    FROM sys_menu
    WHERE permission IS NOT NULL
)
WHERE id = 1;
```

#### 步骤6：清除前端缓存

```bash
# 前端项目目录
cd /e/tuowei/python/erp-frontend

# 清除node_modules缓存（可选）
# rm -rf node_modules/.vite

# 重启前端服务
npm run dev
```

#### 步骤7：验证前端显示

1. 打开浏览器访问：http://localhost:5173
2. 登录系统（使用管理员账号）
3. 检查侧边栏菜单

**预期结果**：
```
✅ 首页
✅ 主数据管理
  ✅ 产品管理
  ✅ 客户管理
  ✅ 供应商管理
  ✅ 仓库管理
✅ 采购管理
  ✅ 采购订单
  ✅ 采购收货
  ✅ 采购退货
✅ 销售管理
  ✅ 销售订单
  ✅ 销售发货
  ✅ 销售退货
✅ 库存管理
  ✅ 库存查询
  ✅ 库存调整
  ✅ 库存盘点
  ✅ 库存调拨
  ✅ 库存预警
✅ 财务管理
  ✅ 会计科目 ⭐ 新增
  ✅ 凭证管理
  ✅ 总账查询 ⭐ 新增
  ✅ 应收账款
  ✅ 应付账款
  ✅ 收付款管理
  ✅ 费用管理 ⭐ 新增
✅ 生产管理 ⭐ 新增模块
  ✅ BOM管理 ⭐ 新增
  ✅ 生产订单 ⭐ 新增
✅ 系统管理
  ✅ 用户管理
  ✅ 角色管理
  ✅ 菜单管理
  ✅ 部门管理
  ✅ 岗位管理 ⭐ 新增
  ✅ 字典管理 ⭐ 新增
  ✅ 系统配置 ⭐ 新增
  ✅ 操作日志 ⭐ 新增
✅ 报表中心
```

#### 步骤8：功能测试

依次点击新增的菜单，验证：
- [ ] 会计科目管理页面正常显示
- [ ] 总账查询页面正常显示
- [ ] 费用管理页面正常显示
- [ ] BOM管理页面正常显示
- [ ] 生产订单页面正常显示
- [ ] 岗位管理页面正常显示
- [ ] 字典管理页面正常显示
- [ ] 系统配置页面正常显示
- [ ] 操作日志页面正常显示

---

## 🔧 故障排除

### 问题1：菜单不显示

**检查清单**：
```sql
-- 1. 菜单数据是否插入成功
SELECT * FROM sys_menu WHERE id = 601;

-- 2. 菜单状态是否启用
SELECT id, name, status FROM sys_menu WHERE status != 'ACTIVE';

-- 3. 角色是否有权限
SELECT * FROM sys_role_menu WHERE role_id = 1 AND menu_id = 601;
```

**解决方案**：
```sql
-- 启用菜单
UPDATE sys_menu SET status = 'ACTIVE' WHERE id = 601;

-- 给角色分配权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 601);
```

### 问题2：菜单顺序不对

```sql
-- 调整排序号
UPDATE sys_menu SET order_num = 1 WHERE id = 601;
UPDATE sys_menu SET order_num = 2 WHERE id = 602;
-- ... 依此类推
```

### 问题3：点击菜单无法跳转

**检查**：
- [ ] 路由路径是否正确（检查 `path` 字段）
- [ ] 前端路由配置是否包含该路径
- [ ] 组件路径是否正确（检查 `component` 字段）

**解决**：
```sql
-- 修正路径
UPDATE sys_menu SET path = '/finance/subjects' WHERE id = 601;
UPDATE sys_menu SET component = 'finance/subjects/index' WHERE id = 601;
```

### 问题4：图标不显示

```sql
-- 检查图标名称
SELECT id, name, icon FROM sys_menu WHERE parent_id = 6;

-- 更新为正确的Element Plus图标名
UPDATE sys_menu SET icon = 'Notebook' WHERE id = 601;
```

---

## 📊 完成验证

### 最终检查清单

- [ ] **数据库层面**
  - [ ] 45个菜单记录已插入
  - [ ] 所有菜单状态为ACTIVE
  - [ ] 管理员角色已关联所有菜单

- [ ] **前端显示**
  - [ ] 侧边栏显示9个一级菜单
  - [ ] 展开可以看到所有二级菜单
  - [ ] 新增的9个菜单都可见

- [ ] **功能验证**
  - [ ] 点击菜单可以正常跳转
  - [ ] 页面内容正常显示
  - [ ] 没有404错误

---

## 🎉 完成标志

当你看到以下情况，说明配置成功：

1. ✅ 侧边栏出现"生产管理"模块（新增）
2. ✅ 财务管理下可以看到"会计科目"、"总账查询"、"费用管理"
3. ✅ 系统管理下可以看到"岗位管理"、"字典管理"、"系统配置"、"操作日志"
4. ✅ 点击任何新增菜单，页面正常加载显示

---

## 📝 记录

执行人：________________  
执行日期：____________  
执行时间：____________  
执行结果：□ 成功  □ 失败  
备注：____________________

---

## 📞 需要帮助？

如果遇到问题：
1. 查看详细配置文档：`docs/menu-configuration-guide.md`
2. 检查SQL脚本：`scripts/init-menu-data.sql`
3. 查看前端路由配置：`erp-frontend/src/router/index.ts`

---

**祝配置顺利！** 🎉

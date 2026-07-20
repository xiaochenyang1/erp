# 集成测试指南

**版本**: v1.0  
**日期**: 2026-06-16  
**目的**: 完整的业务流程端到端测试

---

## 📋 测试准备清单

### 环境准备

- [ ] 后端服务已启动（http://localhost:8080）
- [ ] 数据库已初始化（执行 test-data-init.sql）
- [ ] 前端服务已启动（如需前端测试）
- [ ] 测试账号可用（admin / testuser）
- [ ] Postman已导入测试集合

### 数据准备

```sql
-- 验证测试数据是否就绪
SELECT COUNT(*) FROM md_product WHERE company_id = 1;  -- 应该 >= 3
SELECT COUNT(*) FROM md_warehouse WHERE company_id = 1; -- 应该 >= 2
SELECT COUNT(*) FROM md_supplier WHERE company_id = 1;  -- 应该 >= 2
SELECT COUNT(*) FROM inv_stock_balance WHERE company_id = 1; -- 应该 >= 3
```

---

## 🧪 测试场景

### 场景1：库存调整完整流程 ⭐⭐⭐

**业务目标**: 处理盘盈库存

**前置条件**:
- 产品ID: 1（测试产品A）
- 仓库ID: 1（主仓库）
- 初始库存: 500个

**测试步骤**:

#### 步骤1: 创建库存调整单（DRAFT状态）

```bash
POST /api/inventory/adjustments
Authorization: Bearer {token}

{
  "warehouseId": 1,
  "adjustmentDate": "2026-06-16",
  "type": "GAIN",
  "lines": [
    {
      "productId": 1,
      "direction": "IN",
      "qty": 100,
      "unitCost": 10.50,
      "reason": "盘盈"
    }
  ],
  "remark": "集成测试-库存调整"
}
```

**验证点**:
- ✅ HTTP 200
- ✅ 返回adjustmentId和adjustmentNo
- ✅ status = "DRAFT"

**记录**: adjustmentId = _______

---

#### 步骤2: 查询调整单列表 ⭐新增接口

```bash
GET /api/inventory/adjustments?pageNo=1&pageSize=20&status=DRAFT
Authorization: Bearer {token}
```

**验证点**:
- ✅ HTTP 200
- ✅ 列表中包含刚创建的调整单
- ✅ 分页信息正确

---

#### 步骤3: 查询库存余额（调整前）

```bash
GET /api/inventory/balances?warehouseId=1&productId=1
Authorization: Bearer {token}
```

**验证点**:
- ✅ quantity = 500（未变化）

**记录**: 调整前库存 = _______

---

#### 步骤4: 过账调整单

```bash
POST /api/inventory/adjustments/{adjustmentId}/post
Authorization: Bearer {token}
```

**验证点**:
- ✅ HTTP 200
- ✅ status = "POSTED"

---

#### 步骤5: 查询库存余额（调整后）

```bash
GET /api/inventory/balances?warehouseId=1&productId=1
Authorization: Bearer {token}
```

**验证点**:
- ✅ quantity = 600（500 + 100）

**记录**: 调整后库存 = _______

---

#### 步骤6: 尝试取消已过账的调整单 ⭐新增接口

```bash
POST /api/inventory/adjustments/{adjustmentId}/cancel
Authorization: Bearer {token}
```

**验证点**:
- ✅ HTTP 400 或 422
- ✅ 错误信息：「已过账的调整单不能取消」

---

#### 步骤7: 创建并取消草稿调整单

```bash
# 7.1 创建新的调整单
POST /api/inventory/adjustments
{...} # 与步骤1相同

# 7.2 立即取消（不过账）
POST /api/inventory/adjustments/{newAdjustmentId}/cancel
```

**验证点**:
- ✅ 取消成功
- ✅ status = "CANCELLED"
- ✅ 库存未变化

---

**场景1总结**:
- [ ] 所有步骤通过
- [ ] 库存数据正确
- [ ] 新增接口正常工作

---

### 场景2：库存盘点完整流程 ⭐⭐⭐

**业务目标**: 盘点库存并自动调整差异

**前置条件**:
- 产品ID: 1
- 仓库ID: 1
- 账面库存: 600个（场景1调整后）
- 实际盘点: 590个（差异-10）

**测试步骤**:

#### 步骤1: 创建盘点单

```bash
POST /api/inventory/checks

{
  "warehouseId": 1,
  "checkDate": "2026-06-16",
  "lines": [
    {
      "productId": 1,
      "bookQty": 600,
      "actualQty": 590,
      "unitCost": 10.50
    }
  ],
  "remark": "集成测试-库存盘点"
}
```

**验证点**:
- ✅ HTTP 200
- ✅ 返回checkId和checkNo
- ✅ 差异qty = -10

**记录**: checkId = _______

---

#### 步骤2: 查询盘点列表 ⭐新增接口

```bash
GET /api/inventory/checks?pageNo=1&pageSize=20&status=DRAFT
```

**验证点**:
- ✅ 列表中包含刚创建的盘点单

---

#### 步骤3: 生成调整单

```bash
POST /api/inventory/checks/{checkId}/adjust
```

**验证点**:
- ✅ HTTP 200
- ✅ 盘点单status = "ADJUSTED"
- ✅ 自动创建调整单

---

#### 步骤4: 验证库存变化

```bash
GET /api/inventory/balances?warehouseId=1&productId=1
```

**验证点**:
- ✅ quantity = 590（600 - 10）

---

#### 步骤5: 尝试取消已调整的盘点单 ⭐新增接口

```bash
POST /api/inventory/checks/{checkId}/cancel
```

**验证点**:
- ✅ HTTP 400 或 422
- ✅ 错误信息：「已生成调整的盘点单不能取消」

---

**场景2总结**:
- [ ] 盘点流程完整
- [ ] 自动调整正确
- [ ] 新增接口工作正常

---

### 场景3：库存调拨完整流程 ⭐⭐⭐

**业务目标**: 将库存从主仓库调拨到分仓库

**前置条件**:
- 产品ID: 1
- 从仓库ID: 1（主仓库，当前590个）
- 到仓库ID: 2（分仓库，当前200个）
- 调拨数量: 50个

**测试步骤**:

#### 步骤1: 创建调拨单

```bash
POST /api/inventory/transfers

{
  "fromWarehouseId": 1,
  "toWarehouseId": 2,
  "transferDate": "2026-06-16",
  "lines": [
    {
      "productId": 1,
      "qty": 50,
      "unitCost": 10.50
    }
  ],
  "remark": "集成测试-库存调拨"
}
```

**记录**: transferId = _______

---

#### 步骤2: 查询调拨列表 ⭐新增接口

```bash
GET /api/inventory/transfers?pageNo=1&pageSize=20&status=DRAFT
GET /api/inventory/transfers?fromWarehouseId=1
GET /api/inventory/transfers?toWarehouseId=2
```

**验证点**:
- ✅ 3个查询都能找到刚创建的调拨单
- ✅ 支持按调出仓、调入仓过滤

---

#### 步骤3: 查询调出仓库存（过账前）

```bash
GET /api/inventory/balances?warehouseId=1&productId=1
```

**记录**: 主仓库库存 = 590

---

#### 步骤4: 查询调入仓库存（过账前）

```bash
GET /api/inventory/balances?warehouseId=2&productId=1
```

**记录**: 分仓库库存 = 200

---

#### 步骤5: 过账调拨单

```bash
POST /api/inventory/transfers/{transferId}/post
```

**验证点**:
- ✅ HTTP 200
- ✅ status = "POSTED"

---

#### 步骤6: 验证库存变化

```bash
# 主仓库应减少
GET /api/inventory/balances?warehouseId=1&productId=1
# 预期: 540 (590 - 50)

# 分仓库应增加
GET /api/inventory/balances?warehouseId=2&productId=1
# 预期: 250 (200 + 50)
```

**验证点**:
- ✅ 主仓库: 540
- ✅ 分仓库: 250
- ✅ 总量不变: 790

---

#### 步骤7: 测试取消草稿调拨单 ⭐新增接口

```bash
# 创建新调拨单
POST /api/inventory/transfers {...}

# 立即取消
POST /api/inventory/transfers/{newTransferId}/cancel
```

**验证点**:
- ✅ 取消成功
- ✅ 库存未变化

---

**场景3总结**:
- [ ] 调拨流程完整
- [ ] 两个仓库库存都正确
- [ ] 新增接口工作正常

---

### 场景4：采购订单管理流程 ⭐⭐

**业务目标**: 采购订单的完整生命周期

**前置条件**:
- 已有测试订单: PO20260616001（已审批）

**测试步骤**:

#### 步骤1: 查询订单列表

```bash
GET /api/purchase/orders?pageNo=1&pageSize=20&status=APPROVED
```

**验证点**:
- ✅ 找到测试订单 PO20260616001

---

#### 步骤2: 查询订单跟踪信息 ⭐新增接口

```bash
GET /api/purchase/orders/{orderId}/trace
```

**验证点**:
- ✅ HTTP 200
- ✅ 返回订单执行信息
- ✅ 包含收货记录（如有）
- ✅ 包含退货记录（如有）
- ✅ 包含付款记录（如有）
- ✅ 包含凭证记录（如有）

---

#### 步骤3: 关闭订单 ⭐新增接口

```bash
POST /api/purchase/orders/{orderId}/close
```

**验证点**:
- ✅ HTTP 200
- ✅ status = "CLOSED"

---

#### 步骤4: 导出订单列表 ⭐新增接口

```bash
GET /api/purchase/orders/export?status=CLOSED
```

**验证点**:
- ✅ HTTP 200
- ✅ Content-Type: text/csv
- ✅ 文件可下载
- ✅ Excel可直接打开（UTF-8 BOM）
- ✅ 包含订单数据

---

**场景4总结**:
- [ ] 订单管理完整
- [ ] 新增功能全部可用

---

### 场景5：财务费用登记流程 ⭐⭐

**业务目标**: 登记费用并自动生成凭证

**测试步骤**:

#### 步骤1: 创建费用单

```bash
POST /api/finance/expenses

{
  "expenseDate": "2026-06-16",
  "subjectId": 201,
  "paymentSubjectId": 101,
  "amount": 5000.00,
  "remark": "集成测试-办公费用"
}
```

**记录**: expenseId = _______

---

#### 步骤2: 费用过账

```bash
POST /api/finance/expenses/{expenseId}/post
```

**验证点**:
- ✅ status = "POSTED"
- ✅ 自动生成voucherId

**记录**: voucherId = _______

---

#### 步骤3: 查询生成的凭证

```bash
GET /api/finance/vouchers/{voucherId}
```

**验证点**:
- ✅ 凭证存在
- ✅ sourceType = "EXPENSE"
- ✅ amount = 5000.00

---

#### 步骤4: 查询凭证分录

```bash
GET /api/finance/vouchers/{voucherId}/entries
```

**验证点**:
- ✅ 2条分录
- ✅ 借方：管理费用 5000
- ✅ 贷方：银行存款 5000
- ✅ 借贷平衡

---

**场景5总结**:
- [ ] 费用流程完整
- [ ] 凭证自动生成正确

---

## 📊 集成测试检查清单

### 核心业务流程

- [ ] 库存调整流程（7个步骤）
- [ ] 库存盘点流程（5个步骤）
- [ ] 库存调拨流程（7个步骤）
- [ ] 采购订单管理（4个步骤）
- [ ] 财务费用登记（4个步骤）

### 新增接口验证

- [ ] GET /api/inventory/adjustments - 调整列表查询
- [ ] POST /api/inventory/adjustments/{id}/cancel - 取消调整
- [ ] GET /api/inventory/checks - 盘点列表查询
- [ ] POST /api/inventory/checks/{id}/cancel - 取消盘点
- [ ] GET /api/inventory/transfers - 调拨列表查询
- [ ] POST /api/inventory/transfers/{id}/cancel - 取消调拨
- [ ] POST /api/purchase/orders/{id}/close - 关闭订单
- [ ] GET /api/purchase/orders/{id}/trace - 订单跟踪
- [ ] GET /api/purchase/orders/export - 订单导出

### 数据一致性

- [ ] 库存数量正确
- [ ] 借贷平衡
- [ ] 状态流转正确
- [ ] 租户隔离有效

---

## 🐛 问题记录表

| 编号 | 场景 | 步骤 | 问题描述 | 严重程度 | 状态 |
|------|------|------|---------|---------|------|
| 1 | | | | | |
| 2 | | | | | |

**严重程度**: 🔴高 🟡中 🟢低

---

## ✅ 测试签字

| 角色 | 姓名 | 签字 | 日期 |
|------|------|------|------|
| 测试执行 | | | |
| 测试复核 | | | |
| 开发确认 | | | |

---

## 📝 测试总结

### 通过的场景

- [ ] 场景1: 库存调整
- [ ] 场景2: 库存盘点
- [ ] 场景3: 库存调拨
- [ ] 场景4: 采购订单
- [ ] 场景5: 财务费用

### 发现的问题数

- 🔴 高严重度: ___ 个
- 🟡 中严重度: ___ 个
- 🟢 低严重度: ___ 个

### 整体评价

- [ ] ✅ 通过 - 所有场景成功
- [ ] ⚠️ 有问题但可接受
- [ ] ❌ 不通过 - 有阻塞性问题

---

**测试完成时间**: __________  
**文档版本**: v1.0

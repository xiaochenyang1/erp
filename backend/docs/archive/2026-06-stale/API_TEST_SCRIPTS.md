# ERP系统API测试脚本集合

**测试说明**: 由于Maven版本限制（需要3.9.0+，当前3.6.3），无法自动编译测试。
本文档提供详细的API测试脚本，可用于Postman、curl或其他HTTP客户端。

---

## 环境准备

### 1. 启动后端服务
```bash
# 需要升级Maven到3.9.0+或跳过enforcer检查
cd E:/tuowei/python/erpServer
mvn spring-boot:run -Dmaven.enforcer.skip=true
```

### 2. 获取认证Token
```bash
# POST /api/auth/login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password"
  }'

# 保存返回的token，后续请求需要在Header中携带：
# Authorization: Bearer {token}
```

---

## 测试用例集

### 📦 库存模块测试

#### TC-INV-001: 库存调整完整流程

**步骤1: 创建库存调整单**
```bash
curl -X POST http://localhost:8080/api/inventory/adjustments \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
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
    "remark": "测试库存调整"
  }'
```

**预期结果**: 
- 状态码: 200
- 返回调整单ID和单号
- 状态为 DRAFT

**步骤2: 查询调整单列表**
```bash
curl -X GET 'http://localhost:8080/api/inventory/adjustments?pageNo=1&pageSize=20&status=DRAFT' \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 状态码: 200
- 返回分页数据
- 包含刚创建的调整单

**步骤3: 过账调整单**
```bash
curl -X POST http://localhost:8080/api/inventory/adjustments/{id}/post \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 状态码: 200
- 状态变为 POSTED
- 库存数量应相应增加

**步骤4: 尝试取消（应失败）**
```bash
curl -X POST http://localhost:8080/api/inventory/adjustments/{id}/cancel \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 状态码: 400 或 422
- 错误信息: "已过账的库存调整单不能取消"

---

#### TC-INV-002: 库存盘点完整流程

**步骤1: 创建盘点单**
```bash
curl -X POST http://localhost:8080/api/inventory/checks \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "warehouseId": 1,
    "checkDate": "2026-06-16",
    "lines": [
      {
        "productId": 1,
        "actualQty": 95,
        "unitCost": 10.50
      }
    ],
    "remark": "测试盘点"
  }'
```

**步骤2: 查询盘点列表**
```bash
curl -X GET 'http://localhost:8080/api/inventory/checks?pageNo=1&pageSize=20' \
  -H "Authorization: Bearer {token}"
```

**步骤3: 生成调整**
```bash
curl -X POST http://localhost:8080/api/inventory/checks/{id}/adjust \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 自动创建调整单
- 状态变为 ADJUSTED

**步骤4: 尝试取消（应失败）**
```bash
curl -X POST http://localhost:8080/api/inventory/checks/{id}/cancel \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 错误信息: "已生成调整的盘点单不能取消"

---

#### TC-INV-003: 库存调拨完整流程

**步骤1: 创建调拨单**
```bash
curl -X POST http://localhost:8080/api/inventory/transfers \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
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
    "remark": "测试调拨"
  }'
```

**步骤2: 查询调拨列表**
```bash
curl -X GET 'http://localhost:8080/api/inventory/transfers?pageNo=1&pageSize=20&status=DRAFT' \
  -H "Authorization: Bearer {token}"
```

**步骤3: 过账调拨（同时出库和入库）**
```bash
curl -X POST http://localhost:8080/api/inventory/transfers/{id}/post \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 调出仓库存减少
- 调入仓库存增加
- 支持批次跟踪

**步骤4: 取消草稿调拨单**
```bash
# 先创建一个新的调拨单（不过账）
# 然后测试取消
curl -X POST http://localhost:8080/api/inventory/transfers/{draft_id}/cancel \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 状态变为 CANCELLED
- 库存不变

---

### 🛒 采购模块测试

#### TC-PUR-001: 采购订单完整流程

**步骤1: 创建采购订单**
```bash
curl -X POST http://localhost:8080/api/purchase/orders \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "supplierId": 1,
    "orderDate": "2026-06-16",
    "expectedDate": "2026-06-30",
    "items": [
      {
        "productId": 1,
        "quantity": 100,
        "price": 10.00
      }
    ],
    "remark": "测试采购订单"
  }'
```

**步骤2: 提交审批**
```bash
curl -X POST http://localhost:8080/api/purchase/orders/{id}/submit \
  -H "Authorization: Bearer {token}"
```

**步骤3: 审批通过**
```bash
curl -X POST http://localhost:8080/api/purchase/orders/{id}/approve \
  -H "Authorization: Bearer {token}"
```

**步骤4: 关闭订单** ⭐ 新功能
```bash
curl -X POST http://localhost:8080/api/purchase/orders/{id}/close \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 状态变为 CLOSED
- 不能再进行收货操作

**步骤5: 查询订单跟踪** ⭐ 新功能
```bash
curl -X GET http://localhost:8080/api/purchase/orders/{id}/trace \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 返回订单执行信息
- 包含收货记录
- 包含退货记录
- 包含付款记录
- 包含凭证记录

**步骤6: 导出订单列表** ⭐ 新功能
```bash
curl -X GET 'http://localhost:8080/api/purchase/orders/export?status=APPROVED' \
  -H "Authorization: Bearer {token}" \
  -o purchase_orders.csv
```

**预期结果**:
- 下载CSV文件
- 包含订单编号、供应商、金额、状态等信息
- Excel可直接打开（UTF-8 BOM）

---

### 🏭 生产模块测试

#### TC-PRD-001: 生产订单完整流程

**步骤1: 创建生产订单**
```bash
curl -X POST http://localhost:8080/api/production/orders \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 10,
    "bomId": 1,
    "planQuantity": 100,
    "warehouseId": 1,
    "planStartDate": "2026-06-17",
    "planEndDate": "2026-06-20",
    "priority": "NORMAL"
  }'
```

**步骤2: 下达生产**
```bash
curl -X POST http://localhost:8080/api/production/orders/{id}/release \
  -H "Authorization: Bearer {token}"
```

**步骤3: 生产领料** ⭐ 参数已优化
```bash
curl -X POST http://localhost:8080/api/production/orders/{id}/issue \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "issueDate": "2026-06-17",
    "materials": [
      {
        "materialId": 1,
        "quantity": 200
      }
    ],
    "remark": "领料"
  }'
```

**步骤4: 生产完工** ⭐ 参数已优化
```bash
curl -X POST http://localhost:8080/api/production/orders/{id}/complete \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "completionDate": "2026-06-20",
    "completedQuantity": 95,
    "scrapQuantity": 5,
    "lotNumber": "LOT20260620001",
    "productionDate": "2026-06-20",
    "expiryDate": "2027-06-20",
    "remark": "完工入库"
  }'
```

**预期结果**:
- 完工数量更新
- 成品库存增加
- 生成批次信息

**步骤5: 完工红冲** ⭐ 参数已优化
```bash
curl -X POST http://localhost:8080/api/production/orders/{id}/reverse-completion \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 10,
    "remark": "部分红冲"
  }'
```

**预期结果**:
- 完工数量减少10
- 成品库存相应减少

**步骤6: 生产退料**
```bash
curl -X POST http://localhost:8080/api/production/orders/{id}/return-materials \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "materials": [
      {
        "materialId": 1,
        "quantity": 20
      }
    ],
    "remark": "多领退料"
  }'
```

---

### 💰 财务模块测试

#### TC-FIN-001: 费用登记完整流程

**步骤1: 创建费用单**
```bash
curl -X POST http://localhost:8080/api/finance/expenses \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "expenseDate": "2026-06-16",
    "subjectId": 100,
    "paymentSubjectId": 101,
    "amount": 5000.00,
    "remark": "办公用品采购"
  }'
```

**步骤2: 更新费用单**
```bash
curl -X PUT http://localhost:8080/api/finance/expenses/{id} \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "expenseDate": "2026-06-16",
    "subjectId": 100,
    "paymentSubjectId": 101,
    "amount": 5500.00,
    "remark": "办公用品采购（已修改）"
  }'
```

**步骤3: 费用过账**
```bash
curl -X POST http://localhost:8080/api/finance/expenses/{id}/post \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 状态变为 POSTED
- 自动生成财务凭证
- 凭证包含两条分录：
  - 借：费用科目 5500
  - 贷：支付科目 5500

**步骤4: 查看生成的凭证**
```bash
curl -X GET http://localhost:8080/api/finance/expenses/{id} \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 返回费用信息
- 包含关联的凭证ID
- 包含凭证余额校验信息

**步骤5: 查询凭证详情**
```bash
curl -X GET http://localhost:8080/api/finance/vouchers/{voucherId} \
  -H "Authorization: Bearer {token}"
```

**步骤6: 查询凭证分录**
```bash
curl -X GET http://localhost:8080/api/finance/vouchers/{voucherId}/entries \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 返回凭证分录列表
- 借贷平衡
- 金额正确

**步骤7: 费用红冲**
```bash
curl -X POST http://localhost:8080/api/finance/expenses/{id}/reverse \
  -H "Authorization: Bearer {token}"
```

**预期结果**:
- 自动生成红冲凭证
- 红冲凭证分录与原凭证相反：
  - 借：支付科目 5500
  - 贷：费用科目 5500

---

## 验证清单

### ✅ 路径对接验证

| 模块 | 接口 | 前端路径 | 后端路径 | 状态 |
|------|------|---------|---------|------|
| 采购 | 收货完成 | `/post` | `/post` | ✅ |
| 采购 | 退货完成 | `/post` | `/post` | ✅ |
| 采购 | 关闭订单 | `/close` | `/close` | ✅ |
| 采购 | 订单跟踪 | `/trace` | `/trace` | ✅ |
| 库存 | 库存查询 | `/balances` | `/balances` | ✅ |
| 库存 | 调整过账 | `/post` | `/post` | ✅ |
| 库存 | 盘点调整 | `/adjust` | `/adjust` | ✅ |
| 库存 | 调拨过账 | `/post` | `/post` | ✅ |
| 财务 | 会计科目 | `/account-subjects` | `/account-subjects` | ✅ |
| 财务 | 总账明细 | `/detail` | `/detail` | ✅ |
| 财务 | 总账汇总 | `/general` | `/general` | ✅ |

### ✅ 新功能验证

| 功能 | 状态 | 测试用例 |
|------|------|---------|
| 采购订单关闭 | ⭐ 新增 | TC-PUR-001步骤4 |
| 采购订单跟踪 | ⭐ 新增 | TC-PUR-001步骤5 |
| 采购订单导出 | ⭐ 新增 | TC-PUR-001步骤6 |
| 生产领料参数优化 | ⭐ 优化 | TC-PRD-001步骤3 |
| 生产完工参数优化 | ⭐ 优化 | TC-PRD-001步骤4 |
| 库存调整列表 | ⭐ 新增 | TC-INV-001步骤2 |
| 库存盘点列表 | ⭐ 新增 | TC-INV-002步骤2 |
| 库存调拨列表 | ⭐ 新增 | TC-INV-003步骤2 |
| 各模块取消操作 | ⭐ 新增 | 各TC最后步骤 |

---

## Postman Collection

将以上curl命令导入Postman后，可以：
1. 创建环境变量：`baseUrl`, `token`
2. 在Pre-request Script中自动获取token
3. 使用Tests验证响应状态码和数据

---

## 自动化测试建议

由于Maven版本限制，建议：

### 方案1: 升级Maven
```bash
# 下载Maven 3.9.9
# https://maven.apache.org/download.cgi
# 解压并更新环境变量
```

### 方案2: 跳过enforcer检查
```bash
mvn clean test -Dmaven.enforcer.skip=true
```

### 方案3: 使用Postman + Newman
```bash
# 安装Newman
npm install -g newman

# 运行测试集合
newman run erp-api-tests.json -e env.json
```

---

**测试完成标准**:
- ✅ 所有API返回正确的状态码
- ✅ 业务流程可以完整走通
- ✅ 数据在数据库中正确保存
- ✅ 前后端参数类型匹配
- ✅ 错误情况返回友好的错误信息

**文档更新**: 2026-06-16  
**版本**: v1.0

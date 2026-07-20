# 所有模块字段映射问题汇总

## 发现的字段映射问题

### 1. 产品管理 (Product) ✅ 已修复
| 前端字段 | 后端字段 |
|---------|---------|
| code | productCode |
| name | productName |
| specifications | specification |
| unit | unitName |
| unitPrice | salePrice |
| costPrice | purchasePrice |

### 2. 客户管理 (Customer) ⚠️ 需要修复
| 前端字段 | 后端字段 |
|---------|---------|
| code | customerCode |
| name | customerName |
| contact | contactName |
| mobile | contactPhone |
| createdAt | createdTime |
| updatedAt | updatedTime |

### 3. 供应商管理 (Supplier) ⚠️ 需要修复
| 前端字段 | 后端字段 |
|---------|---------|
| code | supplierCode |
| name | supplierName |
| contact | contactName |
| mobile | contactPhone |
| createdAt | createdTime |
| updatedAt | updatedTime |

### 4. 仓库管理 (Warehouse) ⚠️ 需要修复
| 前端字段 | 后端字段 |
|---------|---------|
| code | warehouseCode |
| name | warehouseName |
| createdAt | createdTime |
| updatedAt | updatedTime |

### 5. 分页响应 ⚠️ 所有模块通用
| 前端字段 | 后端字段 |
|---------|---------|
| content | records |
| totalElements | total |
| number | pageNo |
| size | pageSize |

---

## 修复策略

由于所有模块都存在相同的字段映射问题，建议采用以下两种方案之一：

### 方案A：统一修复前端（推荐）✅

**优点**：
- 保持后端代码不变
- 集中处理，一次性解决
- 前端可以添加字段别名保持兼容

**步骤**：
1. 更新所有API类型定义
2. 在loadData中统一映射字段
3. 在提交时转换字段名

### 方案B：修改后端字段名

**优点**：
- 前后端字段统一
- 长期维护更清晰

**缺点**：
- 需要修改实体类、数据库字段
- 影响范围大，风险高
- 需要数据迁移

---

## 建议

**现阶段采用方案A**，快速解决问题，后续如果需要重构再考虑方案B。

---

## 需要修复的文件清单

### API类型定义
- ✅ `api/masterdata.ts` - Product（已修复）
- ⚠️ `api/masterdata.ts` - Customer
- ⚠️ `api/masterdata.ts` - Supplier
- ⚠️ `api/masterdata.ts` - Warehouse

### 页面组件
- ✅ `views/masterdata/products/index.vue`（已修复）
- ⚠️ `views/masterdata/customers/index.vue`
- ⚠️ `views/masterdata/suppliers/index.vue`
- ⚠️ `views/masterdata/warehouses/index.vue`

### 其他可能受影响的模块
- ⚠️ 采购订单（引用产品、供应商）
- ⚠️ 销售订单（引用产品、客户）
- ⚠️ 库存管理（引用产品、仓库）
- ⚠️ 财务模块（引用客户、供应商）

---

## 是否继续修复其他模块？

我已经发现其他3个主数据模块（客户、供应商、仓库）也存在同样的字段映射问题。

**是否需要我继续修复这些模块？**

如果需要，我会：
1. 更新Customer、Supplier、Warehouse的类型定义
2. 修复对应页面的字段映射
3. 确保新增、编辑、删除功能正常工作

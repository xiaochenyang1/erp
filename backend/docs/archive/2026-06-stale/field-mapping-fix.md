# 前后端字段映射问题修复记录

## 问题描述

前端页面无法显示后端返回的数据，因为字段名不匹配。

## 后端实际返回格式

### 分页响应结构
```json
{
  "code": "0",
  "message": "success",
  "data": {
    "records": [...],      // 数据列表
    "total": 10,           // 总数
    "pageNo": 1,           // 当前页
    "pageSize": 20         // 每页大小
  }
}
```

### 产品对象字段
```json
{
  "id": 1,
  "productCode": "PROD001",     // 后端字段
  "productName": "iPhone 15",   // 后端字段
  "productType": "FINISHED",
  "categoryName": "手机",
  "unit": "台",
  "unitPrice": 8999.00,
  "costPrice": 7500.00,
  "status": "ACTIVE",
  "createdTime": "2026-06-15",  // 后端字段
  "updatedTime": "2026-06-15"   // 后端字段
}
```

## 前端原有期望格式

### 分页响应结构
```json
{
  "content": [...],          // 数据列表
  "totalElements": 10,       // 总数
  "number": 0,               // 当前页
  "size": 20                 // 每页大小
}
```

### 产品对象字段
```json
{
  "code": "PROD001",         // 前端字段
  "name": "iPhone 15",       // 前端字段
  "createdAt": "2026-06-15", // 前端字段
  "updatedAt": "2026-06-15"  // 前端字段
}
```

## 修复方案

### 1. 更新类型定义（`api/masterdata.ts`）

```typescript
// 修改分页响应类型
export interface PageResponse<T> {
  records: T[]          // 改为records
  total: number         // 改为total
  pageNo: number        // 改为pageNo
  pageSize: number      // 改为pageSize
}

// 修改产品类型
export interface Product {
  id: number
  productCode: string      // 后端字段名
  productName: string      // 后端字段名
  productType?: string
  categoryId?: number
  categoryName?: string
  specifications?: string
  unit?: string
  unitPrice?: number
  costPrice?: number
  barcode?: string
  status: 'ACTIVE' | 'INACTIVE'
  remark?: string
  createdTime?: string     // 后端字段名
  updatedTime?: string     // 后端字段名
  
  // 兼容别名
  code?: string
  name?: string
}
```

### 2. 数据加载适配（`views/masterdata/products/index.vue`）

```typescript
const loadData = async () => {
  loading.value = true
  try {
    const res = await getProducts(searchForm)
    
    // 映射字段名
    const products = res.records.map(item => ({
      ...item,
      code: item.productCode,  // 添加别名
      name: item.productName   // 添加别名
    }))
    
    tableData.value = products
    total.value = res.total
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}
```

### 3. 表单提交适配

```typescript
const handleSubmit = async (values: any) => {
  // 转换为后端字段名
  const payload = {
    productCode: values.code,
    productName: values.name,
    categoryId: values.categoryId,
    // ...其他字段
  }
  
  if (formData.id) {
    await updateProduct(formData.id, payload)
  } else {
    await createProduct(payload)
  }
}
```

### 4. 编辑表单适配

```typescript
const handleEdit = (row: Product) => {
  Object.assign(formData, {
    id: row.id,
    code: row.productCode || row.code,    // 兼容处理
    name: row.productName || row.name,    // 兼容处理
    // ...其他字段
  })
}
```

## 字段映射表

| 功能 | 前端字段 | 后端字段 | 说明 |
|------|---------|---------|------|
| 产品编码 | code | productCode | - |
| 产品名称 | name | productName | - |
| 规格型号 | specifications | specification | 注意单复数 |
| 单位 | unit | unitName | - |
| 销售单价 | unitPrice | salePrice | - |
| 成本单价 | costPrice | purchasePrice | 后端叫采购价 |
| 产品分类 | categoryName | categoryName | 相同 |
| 创建时间 | createdAt | createdTime | - |
| 更新时间 | updatedAt | updatedTime | - |
| 数据列表 | content | records | 分页响应 |
| 总数 | totalElements | total | 分页响应 |
| 当前页 | number | pageNo | 分页响应 |
| 每页大小 | size | pageSize | 分页响应 |

## 测试验证

### 1. 查看Network
打开浏览器 F12 -> Network，查看接口返回：
```
GET /api/masterdata/products?page=1&size=20
```

### 2. 查看Console
确认数据正确映射：
```javascript
console.log('API响应:', res)
console.log('映射后的数据:', products)
```

### 3. 功能测试
- ✅ 列表加载正常显示
- ✅ 产品编码和名称正确显示
- ✅ 新增产品功能正常
- ✅ 编辑产品功能正常
- ✅ 删除产品功能正常

## 类似问题的通用解决方案

如果其他模块也遇到字段不匹配问题，按以下步骤处理：

1. **查看实际返回数据** - 在Network中查看API响应
2. **更新类型定义** - 修改interface使用后端字段名
3. **数据映射** - 在loadData中添加字段映射
4. **表单适配** - 在提交和编辑时转换字段名
5. **添加兼容别名** - 在类型中添加可选的别名字段

## 注意事项

⚠️ **统一建议**：前后端应该使用一致的字段命名规范
- 建议后端统一使用驼峰命名（camelCase）
- 或者前端统一适配后端字段名

✅ **当前方案**：前端通过映射适配后端字段名，保持页面模板不变

---

**修复时间**: 2026-06-15  
**修复状态**: ✅ 已完成

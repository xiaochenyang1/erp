# 前端错误排查指南

## 常见渲染错误排查步骤

### 1. 查看浏览器控制台详细错误

打开浏览器开发者工具（F12），查看 Console 标签页的完整错误信息。

常见错误类型：
- `Cannot read property 'xxx' of undefined` - 数据为空
- `xxx is not a function` - 方法不存在
- `Failed to resolve component` - 组件未注册

### 2. 检查API响应数据

在 Network 标签页查看API请求：
1. 找到对应的API请求（如 `/api/masterdata/products`）
2. 查看 Response 标签
3. 确认返回的数据结构是否正确

**预期的响应格式**：
```json
{
  "code": "0",
  "message": "success",
  "data": {
    "content": [...],        // 数据列表
    "totalElements": 100,    // 总数
    "totalPages": 10,        // 总页数
    "number": 0,             // 当前页（从0开始）
    "size": 20               // 每页大小
  }
}
```

### 3. 常见问题修复

#### 问题1: 字段名称不匹配

**症状**: 页面显示为空或显示 `undefined`

**原因**: 后端返回的字段名与前端期望的不一致

**检查方式**:
```javascript
// 前端期望
row.createdAt

// 后端实际返回
row.createdTime
```

**修复方法**: 修改前端代码使用正确的字段名

#### 问题2: 数据为null

**症状**: `Cannot read property 'xxx' of null`

**原因**: API返回的某个字段为null，但前端没有做空值判断

**修复方法**: 使用可选链操作符
```vue
<!-- 修复前 -->
{{ row.createdTime }}

<!-- 修复后 -->
{{ row.createdTime || '-' }}
{{ row?.createdTime }}
```

#### 问题3: 组件未注册

**症状**: `Failed to resolve component: xxx`

**检查**:
1. 确认组件文件存在
2. 确认在 `index.ts` 中正确导出
3. 确认导入路径正确

**修复**:
```typescript
// components/common/index.ts
export { default as PageTable } from './PageTable.vue'

// 使用时
import { PageTable } from '@/components/common'
```

### 4. 产品列表页面特定问题

#### 已修复的问题

1. ✅ `createdAt` 改为 `createdTime`（后端字段名）
2. ✅ 添加空值保护 `|| '-'`

#### 需要确认的点

**后端返回的Product对象结构**：
```typescript
interface Product {
  id: number
  code: string
  name: string
  categoryId?: number
  categoryName?: string    // 可能不存在
  specifications?: string
  unit?: string
  unitPrice?: number
  costPrice?: number
  barcode?: string
  status: string
  remark?: string
  createdTime?: string    // 注意：不是createdAt
  updatedTime?: string
}
```

### 5. 快速调试技巧

#### 在组件中添加调试代码

```vue
<script setup lang="ts">
// 在loadData中添加日志
const loadData = async () => {
  loading.value = true
  try {
    const res = await getProducts(searchForm)
    console.log('API响应:', res)  // 打印响应
    console.log('数据列表:', res.content)  // 打印数据
    tableData.value = res.content
    total.value = res.totalElements
  } catch (error) {
    console.error('请求失败:', error)  // 打印错误
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}
</script>
```

#### 检查数据是否正确加载

```vue
<!-- 在模板中临时添加 -->
<div>
  <pre>{{ tableData }}</pre>
</div>
```

### 6. 后端问题排查

如果前端代码没问题，检查后端：

1. **Swagger测试API**
   - 访问: http://localhost:8080/swagger-ui.html
   - 找到 `/api/masterdata/products` 接口
   - 点击 Try it out 测试
   - 查看响应是否正确

2. **检查后端日志**
   - 查看控制台输出
   - 是否有异常堆栈
   - SQL是否执行成功

3. **检查数据库**
   ```sql
   -- 检查产品表是否有数据
   SELECT * FROM md_product LIMIT 10;
   
   -- 检查表结构
   DESC md_product;
   ```

### 7. 解决当前错误的步骤

**步骤1**: 打开浏览器控制台，找到完整的错误堆栈

**步骤2**: 检查Network标签，查看API请求是否成功
- 状态码是否为200
- 响应数据格式是否正确

**步骤3**: 如果API请求失败
- 检查后端是否启动
- 检查URL是否正确
- 检查是否有权限

**步骤4**: 如果API请求成功但渲染失败
- 对比响应数据字段名
- 检查是否有null值
- 添加console.log调试

**步骤5**: 刷新页面重试

### 8. 常用排查命令

```bash
# 清除前端缓存重新启动
cd E:\tuowei\python\erp-frontend
rm -rf node_modules/.vite
npm run dev

# 查看后端日志
cd E:\tuowei\python\erpServer
.\mvnw.cmd spring-boot:run

# 重新编译
npm run build
```

### 9. 如果还是报错

请提供以下信息：

1. **浏览器控制台的完整错误信息**（截图或复制）
2. **Network标签中API的响应数据**
3. **后端控制台的错误日志**

然后我可以针对性地解决问题。

---

## 当前问题快速修复

我已经修复了一个明显的问题：
- ✅ 将 `createdAt` 改为 `createdTime`

**现在请**：
1. 刷新浏览器页面
2. 查看是否还有错误
3. 如果还有错误，复制完整的错误信息给我

---

**提示**: 大部分渲染错误都是字段名不匹配或空值引起的，仔细对比前后端的字段名即可解决。

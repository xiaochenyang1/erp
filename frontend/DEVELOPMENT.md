# 前端开发指南

## 🚀 快速开始

### 安装依赖
```bash
npm install
```

### 开发模式
```bash
npm run dev
```
访问: http://localhost:5173

### 生产构建
```bash
npm run build
```

---

## 📁 项目结构

```
src/
├── api/                    # API接口定义
│   ├── auth.ts            # 认证接口
│   ├── finance.ts         # 财务接口
│   ├── purchase.ts        # 采购接口
│   ├── sales.ts           # 销售接口
│   ├── inventory.ts       # 库存接口
│   ├── masterdata.ts      # 主数据接口
│   ├── system.ts          # 系统管理接口
│   ├── attachment.ts      # 附件接口
│   └── notification.ts    # 通知接口
├── assets/                # 静态资源
├── components/            # 公共组件
├── directives/            # 自定义指令
│   ├── permission.ts      # 权限指令
│   ├── role.ts           # 角色指令
│   └── index.ts          # 指令注册
├── layout/               # 布局组件
├── router/               # 路由配置
├── store/                # 状态管理
│   └── modules/
│       ├── user.ts       # 用户状态
│       └── app.ts        # 应用状态
├── styles/               # 全局样式
├── types/                # TypeScript类型
├── utils/                # 工具函数
│   ├── request.ts        # HTTP请求封装
│   ├── common.ts         # 通用工具函数
│   ├── validate.ts       # 表单验证规则
│   └── constants.ts      # 常量定义
├── views/                # 页面组件
│   ├── login/           # 登录页
│   ├── dashboard/       # 首页
│   ├── masterdata/      # 主数据管理
│   ├── purchase/        # 采购管理
│   ├── sales/           # 销售管理
│   ├── inventory/       # 库存管理
│   ├── finance/         # 财务管理
│   ├── system/          # 系统管理
│   └── reports/         # 报表中心
├── App.vue              # 根组件
├── main.ts              # 入口文件
└── env.d.ts             # 环境变量类型定义
```

---

## 🔐 权限控制

### 1. 使用权限指令

在模板中使用 `v-permission` 指令控制元素显示：

```vue
<template>
  <!-- 单个权限 -->
  <el-button v-permission="'user:create'">新增用户</el-button>
  
  <!-- 无权限时元素会被移除 -->
  <el-button v-permission="'user:delete'" type="danger">删除</el-button>
</template>
```

### 2. 使用角色指令

在模板中使用 `v-role` 指令控制元素显示：

```vue
<template>
  <!-- 单个角色 -->
  <div v-role="'系统管理员'">管理员功能</div>
  
  <!-- 多个角色（满足任一即可）-->
  <div v-role="['系统管理员', '财务经理']">高级功能</div>
</template>
```

### 3. 在脚本中判断权限

```typescript
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()

// 检查权限
if (userStore.hasPermission('user:create')) {
  // 有权限执行的操作
}

// 检查角色
const roles = userStore.userInfo?.roles || []
if (roles.includes('系统管理员')) {
  // 有角色执行的操作
}
```

---

## 📡 API调用

### 1. 基本用法

```typescript
import { getUsers, createUser, updateUser, deleteUser } from '@/api/system'

// 查询列表
const users = await getUsers({ page: 1, size: 10 })

// 创建
const newUser = await createUser({
  username: 'test',
  realName: '测试用户',
  mobile: '13800138000'
})

// 更新
await updateUser(userId, { realName: '新名称' })

// 删除
await deleteUser(userId)
```

### 2. 错误处理

```typescript
try {
  const result = await createUser(formData)
  ElMessage.success('创建成功')
} catch (error) {
  // 错误已在拦截器中处理，显示了错误消息
  console.error('创建失败', error)
}
```

### 3. 文件上传

```typescript
import { uploadAttachment } from '@/api/attachment'

const handleUpload = async (file: File) => {
  try {
    const result = await uploadAttachment(
      file,
      'PURCHASE_ORDER',  // 业务类型
      orderId,           // 业务ID
      orderNo            // 业务编号（可选）
    )
    ElMessage.success('上传成功')
    return result
  } catch (error) {
    ElMessage.error('上传失败')
  }
}
```

### 4. 文件下载

```typescript
import { downloadAttachment } from '@/api/attachment'
import { downloadFile } from '@/utils/common'

const handleDownload = async (attachmentId: number, filename: string) => {
  try {
    const blob = await downloadAttachment(attachmentId)
    downloadFile(blob, filename)
  } catch (error) {
    ElMessage.error('下载失败')
  }
}
```

---

## 🛠 工具函数

### 1. 格式化金额

```typescript
import { formatMoney } from '@/utils/common'

formatMoney(1234.56)        // "1,234.56"
formatMoney(1234.567, 3)    // "1,234.567"
```

### 2. 格式化日期

```typescript
import { formatDate, formatDateTime } from '@/utils/common'

formatDate(new Date())                          // "2026-06-15"
formatDate(new Date(), 'YYYY-MM-DD HH:mm')     // "2026-06-15 13:30"
formatDateTime(new Date())                      // "2026-06-15 13:30:45"
```

### 3. 格式化文件大小

```typescript
import { formatFileSize } from '@/utils/common'

formatFileSize(1024)        // "1 KB"
formatFileSize(1048576)     // "1 MB"
```

### 4. 防抖和节流

```typescript
import { debounce, throttle } from '@/utils/common'

// 防抖：最后一次调用后延迟执行
const handleSearch = debounce((keyword: string) => {
  console.log('搜索:', keyword)
}, 300)

// 节流：固定时间间隔内只执行一次
const handleScroll = throttle(() => {
  console.log('滚动事件')
}, 300)
```

### 5. 获取状态标签

```typescript
import { getStatusType, getStatusText } from '@/utils/common'

// 获取Element Plus Tag类型
getStatusType('APPROVED')   // "success"
getStatusType('PENDING')    // "warning"
getStatusType('REJECTED')   // "danger"

// 获取中文文本
getStatusText('APPROVED')   // "已审批"
getStatusText('PENDING')    // "待审批"
```

### 6. Loading和对话框 ✨新增

```typescript
import { showLoading, hideLoading, confirm, confirmDelete, confirmSubmit } from '@/utils/dialog'

// 显示全局Loading
const loading = showLoading('正在加载...')
// 隐藏Loading
hideLoading()

// 通用确认对话框
await confirm('确定要执行此操作吗？')

// 删除确认
await confirmDelete('此操作将永久删除该数据，是否继续？')

// 提交确认
await confirmSubmit('确定要提交吗？')

// 审批确认
import { confirmApprove, confirmReject } from '@/utils/dialog'
await confirmApprove('确定要审批通过吗？')
const reason = await confirmReject('请输入驳回原因')

// 使用示例
const handleDelete = async (id: number) => {
  try {
    await confirmDelete()
    await deleteUser(id)
    ElMessage.success('删除成功')
  } catch {
    // 用户取消了操作
  }
}
```

---

## ✅ 表单验证

### 1. 使用验证规则

```vue
<script setup lang="ts">
import { reactive } from 'vue'
import { required, mobile, email, money } from '@/utils/validate'

const formRules = reactive({
  username: [required('请输入用户名')],
  mobile: [required('请输入手机号'), mobile()],
  email: [email()],
  amount: [required('请输入金额'), money()]
})
</script>

<template>
  <el-form :model="form" :rules="formRules">
    <el-form-item label="用户名" prop="username">
      <el-input v-model="form.username" />
    </el-form-item>
    <el-form-item label="手机号" prop="mobile">
      <el-input v-model="form.mobile" />
    </el-form-item>
  </el-form>
</template>
```

### 2. 常用验证规则

```typescript
import {
  required,      // 必填
  mobile,        // 手机号
  email,         // 邮箱
  password,      // 密码强度
  idCard,        // 身份证
  number,        // 数字
  integer,       // 整数
  positiveInteger, // 正整数
  money,         // 金额
  url,           // URL
  length,        // 长度范围
  minLength,     // 最小长度
  maxLength,     // 最大长度
  range,         // 数值范围
  username,      // 用户名
  code           // 编码
} from '@/utils/validate'
```

---

## 📊 常量使用

### 1. 使用系统常量

```typescript
import { 
  ORDER_STATUS, 
  PAYMENT_METHOD_OPTIONS,
  PAGINATION 
} from '@/utils/constants'

// 订单状态
if (order.status === ORDER_STATUS.APPROVED) {
  // 已审批状态
}

// 支付方式选项
const paymentMethods = PAYMENT_METHOD_OPTIONS

// 分页配置
const page = PAGINATION.DEFAULT_PAGE
const size = PAGINATION.DEFAULT_SIZE
```

---

## 🎨 样式规范

### 1. 使用CSS变量

```scss
.custom-button {
  color: var(--el-color-primary);
  background: var(--el-bg-color);
}
```

### 2. 响应式设计

```scss
// 移动端
@media (max-width: 768px) {
  .container {
    padding: 10px;
  }
}

// 平板
@media (min-width: 768px) and (max-width: 1024px) {
  .container {
    padding: 20px;
  }
}

// 桌面端
@media (min-width: 1024px) {
  .container {
    padding: 30px;
  }
}
```

---

## 🔄 审批流程调用

### 采购订单审批

```typescript
import { 
  submitPurchaseOrder, 
  approvePurchaseOrder, 
  rejectPurchaseOrder 
} from '@/api/purchase'

// 提交审批
await submitPurchaseOrder(orderId)

// 审批通过
await approvePurchaseOrder(orderId)

// 审批驳回
await rejectPurchaseOrder(orderId, '不符合采购要求')
```

### 销售订单审批

```typescript
import { 
  submitSalesOrder, 
  approveSalesOrder, 
  rejectSalesOrder 
} from '@/api/sales'

// 提交审批
await submitSalesOrder(orderId)

// 审批通过
await approveSalesOrder(orderId)

// 审批驳回
await rejectSalesOrder(orderId, '客户信息有误')
```

### 费用审批

```typescript
import { 
  submitExpense, 
  approveExpense, 
  rejectExpense 
} from '@/api/finance'

// 提交审批
await submitExpense(expenseId)

// 审批通过
await approveExpense(expenseId)

// 审批驳回
await rejectExpense(expenseId, '费用超出预算')
```

---

## 🔔 消息通知

```typescript
import { 
  getNotifications, 
  getUnreadCount, 
  markNotificationRead,
  markAllNotificationsRead 
} from '@/api/notification'

// 获取未读数量
const { count } = await getUnreadCount()

// 获取通知列表
const messages = await getNotifications({ 
  page: 1, 
  size: 10 
})

// 标记单条已读
await markNotificationRead(notificationId)

// 全部标记已读
await markAllNotificationsRead()
```

---

## 📝 最佳实践

### 1. 组件命名

- 组件文件名使用大驼峰：`UserList.vue`
- 组件name使用大驼峰：`name: 'UserList'`

### 2. API文件组织

- 按业务模块划分API文件
- 导出类型定义和API函数
- 使用统一的命名规范

### 3. 错误处理

- 使用try-catch捕获异常
- 错误消息已在拦截器统一处理
- 特殊场景可自定义错误提示

### 4. 性能优化

- 使用防抖/节流优化高频操作
- 列表渲染使用虚拟滚动（大数据量）
- 路由懒加载

---

## 🐛 常见问题

### Q: 如何调试API请求？
A: 打开浏览器开发者工具 Network 标签查看请求和响应

### Q: 401错误如何处理？
A: 系统会自动清除token并跳转到登录页

### Q: 如何添加新的API接口？
A: 在对应的api文件中添加类型定义和API函数

### Q: 权限控制不生效？
A: 检查是否正确调用 `getUserInfo()` 获取权限数据

---

## 📚 相关文档

- [Vue 3文档](https://cn.vuejs.org/)
- [Element Plus文档](https://element-plus.org/)
- [TypeScript文档](https://www.typescriptlang.org/)
- [Pinia文档](https://pinia.vuejs.org/)

---

## 💡 提示

- 开发前先阅读本文档和后端API文档
- 遵循项目代码规范
- 提交代码前进行自测
- 有问题及时沟通

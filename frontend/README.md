# ERP管理系统前端

基于 Vue 3 + TypeScript + Element Plus 开发的企业资源计划(ERP)管理系统前端。

## 技术栈

- **框架**: Vue 3.4+ (Composition API)
- **语言**: TypeScript 5.0+
- **UI库**: Element Plus 2.8+
- **构建工具**: Vite 5.0+
- **状态管理**: Pinia 2.1+
- **路由**: Vue Router 4.0+
- **HTTP**: Axios 1.7+
- **图表**: ECharts 5.5+

## 功能模块

- ✅ 用户认证（登录/登出/权限控制/用户信息）
- ✅ 主数据管理（产品/客户/供应商/仓库）
- ✅ 采购管理（采购订单/收货/退货）
- ✅ 销售管理（销售订单/发货/退货）
- ✅ 库存管理（库存查询/调整/盘点/调拨/预警）
- ✅ 财务管理（应收应付/收付款/凭证/总账/费用）
- ✅ 系统管理（用户/角色/权限/菜单/部门/岗位/字典/日志/配置）
- ✅ 附件管理（上传/下载/查询/删除）
- ✅ 通知消息（消息列表/未读提醒/标记已读）
- ⚠️ 工作流（审批流程嵌入在各业务模块中）

## 项目结构

```
erp-frontend/
├── public/              # 静态资源
├── src/
│   ├── api/            # API接口
│   ├── assets/         # 资源文件
│   ├── components/     # 公共组件
│   ├── views/          # 页面组件
│   ├── router/         # 路由配置
│   ├── store/          # 状态管理
│   ├── utils/          # 工具函数
│   ├── types/          # TypeScript类型
│   ├── directives/     # 自定义指令
│   ├── App.vue         # 根组件
│   └── main.ts         # 入口文件
├── index.html          # HTML模板
├── package.json        # 依赖配置
├── tsconfig.json       # TS配置
└── vite.config.ts      # Vite配置
```

## 开始使用

### 安装依赖

```bash
npm install
```

### 开发模式

```bash
npm run dev
```

访问 http://localhost:5173

### 生产构建

```bash
npm run build
```

### 预览构建

```bash
npm run preview
```

## 开发规范

### 命名规范

- 组件名：大驼峰（PascalCase）
- 文件名：短横线（kebab-case）
- 变量名：小驼峰（camelCase）
- 常量名：大写下划线（UPPER_SNAKE_CASE）

### Git提交规范

```
feat: 新功能
fix: 修复bug
docs: 文档更新
style: 代码格式
refactor: 重构
perf: 性能优化
test: 测试
chore: 构建/工具
```

## API配置

开发环境后端API地址配置在 `vite.config.ts`:

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

生产环境在 `.env.production` 中配置。

## 浏览器支持

- Chrome >= 90
- Firefox >= 88
- Safari >= 14
- Edge >= 90

## 相关文档

- [Vue 3](https://cn.vuejs.org/)
- [Element Plus](https://element-plus.org/)
- [TypeScript](https://www.typescriptlang.org/)
- [Vite](https://cn.vitejs.dev/)

## 开发进度

查看 [frontend-development-progress.md](../erpServer/docs/frontend-development-progress.md)

## License

Private

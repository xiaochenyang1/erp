# ERP管理系统前端

基于 Vue 3 + TypeScript + Element Plus 开发的企业资源计划(ERP)管理系统前端。

## 技术栈

- **框架**: Vue 3.4+ (Composition API)
- **语言**: TypeScript 5.0+
- **UI库**: Element Plus 2.8+
- **构建工具**: Vite 8.1+
- **状态管理**: Pinia 2.1+
- **路由**: Vue Router 4.0+
- **HTTP**: Axios 1.7+
- **图表**: ECharts 6.1+

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

### 发布制品与离线复验

在已提交、干净的候选上运行 `npm run release:build`。该命令重新构建，生成
`release-artifacts/dist/`、构建日志和 `manifest.json`，记录候选提交、源码与依赖锁指纹、
每个文件的长度和 SHA-256。目录已存在时拒绝覆盖；可用 `--output /absolute/new/directory`
指定仓库外的新目录。构建失败或构建中源码变化时不会留下有效发布清单。

```bash
npm run release:build -- --output /absolute/new/frontend-artifact
npm run release:verify -- --artifact /absolute/new/frontend-artifact \
  --expected-revision <完整候选提交SHA>
```

保存验证结果中的 `manifestSha256` 到独立交接记录。下载或复制后，用原始记录复验：

```bash
npm run release:verify -- --artifact /absolute/downloaded/frontend-artifact \
  --expected-revision <完整候选提交SHA> --expected-manifest-sha256 <原始清单SHA256>
```

验证脚本应来自已核准候选，不应从待验证包中替换。文件改写、缺失、增加、符号链接、
候选不一致或清单摘要不符都会导致失败。`--allow-dirty-worktree` 仅用于开发诊断，
其结果不能通过默认发布验证。

Frontend Verify 会完成检查、构建、生产依赖审计、上传，再按本轮 artifact ID 下载并复验。
制品名为 `erp-frontend-release-artifacts`，保留 90 天；这不是永久备份，交付时仍须另行归档。
本地完整候选入口 `../backend/scripts/release-candidate-local.sh` 将前端包保存在证据目录的
`frontend/` 下，并在 `artifact-sha256.json` 中绑定清单摘要。技术验证不替代业务签字。

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

### OpenAPI 类型同步

产品主数据模块已使用后端 springdoc 生成类型。版本化契约位于
`../backend/docs/openapi/product-api.json`，生成文件位于
`src/api/generated/product.ts`，禁止手工修改生成文件。

后端在本机运行并开放 `/v3/api-docs` 后刷新契约与类型：

```bash
npm run openapi:refresh
```

仅从已提交契约重新生成，或检查生成文件是否漂移：

```bash
npm run openapi:generate
npm run openapi:check
```

`npm run check:contracts` 已包含漂移检查，并由前端 CI 执行。

## 语言与时区

用户可在顶部个人菜单中切换界面语言和显示时区，偏好保存在浏览器本地。当前支持简体中文、英文，以及 Asia/Shanghai、UTC、America/New_York 和 Europe/London。

后端不含时区的 `LocalDateTime` 按 Asia/Shanghai 解析，然后转换到用户选择的显示时区。`YYYY-MM-DD` 类型的业务日期（如会计期间、交付日）是日历日期，不做时区换算。

新页面应使用 `src/utils/locale.ts` 中的日期和数字格式化函数，避免写死 `zh-CN` 或直接截取时间字符串。

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

查看 [项目当前完成度与缺口盘点](../backend/docs/WHAT_IS_MISSING.md) 和 [未完成功能执行板](../backend/docs/未完成.md)。

## License

Private

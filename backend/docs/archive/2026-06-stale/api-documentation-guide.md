# API文档访问指南

## Swagger UI / OpenAPI

本项目使用 SpringDoc OpenAPI 3 提供交互式API文档。

### 访问地址

**开发环境**:
- Swagger UI: http://localhost:8080/swagger-ui.html
- API文档: http://localhost:8080/v3/api-docs

**生产环境**:
- 根据部署配置，通常禁用或限制访问

### 功能特性

1. **浏览所有API接口** - 按模块分组显示
2. **查看请求参数** - 参数类型、是否必填、示例值
3. **查看响应结构** - 响应格式、字段说明
4. **在线测试** - 直接在页面中测试API
5. **生成客户端代码** - 支持多种语言

### 使用步骤

#### 1. 启动后端服务

```bash
cd E:\tuowei\python\erpServer
.\mvnw.cmd spring-boot:run
```

#### 2. 访问Swagger UI

在浏览器中打开: http://localhost:8080/swagger-ui.html

#### 3. 认证（如需调用需要权限的接口）

1. 点击右上角的 **Authorize** 按钮
2. 在弹出框中输入: `Bearer <your-token>`
   - 先调用 `/api/auth/login` 接口获取token
   - 然后在Authorize中填入: `Bearer eyJhbGci...`（注意Bearer后有空格）
3. 点击 **Authorize** 确认
4. 关闭对话框

#### 4. 测试接口

1. 选择要测试的接口，点击展开
2. 点击 **Try it out** 按钮
3. 填写请求参数
4. 点击 **Execute** 执行
5. 查看响应结果

### API分组说明

- **认证模块** (auth-controller) - 登录、登出、刷新token、获取用户信息
- **系统管理** (system-*) - 用户、角色、菜单、部门、岗位等
- **采购管理** (purchase-*) - 采购订单、收货、退货
- **销售管理** (sales-*) - 销售订单、发货、退货
- **库存管理** (inventory-*) - 库存查询、调整、盘点、调拨、预警
- **财务管理** (finance-*) - 应收应付、收付款、凭证、总账、费用
- **主数据** (masterdata-*) - 产品、客户、供应商、仓库
- **附件管理** (attachment-controller) - 文件上传、下载
- **通知管理** (notification-controller) - 消息通知

### 常见问题

#### Q: 401错误 - Unauthorized
A: Token未配置或已过期，重新登录获取新token

#### Q: 403错误 - Forbidden
A: 当前用户没有该接口的访问权限

#### Q: 如何获取Token？
A: 
1. 找到 `/api/auth/login` 接口
2. 点击 Try it out
3. 输入用户名和密码
4. 执行请求
5. 从响应中复制 `accessToken` 的值

#### Q: 生产环境能访问Swagger吗？
A: 默认配置中生产环境已禁用Swagger UI，出于安全考虑

### 导出API文档

#### JSON格式
访问: http://localhost:8080/v3/api-docs

#### YAML格式
访问: http://localhost:8080/v3/api-docs.yaml

可以将导出的文档导入到其他工具（如Postman、Apifox）使用。

### 配置说明

SpringDoc配置位于 `application-dev.yml`:

```yaml
springdoc:
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
  api-docs:
    enabled: true
    path: /v3/api-docs
```

生产环境配置（`application-prod.yml`）中通常会禁用：

```yaml
springdoc:
  swagger-ui:
    enabled: false
  api-docs:
    enabled: false
```

### 最佳实践

1. **开发阶段** - 使用Swagger UI进行接口调试
2. **联调阶段** - 导出文档给前端团队
3. **测试阶段** - 使用Swagger快速验证接口
4. **生产环境** - 禁用Swagger避免暴露接口信息

### 相关资源

- [SpringDoc官方文档](https://springdoc.org/)
- [OpenAPI规范](https://swagger.io/specification/)
- [Swagger UI文档](https://swagger.io/tools/swagger-ui/)

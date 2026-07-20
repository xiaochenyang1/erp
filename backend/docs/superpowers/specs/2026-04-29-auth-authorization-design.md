# ERP 认证授权闭环设计

**日期**：2026-04-29
**项目**：`erp-server`
**范围**：登录接口、数据库用户认证、JWT 访问令牌、基于 `sys_menu.permission` 的接口鉴权、管理员种子数据。

## 1. 目标

为现有 ERP 后端补齐可测试、可扩展的认证授权闭环：

- 用户通过 `POST /api/auth/login` 使用用户名和密码登录。
- 后端基于 `sys_user` 中的 BCrypt 密码完成认证。
- 登录成功后签发短期 `accessToken`，客户端使用 `Authorization: Bearer <token>` 访问业务接口。
- 业务接口基于 `sys_menu.permission` 做细粒度权限控制。
- Flyway 自动初始化可登录的 `admin` 管理员和核心权限数据。

本设计不包含 refresh token、登录日志、强制改密、多端会话管理和外部 OAuth2/OIDC 集成。

## 2. 技术方案

采用 Spring Security 原生链路：

- `UserDetailsService` 从 `sys_user` 加载用户。
- `AuthenticationManager` 校验用户名和密码。
- 登录成功后由 JWT 服务生成短期 `accessToken`。
- JWT 过滤器解析 Bearer token 并写入 `SecurityContext`。
- Controller 使用 `@PreAuthorize("hasAuthority('permission:code')")` 控制访问。

不采用自定义 `HandlerInterceptor` 鉴权，因为它会绕开 Spring Security 的方法级权限、异常处理和测试支持。也不引入 OAuth2 Resource Server，因为当前是单体 ERP 后端，一期复杂度不划算。

## 3. 登录接口

新增认证模块，提供接口：

```http
POST /api/auth/login
Content-Type: application/json
```

请求体：

```json
{
  "username": "admin",
  "password": "password"
}
```

成功响应：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "accessToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": {
      "id": 4001,
      "username": "admin",
      "realName": "系统管理员"
    },
    "permissions": [
      "system:user:list",
      "purchase:order:approve"
    ]
  }
}
```

失败行为：

- 用户名不存在、密码错误、账号停用或账号删除，统一返回 `401`。
- 请求参数缺失或格式错误返回 `400`。

## 4. JWT 设计

JWT 只保存必要身份信息：

- `sub`：用户名。
- `uid`：用户 ID。
- `iat`：签发时间。
- `exp`：过期时间。

权限不写入 JWT。每次请求解析 token 后，从数据库加载当前用户和权限，保证角色或菜单权限调整后立即生效，不需要等待 token 过期。

配置项：

```yaml
erp:
  security:
    jwt:
      secret: ${ERP_JWT_SECRET:local-dev-secret-change-me}
      access-token-ttl-seconds: 7200
```

生产环境必须通过 `ERP_JWT_SECRET` 覆盖默认密钥。应用启动时需要校验密钥长度，避免弱密钥签发 token。

## 5. 权限模型

权限复用现有数据结构：

```text
sys_user
  -> sys_user_role
  -> sys_role
  -> sys_role_menu
  -> sys_menu.permission
```

权限加载规则：

- 用户必须 `status = 'ACTIVE'` 且 `deleted_flag = 0`。
- 角色必须 `status = 'ACTIVE'` 且 `deleted_flag = 0`。
- 菜单必须 `status = 'ACTIVE'` 且 `deleted_flag = 0`。
- `sys_menu.permission` 为空的记录只用于前端展示，不作为接口权限。

接口权限示例：

```java
@PreAuthorize("hasAuthority('system:user:list')")
@GetMapping
public ApiResponse<PageResponse<UserResponse>> list(UserPageQuery query) {
    return ApiResponse.success(userService.list(query));
}
```

推荐权限码命名：

```text
system:user:list
system:user:create
system:user:update
system:user:enable
system:user:disable
system:user:assign-role
system:role:list
system:role:create
system:role:update
system:role:assign-menu
purchase:order:create
purchase:order:update
purchase:order:submit
purchase:order:approve
purchase:order:reject
purchase:order:cancel
purchase:receipt:create
purchase:receipt:update
purchase:receipt:post
purchase:return:create
purchase:return:update
purchase:return:post
```

`SUPER_ADMIN` 不使用代码后门。它通过种子数据绑定全部核心权限，行为和普通角色一致。

## 6. Flyway 种子数据

新增 Flyway 迁移 `V16__auth_seed_data.sql`，初始化：

- `SUPER_ADMIN` 角色。
- `admin` 用户。
- `admin -> SUPER_ADMIN` 用户角色绑定。
- 核心菜单或按钮权限。
- `SUPER_ADMIN -> 全部核心权限` 角色菜单绑定。

默认管理员密码使用现有 `db/init/05_init_security.sql` 中的 BCrypt 哈希，不在代码中保存明文。文档必须明确：默认管理员仅用于本地和测试初始化，生产部署后必须立即改密。

种子 SQL 必须幂等，可重复执行，不破坏已有用户自定义数据。

## 7. 安全配置

安全链路调整：

- `/api/health`、`/actuator/health`、OpenAPI 地址继续允许匿名访问。
- `POST /api/auth/login` 允许匿名访问。
- 业务接口默认需要认证。
- 需要细粒度限制的接口添加 `@PreAuthorize`。
- 未登录访问返回 `401`。
- 已登录但权限不足返回 `403`。
- 禁用 HTTP Basic，避免与 Bearer token 行为混淆。
- 保持无状态会话：`SessionCreationPolicy.STATELESS`。

## 8. 当前用户与审计衔接

本次设计先提供当前用户上下文能力，供后续审计字段替换使用：

- 能从 `SecurityContext` 获取当前用户 ID、用户名、公司 ID、账套 ID。
- 新增工具或组件统一读取当前用户。

本次实施不强制一次性替换所有 `createdBy=0L`、`updatedBy=0L`、`companyId=1L`、`accountBookId=1L`。这些硬编码会在认证闭环完成后作为独立审计改造处理，避免本次范围失控。

## 9. 测试策略

新增或调整测试覆盖：

- 登录成功返回 `accessToken`、`tokenType=Bearer`、`expiresIn=7200`、用户摘要和权限列表。
- 用户名不存在、密码错误、账号停用返回 `401`。
- 无 token 访问受保护接口返回 `401`。
- 合法 token 访问有权限接口返回成功。
- 合法 token 访问缺少权限接口返回 `403`。
- Flyway 测试验证 `admin`、`SUPER_ADMIN`、用户角色绑定和核心权限存在。

实施完成后必须运行：

```bash
mvn test
```

期望所有既有测试和新增测试通过。

## 10. 非目标

本次不做：

- refresh token。
- token 黑名单或主动吊销。
- 登录日志和操作日志。
- 首次登录强制改密。
- 图形验证码、短信验证码、MFA。
- 多租户数据权限。
- 把所有已有服务的审计字段一次性替换为当前用户。

这些能力可以在认证授权闭环稳定后逐步补齐。

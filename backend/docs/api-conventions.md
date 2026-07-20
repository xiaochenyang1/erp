# API 响应约定

本文档固定当前后端对外 API 的基础响应协议，供前端、开放集成和验收脚本使用。除文件下载、Prometheus 文本、健康检查等明确非 JSON 场景外，业务接口默认返回统一 JSON envelope。

## 统一响应结构

业务 JSON 响应使用 `ApiResponse<T>`：

```json
{
  "code": "0",
  "message": "success",
  "data": {}
}
```

- `code` 是字符串，不是数字。
- `message` 是面向调用方的简短说明。
- `data` 是业务数据；无数据时可为 `null`。
- 成功响应固定使用 `code="0"`，当前不改成 HTTP 状态码或数字枚举。

## 成功与失败

| 场景 | HTTP 状态 | `code` | 说明 |
|---|---:|---|---|
| 业务成功 | 200 | `"0"` | `ApiResponse.success(data)` |
| 未登录或 token 无效 | 401 | `"401"` | Spring Security authentication entry point |
| 已登录但权限不足 | 403 | `"403"` | Spring Security access denied handler |
| 参数校验失败 | 400 | 非 `"0"` | Validation / Bean Validation 错误，由全局异常处理返回 |
| 业务冲突 | 409 | 非 `"0"` | `BusinessConflictException`，例如期间锁账、重复提交、状态不允许 |
| 未知服务端异常 | 500 | 非 `"0"` | 默认不暴露异常明文；生产环境必须关闭未知异常明文 |

前端和集成客户端必须同时看 HTTP 状态和 `code`。HTTP 2xx 代表请求到达并被正常处理，`code="0"` 才代表业务成功。

## 错误消息边界

- `BusinessConflictException` 可返回明确业务原因，便于用户修正操作。
- Validation 错误应返回字段或参数层面的可读提示。
- 未知异常不得在生产环境暴露堆栈、SQL、路径、密钥或内部类名。
- 认证和授权错误统一返回 `code="401"` / `code="403"`，不要在业务 Controller 里手写另一套。

## 非 JSON 响应

以下端点可以不使用 `ApiResponse`：

- CSV / 文件下载接口，按对应 `Content-Type` 和 `Content-Disposition` 返回流。
- `/actuator/prometheus` 返回 Prometheus 文本指标。
- Spring Boot actuator 健康检查可以使用 actuator 原生结构。

新增非 JSON 响应时，必须在接口测试或文档中说明原因，别让调用方猜。

## 变更规则

- 任何改变 `code="0"`、`message="success"`、认证/授权错误码或 envelope 字段名的改动，都属于 API 破坏性变更。
- 破坏性变更必须同步更新本文档、Controller 契约测试和预生产验收脚本。
- 不允许同一业务含义在不同 Controller 返回不同 `code`。

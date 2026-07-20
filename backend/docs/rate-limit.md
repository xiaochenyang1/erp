# API限流功能文档

## 概述

API限流功能基于滑动窗口算法实现，防止API被恶意刷爆，保护系统稳定性。

## 功能特点

✅ **轻量级实现** - 基于内存，无需额外依赖
✅ **滑动窗口算法** - 精确限流，避免突刺流量
✅ **灵活配置** - 可按IP、用户或自定义维度限流
✅ **注解驱动** - 使用简单，一个注解即可
✅ **可开关** - 测试环境可禁用

## 使用方法

### 1. 基本使用

在Controller方法上添加 `@RateLimit` 注解：

```java
@PostMapping("/api/sensitive-operation")
@RateLimit(limit = 10, window = 60)  // 每60秒最多10次请求
public ApiResponse<Void> sensitiveOperation() {
    // 业务逻辑
}
```

### 2. 登录限流（推荐配置）

```java
@PostMapping("/api/auth/login")
@RateLimit(limit = 5, window = 60)  // 每分钟最多5次登录尝试
public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
    return authService.login(request);
}
```

### 3. 自定义限流键

默认按IP地址限流，也可以自定义：

```java
// 按用户ID限流
@RateLimit(
    key = "#{@rateLimitKeyResolver.resolveUserId()}", 
    limit = 100, 
    window = 60
)
```

## 配置

### application.yml

```yaml
erp:
  rate-limit:
    enabled: true  # 生产环境开启
```

### application-test.yml

```yaml
erp:
  rate-limit:
    enabled: false  # 测试环境关闭
```

### 环境变量

```bash
export ERP_RATE_LIMIT_ENABLED=true
```

## 注解参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `limit` | int | 100 | 时间窗口内允许的最大请求数 |
| `window` | int | 60 | 时间窗口大小（秒） |
| `key` | String | `#{@rateLimitKeyResolver.resolveIp()}` | 限流键（支持SpEL表达式） |

## 错误响应

当请求超过限流阈值时，返回：

```json
{
  "code": "429",
  "message": "请求过于频繁，请稍后再试",
  "data": null
}
```

HTTP状态码：`429 Too Many Requests`

## 推荐配置

### 敏感操作限流

| 场景 | limit | window | 说明 |
|------|-------|--------|------|
| 登录 | 5 | 60 | 每分钟5次，防止暴力破解 |
| 修改密码 | 3 | 300 | 每5分钟3次 |
| 发送验证码 | 1 | 60 | 每分钟1次 |
| 导出报表 | 10 | 60 | 每分钟10次 |

### 普通API限流

| 场景 | limit | window | 说明 |
|------|-------|--------|------|
| 查询列表 | 100 | 60 | 每分钟100次 |
| 创建/更新 | 50 | 60 | 每分钟50次 |
| 文件上传 | 20 | 60 | 每分钟20次 |

## 实现原理

### 滑动窗口算法

```
时间窗口: 60秒
限流阈值: 5次

请求时间轴：
|-----|-----|-----|-----|-----|
0s   12s   24s   36s   48s   60s

窗口会随时间滑动，在任意60秒内不超过5次请求
```

### 限流键生成

默认使用IP地址：
- 支持 `X-Forwarded-For` 头（代理场景）
- 回退到 `request.getRemoteAddr()`

### 线程安全

使用 `ConcurrentHashMap` 和 `AtomicLong` 保证线程安全。

## 监控

限流被触发时，会记录WARNING级别日志：

```
WARN  Rate limit exceeded: 请求过于频繁，请稍后再试
```

## 注意事项

1. **内存占用** - 每个限流键占用约100字节内存
2. **分布式部署** - 当前实现基于单机内存，分布式部署需要使用Redis实现
3. **窗口重置** - 窗口重置时计数器归零，可能出现短暂的双倍流量
4. **测试环境** - 测试时记得禁用限流，避免测试失败

## 扩展建议

### 1. 升级为Redis实现（分布式）

```java
// 使用Redis存储限流计数
redisTemplate.opsForValue().increment(key, 1);
redisTemplate.expire(key, window, TimeUnit.SECONDS);
```

### 2. 添加限流监控指标

```java
@Component
public class RateLimitMetrics {
    private final Counter rateLimitCounter;
    
    public RateLimitMetrics(MeterRegistry registry) {
        this.rateLimitCounter = Counter.builder("erp.rate_limit.rejected")
            .description("被限流拒绝的请求数")
            .register(registry);
    }
}
```

### 3. 支持动态配置

从配置中心读取限流参数，无需重启即可调整。

## 示例代码

完整的使用示例参见：
- `AuthController.java` - 登录限流示例
- `RateLimitAspect.java` - 核心实现
- `RateLimit.java` - 注解定义

## 测试

运行测试验证限流功能：

```bash
./mvnw.cmd test -Dtest=AuthControllerContractTest
```

---

**Created**: 2026-06-12  
**Status**: ✅ 已实现并通过所有测试

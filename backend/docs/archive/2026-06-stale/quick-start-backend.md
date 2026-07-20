# ERP后端快速启动指南

## 📋 前置要求

### 必需软件
- ✅ Java 17 - 已安装 (17.0.17)
- ❓ MySQL 8.0+ - 需要确认
- ❓ Redis 6.0+ - 需要确认

---

## 🚀 快速启动步骤

### 方式一：使用IDE启动（推荐）⭐

#### 1. 导入项目
```
打开 IntelliJ IDEA
→ File → Open
→ 选择 E:\tuowei\python\erpServer
→ 等待Maven导入依赖
```

#### 2. 配置数据库
编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/erp?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: root        # 改成你的MySQL用户名
    password: 123456      # 改成你的MySQL密码
  
  data:
    redis:
      host: localhost
      port: 6379
      password:           # 如果Redis有密码，填写在这里
```

#### 3. 创建数据库
在MySQL中执行：
```sql
CREATE DATABASE IF NOT EXISTS erp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 4. 启动应用
```
右键点击 src/main/java/com/tuowei/erp/ErpServerApplication.java
→ Run 'ErpServerApplication'
```

---

### 方式二：命令行启动

#### Windows命令行：

```batch
# 1. 进入项目目录
cd E:\tuowei\python\erpServer

# 2. 编译打包（跳过测试）
mvnw.cmd clean package -DskipTests

# 3. 运行
java -jar target\erp-server-1.0.0.jar
```

#### 或者直接运行（不打包）：

```batch
# 使用Maven插件运行
mvnw.cmd spring-boot:run
```

---

## ⚙️ 配置说明

### application.yml 关键配置

```yaml
server:
  port: 8080              # 应用端口

spring:
  # 数据库配置
  datasource:
    url: jdbc:mysql://localhost:3306/erp
    username: root        # ← 修改这里
    password: your_pwd    # ← 修改这里
  
  # Redis配置
  data:
    redis:
      host: localhost
      port: 6379
      password:           # ← 如果Redis有密码
  
  # Flyway数据库迁移
  flyway:
    enabled: true         # 自动创建表结构
    baseline-on-migrate: true

# 限流配置
erp:
  rate-limit:
    enabled: true         # 开启API限流
```

---

## 🔧 常见问题解决

### 问题1：MySQL连接失败

**错误信息**：
```
Unable to obtain JDBC Connection
```

**解决方案**：
1. 确认MySQL服务正在运行
   ```batch
   # 查看MySQL服务状态
   net start | findstr MySQL
   ```

2. 检查数据库配置
   - 用户名/密码是否正确
   - 数据库 `erp` 是否已创建
   - 端口是否是3306

3. 测试连接
   ```batch
   mysql -u root -p -e "SHOW DATABASES;"
   ```

---

### 问题2：Redis连接失败

**错误信息**：
```
Unable to connect to Redis
```

**解决方案**：
1. 确认Redis服务正在运行
   ```batch
   # 查看Redis服务
   net start | findstr Redis
   
   # 或测试连接
   redis-cli ping
   ```

2. 如果没有Redis：
   - **选项A**：安装Redis（推荐）
   - **选项B**：临时禁用Redis缓存
     在 `application.yml` 中添加：
     ```yaml
     spring:
       cache:
         type: none
       data:
         redis:
           enabled: false
     ```

---

### 问题3：端口被占用

**错误信息**：
```
Port 8080 was already in use
```

**解决方案**：
1. 修改端口
   在 `application.yml` 中：
   ```yaml
   server:
     port: 8081  # 改成其他端口
   ```

2. 或者杀掉占用进程
   ```batch
   # 查看占用8080的进程
   netstat -ano | findstr :8080
   
   # 杀掉进程（PID是上一步看到的数字）
   taskkill /F /PID <PID>
   ```

---

### 问题4：Maven依赖下载慢

**解决方案**：
使用国内镜像，编辑 `pom.xml`：

```xml
<repositories>
  <repository>
    <id>aliyun</id>
    <url>https://maven.aliyun.com/repository/public</url>
  </repository>
</repositories>
```

或者编辑 `~/.m2/settings.xml`：

```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

---

## ✅ 启动成功的标志

看到以下日志说明启动成功：

```
  ______ _____  _____     _____                           
 |  ____|  __ \|  __ \   / ____|                          
 | |__  | |__) | |__) | | (___   ___ _ ____   _____ _ __ 
 |  __| |  _  /|  ___/   \___ \ / _ \ '__\ \ / / _ \ '__|
 | |____| | \ \| |       ____) |  __/ |   \ V /  __/ |   
 |______|_|  \_\_|      |_____/ \___|_|    \_/ \___|_|   

Started ErpServerApplication in 8.345 seconds
```

---

## 🧪 验证是否启动成功

### 1. 访问健康检查
打开浏览器访问：
```
http://localhost:8080/actuator/health
```

应该看到：
```json
{
  "status": "UP"
}
```

### 2. 访问业务健康检查
```
http://localhost:8080/actuator/health/business
```

### 3. 查看监控指标
```
http://localhost:8080/actuator/prometheus
```

---

## 📝 启动后的默认配置

- **应用端口**: 8080
- **数据库**: erp
- **日志目录**: `logs/`
- **附件目录**: 默认临时目录

---

## 🎯 下一步

启动成功后：

1. ✅ 查看日志确认没有错误
2. ✅ 访问健康检查端点验证
3. ✅ 启动前端项目（http://localhost:5173）
4. ✅ 前端登录测试（admin/admin123）

---

## 📞 需要帮助？

如果遇到问题：
1. 查看日志文件 `logs/erp-server.log`
2. 检查控制台错误信息
3. 参考上面的"常见问题解决"

---

**创建时间**: 2026-06-12  
**文档版本**: v1.0

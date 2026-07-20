# 生产部署检查清单

本文档提供生产环境部署前的完整检查清单和部署步骤。

## 📋 部署前检查清单

### ✅ 1. 环境准备

#### 1.1 服务器要求

- [ ] **CPU**: 4核以上（推荐8核）
- [ ] **内存**: 8GB以上（推荐16GB）
- [ ] **磁盘**: 
  - 系统盘: 50GB SSD
  - 数据盘: 200GB以上（根据业务量调整）
- [ ] **操作系统**: CentOS 7+ / Ubuntu 20.04+ / Red Hat 8+
- [ ] **网络**: 内网互通，允许外部访问8080端口

#### 1.2 软件依赖

- [ ] **Java**: OpenJDK 17 或 Oracle JDK 17
  ```bash
  java -version  # 验证版本
  ```

- [ ] **MySQL**: 8.0+
  ```bash
  mysql --version  # 验证版本
  ```

- [ ] **Redis**: 6.0+
  ```bash
  redis-cli --version  # 验证版本
  ```

### ✅ 2. 数据库准备

- [ ] **创建数据库**
  ```sql
  CREATE DATABASE erp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```

- [ ] **创建应用用户**
  ```sql
  CREATE USER 'erp_app'@'%' IDENTIFIED BY 'strong_password_here';
  GRANT ALL PRIVILEGES ON erp.* TO 'erp_app'@'%';
  FLUSH PRIVILEGES;
  ```

- [ ] **验证连接**
  ```bash
  mysql -h <host> -u erp_app -p erp
  ```

- [ ] **配置优化**（根据服务器配置调整）
  ```ini
  # /etc/my.cnf
  [mysqld]
  max_connections = 500
  innodb_buffer_pool_size = 4G
  innodb_log_file_size = 512M
  character-set-server = utf8mb4
  collation-server = utf8mb4_unicode_ci
  ```

### ✅ 3. Redis准备

- [ ] **配置密码**
  ```bash
  # /etc/redis/redis.conf
  requirepass your_redis_password_here
  maxmemory 2gb
  maxmemory-policy allkeys-lru
  ```

- [ ] **验证连接**
  ```bash
  redis-cli -a your_redis_password_here ping
  # 应返回: PONG
  ```

### ✅ 4. 环境变量配置

创建 `/etc/erp-server/.env` 文件：

```bash
# 应用配置
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

# 数据库配置
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/erp?useSSL=true&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=erp_app
SPRING_DATASOURCE_PASSWORD=your_db_password_here

# Redis配置
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=your_redis_password_here

# JWT密钥（必须修改为随机字符串，至少32字符）
ERP_JWT_SECRET=your-very-long-random-secret-key-at-least-32-characters-here

# 附件存储
ERP_ATTACHMENT_STORAGE_ROOT=/data/erp/attachments

# 日志路径
LOG_PATH=/data/erp/logs

# 可选：限流开关
ERP_RATE_LIMIT_ENABLED=true

# 可选：信任的代理IP（nginx/负载均衡）
ERP_TRUSTED_PROXIES=192.168.1.100,192.168.1.101
```

**安全注意**：
- [ ] `.env` 文件权限设置为 600
  ```bash
  chmod 600 /etc/erp-server/.env
  chown erp-app:erp-app /etc/erp-server/.env
  ```

### ✅ 5. 目录准备

- [ ] **创建应用目录**
  ```bash
  mkdir -p /opt/erp-server
  mkdir -p /data/erp/logs
  mkdir -p /data/erp/attachments
  mkdir -p /data/backups/mysql
  ```

- [ ] **设置权限**
  ```bash
  useradd -r -s /bin/false erp-app
  chown -R erp-app:erp-app /opt/erp-server
  chown -R erp-app:erp-app /data/erp
  chown -R erp-app:erp-app /data/backups
  ```

### ✅ 6. 应用构建

- [ ] **编译打包**
  ```bash
  ./mvnw.cmd clean package -DskipTests
  # 或 Linux/Mac:
  ./mvnw clean package -DskipTests
  ```

- [ ] **验证JAR文件**
  ```bash
  ls -lh target/erp-server-*.jar
  ```

- [ ] **复制到服务器**
  ```bash
  scp target/erp-server-1.0.0.jar erp-server:/opt/erp-server/
  ```

### ✅ 7. 数据库迁移

- [ ] **执行Flyway迁移**
  ```bash
  java -jar erp-server-1.0.0.jar --spring.flyway.enabled=true
  ```

- [ ] **验证表结构**
  ```sql
  USE erp;
  SHOW TABLES;
  SELECT * FROM flyway_schema_history;
  ```

### ✅ 8. 初始数据

- [ ] **创建超级管理员用户**（应用启动后通过API）
  ```bash
  curl -X POST http://localhost:8080/api/system/users \
    -H "Content-Type: application/json" \
    -d '{
      "username": "admin",
      "password": "change_me_immediately",
      "email": "admin@company.com",
      "mobile": "13800138000"
    }'
  ```

- [ ] **首次登录后立即修改密码**

### ✅ 9. Systemd服务配置

创建 `/etc/systemd/system/erp-server.service`：

```ini
[Unit]
Description=ERP Server
After=network.target mysql.service redis.service

[Service]
Type=simple
User=erp-app
Group=erp-app
WorkingDirectory=/opt/erp-server
EnvironmentFile=/etc/erp-server/.env
ExecStart=/usr/bin/java \
    -Xms2g \
    -Xmx4g \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/data/erp/logs/heap-dump.hprof \
    -Djava.security.egd=file:/dev/./urandom \
    -jar /opt/erp-server/erp-server-1.0.0.jar

Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=erp-server

[Install]
WantedBy=multi-user.target
```

- [ ] **启用服务**
  ```bash
  systemctl daemon-reload
  systemctl enable erp-server
  systemctl start erp-server
  ```

- [ ] **验证服务状态**
  ```bash
  systemctl status erp-server
  journalctl -u erp-server -f
  ```

### ✅ 10. 数据库备份配置

- [ ] **复制备份脚本**
  ```bash
  cp scripts/backup-database.sh /opt/erp-server/
  chmod +x /opt/erp-server/backup-database.sh
  ```

- [ ] **配置定时备份**
  ```bash
  crontab -e -u erp-app
  # 添加以下行（每天凌晨2点备份）
  0 2 * * * /opt/erp-server/backup-database.sh production >> /data/erp/logs/backup.log 2>&1
  ```

- [ ] **测试备份**
  ```bash
  sudo -u erp-app /opt/erp-server/backup-database.sh production
  ```

### ✅ 11. 监控配置

- [ ] **配置Prometheus**
  - 添加 `/etc/prometheus/prometheus.yml`：
    ```yaml
    scrape_configs:
      - job_name: 'erp-server'
        metrics_path: '/actuator/prometheus'
        static_configs:
          - targets: ['localhost:8080']
    ```

- [ ] **配置告警规则**
  ```bash
  cp monitoring/alert-rules.yml /etc/prometheus/rules/
  ```

- [ ] **验证指标**
  ```bash
  curl http://localhost:8080/actuator/prometheus
  ```

### ✅ 12. 反向代理配置（Nginx）

创建 `/etc/nginx/conf.d/erp-server.conf`：

```nginx
upstream erp_backend {
    server 127.0.0.1:8080;
    keepalive 32;
}

server {
    listen 80;
    server_name erp.company.com;

    # 重定向到HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name erp.company.com;

    ssl_certificate /etc/nginx/ssl/erp.crt;
    ssl_certificate_key /etc/nginx/ssl/erp.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    client_max_body_size 20M;

    location / {
        proxy_pass http://erp_backend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    location /actuator {
        deny all;  # 禁止外部访问监控端点
    }
}
```

- [ ] **测试Nginx配置**
  ```bash
  nginx -t
  ```

- [ ] **重载Nginx**
  ```bash
  systemctl reload nginx
  ```

### ✅ 13. 防火墙配置

- [ ] **开放必要端口**
  ```bash
  # CentOS/RHEL
  firewall-cmd --permanent --add-service=http
  firewall-cmd --permanent --add-service=https
  firewall-cmd --reload

  # Ubuntu
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw enable
  ```

### ✅ 14. 启动后验证

- [ ] **健康检查**
  ```bash
  curl http://localhost:8080/actuator/health
  # 应返回: {"status":"UP"}
  ```

- [ ] **业务健康检查**
  ```bash
  curl http://localhost:8080/actuator/health/business
  ```

- [ ] **登录测试**
  ```bash
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"your_password"}'
  ```

- [ ] **API限流测试**
  ```bash
  for i in {1..10}; do
    curl http://localhost:8080/api/auth/login
  done
  # 第6次应返回429
  ```

- [ ] **查看日志**
  ```bash
  tail -f /data/erp/logs/erp-server.log
  tail -f /data/erp/logs/audit.log
  ```

### ✅ 15. 性能测试（可选）

- [ ] **压力测试**
  ```bash
  # 使用Apache Bench
  ab -n 1000 -c 10 http://localhost:8080/actuator/health
  ```

- [ ] **监控资源使用**
  ```bash
  top -p $(pgrep -f erp-server)
  ```

## 🚀 部署步骤总结

### 快速部署（已准备好环境）

```bash
# 1. 停止旧版本
systemctl stop erp-server

# 2. 备份数据库
/opt/erp-server/backup-database.sh production

# 3. 部署新版本
cp erp-server-new.jar /opt/erp-server/erp-server-1.0.0.jar

# 4. 启动服务
systemctl start erp-server

# 5. 验证
curl http://localhost:8080/actuator/health

# 6. 查看日志
journalctl -u erp-server -f
```

## 🔧 故障排查

### 服务无法启动

```bash
# 查看启动日志
journalctl -u erp-server -n 100 --no-pager

# 检查端口占用
netstat -tlnp | grep 8080

# 检查文件权限
ls -l /opt/erp-server/
ls -l /data/erp/
```

### 数据库连接失败

```bash
# 测试数据库连接
mysql -h localhost -u erp_app -p erp

# 检查防火墙
firewall-cmd --list-all
```

### 内存溢出

```bash
# 分析堆转储
jmap -dump:live,format=b,file=heap.bin $(pgrep -f erp-server)

# 调整JVM参数（在systemd配置中）
-Xms4g -Xmx8g
```

## 📞 上线后联系人

- **技术负责人**: [姓名] [电话]
- **DBA**: [姓名] [电话]
- **运维**: [姓名] [电话]

## 📝 上线记录

- **上线日期**: _______________
- **版本号**: _______________
- **部署人**: _______________
- **验证人**: _______________
- **备注**: _______________

---

**检查清单完成日期**: _______________  
**签字**: _______________

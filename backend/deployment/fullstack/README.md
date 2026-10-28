# ERP 前后端部署包

此包包含已经通过技术检查的 JAR、前端静态文件、SBOM、Nginx 和 Compose 配置。应用候选与部署工具版本分别记录在 `deployment-manifest.json`。它不是业务验收通过或生产发布 GO。

## 校验和初始化

使用可信源码检出中的 `backend/scripts/deployment-bundle.mjs`，不要从未验证的下载内容替换校验工具。Node.js 22 用于校验及生成配置；启动服务需要 Docker Compose，不需要 Maven 或重新构建 Vue。

```bash
node /trusted/erp/backend/scripts/deployment-bundle.mjs verify \
  --bundle /absolute/bundle --expected-manifest-sha256 <交接记录中的清单摘要>
node /trusted/erp/backend/scripts/deployment-bundle.mjs init \
  --bundle /absolute/bundle --expected-manifest-sha256 <交接记录中的清单摘要> \
  --origin http://127.0.0.1:8080 --port 8080
cd /absolute/bundle
docker compose --project-name erp-preprod --env-file .env -f compose.yaml up -d --build --wait --wait-timeout 300
```

`init` 为数据库、Redis、JWT 和首次管理员生成随机凭据，保存在权限为 `0600` 的 `.env`，不输出密码、不覆盖已有配置。管理员用户名是 `admin`；首次密码取该私有文件的 `ERP_BOOTSTRAP_ADMIN_PASSWORD`。不要将 `.env` 加入制品或版本控制。

`--origin` 必须是实际前端 Origin。默认只有 Nginx 的 HTTP 端口绑定宿主机回环地址，数据库、Redis 和后端不发布宿主机端口。服务器部署时，在宿主机或可信入口终止 TLS，并把 Origin 改为真实 HTTPS 域名。此默认包不包含目标环境证书和 DNS 配置。

Compose 使用项目隔离的持久卷。保持项目名不变可保留数据库和附件；`docker compose down` 停止容器并保留卷。常规升级、停止或回退不要使用 `down --volumes`。

## 入口和运维边界

- `/` 和前端深链接由 Nginx 提供；刷新业务页面不会返回静态 404。
- `/api/` 保留原始 API 路径、认证和下载响应头；错误响应不会降级成首页 HTML。
- `/readyz` 表示后端初始化已完成；`/__deployment.json` 返回应用和部署工具版本。
- 带哈希的 `/assets/` 长缓存；入口 HTML 和版本信息不缓存；缺失资源返回 404。
- 公共入口不转发 `/actuator/`；监控应在内部访问 `erp-server:8080/actuator/prometheus`，并使用 Bearer Token。
- Nginx、Servlet multipart 与业务附件默认上限配套为单文件 20 MB。若扩大限额，应同时调整三处并重新验证；默认覆盖 5 MB 导入文件和正常附件上传。
- Nginx 会覆盖客户端提交的转发 IP 头。只有在明确代理拓扑后，才设置 `.env` 的 `ERP_TRUSTED_PROXIES`；默认不信任转发头。若上游还有 TLS 代理，按目标拓扑配置真实 IP 和协议传递，不能任意信任外部传入头。
- 数据库、Redis、应用及 Nginx 基础镜像首次构建需要可访问镜像仓库；应用字节不会重新构建。基础镜像版本与实际运行 image ID 应随目标部署记录保存。

## 预生产验收

配置实际账套、仓库、开放期间和岗位账号，导入真实主数据与期初数据；执行采购到付款、销售到收款、退货、生产、核销及月结，再完成财务/质检签字。数据库与附件备份、上一版本回退、TLS/代理、真实告警和容量验收仍在目标环境完成。

升级前保存原包、私有配置及数据库/附件备份。更换包之前先按独立摘要复验；Flyway 已迁移的数据库不能通过仅换回旧 JAR 保证兼容，回退方案必须包含对应数据恢复策略。

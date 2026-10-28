# 前后端统一部署

`docker-compose.yml` 保留后端开发/部署入口。需要交付完整 Web 应用时，使用 `scripts/deployment-bundle.mjs` 从已通过本地技术门禁的候选目录生成自包含部署包。它复用原始 JAR 和前端字节，不重新打包或把旧包改标成新应用版本。

包内提供 MySQL、Redis、真实 prod 后端及非 root Nginx。仅 Web 端口默认绑定宿主机回环地址；数据库、Redis、后端保留在 Compose 内部。Nginx 提供业务深链接、同源 API 代理、静态缓存策略、20 MB 附件支持和后端重建后的 DNS 更新。服务数据与附件使用项目隔离的持久卷。

## 生成和复验

在干净、已提交的工具检出中执行。`--expected-revision` 是被测应用的完整提交；`--expected-artifacts-sha256` 是原候选 `artifact-sha256.json` 的独立摘要，应从可信交接记录获得。应用候选与工具提交可以不同，两者都会写入部署清单。

```bash
node backend/scripts/deployment-bundle.mjs build \
  --candidate /absolute/accepted-candidate \
  --expected-revision <应用完整SHA> \
  --expected-artifacts-sha256 <原候选artifact-sha256.json的SHA256> \
  --output /absolute/new/deployment-bundle
node backend/scripts/deployment-bundle.mjs verify \
  --bundle /absolute/new/deployment-bundle \
  --expected-manifest-sha256 <build输出的manifestSha256>
```

候选必须为 PASSED、来自干净工作树，前后端候选一致且全部摘要匹配。输出目录已存在时拒绝覆盖。生成后把 `manifestSha256` 单独记录在交接材料；复制/下载后先用可信工具按该摘要复验。额外文件、缺失、改写和符号链接均被拒绝，唯一可变文件是包根目录的私有 `.env`。

## 初始化和启动

```bash
node backend/scripts/deployment-bundle.mjs init \
  --bundle /absolute/new/deployment-bundle \
  --expected-manifest-sha256 <清单摘要> \
  --origin http://127.0.0.1:8080 --port 8080
cd /absolute/new/deployment-bundle
docker compose -p erp-preprod --env-file .env -f compose.yaml up -d --build --wait --wait-timeout 300
```

初始化仅生成 `0600` 私有配置，拒绝覆盖、不输出凭据。部署细节和停止/升级注意事项在包内 `README.md`；目标域名、TLS、代理信任范围和实际岗位/数据仍需绑定。不要把本机默认 Origin 当作真实预生产环境。

本机新库需要可操作样例时，继续使用 [采销与财务演示](LOCAL-BUSINESS-TRIAL.md)，生成受限录单账号和保留的业务单据，核对分次收付款及库存结余。

## 隔离运行验证

```bash
node --test backend/scripts/deployment-bundle.test.mjs
node backend/scripts/deployment-smoke.mjs \
  --bundle /absolute/new/deployment-bundle \
  --expected-manifest-sha256 <清单摘要> \
  --evidence /absolute/new/deployment-evidence
```

需要 Docker、Node.js 22 和 Chrome/Edge（可设置 `CHROME_PATH`）。脚本复制部署包，生成临时私有配置，用随机 Compose 项目及回环端口启动一次性环境；不连接任何既有预生产地址。它验证真实浏览器登录/深链接刷新、静态文件字节和缓存、API 认证及错误、2 MB 附件上传下载，以及强制更换后端 IP 后 Nginx 无重启恢复和附件持久化。日志脱敏，记录应用/打包工具/运行工具版本、镜像和清理结果；失败不会生成 PASSED。

隔离脚本结束时删除自己创建的容器、卷、镜像标签及临时凭据。正常部署应保留卷，不能照搬测试清理命令。目标环境历史数据升级、数据库和附件恢复、上一版回退、真实告警、TLS/代理、容量以及业务签字仍按 [预生产交接](PREPROD-HANDOFF-2026-10-21.md) 执行。

# F10 月结前置条件修复与候选复验

本次继续推进项目分析中的第一步：处理最新候选 `d6703b6` 的 F10 失败，随后重新收集干净候选证据。完整候选是否通过，以实际生成的 `summary.json` 为准。

**已复现的原因**

复用原失败候选留下的独立 `erp_ui_smoke` 测试卷，启动相同应用后读取 2099-01 的 `close-check`：唯一失败项为 `FX_REVALUATION / 尚未执行期末调汇`，未结原币合计为 265.83；其余六项检查通过。多币种尾差 UI 样例留下了未结外币应收，未来期间仍包含这些余额。2099 年只隔开业务日期，不隔开账套余额。

诊断证据：`/Users/xiao/Desktop/python/erp-validation/2026-10-21-f10-diagnosis/original-close-check.json`。

**修复行为**

- F10 先读取并保存月结检查明细。仅当问题全部为“尚未执行期末调汇”时，通过正常 API 完成调汇，再次检查后锁定期间。
- 缺汇率、调汇依据过期、借贷不平和其他月结问题继续失败，不自动修改汇率或财务记录以消除问题。
- 已锁定或关闭的既有期间不会被脚本擅自解锁。测试要求专用验收账套和 OPEN 的 2099-01 期间。
- 目标负例必须返回明确的锁定期间 HTTP 409，且库存调整仍为 DRAFT；泛化错误、缺期间或权限错误不能冒充通过。
- 结束时取消本次创建的调整草稿、停用样例商品，并撤销本次新增的调汇。已有或并发创建的调汇不由本轮撤销。清理失败会令 F10 失败。
- `human-items-report.json` 保留调汇前后月结检查、锁定、过账拒绝、单据回查、解锁和清理证据。

原失败样例库定向复跑已通过；随后完整 F/Q 技术预检 `technicalPass=true`，补充四项 4/4 通过。此为开发期复验，不能替代干净候选检查或真人签字。证据：`/Users/xiao/Desktop/python/erp-validation/2026-10-21-f10-fixed-fq-replay/`。

新增 F10 脚本行为测试覆盖缺调汇、正常期间、无关/过期前置条件、已锁定期间、调汇失败、二次检查失败、并发已有调汇、错误负例及清理失败；已接入本地候选入口和 UI CI。

**本机验证环境修复**

复验期间另遇到浏览器断连和临时 MySQL 启动失败。MySQL 日志明确为 `No space left on device`：Colima 的 60 GiB 虚拟磁盘已满，主要占用是闲置匿名卷。

仅选取已确认无容器引用、且数据库目录属于 `erp_codex_test` / `erp_ui_smoke` 的 18 个旧验收卷，逐一完整归档到宿主机、以 `tar --compare` 对照源数据校验并记录 SHA256 后移出 Docker。未删除未知卷或停止其他项目服务。归档及恢复所需元数据位于：

`/Users/xiao/Desktop/python/erp-validation/2026-10-21-docker-test-volume-archive/manifest.json`

本地测试脚本现改为显式删除本轮容器及匿名卷；保留退出容器至清理阶段，以便读取启动失败日志。UI 入口归档 `ui-smoke-mysql.log`，启动异常不再只留下超时提示。完整候选结束后需核对未新增遗留卷。

**后续执行**

```bash
cd backend
PWSH="$HOME/.local/bin/pwsh" caffeinate -is ./scripts/release-candidate-local.sh \
  /absolute/new/evidence-directory
```

预生产仍需提供目标地址、部署方式、私有配置文件路径、专用验收账套/账号以及真实期初核对基准。取得信息后，继续按 [预生产交接](PREPROD-HANDOFF-2026-10-21.md) 完成部署、业务和目标环境运维验收；本机技术预检不代替财务/质检签字及上线决定。

**2026-10-21 预生产交接：恢复演练结果与 F/Q 执行入口**

后续 F10 复验：`d6703b6` 新增完整 F/Q 后发现未来期间仍需执行期末调汇；已补齐验收前置条件和清理，原失败样例库完整 F/Q 技术预检通过。原因、开发期证据及新候选复验入口见 [F10 修复记录](F10-ACCEPTANCE-FIX-2026-10-21.md)。新候选结果以实际归档为准。

本机生产配置与数据库恢复演练已完成：`/Users/xiao/Desktop/python/erp-validation/2026-10-21-production-recovery-rerun/report.json` 为 `PASSED`，14/14 项通过，临时资源清理通过。

- 被测应用候选：`d2d6975faccb2d71fb8218ab4c59accbc1c421df`。
- 演练工具提交：`1e3f67bb3d1ef17752f236568086b9e4aa2065b3`。
- 候选完整本地技术证据：`/Users/xiao/Desktop/python/erp-validation/2026-10-21-release-prod-fixes/`。
- 演练验证了损坏备份拒绝、131 张表行数及全库数据哈希一致、恢复后的登录/业务查询、原库与恢复库数据隔离、监控重新抓取和实际停机/恢复通知；告警使用原始生产等待时间。

此前重启超时对应宿主机休眠。完整复跑通过，原失败报告保留。应用 JAR 未因本轮演练工具调整而修改；后续工具版本与被测应用版本分别记录。

**财务／质检预检如何接到真实预生产**

`fq-signoff-all.cjs`、`fq-signoff-api-check.cjs`、`fq-signoff-human-items.cjs` 共用目标配置。本机 loopback 的 `local-runtime` 保留原有默认账号；预生产要求显式填写地址、两类账号、环境/数据库/Redis 标识、被测应用提交和独立证据目录。通过本机隧道访问预生产时，仍须设置 `ERP_FQ_ENVIRONMENT=preprod`。

复制 [.env.fq.preprod.example](../.env.fq.preprod.example) 到仓库外的私有文件，限制文件读取权限，填写真实值。该配置与部署用 `.env.prod` 分开；账号应属于专用验收账套。执行者账号需具备建立样例、验收记录和权限负例所需权限；提交账号应符合现有审批职责分离要求。

在 `backend` 目录先检查配置。此命令输出脱敏环境、应用/工具提交及证据位置，不登录、不创建业务数据：

```bash
node --env-file=/absolute/private/fq-preprod.env scripts/fq-signoff-all.cjs --config-check
```

部署候选并按 [P0 执行包](P0-HUMAN-GATES-EXECUTION-PACKAGE-2026-10-16.md) 生成当前环境的费用、凭证、收发货和质检样例后，再执行：

```bash
node --env-file=/absolute/private/fq-preprod.env scripts/fq-signoff-all.cjs
```

该命令执行 F/Q 正向查询、F10/F11/Q4/Q5 负例并登记技术验收项，不提交 GO。负例会生成 2099 年会计期间及测试主数据、单据、角色和用户，必须使用隔离验收账套；F10 仅在缺少期末调汇时按正常 API 补齐前置条件，结束时撤销本轮新建的调汇，其他月结问题继续报告失败。清理是业务补偿，不是数据库回滚。每轮预生产使用新证据目录，拒绝覆盖旧材料。`all-summary.json` 的 `context` 同时记录被测候选和工具提交；汇总会校验两个子报告来自同一环境/版本。

本机可用一次性 MySQL 和完整 UI 样例验证入口：

```bash
ERP_UI_SMOKE_FQ_REGRESSION=1 \
ERP_FQ_EVIDENCE_DIRECTORY=/absolute/new/fq-evidence \
./scripts/ui-smoke-local.sh
```

该入口会完成 UI 工作流，随后对同一临时库执行 F/Q 技术预检，退出时删除临时数据库。`release-candidate-local.sh` 已纳入该步骤及配置测试。正式候选结论仍以对应提交实际生成的报告为准。

**下一步仍需绑定的信息**

| 项目 | 用途 | 当前状态 |
|---|---|---|
| 独立预生产地址、部署入口、配置文件路径 | 部署已验收制品并确认实际 MySQL/Redis/网络拓扑 | 尚未提供 |
| 专用验收账套、业务/财务/质检账号、仓库 ID、开放期间 | 真实岗位权限、业务链与多币种/月结验收 | 尚未绑定 |
| 真实主数据、期初库存、应收应付及核对基准 | 对账并识别历史数据兼容问题 | 尚未提供 |
| 运维、财务、质检、业务负责人 | 目标环境演练、逐项签字及试运行安排 | 尚未绑定 |

部署与业务链继续使用 [生产部署](production-deployment.md) 和 `preprod-full-acceptance.ps1`；监控使用 [监控部署说明](monitoring/README.md)。还须完成目标环境历史数据兼容、上一版镜像回退、附件备份恢复、TLS/反向代理及容量验收。上述本机通过记录不替代这些验收。

**ERP 发布候选与验收交接 · 2026-10-15**

当前交付目标：将多币种收尾与 MRP 修复整理为干净候选，完成本地完整发布检查和浏览器回归，准备真实预生产验收。正式上线仍需要目标环境的执行证据和财务/质检确认。最终检查结果、准确 commit 和制品 SHA256 以独立证据目录的报告为准，本文件不自引用提交哈希，也不预填检查通过。

**本轮 MRP 修复范围**

- 安全库存按目标结余参与总需求，现存与在途/在制供应仅抵扣一次；销售消耗库存后低于目标时也会产生建议。
- 同一物料的直接销售、安全库存、多个 BOM 材料需求合并净算；既直接生产又作为材料的商品只使用尚未分配的供应。
- BOM 展开使用基准数量与损耗率，舍入方式与生产工单一致。
- 预警规则查询使用实际的 `enabled` 字段。移除吞掉安全库存查询错误、以全量采购量冒充未收货量的异常降级；查询异常中止本次运行。
- 保留 MRP-lite 单层计划：直接需求有 BOM 时建议生产，展开的材料建议采购。未新增递归生产排程；现有跨仓安全库存仍按商品的最大规则阈值汇总，不是逐仓补货计划。
- 历史 MRP 运行记录不回写。未转单的旧建议应重新运行后复核，避免沿用旧数量。

业务验收算例：

| 场景 | 正确结果 |
|---|---|
| 安全库存 10，现存 4，无其他需求/供应 | 采购 6 |
| 安全库存 10，现存 6，无其他需求/供应 | 采购 4 |
| 销售 5、安全库存 10、现存 12 | 采购 3 |
| 材料销售 5、生产用料 6、材料现存 5 | 材料采购 6 |
| BOM 基准 10、用料 4、损耗 10%，生产 6 | 工单用料与对应 MRP 材料需求均为 2.64 |
| 禁用的预警规则、其他账套库存 | 不影响当前启用规则和当前账套净需求 |

**依赖安全收尾**

生产依赖审计发现旧锁文件中的 Vue/server-renderer、Axios、source-map-js 涉及高危告警。本轮升级为 Vue 3.5.44、Axios 1.20.0、source-map-js 1.2.2（传递依赖），更新锁文件；升级后生产依赖审计为 0 漏洞。最终候选仍须执行以下完整检查，旧候选检查不能替代升级后回归。

**本地候选验证入口**

前置：Java 17、Maven Wrapper、Node 22、前端依赖、Docker、PowerShell 7、Chrome/Edge；`pwsh` 位于 PATH。下面的后端和 UI 入口使用独立临时 MySQL，结束后清理，不依赖本机业务库。

```bash
cd frontend
npm audit --omit=dev --audit-level=high --registry=https://registry.npmjs.org
npm run type-check
npm run lint
npm test
npm run check:contracts
npm run build
cd ../backend
./scripts/test-local.sh --release-check -MavenRepoLocal "$HOME/.m2/repository"
./scripts/ui-smoke-local.sh
```

`--release-check` 调用现有 PowerShell 正式检查入口并强制包含 Testcontainers，默认拒绝未提交修改。报告位于 `target/release-check-report.json`。若需独立目录，可增加 `-ReportDirectory /absolute/evidence/path`。执行 MRP 页面专项可使用：

```bash
UI_SMOKE_ROUTES=0 UI_SMOKE_WORKFLOW=mrp ./scripts/ui-smoke-local.sh
```

完整 UI 报告包含 MRP 页面运行、建议数量、转采购及采购单数量核对，以及既有多币种和采销产等流程。不要在全量通过后用专项报告覆盖唯一的完整报告副本。

**候选归档要求**

在仓库之外建立独立证据目录，保存：完整 Git SHA、干净工作区检查、源文件 SHA256 清单、前端检查日志与构建、后端 XML、发布检查 JSON/Markdown、JAR、两份 SBOM、完整 UI 报告及日志、迁移版本清单。历史失败日志单独保留，不计作最终结果。正式候选必须记录 `allowDirtyWorktree=false`，UI 报告必须记录同一 SHA 和 `dirtyWorktree=false`。

**真实预生产交接**

需要环境负责人提供已有的部署地址/方式、独立 MySQL 与 Redis、环境文件位置、测试岗位账号和业务日期/仓库；凭据使用环境变量或受控配置，不写入本文。当前没有绑定真实目标环境。

先用 [生产部署说明](production-deployment.md) 配置目标环境，再执行现有 `scripts/preprod-full-acceptance.ps1`，明确传入目标 `EnvFile`、`BaseUrl`、`EvidenceDirectory`、账号/令牌和仓库参数。该脚本会启动或检查目标服务并写入验收业务数据，须只对确认的预生产环境执行。不要用本地 smoke 数据冒充真实期初数据。

| 项目 | 负责人 | 需要的证据 | 当前业务放行状态 |
|---|---|---|---|
| 数据库 V167/V168 升级、既有数据兼容 | 开发/运维 | 从目标环境备份副本升级的日志和金额核对 | 待目标环境验收 |
| 岗位与跨账套权限 | 业务/实施 | 采购、销售、仓库、财务、审批的正反例 | 待目标环境验收 |
| 真实主数据与期初余额 | 业务/财务 | 导入核对、库存与账簿对平 | 待业务确认 |
| 财务 F1–F12、质检 Q1–Q5 | 财务/质检 | 逐项核对和真实签字；补充多币种与 MRP 算例 | 待业务确认 |
| 备份恢复与回滚 | 运维 | 实际恢复和回滚演练记录、耗时 | 待目标环境验收 |
| 监控与告警 | 运维 | 告警触发、送达、恢复记录 | 待目标环境验收 |
| GO/NO-GO | 授权业务负责人 | 对同一候选证据包作出的决定 | 未放行 |

财务/质检执行依据为 [业务验收清单](business-readiness-checklist.md) 与 [财务质检 runbook](finance-qc-acceptance-runbook.md)。固定资产、税务、门户和高级生产仍按独立需求排期。

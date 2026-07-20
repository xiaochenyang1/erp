# 工作区安全盘点（2026-07-17）

**原则**: 不丢改动、不强制 push、不把密钥/大 jar 提交进仓、不把未验证大重构当成已发布。  
**门禁基线 commit**: `094d835`（`release-check -IncludeTestcontainers` PASSED + Docker DECIDED_GO）  
**安全备份 stash**（已 apply 回工作区，stash 仍保留可恢复）:

| 仓 | stash |
|----|--------|
| erpServer | `stash@{0}: SAFETY-BACKUP-code-wip-20260717-before-safe-cleanup` |
| erp-frontend | `stash@{0}: SAFETY-BACKUP-frontend-wip-20260717` |

恢复示例：`git stash apply stash@{0}`（若工作区已有同内容会冲突，先 `status` 再决定）。

---

## A. 切勿提交（已处理 / 保持忽略）

| 路径 | 原因 | 状态 |
|------|------|------|
| `.env.preprod-local` | 本机预生产口令 | 已被 `.env.*` ignore |
| `docker-dist/*.jar` | ~55MB 发布 jar 副本 | **2026-07-17 已加入 `.gitignore` 的 `docker-dist/`** |
| `target/**` | 构建与证据 | 已 ignore |
| `logs/**` | 运行日志 | 已 ignore |
| Docker 容器内数据卷 | 本机 compose | 不入库 |

**本机 compose 辅助文件**（可选入库，当前 untracked）:

- `Dockerfile.prebuilt` / `docker-compose.prebuilt.yml` — 用预构建 jar 起 core 栈；说明见 `docs/GATE_PROGRESS_2026-07-17.md`  
- **建议**: 单独 commit「docs+compose 辅助」或继续 untracked；**不要**和业务大重构捆在一起。

---

## B. 建议单独提交（低风险 · 文档/门禁）

| 路径 | 说明 |
|------|------|
| `docs/GATE_PROGRESS_2026-07-17.md` | 今日门禁推进记录 |
| `docs/WHAT_IS_MISSING.md` | 结论刷新到 DECIDED_GO |
| `docs/FQ-SIGNOFF-FINAL-2026-07-16.md` | 技术证据刷新（业务签字栏仍空） |
| `docs/WORKTREE_SAFETY_INVENTORY_2026-07-17.md` | 本文 |
| `docs/FQ-SIGNOFF-CLICKPATH-2026-07-17.md` | 签字点击路径（更新版） |
| `.gitignore` | 增加 `docker-dist/` |

**安全**: 不改运行时逻辑；可随时单独提交。  
**未自动 commit** — 等你确认一句「提交文档」再动。

---

## C. 后端大 WIP（中高风险 · 勿与门禁混交）

约 **80+ 文件**，统计约 `+1188 / -741`。主题大致是：

1. **抽取 `ProductValidator`**（untracked 新类）— 多模块去掉私有 `requireProduct`，改注入校验  
2. **删除 common 半成品/未用类**: `AsyncConfig`、`AsyncExportService`、`BusinessMetrics`、`AuditFieldFiller`、`EntityRequirer`、`RequestIdFilter`（当前工作区已无引用）  
3. **多 Service/Controller 小改**（finance/inventory/purchase/sales/production/issue/workflow/auth…）— 疑似只读事务、导出可见性、租户边界等  
4. **测试适配** — 多处 TenantBoundary / ProductionOrder / Reconciliation 测试增量  
5. **untracked** `V105__add_missing_indexes.sql` — 索引补强；**本机 `erp_codex_runtime` Flyway 尚未应用 V105**（最新仍约 V104）  
6. **脚本** `preprod-acceptance.ps1` / `preprod-full-acceptance.ps1` 有改动；`register-readiness-item-result.ps1` untracked  

### 风险

- 与 **已归档 DECIDED_GO 的 jar（094d835）** 不是同一代码树  
- `ProductValidator` + 大量构造器注入：未跑全量测试前 **不能当发布候选**  
- `V105` 含 `DROP INDEX`：上生产前必须在干净库验证，禁止凭感觉 apply  
- 删除 `AuditFieldFiller`：若线上曾依赖 MetaObjectHandler 填审计字段，需确认是否有等价实现（当前 diff 显示删除；提交前必须 `mvnw test`）

### 建议操作（未执行）

```text
1. 保持工作区继续开发，或切到 feature 分支再提交
2. 提交前: .\mvnw.cmd -B test （最好再加 -Ptestcontainers）
3. V105 单独迁移 commit + 在空库 flyway 验证
4. 切勿在脏树复跑「正式」release-check 并宣称 PASSED
```

---

## D. 前端 WIP（低风险 · UX）

10 个文件，约 `+70 / -10`：

| 类型 | 文件 |
|------|------|
| 提交按钮 loading 防重复点 | adjustments/checks/transfers、sales deliveries/returns、depts/menus/roles |
| API | `getSystemProfile` |
| observability | 小改动 |

**建议**: `npm run type-check` + `npm run build` 绿后可单独 commit「fix(ui): submit loading + system profile」。  
**未自动 commit。**

---

## E. 与发布证据的关系

| 产物 | 是否依赖当前脏树 |
|------|------------------|
| `target/release-check-report.md` PASSED + Testcontainers | **否** — 干净 `094d835` |
| Docker DECIDED_GO + zip 证据包 | **否** — 当时用的是该 jar |
| 当前 working tree | **是另一条未发布线** |

结论：**发布证据仍然有效**；当前 WIP 是后续候选，不是已签字代码。

---

## F. 推荐下一步（人工确认后执行）

1. **你确认后** → 我只提交 B 类文档 + `.gitignore`（一条 docs commit）  
2. **你确认后** → 前端 type-check/build → 一条 UX commit  
3. **后端大 WIP** → 你决定：继续改 / 我帮拆 feature 分支 / 我帮跑测试再评估能否提交  
4. **财务质检** → 按 `docs/FQ-SIGNOFF-CLICKPATH-2026-07-17.md` 签字  

**禁止默认执行**: `git reset --hard`、丢弃 WIP、push、把 docker-dist jar 加入版本库。

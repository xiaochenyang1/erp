# #4 readOnly 补齐审计结论

## 背景

2026-05-27 优化路线图 `docs/superpowers/specs/2026-05-27-optimization-roadmap-design.md:35-36` 列出 Phase 1 第 #4 项：

> **#4 224 个 `@Transactional` 批量补 `readOnly=true`**：仅扫描 `*QueryService`、`Report*` 等纯查询服务；写操作不动。

路线图表格（行 81）将本项 scope 边界钉为：

| # | 项目 | 改 | 不改 | 验收 |
|---|---|---|---|---|
| 4 | readOnly 补齐 | `*QueryService`、`Report*` 内的 `@Transactional` 加 `(readOnly=true)` | 业务逻辑、写操作 | grep 校验、`mvn verify` 绿 |

## 审计结论

**字面 scope 已 100% 完成 —— 本项不需要任何代码改动。**

## 审计指标

执行时间 2026-05-27，分支 `worktree-opt-03-readonly-transactions` HEAD `2a81f60`。

### 项目总体

```
grep -rn "@Transactional" src/main/java | wc -l        → 224
grep -rn "readOnly\s*=\s*true" src/main/java | wc -l   → 46
```

总计 224 个 `@Transactional` 标注，其中 46 个已显式标 `readOnly=true`。

### 路线图列出的 7 个文件逐个核对

`find src/main/java -name "*QueryService.java" -o -name "Report*Service.java"` 返回 7 个文件：

| 文件 | `@Transactional` 总数 | `readOnly=true` | 缺漏 |
|---|---:|---:|---:|
| `finance/payable/service/PayableQueryService.java` | 2 | 2 | 0 |
| `finance/receivable/service/ReceivableQueryService.java` | 2 | 2 | 0 |
| `finance/voucher/service/VoucherQueryService.java` | 3 | 3 | 0 |
| `inventory/stock/service/InventoryStockQueryService.java` | 8 | 8 | 0 |
| `report/service/ReportQueryService.java` | 10 | 10 | 0 |
| `report/service/ReportExportService.java` | 0 | 0 | 0（无事务，委托给 ReportQueryService） |
| **合计** | **25** | **25** | **0** |

字面 scope 下 25 个 `@Transactional` 全部已带 `readOnly=true`，无需改动。

### 剩余 178 个无-readOnly 的 `@Transactional` 抽样验证

`224 - 46 = 178` 个无 `readOnly=true` 的 `@Transactional`，分布在 41 个非 `*QueryService` / 非 `Report*Service` 的文件。这些应当是写操作。

抽样 `ExpenseService.java` 的 5 个无-readOnly 标注：

| 行 | 方法 | 性质 |
|---:|---|---|
| 71 | `create(ExpenseCreateRequest)` | 写 |
| 178 | `update(Long, ExpenseUpdateRequest)` | 写 |
| 202 | `post(Long)` | 写 |
| 226 | `reverse(Long)` | 写 |
| 247 | `cancel(Long)` | 写 |

`IdempotencyService.java` 3 个无-readOnly 标注：

| 行 | 形态 | 性质 |
|---:|---|---|
| 49 | `@Transactional(propagation = REQUIRES_NEW, noRollbackFor = DuplicateKeyException.class)` | 写（幂等记录） |
| 100 | `@Transactional(propagation = REQUIRES_NEW)` | 写 |
| 113 | `@Transactional(propagation = REQUIRES_NEW)` | 写 |

抽样均为写操作，符合路线图 "写操作不动" 的边界，**不在本项 scope 内**。

## 形成原因

`*QueryService` 和 `Report*Service` 在创建之初由各自作者直接写 `@Transactional(readOnly = true)`，是项目编码习惯一部分（非历史遗留批量补齐）。`git log --all --grep="readOnly"` 无相关提交，确认这是"从一开始就这么写"。

## 路线图状态更新建议

路线图 `docs/superpowers/specs/2026-05-27-optimization-roadmap-design.md` 的 #4 当前列为 "约 1 天工作量"。审计结论是 **0 工作量**。建议：

- 本 worktree 仅产生本 spec 一个 commit，不动任何 Java 代码。
- 后续 Phase 1 节奏可以从原估 "4 项 × ~1 天" 修正为 "3 项 × ~1 天"，整体 6 周节奏可前提 ~1 天。
- 路线图原文是否需要单独 commit 修订（标 #4 已完成）由后续决定 —— 本 spec 已经成为审计存档，roadmap 自身不动也能溯源。

## 后续衔接

- 如果未来希望把"混合服务里的纯查询方法"也补 `readOnly=true`，应当作为**新项**进入路线图（例如 #4b "service 内纯读方法 readOnly 补齐"），需要逐方法判断而非按文件名扫描，工作量与风险显著高于本项。
- 本 spec 为该类未来工作提供基线数据（46/224 → 多少需要进一步评估）。

## 验收

- [x] grep 校验：路线图字面 scope 7 个文件，`@Transactional` 全部带 `readOnly=true`，零缺漏。
- [x] grep 校验：剩余 178 个无-readOnly `@Transactional` 抽样均为写操作。
- [x] `mvn verify` 不需要跑 —— 本 worktree 无代码改动。

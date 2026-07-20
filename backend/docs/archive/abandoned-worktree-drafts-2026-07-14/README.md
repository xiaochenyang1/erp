# 废弃 worktree 未提交草稿归档(2026-07-14)

## 背景

清理长期堆积的 worktree/分支时,发现两个旧 worktree 里存在 **master 上没有、且未提交** 的草稿改动。
这些 worktree 的**已提交内容**均已被 master 通过另一条实现路径吸收(功能类已存在、迁移用不同版本号落地),
故 worktree 与分支本身作为旧探索副本被清理。但为避免误丢弃可能有价值的未落地设计,先把这两处的
**未提交改动**(已跟踪 diff + 未跟踪新文件)各打成一个自包含 patch 归档于此。

## 归档内容

### integration-gap-closure__full-uncommitted.patch
- 来源分支:`feature/integration-gap-closure`,tip SHA `bd88b6af68ad9e8c4b91e19056c36d0e9816c500`
- 草稿主题:凭证手工命令服务(`VoucherCommandService` / `VoucherNumberService` / `VoucherCreateRequest`)、
  workflow 任务动作请求(`WorkflowTaskActionRequest`),迁移 `V78__workflow_and_voucher_command_integration.sql`。
- 18 个文件(含已跟踪改动 + 未跟踪新文件)。

### inventory-finance-period-lock__full-uncommitted.patch
- 来源分支:`codex/inventory-finance-period-lock`,tip SHA `b7b476c`
- 草稿主题:凭证审计过账 schema(`V41__finance_voucher_audit_posting_schema.sql`)及配套 `VoucherEntity` 字段、
  权限码、迁移守卫测试。
- 4 个文件。

## 为何未直接采纳

master 已有**另一套**手工凭证实现(独立 `ManualVoucherController` + `/api/finance/vouchers/manual` 全生命周期,
本会话还修了其 V99/V100 迁移缺陷)。这两个草稿的 `VoucherCommandService` / 审计过账 schema 属于**不同设计路径**,
与现有实现在迁移号(草稿 V78/V41 在 master 已被其它内容占用)和领域模型上均有冲突,不能直接 apply。
若将来要吸收其中设计,需先对齐现有 `ManualVoucher*` 领域模型、重新分配迁移号,再按 TDD 做最小闭环,
不可直接 `git apply`。

## 如何恢复

> 关键前提:每个 patch 是相对**其来源分支的 tip SHA**(见上)生成的 delta,**不是**相对 master。
> 直接 `git apply` 到 master 会因迁移号/领域模型冲突失败(已实测),这是预期的,不代表 patch 损坏。

```bash
# 1) 查看某个 patch 的内容(任意工作树都可 --stat)
git apply --stat docs/archive/abandoned-worktree-drafts-2026-07-14/integration-gap-closure__full-uncommitted.patch

# 2) 若要还原草稿原貌:先在来源分支 tip 上建临时分支,再 apply
git switch -c recover-integration-draft bd88b6a
git apply docs/archive/abandoned-worktree-drafts-2026-07-14/integration-gap-closure__full-uncommitted.patch
#   (period-lock 草稿则 checkout b7b476c 后 apply 对应 patch)

# 3) 若要移植进当前 master:不要 apply,按草稿主题手动重写 ——
#    先对齐现有 ManualVoucher* 领域模型、重新分配迁移号,再按 TDD 做最小闭环。
```

## 同批清理的其它 worktree(已提交内容完全被 master 吸收,无未提交草稿或草稿为冗余副本)

| worktree/分支 | tip SHA | 处置理由 |
|---|---|---|
| worktree-opt-01-dev-profile-tighten | 80786a9 | dev profile 密钥收紧已被 master 更彻底实现(`secret: ${ERP_JWT_SECRET}` 无默认值 + 32 字节校验) |
| worktree-opt-02-startup-validator | bbfc6ae | `ProductionStartupValidator` 已在 master |
| worktree-opt-03-readonly-transactions | 9dee2c1 | 唯一文档产物已抢救进 master(`docs/superpowers/specs/2026-05-27-readonly-transactions-design.md`) |
| worktree-opt-04-mdc-traceid | 0cb152a | MdcTraceFilter 已接入 master SecurityConfig + logback pattern |
| codex/finance-ar-ap-entrypoints | 1277d9a | PayableController/ReceivableController/import* 等均在 master(迁移改用 V18/V37) |
| feature/fund-reconciliation-center | 6da42ad | FundService/FundController 等在 master(迁移改用 V58) |
| codex/inventory-reservation-ops | d93ccec | InventoryReservationOps* + V33 在 master |
| codex/observability-alerts | db027ab | 独有 commit=0,完全被 master 吸收 |
| codex/production-manufacturing-minimum | a093f2b | production/bom+order + V38 在 master |
| feature/sales-reservation-delivery-link | 2954c1b | 独有仅 1 个 plan 文档;delivery link 已在 master |
| detached HEAD | 07b7c52 | 2026-05-11 后端 baseline,早被覆盖 |

# 财务增强最小闭环设计

## 背景

当前后端已经具备轻量财务主链路：采购入库形成应付，销售出库形成应收，收付款可以核销往来，业务过账会生成 `fin_voucher` 凭证头。这个模型能支撑采购到付款、销售到收款的业务闭环，但还缺少一期设计里提到的费用登记、会计科目、凭证分录、总账和明细账查询。

本阶段目标不是把系统升级成完整会计中台。完整结账、税务申报、多币种、成本重算、固定资产和期末处理先不碰。硬把这些一锅炖进去，只会把现在还算清楚的轻量财务搞成一锅粘豆包。

## 目标

- 提供最小会计科目维护能力，支持科目编码、名称、方向、启停和树形层级。
- 提供费用登记能力，费用单过账后生成凭证头和凭证分录。
- 改造业务自动凭证生成，让采购入库、采购退货、销售出库、销售退货也写入凭证分录。
- 提供凭证查询、凭证分录查询、总账查询和明细账查询接口。
- 保持现有应收、应付、收款、付款接口兼容，不破坏已完成业务链路。

## 非目标

- 不做会计期间、结账、反结账和期初余额。
- 不做税务申报、固定资产、成本重算和库存成本重估。
- 不做多币种、汇率损益和多账簿会计准则。
- 不做复杂凭证审核流，凭证仍由业务过账或费用过账直接生成。
- 不做异步汇总表，账簿查询先从凭证分录实时聚合。

## 方案

采用“凭证头 + 凭证分录 + 实时账簿查询”的最小闭环。

`fin_voucher` 继续作为凭证头，新增 `fin_voucher_entry` 保存借贷分录。每个已过账凭证至少有两条分录，并且借贷金额必须平衡。新增 `fin_account_subject` 存会计科目。新增 `fin_expense` 存费用单，费用单过账时生成凭证头和分录，来源类型使用 `EXPENSE`。

业务自动凭证不改现有业务语义，只补分录：

- 采购入库：借库存类科目，贷应付类科目。
- 采购退货：借应付类科目，贷库存类科目。
- 销售出库：借应收类科目，贷销售收入类科目。
- 销售退货：借销售退回类科目，贷应收类科目。
- 费用登记：借费用类科目，贷现金/银行类科目。

初始科目通过迁移脚本种子数据提供。为了避免做成“会计配置后台大型连环套”，本阶段只内置最小科目集合，并允许启停和查询，不先开放删除。

## 数据模型

新增 `fin_account_subject`：

- `id`
- `company_id`
- `account_book_id`
- `subject_code`
- `subject_name`
- `parent_id`
- `subject_type`
- `balance_direction`
- `status`
- `deleted_flag`
- 审计字段和 `version`

新增 `fin_voucher_entry`：

- `id`
- `company_id`
- `account_book_id`
- `voucher_id`
- `line_no`
- `subject_id`
- `subject_code`
- `subject_name`
- `debit_amount`
- `credit_amount`
- `summary`
- 审计字段和 `version`

新增 `fin_expense`：

- `id`
- `company_id`
- `account_book_id`
- `expense_no`
- `expense_date`
- `subject_id`
- `payment_subject_id`
- `amount`
- `status`
- `remark`
- 审计字段和 `version`

索引要求：

- 科目编码在公司内唯一。
- 费用单号在公司内唯一。
- 凭证分录按 `voucher_id`、`subject_code`、`company_id + biz_date` 查询要有索引。

## API

新增科目接口：

- `GET /api/finance/account-subjects`
- `GET /api/finance/account-subjects/tree`
- `POST /api/finance/account-subjects`
- `PUT /api/finance/account-subjects/{id}`
- `POST /api/finance/account-subjects/{id}/enable`
- `POST /api/finance/account-subjects/{id}/disable`

新增费用接口：

- `POST /api/finance/expenses`
- `GET /api/finance/expenses`
- `GET /api/finance/expenses/{id}`
- `POST /api/finance/expenses/{id}/post`
- `POST /api/finance/expenses/{id}/cancel`

新增凭证和账簿查询接口：

- `GET /api/finance/vouchers`
- `GET /api/finance/vouchers/{id}`
- `GET /api/finance/vouchers/{id}/entries`
- `GET /api/finance/ledger/general`
- `GET /api/finance/ledger/detail`

权限码：

- `finance:subject:manage`
- `finance:expense:manage`
- `finance:voucher:view`
- `finance:ledger:view`

## 业务规则

- 科目编码不能为空，同一公司内不能重复。
- 非叶子科目不能直接用于费用单和凭证分录。
- 停用科目不能用于新增费用单和新凭证分录。
- 费用金额必须大于 0。
- 费用单草稿可取消，已过账费用单不能直接删除。
- 费用过账必须生成借贷平衡凭证，任何一边科目缺失都要拒绝。
- 凭证分录借贷金额不能同时为 0，也不能同时大于 0。
- 总账按科目聚合借方、贷方和净额；明细账按凭证分录逐行返回。

## 兼容性

现有 `fin_voucher` 数据没有分录。迁移脚本不回填历史分录，避免编造历史会计事实。上线后新生成的业务凭证和费用凭证才写入分录。

如果后续确实要补历史凭证分录，另开一次性数据治理脚本，必须以业务单据和财务确认结果为依据，不能靠迁移脚本拍脑袋自动补。

## 实施状态

截至 2026-05-15，当前工作区已实现本设计的最小闭环：

- 新增 `fin_account_subject`、`fin_expense`、`fin_voucher_entry` 迁移、实体、Mapper 和租户表配置。
- 新增会计科目、费用登记、凭证分录、总账和明细账查询接口。
- 业务自动凭证生成已补写凭证分录。
- 新增 `finance:ledger:view` 等财务账簿相关权限码和菜单初始化数据。
- 2026-05-19 已恢复最小 `src/test` 自动化回归；当前验证口径为最小测试集、Maven 构建、预生产冒烟和人工业务验收。

## 测试计划

- 编译验证：`.\scripts\release-check.ps1` 必须 `BUILD SUCCESS`，通过当前最小测试集，并产出 `target/erp-server-1.0.0.jar` 与 SBOM 文件。
- 冒烟验证：在预生产环境执行登录、科目查询、费用创建、费用过账、凭证分录查询、总账查询、明细账查询。
- 业务验证：执行采购入库、采购退货、销售出库、销售退货，确认生成凭证头和借贷平衡分录。
- 权限验证：用缺少财务权限的账号访问费用、科目、凭证、账簿接口应返回 `403`。
- 发布验证：Docker、MySQL、Redis 和真实 `.env.prod` 仍按生产部署文档执行。

## 上线注意事项

- 这次只补新表和新菜单权限，不修改现有往来表金额口径。
- 生产环境上线后，新业务凭证才有分录，历史凭证没有分录属于预期行为。
- 报表或前端如果展示“凭证分录完整率”，必须明确区分历史数据和新数据。
- Docker 和真实 MySQL/Redis 验证仍然按生产部署文档执行，本设计不替代发布门禁。

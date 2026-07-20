# 手工凭证完整闭环设计

日期：2026-07-07

## 背景

手工凭证已有录入、提交、审批、过账、作废、删除入口。当前风险集中在已过账凭证作废：后端会删除已经写入 `fin_voucher_entry` 的总账分录，并把共享凭证头置为 `CANCELLED`。这会破坏财务审计轨迹，也会让前端“冲回分录”的文案和真实行为不一致。

第一期目标是修正这个核心风险，不重构整个财务凭证体系。

## 目标

- 保留现有手工凭证状态机：`DRAFT -> PENDING -> APPROVED -> POSTED -> CANCELLED`，`PENDING -> DRAFT` 表示驳回。
- 已过账手工凭证作废时不删除原始 `fin_voucher_entry`。
- 作废通过新增红冲凭证完成：原借方转贷方，原贷方转借方，使总账净额归零。
- 手工凭证主表记录原过账凭证、红冲凭证、作废原因、作废人、作废时间。
- 前端作废必须填写原因，并在详情中展示过账与红冲追溯信息。
- 新增专项测试覆盖状态机、期间校验、分录保留、红冲净额、租户隔离。

## 非目标

- 不重构 `fin_voucher` / `fin_voucher_entry` 的通用凭证内核。
- 不新增多级审批流。
- 不新增独立审计事件表。
- 不处理历史已经被删除分录的数据修复；本设计只保证新逻辑正确。

## 状态机

手工凭证主状态继续使用现有字段 `fin_manual_voucher.status`：

- `DRAFT`：可编辑、可删除、可提交。
- `PENDING`：可审批、可驳回。
- `APPROVED`：可过账。
- `POSTED`：已生成正向总账凭证，可作废。
- `CANCELLED`：已通过红冲作废，不可编辑、不可删除、不可再次作废。

`CANCELLED` 表示业务单据已作废，不表示原始总账凭证被删除。原始 `fin_voucher` 保持 `POSTED`，红冲 `fin_voucher` 也为 `POSTED`，两者共同构成完整审计链。

## 数据模型

在 `fin_manual_voucher` 增加字段：

- `reversal_voucher_id BIGINT`：红冲凭证 ID。
- `cancel_reason VARCHAR(512)`：作废原因。

现有字段继续使用：

- `posted_voucher_id`：原始过账凭证 ID。
- `cancelled_by`：作废人。
- `cancelled_time`：作废时间。

共享凭证规则：

- 正向过账凭证：`fin_voucher.source_type = 'MANUAL'`，`source_id = fin_manual_voucher.id`。
- 红冲凭证：`fin_voucher.source_type = 'MANUAL_REVERSAL'`，`source_id = fin_manual_voucher.id`。
- 红冲凭证号使用可追溯格式，例如 `原凭证号 + '-REV'`；如未来需要严格流水号，可再接入序列规则。

## 后端行为

### 过账

`ManualVoucherService.post(id)` 保持现有主流程：

- 只允许 `APPROVED`。
- 校验凭证日期所属期间为 `OPEN`。
- 重新校验手工凭证分录借贷平衡。
- 写入正向 `fin_voucher` 和 `fin_voucher_entry`。
- 回填 `posted_voucher_id`、`posted_by`、`posted_time`，状态改为 `POSTED`。

### 作废

`POST /api/finance/vouchers/manual/{id}/cancel` 改为接收请求体：

```json
{
  "reason": "录入错误，需要红冲作废"
}
```

`ManualVoucherService.cancel(id, reason)` 规则：

- 只允许 `POSTED`。
- `reason` 必填，去除首尾空格后不能为空。
- 使用当前审计时间 `audit.now().toLocalDate()` 作为红冲凭证日期。
- 校验红冲凭证日期所属期间为 `OPEN`。
- 必须能找到 `posted_voucher_id` 对应的原始凭证。
- 必须能找到原始凭证的分录。
- 不删除原始 `fin_voucher_entry`。
- 不把原始 `fin_voucher` 改为 `CANCELLED`。
- 新增红冲 `fin_voucher`：
  - `source_type = 'MANUAL_REVERSAL'`
  - `source_id = 手工凭证 ID`
  - `source_no = 手工凭证号`
  - `biz_date = 作废日期`
  - `amount = 原始凭证金额`
  - `status = 'POSTED'`
  - `remark` 包含原凭证号和作废原因。
- 新增红冲 `fin_voucher_entry`：
  - 保留科目、行号、科目编码、科目名称。
  - `debit_amount = 原分录 credit_amount`
  - `credit_amount = 原分录 debit_amount`
  - `biz_date = 红冲凭证日期`
  - `summary` 带 `红冲:` 前缀。
- 回填手工凭证：
  - `status = 'CANCELLED'`
  - `reversal_voucher_id`
  - `cancel_reason`
  - `cancelled_by`
  - `cancelled_time`

作废接口不设计为幂等。重复作废应返回业务冲突，防止同一凭证生成多张红冲凭证。

## API 响应

`ManualVoucherResponse` 增加字段：

- `reversalVoucherId`
- `cancelReason`

现有 `postedVoucherId`、`submittedTime`、`approvedTime`、`postedTime`、`cancelledTime` 保留。

## 前端行为

手工凭证页面保留现有入口，只增强作废与详情：

- 点击 `POSTED` 凭证的“作废”时打开弹窗。
- 弹窗展示凭证号和风险提示。
- 作废原因必填，空白不可提交。
- 调用 `cancelManualVoucher(id, reason)`，请求体为 `{ reason }`。
- 详情展示：
  - 原过账凭证 ID。
  - 红冲凭证 ID。
  - 作废原因。
  - 提交、审批、过账、作废时间。

## 权限

继续沿用现有权限：

- 查看：`finance:voucher:view`
- 录入、编辑、提交、删除：`finance:voucher:manage`
- 审批、驳回：`finance:voucher:approve`
- 过账、作废：`finance:voucher:post`

## 测试范围

新增后端专项测试，至少覆盖：

- 创建草稿时必须借贷平衡。
- 非草稿不可编辑、不可删除。
- `DRAFT -> PENDING -> APPROVED -> POSTED` 状态流转。
- `PENDING -> DRAFT` 驳回并记录原因。
- 过账生成正向 `fin_voucher` 和 `fin_voucher_entry`。
- 作废原因必填。
- 非 `POSTED` 不可作废。
- 作废不删除原始分录。
- 作废生成 `MANUAL_REVERSAL` 红冲凭证。
- 红冲分录借贷对调，原始分录加红冲分录后净额为零。
- 锁定或关闭期间不可过账、不可作废。
- 跨公司或跨账套不能访问、过账、作废他人凭证。

前端验证覆盖：

- `npm run type-check`
- `npm run lint`
- `npm run build`

后端验证覆盖：

- `.\mvnw.cmd -B test`

## 交付顺序

1. 增加数据库迁移字段。
2. 扩展实体、请求、响应 DTO。
3. 修改后端作废逻辑为红冲。
4. 增加后端专项测试。
5. 修改前端 API 与作废弹窗。
6. 修改详情展示。
7. 运行前后端验证命令。

## 风险与约束

- 当前仓库已有大量未提交改动，实施时只修改手工凭证相关文件，避免顺手重构。
- 历史已作废且分录被删除的数据不在本期修复范围内。
- 红冲日期采用当前审计日期，若用户未来要求按原凭证日期红冲，需要重新设计期间规则。

# 采购来料质检(QC)模块设计方案

日期:2026-07-09
范围:后端新模块 + 前端页面 + 迁移/菜单/权限。挂接采购来料检验(IQC)。

## 已确认的业务决策

1. **入库前独立检验单**:采购入库单(DRAFT)创建后、过账前,需求检验的商品必须先有一张"已判定"的检验单;入库过账时校验通过才放行。
2. **记录合格/不合格数量,仅合格品入库**:检验单按行记录 合格数/不合格数/不合格原因;过账时只有合格数量进入库存,不合格数量仅登记留痕(本期不做退供应商/让步接收联动)。
3. **商品级"是否需检验"开关**:`md_product` 增加 `inspection_required` 标志;只有开启的商品在采购入库过账时强制检验,其余商品照常直接过账。

## 关键口径与一致性(实现时遵守)

- **库存 = 合格数量**:需检验商品的入库过账数量取检验单的合格数量,不合格部分不进库存。
- **应付/凭证一致性(已确认实现路径)**:`FinancePostingService.recordPurchaseReceipt` 直接读取 `receipt.getTotalAmount()/getTotalTaxAmount()` 生成应付与凭证(已核对源码 58-65 行),不接收外部金额参数。因此**采用"入库单数量即合格数量"的数据流**,让入库单总额天然只含合格品:检验判定后据合格数量生成/校正入库单行,过账时 `postInbound`、应付、凭证、订单已收回写全部读同一份入库单合格口径,三方账实一致,**无需改动 `FinancePostingService` 签名**。这是本设计的核心前提,实现时不得走"入库单存全额、过账时再按比例折算"的路子(那样会与财务读取口径不一致)。
- **采购订单已收数量回写**:按合格数量回写,保持"剩余可入库"口径与实际入库一致。
- **不需检验商品**:过账逻辑完全不变(全量入库、全额应付)。
- **多租户/软删/乐观锁**:所有新表带 `company_id/account_book_id/deleted_flag/version` 与 4 个审计列,查询与唯一索引以 `(company_id, account_book_id, ...)` 打头,复用现有 `AuditMetadataFactory`/`DataScopeService` 模式。

## 数据模型(迁移 V94)

新增 `md_product.inspection_required TINYINT NOT NULL DEFAULT 0`。

`qc_inspection_order`(检验单头):
- id, company_id, account_book_id, inspection_no(单号,走 sequence rule)
- receipt_id(源采购入库单), order_id(采购订单), warehouse_id, supplier_id, inspection_date
- status:DRAFT / SUBMITTED / JUDGED / CANCELLED
- total_qty, qualified_qty, unqualified_qty(汇总)
- remark + 审计列 + version

`qc_inspection_line`(检验单行):
- id, company_id, account_book_id, inspection_id, line_no
- receipt_line_id, product_id
- inspected_qty, qualified_qty, unqualified_qty, defect_reason
- remark + 审计列 + version

索引:`uk_qc_inspection_order_company_book_no`、`idx_qc_inspection_order_company_book_status`、`idx_qc_inspection_order_company_book_receipt`、`uk_qc_inspection_line_company_book_line`。

## 检验单生命周期

- **create**(DRAFT):引用一张 DRAFT 采购入库单,按其行带出待检数量,生成检验行。
- **update**(仅 DRAFT):修改检验数量/原因。
- **submit**(DRAFT→SUBMITTED):提交检验。
- **judge**(SUBMITTED→JUDGED):录入每行合格/不合格数(合格+不合格≤检验数),汇总头。判定后不可改。
- **cancel**(DRAFT/SUBMITTED→CANCELLED)。
- 校验:合格数+不合格数 = 检验数;检验数 ≤ 入库单对应行数量;同一入库单不允许重复有效检验单。

## 采购入库过账挂接(改 `PurchaseReceiptService.post()`)

数据流采用"入库单即合格口径"(见上文核心前提):检验判定后,入库单行数量已等于合格数量、总额已只含合格品。过账挂接只做**校验闸门**,不做金额折算:

在现有数量校验之后、`postInbound` 之前插入质检闸门:
1. 逐行判断商品 `inspection_required`。
2. 对需检验行:查找引用本入库单且 status=JUDGED 的检验单;缺失则抛 `IllegalArgumentException("存在需检验商品尚未完成质检,不能过账")`。
3. 校验入库单该行数量与检验单合格数量一致(不一致说明入库单未按合格口径生成,抛错拦截),不在过账时改写数量或金额。
4. 不需检验行:同现状。
5. 闸门通过后,`postInbound`、应付/凭证、订单已收回写沿用现有逻辑读入库单口径,无需改动。

> 由此,"仅合格品入库"落在**检验判定 → 生成/校正入库单**这一步(入库单数量即合格量),而非过账时折算。检验单 judge 后应提供据合格数量生成或回填入库单行的能力(实现细节在 service 层确定:judge 时校正已引用的 DRAFT 入库单行数量为合格数量)。

## 后端落地清单(按现有模块结构)

- 包 `com.tuowei.erp.qc.inspection`:`model`(2 实体)、`mapper`(2)、`web`(Create/Update/Judge 请求 + Response + PageQuery)、`service`(QcInspectionService + 单号服务)、`controller`。
- 控制器路径 `/api/qc/inspections`:POST 创建 / GET 列表 / GET 详情 / PUT 更新 / POST submit / POST judge / POST cancel / GET export(CSV,复用 `CsvExport` + `withAuthentication` 模式)。
- 新增 `QcPermissionCodes` 接口(裸码 + `HAS_` 对:qc:inspection:view/create/update/submit/judge/cancel),加入 `PermissionCodes` 的 implements 列表;控制器方法加 `@PreAuthorize`。
- `md_product` 实体/请求/响应/服务增加 `inspectionRequired` 字段(create/update/toResponse)。
- 迁移 `V94__qc_inspection_schema.sql`(建表 + md_product 加列)、`V95__qc_inspection_menu_seed.sql`(sys_menu 用 **5200 段** CATALOG+MENU+BUTTON,sys_role_menu 用 **7200 段** 绑 role_id=3002;均带 `ON DUPLICATE KEY UPDATE`)。若单号需要,`sys_sequence_rule` 用 2030+ 段并加 QC 单号规则。

## 前端落地清单

- API 模块 `src/api/qc.ts`:上述端点。
- 页面 `src/views/qc/inspection/index.vue`:列表 + 检验单创建(选 DRAFT 入库单带出行)+ 判定弹窗(录合格/不合格)+ 提交/判定/作废 + 导出;按钮挂 `v-permission`。
- 路由 `/qc/inspections`(meta.permission=`qc:inspection:view`),菜单由后端 runtime-menu-tree 驱动。
- 产品页表单/详情增加"需检验"开关。

## 测试

- `QcInspectionServiceTest`:生命周期状态机、合格+不合格≤检验数、检验数≤入库行数、租户隔离、重复检验单拦截。
- 采购入库过账质检闸门测试:需检验商品无 JUDGED 检验单被拦、合格数量正确入库、不需检验商品不受影响。
- 全量后端测试 `.\mvnw.cmd test` 保持绿。

## 分步实施顺序

1. 迁移 V94/V95 + md_product 加列(schema 先行)。
2. QcPermissionCodes + 实体/mapper/DTO。
3. QcInspectionService(生命周期)+ 单号服务 + Controller。
4. 改 PurchaseReceiptService.post() 质检闸门 + 合格口径入库/应付。
5. 后端测试。
6. 前端 API + 页面 + 产品开关。
7. 前端 build + 后端全量测试。

## 已知范围边界(本期不做,留待后续)

- 不合格品退供应商 / 让步接收联动(本期仅登记不合格数量)。
- 生产完工检验、过程检验(仅做采购来料 IQC)。
- 检验模板/抽样方案(AQL 等)。

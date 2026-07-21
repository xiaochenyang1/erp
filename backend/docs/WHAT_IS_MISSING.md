# 项目当前完成度与缺口盘点

**更新时间**: 2026-07-17 深夜（销售价目最小闭环落地）
**范围**: `erpServer` 后端 + 相邻 `erp-frontend` 前端
**结论**: 主干业务在 `erp_codex_runtime` 已闭环。浏览器 smoke **36/36**；写端点主干 **194/194** + 价目新接口；扩展 smoke **15/15**。**新增：销售价目（V111）；期间结账向导；应收/应付账龄分析（V112）；工序报工（V113）**。**仍缺财务/质检真人签字（A1）**。**未完成功能总清单见 `docs/未完成.md`**。总看板：`docs/EXECUTION-BOARD-2026-07-17.md`。

---

## 已有能力

### 后端

当前后端是 Java 17 / Spring Boot / Maven / Flyway / MyBatis-Plus 项目，已覆盖以下模块：

- 系统基础：登录认证、用户、角色、菜单、部门、岗位、字典、配置、流水号、日志、附件、通知、就绪检查、观测指标。
- 主数据：产品、客户、供应商、仓库。
- 采购：采购订单、采购入库、采购退货、导出、库存和财务联动。
- 销售：销售订单、销售出库、销售退货、库存和财务联动。
- 库存：库存余额、批次、调拨、调整、盘点、预留、预警、导出。
- 财务：应收、应付、收款、付款、费用单、会计科目、凭证查询、凭证分录、总账、明细账、资金对账、期间锁定和月结检查。
- 生产：BOM、生产工单、领料、完工、反完工、退料。
- 质量：采购来料质检(IQC)检验单 —— 创建(引用草稿采购入库单带出检验行)、提交、判定(录合格/不合格)、作废、导出;判定按合格数量回写入库单实现"仅合格品入库",采购入库过账前设质检闸门(2026-07-13 落地并端到端 smoke 通过)。
- 工作流：审批任务、审批记录、审批配置、撤回重提、通知联动。
- 导入：初始数据导入、导入任务和行级结果。
- 报表：库存、财务、业务追踪、导出。
- 运营扩展：运营看板、异常工单、异常规则、规则自动扫描、SLA 策略、业务时间线。

### 前端

相邻目录 `E:\tuowei\python\erp-frontend` 已存在 Vue 3 / Vite / Element Plus 前端。当前可见页面包括：

- 登录、首页看板、主数据、采购、销售、库存、生产、财务、报表、系统管理。
- 新增扩展页面：异常工单、异常规则、异常 SLA、业务追踪、工作流任务/记录、财务期间、附件中心、在线会话、通知中心、可观测性、预生产验收、资金对账。
- 本轮补齐原前端空壳/半成品页面：库存查询、销售订单、用户管理、收付款管理；生产订单领料从“待实现”提示改为调用后端真实领料接口，补齐 `MATERIAL_ISSUED` 状态展示和完工入口，并修正生产订单/BOM API 字段归一化，避免后端 `Long` 雪花 ID 被前端误当 `number` 使用。
- 本轮补齐后端已有菜单/Controller 但前端缺路由的页面：在线会话、通知中心、可观测性、预生产验收、资金对账；同步补齐对应 API 字段归一化和基础操作入口。

---

## 已验证结果

### 后端验证

```powershell
.\mvnw.cmd -B test
```

结果：

- Tests run: 935
- Failures: 0
- Errors: 0
- Skipped: 0

本轮已补齐的半成品：

- `AttachmentServiceStreamingTest` 同步 `AttachmentService` 新增的 `BusinessTimelineService` 构造依赖。
- `biz_business_timeline` 加入 MyBatis-Plus 租户拦截表清单，避免多租户表门禁失败。
- `BusinessTimelineControllerTest` 补充账套 2 只读角色，并清理权限缓存和 principal 缓存，避免全量测试顺序污染。
- `LocalBootstrapService` 补齐 `local` profile 下全新本地库首次登录能力：默认初始化 `admin / LocalAdmin123`，并支持 `ERP_LOCAL_ADMIN_PASSWORD` 覆盖。
- `ReadmeDocumentationTest` 增加本地登录文档与 `application-local.yml` 默认 bootstrap 配置一致性检查，防止后续文档和配置漂移。
- `ExceptionTicketService` 修复应用重启后异常工单号从 `0001` 重新生成导致唯一键冲突的问题，新建时会按租户查询当天已存在最大工单号后接续。
- `JacksonConfig` 补齐后端 JSON 大整数契约，`Long/long` 序列化为字符串，避免前端 JavaScript 丢失雪花 ID 精度；`JwtTokenService` 同步兼容字符串数值 claim。
- `JwtAuthenticationFilter` 补齐 `ASYNC` dispatch 认证恢复，修复报表 `StreamingResponseBody` 导出在真实浏览器下载时异步分发变匿名、触发 `AuthorizationDeniedException` 的问题，并新增真实登录 token 的异步导出回归测试。

新增本地登录口径的针对性回归也已通过：

```powershell
.\mvnw.cmd -B "-Dtest=ReadmeDocumentationTest,LocalBootstrapServiceTest,ProductionBootstrapServicePasswordPolicyTest" test
```

结果：

- Tests run: 6
- Failures: 0
- Errors: 0
- Skipped: 0

本地发布门禁也已通过：

```powershell
.\scripts\release-check.ps1 -AllowDirtyWorktree -MavenRepoLocal .\.m2-release
```

结果：

- PowerShell 脚本语法门禁通过。
- Maven `clean package` 通过。
- 已校验 `target\erp-server-1.0.0.jar`。
- 已生成并校验 SBOM：`target\classes\META-INF\sbom\application.cdx.json`、`target\bom.json`。
- Release check report 自校验通过。

运行时基础链路已用干净联调库补测：

```powershell
java -jar target\erp-server-1.0.0.jar --spring.profiles.active=local "--spring.datasource.url=jdbc:mysql://localhost:3306/erp_codex_runtime?useSSL=false"
```

结果：

- 干净 schema `erp_codex_runtime` 成功校验并应用 82 个 Flyway migrations，当前版本到 `v84`。
- 后端 `http://localhost:8080/actuator/health` 返回 `{"status":"UP"}`。
- 前端 Vite dev server `http://127.0.0.1:5173/` 返回 `200`。
- 前端代理 `http://127.0.0.1:5173/actuator/health` 返回 `{"status":"UP"}`。
- 临时联调用户 `runtime_smoke` 可登录，`POST /api/auth/login` 返回 token，权限数 171。
- 登录后接口 smoke 已通过：`/api/auth/user-info`、`/api/system/menus/tree`、`/api/dashboard/operations`、`/api/exception-tickets`、`/api/exception-rules`、`/api/exception-rules/hits`、`/api/exception-sla-policies`、`/api/reports/business-traces`。
- 工作台审批待办跳转已补齐上下文：`/api/dashboard/operations` 返回的 `WORKFLOW` 待办 route 从裸 `/workflow/tasks` 改为携带 `businessType + businessId + status=PENDING`，前端审批待办页可直接按 URL 查询参数定位对应单据。
- 业务时间线写读通过：`POST /api/business-timeline/comments` 创建 `COMMENT`，再按 `businessType + businessId` 查询返回 1 条。
- 异常工单状态流转通过：创建 `OPEN` -> 分派 -> `PROCESSING` -> `RESOLVED` -> `CLOSED`。
- 本地登录种子口径已补齐：全新 schema 使用 `local` profile 启动后，`admin / LocalAdmin123` 可登录，返回 token 和 171 个权限；可通过 `ERP_LOCAL_ADMIN_PASSWORD` 覆盖默认本地密码。

### 前端验证

前端本地登录页已同步后端 `local` profile 的默认账号口径：

- 登录页预填密码已从旧的 `123456` 改为 `LocalAdmin123`。
- 登录页“测试账号”提示已改为 `admin / LocalAdmin123（已填充）`。
- 窄范围契约检查已确认登录页、README、本地配置和缺口文档都使用 `LocalAdmin123` / `ERP_LOCAL_ADMIN_PASSWORD` 口径，没有继续展示 `admin / 123456`。

```powershell
npm run check:contracts
npm run type-check
npm test
npm run build
```

结果：

- 生产订单仓库契约检查通过：页面已拆分 `materialWarehouseId` 材料出库仓和 `finishedWarehouseId` 成品入库仓，不再使用单一“目标仓库”提交口径；选项加载使用后端真实分页参数 `pageNo/pageSize`，避免数据量上来后新建商品/仓库被第一页挡在外面。
- `vue-tsc --noEmit --skipLibCheck` 通过。
- `vitest run` 通过：3 个测试文件、10 个测试全绿。
- `vite build` 通过。

旧文档中提到的 PageResponse 类型错误和 48 个 TypeScript 编译错误，按当前工作区验证已经不是事实。

关键页面基础 smoke、异常工单页面闭环 smoke、异常规则配置/启停/扫描、SLA 策略保存、业务追踪查询重置、财务期间生成检查对账、报表导出下载、附件中心上传/下载/删除、预生产验收运行单创建/证据/结果/No-Go 决策、资金账户/银行流水创建、销售订单创建/提交/审批通过、销售发货创建/过账/应收生成、销售收款核销、销售退货创建/过账/库存回补/应收冲减、采购退货创建/过账/库存扣减/应付冲减和生产订单创建/下达/领料/完工已用 Headless Chrome + Vite dev server + 干净联调库跑通：

```powershell
node scripts\ui-smoke.mjs
```

结果：

- 2026-07-15 晚间全量复跑采用 `UI_SMOKE_ROUTES=0`，`target/ui-smoke-report.json` 记录 **34/34 workflow 全绿、0 失败**；下列页面标题/首屏渲染项保留此前基础路由 smoke 的分项证据。
- 登录用户：`admin`
- 检查时间：`2026-07-02T12:30:41.253Z`
- 覆盖汇总：19 个基础路由、19 个真实 workflow；本轮新增的会计科目与凭证查询、审批中心任务详情/通过/记录过滤 workflow 已单独通过，既有 workflow 继续按前述分项证据追踪。
- `/dashboard`：页面标题 `首页 - ERP系统`，渲染 `今日采购订单`
- `/exception-tickets`：页面标题 `异常处理 - ERP系统`，渲染 `异常工单`
- `/exception-rules`：页面标题 `异常规则 - ERP系统`，渲染 `异常规则`
- `/exception-sla-policies`：页面标题 `异常SLA策略 - ERP系统`，渲染 `SLA策略`
- `/reports/traces`：页面标题 `单据追踪 - ERP系统`，渲染 `暂无追踪事件`
- `/finance/periods`：页面标题 `会计期间 - ERP系统`，渲染 `会计期间管理`
- `/system/attachments`：页面标题 `附件中心 - ERP系统`，渲染 `附件中心`
- `/inventory/stocks`：页面标题 `库存查询 - ERP系统`，渲染 `库存查询`
- `/sales/orders`：页面标题 `销售订单 - ERP系统`，渲染 `销售订单`
- `/sales/deliveries`：页面标题 `销售发货 - ERP系统`，渲染 `销售发货`
- `/system/users`：页面标题 `用户管理 - ERP系统`，渲染 `用户管理`
- `/finance/payments`：页面标题 `收付款管理 - ERP系统`，渲染 `收款管理`
- `/finance/expenses`：页面标题 `费用管理 - ERP系统`，渲染 `费用管理`
- `/production/orders`：页面标题 `生产订单 - ERP系统`，渲染 `生产订单管理`
- `/system/user-sessions`：页面标题 `在线会话 - ERP系统`，渲染 `在线会话`
- `/system/notifications`：页面标题 `通知中心 - ERP系统`，渲染 `通知中心`
- `/system/observability`：页面标题 `可观测性 - ERP系统`，渲染 `可观测性`
- `/system/readiness`：页面标题 `预生产验收 - ERP系统`，渲染 `预生产验收`
- `/finance/funds`：页面标题 `资金对账 - ERP系统`，渲染 `资金对账`
- 异常工单页面真实操作闭环：新建异常工单 -> 分派 -> 开始 -> 解决 -> 关闭。
- SLA 策略页面真实保存：打开配置弹窗 -> 修改说明 -> 保存 -> 列表回显。
- 异常规则页面真实操作闭环：打开配置弹窗 -> 修改说明 -> 保存回显 -> 停用 -> 恢复启用 -> 扫描全部。
- 业务追踪页面真实操作：按报表数据抽取可用业务号/来源号查询并重置；无数据时验证空态。
- 财务期间页面真实操作：生成年期间 -> 确认 -> 检查 -> 对账；不执行锁定、结账、反开等破坏性状态变更。
- 报表导出真实操作：触发导出，验证返回非空 `text/csv` blob，并验证前端下载文件名为 `.csv`。
- 附件中心真实操作：上传文本附件 -> 列表查询回显 -> 下载并验证非空 `text/plain` blob 和原始文件名 -> 删除。
- 预生产验收页面真实操作：打开页面 -> 新建验收运行单 -> 保存后进入详情抽屉 -> 校验默认验收项回显 -> 为 `RELEASE_GATE` 登记手工证据 -> 记录验收结果 -> 保存 `No-Go` 决策 -> 通过同一浏览器会话回查后端确认 `MIGRATION_PREFLIGHT` 预检证据、`RELEASE_GATE` 证据/结果和运行单 `NO_GO` 决策已落库。
- 资金对账页面真实操作：新增资金账户 -> 切换银行流水 -> 新增银行流水 -> 列表回显 -> 通过同一浏览器会话回查后端确认银行流水为 `UNMATCHED` 且金额、摘要、外部流水号落库。
- 资金对账匹配真实操作：通过 API 准备已过账销售发货、同额已过账收款单和同额银行收入流水 -> 页面点击匹配并录入收款单 ID -> 回查后端确认银行流水为 `MATCHED` 且 `matchedBizType=RECEIPT` / `matchedBizNo` 落库 -> 页面点击取消匹配并填写原因 -> 回查后端确认银行流水回到 `UNMATCHED`、匹配字段清空且 `unmatchReason` 落库。
- 费用单页面真实操作：通过 API 准备开放期间和独立费用/支付会计科目 -> 浏览器切换为 `runtime_smoke` 新增费用单并提交审批 -> 浏览器切回 `admin` 审批通过、过账并红冲 -> 通过同一浏览器会话回查后端确认费用单 `POSTED`、原始凭证 `POSTED`、2 条分录借贷平衡、金额匹配，并生成已平衡的红冲凭证。最新样例费用单：`FE202607030002`。本轮同时修正前端费用页从旧的 `expenseType/departmentId/items` 报销明细口径，改为后端真实的 `subjectId/paymentSubjectId/amount` 费用登记口径，并补上契约检查防回归。
- 会计科目与凭证查询真实操作：页面新增会计科目 -> 编辑名称和备注 -> 停用 -> 启用 -> 通过同一浏览器会话回查后端确认 `subjectCode/subjectName/subjectType/balanceDirection` 与状态落库；再通过 API 准备已过账费用凭证 -> `/finance/vouchers` 只读查询页打开凭证详情并验证 2 条分录回显。手工凭证写流程后续已补齐独立入口 `/api/finance/vouchers/manual` 和前端页面，但还需要补一轮真实浏览器生命周期 smoke 与预生产业务验收证据。
- 审批中心真实操作：通过 API 使用 `runtime_smoke` 创建并提交销售订单 -> 浏览器切回 `admin` 打开 `/workflow/tasks?businessType=SALES_ORDER&businessId=...&status=PENDING` -> 查看任务详情 -> 在审批中心点击通过 -> 回查后端确认销售订单 `status=APPROVED` / `approvalStatus=APPROVED`、审批任务 `APPROVED`、审批记录按 `businessType + businessId` 可过滤。本轮同时补齐后端 `GET /api/workflow/tasks/{id}`、`POST /api/workflow/tasks/{id}/approve|reject`，并修正前端工作流 Long ID 字符串归一化和 URL `businessId` 精度风险。最新样例销售订单：`SO202607030006`。
- 销售订单页面真实操作：通过 API 准备启用客户、仓库、商品、开放期间和库存 -> 浏览器切换为 `runtime_smoke` 创建销售订单并提交审批 -> 浏览器切回 `admin` 审批通过 -> 通过同一浏览器会话回查后端确认订单 `APPROVED`、审批状态 `APPROVED`、数量、金额和明细已落库；同一提交人自审会被后端拒绝，smoke 已按该业务规则使用双账号。
- 销售发货页面真实操作：通过 API 准备已审批销售订单、预留库存和开放期间 -> 页面新增销售发货 -> 过账 -> 通过同一浏览器会话回查后端确认发货单 `POSTED`、销售订单 `FULL_DELIVERED`、应收账款 `UNSETTLED` 且剩余金额等于发货含税金额。
- 销售收款页面真实操作：通过 API 准备已过账销售发货和未结应收 -> 页面新增收款 -> 选择客户和应收账款 -> 保存核销 -> 通过同一浏览器会话回查后端确认收款单 `POSTED`、应收账款 `SETTLED` 且剩余金额为 0。
- 销售退货页面真实操作：通过 API 准备已过账销售发货、开放期间和已扣减库存 -> 页面新增销售退货并选择发货单 -> 自动带出可退发货明细 -> 过账 -> 通过同一浏览器会话回查后端确认退货单 `POSTED`、发货明细 `returnedQty=3`、库存余额从 17 回补到 20，并生成 `SALES_RETURN` / `DECREASE` / `OFFSET` 应收冲减记录。最新样例退货单：`SRT202607030002`，销售发货 `SD202607030002`，商品 `UISP3055193933`。本轮同时修正前端销售退货从错误的 `orderId/items/productId/quantity` 提交口径，改为后端真实的 `deliveryId/lines/deliveryLineId/qty`，并补上 `/post` 过账入口和契约检查防回归。
- 采购退货页面真实操作：通过 API 准备已审批采购订单、已过账采购收货、开放期间和已入库库存 -> 页面新增采购退货并选择收货单 -> 自动带出可退收货明细 -> 过账 -> 通过同一浏览器会话回查后端确认退货单 `POSTED`、退货明细 `qty=2` / `returnedQty=2` / `availableReturnQty=2`、采购订单明细 `receivedQty=2`、库存余额从 4 扣到 2，并生成 `PURCHASE_RETURN` / `DECREASE` / `OFFSET` 应付冲减记录。最新样例退货单：`PRT202607030003`，采购收货 `PR202607030003`，商品 `UIPP3057707895`。本轮同时修正前端采购退货从错误的 `orderId/warehouseId/items/productId/quantity` 提交口径，改为后端真实的 `receiptId/lines/receiptLineId/qty`，补上真实 `/post` 过账入口、产品显示字段补齐和契约检查防回归。
- 生产订单页面真实操作：通过 API 准备启用成品、材料、材料出库仓、成品入库仓、BOM、开放期间和材料库存 -> 页面新增生产订单并选择两个不同仓库 -> 下达 -> 按剩余需求领料 -> 完工 -> 通过同一浏览器会话回查后端确认工单 `COMPLETED`、完工数量、材料领用数量、材料仓扣减和成品仓入库已落库。最新样例工单：`MO202607030001`，材料仓 `UI材料仓3048979144`，成品仓 `UI成品仓3048979144`。
- 库存调整页面真实操作：通过 API 准备启用仓库、商品和开放期间 -> 页面新增盘盈调整 -> 过账 -> 通过同一浏览器会话回查后端确认调整单 `POSTED`，库存余额 `qtyOnHand=7`、`qtyAvailable=7`、`qtyReserved=0`。最新样例调整单：`IA202607030007`，仓库 `UIAW3051671662`，商品 `UIAP3051671662`。本轮同时修复前端库存调整 ID 被转成 `number` 导致雪花 ID 精度丢失、过账误报“库存调整单不存在”的问题，并加入契约检查防回归。
- 库存调拨页面真实操作：通过 API 准备启用调出仓、调入仓、商品、开放期间和调出仓库存 -> 页面新增库存调拨 -> 过账 -> 通过同一浏览器会话回查后端确认调拨单 `POSTED`，调出仓余额从 11 扣到 7，调入仓余额从 0 增到 4，双方 `qtyReserved=0`。最新样例调拨单：`IT202607030002`，调出仓 `UITF3052530348`，调入仓 `UITT3052530348`，商品 `UITP3052530348`。本轮同时修正前端调拨的假两段式“发货/收货/在途”语义，改为对齐后端单次 `/post` 过账，并修复 `items/quantity` 到 `lines/qty/unitCost` 的提交映射、Long ID 精度和商品/仓库显示字段兼容。
- 库存盘点页面真实操作：通过 API 准备启用仓库、商品、开放期间和初始库存 -> 页面新增库存盘点并自动带出账面库存 -> 录入实际数量形成差异 -> 调整 -> 通过同一浏览器会话回查后端确认盘点单 `ADJUSTED`、已生成调整单号，库存余额从 10 调整到 13，且 `qtyReserved=0`。最新样例盘点单：`IC202607030003`，仓库 `UICH3053949671`，商品 `UICP3053949671`。本轮同时收窄前端盘点状态为后端真实的 `COUNTED/ADJUSTED/CANCELLED`，修复盘点 Long ID 精度风险，并加入契约检查防回归。
- 本次全量样例单据：销售审批 `SO202607020015`，销售发货 `SO202607020016 -> SD202607020007`，收款核销 `SO202607020017 -> SD202607020008 -> FR202607020004`，生产工单 `MO202607020008`。
- smoke 脚本未记录控制台异常、网络失败、API 4xx/5xx、鉴权重定向或权限拒绝。

---

## 仍缺或需要确认的内容

### P0：完整发布验收还没形成最终证据

后端单元和集成测试已通过，后端本地 release gate 已通过，前端类型检查和构建已通过。正式发布还缺以下证据：

- 如环境有 Docker，还要跑包含 Testcontainers 的门禁：`.\scripts\release-check.ps1 -AllowDirtyWorktree -IncludeTestcontainers -MavenRepoLocal .\.m2-release`。
- 当前机器未检测到 `docker` 命令，暂不能形成 Testcontainers 门禁证据。
- 本机替代跑法 `.\scripts\local-runtime-acceptance.ps1 -NoRollback` 已在干净联调库 `erp_codex_runtime` 上跑通，结论 **GO**，证据目录：`target/local-runtime-acceptance-20260716-113027/`。
- 仍缺独立预生产环境的同口径复验与证据归档；本机技术 GO 不能替代预生产环境签字。
- 人工验收：`docs/business-readiness-checklist.md` 中的业务核对项。

### P1：手工凭证 — API 全生命周期 smoke 已完成，并修复两个致命迁移缺陷（2026-07-13）

当前后端已支持业务自动生成凭证、费用过账生成凭证、凭证头/分录/总账/明细账查询；手工凭证写流程落在独立接口，避免和自动凭证只读查询混在一起：

- `GET /api/finance/vouchers/manual`
- `GET /api/finance/vouchers/manual/{id}`
- `POST /api/finance/vouchers/manual`
- `PUT /api/finance/vouchers/manual/{id}`
- `POST /api/finance/vouchers/manual/{id}/submit`
- `POST /api/finance/vouchers/manual/{id}/approve`
- `POST /api/finance/vouchers/manual/{id}/reject`
- `POST /api/finance/vouchers/manual/{id}/post`
- `POST /api/finance/vouchers/manual/{id}/cancel`
- `DELETE /api/finance/vouchers/manual/{id}`

对应源码和页面：

- 后端：`src/main/java/com/tuowei/erp/finance/voucher/controller/ManualVoucherController.java`
- 前端：`E:\tuowei\python\erp-frontend\src\views\finance\vouchers\manual\index.vue`
- 后端测试：`src/test/java/com/tuowei/erp/finance/voucher/ManualVoucherServiceTest.java`

**2026-07-13 已在干净库(Flyway V1→V100)跑通全生命周期 API smoke**：创建草稿 -> 编辑 -> 提交 -> 驳回回草稿 -> 重提 -> 审批 -> 过账 -> 作废红冲；并验证不平衡分录被拒、未审批先过账被拒；过账凭证与红冲凭证各自借贷平衡，两科目跨过账+红冲净额归零(总账正确抵销)。

**该 smoke 暴露并修复了两个此前从未被发现的致命迁移缺陷**（详见 commit）：

- **V99**：手工凭证单号规则 `FIN_MANUAL_VOUCHER` 从未生成 —— V86 复用了 V58 已占用的 `sys_sequence_rule.id=2013`(`FIN_BANK_STATEMENT`)，`ON DUPLICATE KEY UPDATE` 不改写 `biz_type`，导致创建第一步(nextNumber)必抛"编号规则不存在"，**手工凭证在所有环境从未可用**。V99 用未占用主键 2014 补种。
- **V100**：手工凭证菜单与 `finance:voucher:manage/approve/post` 按钮节点丢失 —— V86 用 `sys_menu` 5104-5107 播种，V87 复用同 id 播种库存补货并覆盖 path/permission，致非超管角色(如 ERP_ADMIN)看不到手工凭证页、拿不到按钮权限(超管走全树+反射权限故未暴露)。V100 用 5310-5313 重建财务节点并纠正补货节点错误 menu_code。回归测试 `ManualVoucherSeedCollisionMigrationTest`。

**剩余(降级为 P2)**：预生产环境中财务人员对分录、期间、总账影响和权限边界的**人工业务验收**。核心可用性与账务一致性已由 API smoke + 总账回查、以及**真实浏览器**手工凭证生命周期 smoke(创建 -> 提交 -> 审批 -> 过账 -> 作废红冲，逐步回查后端 `DRAFT/PENDING/APPROVED/POSTED/CANCELLED` 状态、`postedVoucherId` 与 `reversalVoucherId` 落库)共同证明；该浏览器 workflow 已纳入 `ui-smoke.mjs`(`manual-voucher-create-submit-approve-post-cancel`)。

### P1：历史报告已按归档快照口径处理

以下历史文档已明确标注为“归档历史快照”，正文保留当时结论用于追溯，不代表当前状态：

- `docs/BACKEND_API_DEVELOPMENT_PROGRESS.md` 正文仍称费用审批流程待开发，但源码已具备费用创建、更新、提交、审批、驳回、过账、红冲、作废。
- `docs/GLOBAL_PROJECT_AUDIT.md` 正文仍保留前端 TypeScript 编译失败记录，但当前 `npm run type-check` 已通过。
- `docs/WHAT_IS_MISSING.md` 已在本次更新为当前证据版。

后续如要重写旧报告，应改为事实流水或迁移到专门历史目录；当前状态判断以本文档和最新命令输出为准，别让后来的人看了跟着绕圈。

### P2：部分业务页面深度操作联调还没完成

构建通过不等于页面都能点通。当前基础运行链路、登录后 API smoke、部分写流程和关键页面基础 smoke 已经打通，但还需要做真实浏览器里的深度操作联调：

本轮尝试直接使用默认 `local` profile 连接默认库 `erp` 时，MySQL `localhost:3306` 可连接，但 Spring 启动在 Flyway 校验阶段失败：

- `V78` migration checksum mismatch：本地库已应用 checksum `-360653892`，当前源码解析为 `-2129362194`。
- 本地库存在已应用但当前源码未解析的 `V79`；`docs/migrations-history.md` 已说明该版本号为本机历史脏记录保留。

因此当前默认本地库不适合直接作为运行时联调库。已改用干净联调库 `erp_codex_runtime` 验证后端和前端代理基础链路；后续如果要继续用默认 `erp` 库，应在确认本机历史数据可丢弃/可修复后再处理 Flyway 历史，不要不看库状态就直接 `flyway repair`。

- 异常工单页面已完成真实浏览器闭环验证：创建、分派、开始处理、解决、关闭。
- SLA 策略页面已完成真实浏览器保存验证：配置说明保存并回显。
- 异常规则页面已完成真实浏览器配置保存、启停切换恢复和扫描全部验证。
- 业务追踪页面已完成真实浏览器查询和重置验证；当前干净联调库无追踪数据时覆盖空态。
- 财务期间页面已完成真实浏览器生成年期间、确认、检查和对账验证。
- 报表 CSV 导出已完成真实浏览器下载验证。
- 附件中心已完成真实浏览器上传、列表、下载和删除验证；本轮同时修复了前端附件 ID 被转成 `number` 导致雪花 ID 精度丢失的问题。
- 预生产验收页面已完成真实浏览器运行单创建、默认验收项回显、预检证据落库、手工证据登记、结果记录和 `No-Go` 决策验证；`Go` 决策要求所有 P0/P1 验收项通过，暂未纳入 smoke。
- 资金对账页面已完成真实浏览器资金账户创建、银行流水创建和后端落库回查验证；银行流水匹配/取消匹配已纳入真实浏览器 workflow，覆盖等额已过账收款单匹配、取消匹配原因落库和匹配字段清空。
- 费用单页面已对齐后端费用登记真实口径，完成真实浏览器创建、双账号提交/审批、过账生成凭证、红冲生成反向凭证和凭证金额/分录平衡回查验证。
- 库存查询、库存预警、用户管理、在线会话、通知中心、可观测性已完成基础路由 smoke；页面不再渲染“开发中”空壳，且首屏接口未出现 API 4xx/5xx。库存调整、库存调拨、库存盘点页的仓库/商品选项分页口径已修正为 `pageNo: 1, pageSize: 200`，避免误把 `pageNo` 当 `pageSize` 导致新增弹窗拿到空选项；库存调整创建 -> 过账 -> 库存余额回查、库存调拨创建 -> 过账 -> 双仓库存余额回查、库存盘点创建 -> 差异调整 -> 库存余额回查已完成真实浏览器 workflow。库存预警页当前对齐后端低库存规则命中列表：后端返回仓库/商品展示字段、当前库存、安全库存、缺口、`LOW_STOCK`/`OUT_OF_STOCK` 派生类型和 `ACTIVE/IGNORED/RESOLVED` 处置状态；前后端已接通 ignore/resolve/reactivate（`inv_alert_disposition`），**不需要**再造独立预警实例表（决策见 `docs/inventory-alert-lifecycle-decision.md`）。销售订单创建 -> 提交 -> 审批通过 -> 销售发货创建 -> 过账生成应收 -> 收款核销、销售退货创建 -> 过账 -> 库存回补 -> 应收冲减、采购退货创建 -> 过账 -> 库存扣减 -> 应付冲减和生产订单创建 -> 下达 -> 领料 -> 完工已完成真实浏览器 workflow；在线会话支持撤销入口，通知中心支持标记已读入口。
- 生产订单页面已拆分为“材料出库仓”和“成品入库仓”，并新增前端契约检查防止退回单仓口径。拆分后的真实浏览器双仓库 workflow 已完成：`UI_SMOKE_ROUTES=0 UI_SMOKE_WORKFLOW=production node scripts\ui-smoke.mjs` 跑通创建 -> 下达 -> 领料 -> 完工，并断言后端明细保留不同的 `materialWarehouseId` / `finishedWarehouseId`、材料仓材料 `qtyOnHand=10` / `qtyReserved=0` / `qtyAvailable=10`、成品仓成品 `qtyOnHand=5`。
- Controller 写端点与前端页面/API 的联调缺口已清零；剩余工作主要是持续补强真实浏览器证据和发布侧门禁，不再是“后端有写端点但前端没接线”的老问题。
- 手工凭证页面已完成真实浏览器全生命周期 workflow：API 预置借/贷会计科目和开放期间 -> 页面「录入凭证」填凭证日期/摘要、逐行选科目并录借贷金额、校验「借贷平衡」后保存草稿 -> 行内「提交」->「审批」->「过账」->「作废」填原因红冲，每步通过同一浏览器会话回查后端确认状态流转到 `DRAFT/PENDING/APPROVED/POSTED/CANCELLED`、`postedVoucherId` 与 `reversalVoucherId` 已落库。最新样例凭证：`MV202607130004`。workflow 名 `manual-voucher-create-submit-approve-post-cancel`。
- 采购来料质检(IQC)检验单已完成真实浏览器 workflow：API 预置需检验商品(`inspectionRequired=true`)、已审批采购订单和一张 **DRAFT** 采购入库单 -> 页面「新建检验单」选草稿入库单自动带出检验行 -> 提交 -> 判定录入合格 3 / 不合格 1 -> 通过同一浏览器会话回查后端确认检验单 `JUDGED`、合格/不合格数量落库，并验证**仅合格品入库**口径：引用的草稿入库单行数量由 4 回写为合格数 3。最新样例检验单：`QC202607130003`，采购收货 `PR202607130005`。workflow 名 `qc-inspection-create-submit-judge-only-qualified`。
- P2-7 Web 扫码录入已完成真实浏览器 workflow 验证：产品条码通过 V119 持久化，`/api/masterdata/products/by-barcode` 提供租户内精确查找，采购收货草稿编辑与销售发货草稿编辑均支持扫码枪 Enter 录入后把匹配商品行数量累加到 1。当前证据命令：`UI_SMOKE_ROUTES=0 UI_SMOKE_WORKFLOW=purchase-receipt-draft-edit,sales-delivery-draft-edit node scripts\ui-smoke.mjs`，2 个 workflow 均 `passed=true`，截图落在 `target/ui-smoke-*-barcode.png`。
- 全套 34 个 `ui-smoke.mjs` workflow 已在干净联调库 `erp_codex_runtime` 上跑通、0 失败、无控制台异常/网络失败/API 4xx/5xx、鉴权重定向或权限拒绝。运行方式：`UI_SMOKE_ROUTES=0 node scripts\ui-smoke.mjs`(harness 自启后端 jar + Vite dev + Headless Chrome)。

### 2026-07-13 再补：补齐"后端就绪但前端缺失"的三页 + 前端测试基线 + 按钮权限安全子批

- **生产工作中心** `/production/work-centers`、**生产工艺路线** `/production/routings`：后端 CRUD/启停早已就绪(V92 schema + V93 菜单种子已绑 3002),此前前端零页面零 API。本轮补齐 `api/production.ts` 类型+封装、两个页面(自带按钮级 `v-permission`)、router 路由;路由 smoke 已通过(页面渲染、首屏接口无 4xx/5xx)。
- **单据状态流转规则(只读)** `/system/document-state-rules`：后端只读 API(`system:config:view` 门禁)早已就绪但前端无入口。本轮补 `V101__document_state_rule_menu_seed.sql`(menu 5320 / role_menu 7330,绑 3002)、`api/system.ts` 封装、只读矩阵页(按单据类型合并展示动作/源状态/目标状态/执行约束);路由 smoke 已通过。
- **前端单元测试基线**：此前前端零测试(无 test 脚本/无 vitest/0 文件)。本轮引入 vitest + happy-dom,新增 `vitest.config.ts`、`src/test/setup.ts`,覆盖 `auth`/`production` API 雪花 ID→字符串归一化和 `user` store 权限/登录态共 10 个确定性用例,全绿;`package.json` 加 `test`/`test:watch` 脚本,`frontend-verify.yml` CI 加"Unit tests"步。
- **按钮级权限安全子批**:对"后端 BUTTON 种子已就绪且已绑 ERP_ADMIN(3002)"的 5 页挂 `v-permission`(user-sessions/attachments/boms/workflow-configs/readiness),收口后 admin 不受影响。随后 `V102__notification_workflow_button_permission_seed.sql` 与 `V103__inventory_reservation_release_button_bind.sql` 已补齐 notifications read、workflow approve/reject、reservation release 的后端权限码/种子，前端对应按钮也已挂 `v-permission`；按钮权限收口现已清零。

### P2：仓库状态需要收敛（2026-07-13 已完成）

~~后端和前端都存在大量未提交改动~~ —— 已按功能分组 review 并拆分提交，两仓工作区现已干净：

- QC 模块(后端 + 前端 + 产品需检验开关)、V99/V100 迁移修复及回归测试、V95 按钮种子迁移断言、按钮级权限收口 7 页、superpowers 规划文档归档,均已各自成 commit。
- `dist`、构建缓存、运行日志未纳入提交。

后续新增迁移前务必先查 `sys_*` 表已占用的显式主键 id(sequence_rule/menu/role_menu),避免重蹈 V86 的主键复用覆盖(V99/V100 已修的两处即此类)。

---

## 不建议现在直接硬补的内容

### 在手工凭证上继续硬扩复杂财务规则

手工凭证基础生命周期已经存在。后续如果要继续扩展复杂能力，例如跨期间调整、反审核、红字凭证策略、附件强制留痕、多人复核或和外部财务系统对账，必须先确认业务口径，再按 TDD 做最小闭环。别在现有页面上顺手堆按钮，财务账不是用来开盲盒的。

### 大规模前端重构

当前前端能过类型检查和生产构建。除非运行时联调发现具体问题，否则不建议为了“看着更整齐”乱动页面结构。

---

## 推荐下一步

1. ~~跑绿四单据草稿 PUT 编辑 / 反审核 smoke~~ **已完成**
2. ~~日常联调固定 erp_codex_runtime + 本机验收~~ **已完成**（含回滚 GO）
3. ~~Controller 写端点全量对前端~~ **已完成**（194/194；IQC 草稿编辑已接）
4. ~~F/Q 技术预检（含 F10/F11/Q4/Q5 负例）~~ **已完成**（`fq-signoff-*.cjs`）
5. **财务/质检在 `docs/FQ-SIGNOFF-FINAL-2026-07-16.md` 签字**（当前唯一业务门禁）
6. ~~有 Docker 的环境跑 `.\scripts\release-check.ps1 -IncludeTestcontainers`~~ **已完成**（2026-07-17，commit 当时基线 + Docker core DECIDED_GO）
7. ~~Track B 扩展 API 证据~~ **已完成**（`node scripts/extension-features-api-smoke.cjs` → **15/15**，证据 `target/extension-features-api-smoke/`；覆盖询价→PO、发票确认/作废、信用超限拦截、OQC 闸门）
8. 本机 readiness 子项回填用 `.\scripts\register-readiness-item-result.ps1`，覆盖 `FINANCE_LEDGER`、`PERIOD_LOCK`、`INVENTORY_FINANCE_RECONCILIATION`、`INITIAL_IMPORT`、`BACKUP_ROLLBACK`（能证则 PASSED，不能证则保持 BLOCKED）
9. 可选：独立机房预生产；C4 巨石拆分 / C5 缓存深化单独立项
10. 发布前若工作树有未入候选的 WIP：干净 commit 后重跑 release-check + ui-smoke + extension-features-api-smoke

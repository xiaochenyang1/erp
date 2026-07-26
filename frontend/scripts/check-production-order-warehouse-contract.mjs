import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { interfaceIncludes } from './check-contract-utils.mjs'

const root = resolve(import.meta.dirname, '..')
const orderView = readFileSync(resolve(root, 'src/views/production/orders/index.vue'), 'utf8')
// BOM 页已按 E-1 拆分为展示/列表/表单 composable，契约仍按整块特性校验
const bomView = [
  'src/views/production/boms/index.vue',
  'src/composables/useProductionBomPresentation.ts',
  'src/composables/useProductionBomList.ts',
  'src/composables/useProductionBomForm.ts'
].map((path) => readFileSync(resolve(root, path), 'utf8')).join('\n')
const productionApi = readFileSync(resolve(root, 'src/api/production.ts'), 'utf8')
const inventoryApi = readFileSync(resolve(root, 'src/api/inventory.ts'), 'utf8')
const authApi = readFileSync(resolve(root, 'src/api/auth.ts'), 'utf8')
const purchaseApi = readFileSync(resolve(root, 'src/api/purchase.ts'), 'utf8')
const purchaseInquiryView = readFileSync(resolve(root, 'src/views/purchase/inquiries/index.vue'), 'utf8')
const purchaseReceiptView = readFileSync(resolve(root, 'src/views/purchase/receipts/index.vue'), 'utf8')
const purchaseReturnView = readFileSync(resolve(root, 'src/views/purchase/returns/index.vue'), 'utf8')
const salesApi = readFileSync(resolve(root, 'src/api/sales.ts'), 'utf8')
const dashboardApi = readFileSync(resolve(root, 'src/api/dashboard.ts'), 'utf8')
const businessTraceApi = readFileSync(resolve(root, 'src/api/businessTrace.ts'), 'utf8')
const businessTimelineApiPath = resolve(root, 'src/api/businessTimeline.ts')
const businessTimelineApi = existsSync(businessTimelineApiPath) ? readFileSync(businessTimelineApiPath, 'utf8') : ''
const businessTraceView = readFileSync(resolve(root, 'src/views/reports/traces/index.vue'), 'utf8')
const salesOrderView = readFileSync(resolve(root, 'src/views/sales/orders/index.vue'), 'utf8')
const salesDeliveryView = readFileSync(resolve(root, 'src/views/sales/deliveries/index.vue'), 'utf8')
const purchaseOrderView = readFileSync(resolve(root, 'src/views/purchase/orders/index.vue'), 'utf8')
const financeReceivableView = readFileSync(resolve(root, 'src/views/finance/receivables/index.vue'), 'utf8')
const financePayableView = readFileSync(resolve(root, 'src/views/finance/payables/index.vue'), 'utf8')
const financeAccountView = readFileSync(resolve(root, 'src/views/finance/FinanceAccountsPage.vue'), 'utf8')
// 收付款页已按 E-1 拆分为展示/列表/表单/详情 composable，契约仍按整块特性校验
const financePaymentView = [
  'src/views/finance/payments/index.vue',
  'src/composables/useSettlementPresentation.ts',
  'src/composables/useSettlementList.ts',
  'src/composables/useSettlementForm.ts',
  'src/composables/useSettlementDetail.ts'
].map((path) => readFileSync(resolve(root, path), 'utf8')).join('\n')
const systemLogView = readFileSync(resolve(root, 'src/views/system/logs/index.vue'), 'utf8')
const systemApi = readFileSync(resolve(root, 'src/api/system.ts'), 'utf8')
// 用户管理页已按 E-1 拆分为展示/列表/表单 composable，契约仍按整块特性校验
const systemUserView = [
  'src/views/system/users/index.vue',
  'src/composables/useSystemUserPresentation.ts',
  'src/composables/useSystemUserList.ts',
  'src/composables/useSystemUserForm.ts'
].map((path) => readFileSync(resolve(root, path), 'utf8')).join('\n')
const userStore = readFileSync(resolve(root, 'src/store/modules/user.ts'), 'utf8')
const layoutView = readFileSync(resolve(root, 'src/layout/index.vue'), 'utf8')
const observabilityApi = readFileSync(resolve(root, 'src/api/observability.ts'), 'utf8')
const observabilityView = readFileSync(resolve(root, 'src/views/system/observability/index.vue'), 'utf8')
const readinessApi = readFileSync(resolve(root, 'src/api/readiness.ts'), 'utf8')
const readinessView = readFileSync(resolve(root, 'src/views/system/readiness/index.vue'), 'utf8')
const roleView = readFileSync(resolve(root, 'src/views/system/roles/index.vue'), 'utf8')
const menuView = readFileSync(resolve(root, 'src/views/system/menus/index.vue'), 'utf8')
const deptView = readFileSync(resolve(root, 'src/views/system/depts/index.vue'), 'utf8')
const postView = readFileSync(resolve(root, 'src/views/system/posts/index.vue'), 'utf8')
const dictView = readFileSync(resolve(root, 'src/views/system/dicts/index.vue'), 'utf8')
const configView = readFileSync(resolve(root, 'src/views/system/configs/index.vue'), 'utf8')
const salesReturnView = readFileSync(resolve(root, 'src/views/sales/returns/index.vue'), 'utf8')
const financeApi = readFileSync(resolve(root, 'src/api/finance.ts'), 'utf8')
// 费用页已按 E-1 拆分为展示/列表/表单 composable，契约仍按整块特性校验
const expenseView = [
  'src/views/finance/expenses/index.vue',
  'src/composables/useExpensePresentation.ts',
  'src/composables/useExpenseList.ts',
  'src/composables/useExpenseForm.ts'
].map((path) => readFileSync(resolve(root, path), 'utf8')).join('\n')
const voucherView = readFileSync(resolve(root, 'src/views/finance/vouchers/index.vue'), 'utf8')
const subjectView = readFileSync(resolve(root, 'src/views/finance/subjects/index.vue'), 'utf8')
const ledgerView = readFileSync(resolve(root, 'src/views/finance/ledger/index.vue'), 'utf8')
const financePeriodView = readFileSync(resolve(root, 'src/views/finance/periods/index.vue'), 'utf8')
const fundApi = readFileSync(resolve(root, 'src/api/fund.ts'), 'utf8')
// 资金页已按 E-1 拆分为展示/列表/表单 composable，契约仍按整块特性校验
const fundView = [
  'src/views/finance/funds/index.vue',
  'src/composables/useFundPresentation.ts',
  'src/composables/useFundList.ts',
  'src/composables/useFundForm.ts'
].map((path) => readFileSync(resolve(root, path), 'utf8')).join('\n')
const masterdataApi = readFileSync(resolve(root, 'src/api/masterdata.ts'), 'utf8')
const barcodeScanField = readFileSync(resolve(root, 'src/components/common/BarcodeScanField.vue'), 'utf8')
const barcodeUtils = readFileSync(resolve(root, 'src/utils/barcode.ts'), 'utf8')
const productView = readFileSync(resolve(root, 'src/views/masterdata/products/index.vue'), 'utf8')
const customerView = readFileSync(resolve(root, 'src/views/masterdata/customers/index.vue'), 'utf8')
const supplierView = readFileSync(resolve(root, 'src/views/masterdata/suppliers/index.vue'), 'utf8')
const warehouseView = readFileSync(resolve(root, 'src/views/masterdata/warehouses/index.vue'), 'utf8')
const workflowApi = readFileSync(resolve(root, 'src/api/workflow.ts'), 'utf8')
const workflowTaskView = readFileSync(resolve(root, 'src/views/workflow/tasks/index.vue'), 'utf8')
const workflowTaskQuery = readFileSync(resolve(root, 'src/views/workflow/tasks/query.ts'), 'utf8')
const workflowRecordView = readFileSync(resolve(root, 'src/views/workflow/records/index.vue'), 'utf8')
const workflowConfigViewPath = resolve(root, 'src/views/workflow/configs/index.vue')
const workflowConfigView = existsSync(workflowConfigViewPath) ? readFileSync(workflowConfigViewPath, 'utf8') : ''
const exceptionTicketApi = readFileSync(resolve(root, 'src/api/exceptionTicket.ts'), 'utf8')
const exceptionTicketView = readFileSync(resolve(root, 'src/views/exception-tickets/index.vue'), 'utf8')
const exceptionRuleApi = readFileSync(resolve(root, 'src/api/exceptionRule.ts'), 'utf8')
const exceptionRuleView = readFileSync(resolve(root, 'src/views/exception-rules/index.vue'), 'utf8')
const exceptionSlaPolicyApi = readFileSync(resolve(root, 'src/api/exceptionSlaPolicy.ts'), 'utf8')
const exceptionSlaPolicyView = readFileSync(resolve(root, 'src/views/exception-sla-policies/index.vue'), 'utf8')
const attachmentApi = readFileSync(resolve(root, 'src/api/attachment.ts'), 'utf8')
const attachmentView = readFileSync(resolve(root, 'src/views/system/attachments/index.vue'), 'utf8')
const routerConfig = readFileSync(resolve(root, 'src/router/index.ts'), 'utf8')
const importsApiPath = resolve(root, 'src/api/imports.ts')
const importsViewPath = resolve(root, 'src/views/system/imports/index.vue')
const importsApi = existsSync(importsApiPath) ? readFileSync(importsApiPath, 'utf8') : ''
const importsView = existsSync(importsViewPath) ? readFileSync(importsViewPath, 'utf8') : ''
const inventoryStockView = readFileSync(resolve(root, 'src/views/inventory/stocks/index.vue'), 'utf8')
const inventoryStockDetails = readFileSync(resolve(root, 'src/composables/useInventoryStockDetails.ts'), 'utf8')
const inventoryStockActions = readFileSync(resolve(root, 'src/composables/useInventoryStockActions.ts'), 'utf8')
const inventoryStockResources = readFileSync(resolve(root, 'src/composables/useInventoryStockResources.ts'), 'utf8')
const inventoryStockFeature = `${inventoryStockView}\n${inventoryStockDetails}\n${inventoryStockActions}\n${inventoryStockResources}`
const inventoryAlertView = readFileSync(resolve(root, 'src/views/inventory/alerts/index.vue'), 'utf8')
// 盘点页已按 E-1 拆分为展示/列表/表单 composable，契约仍按整块特性校验
const inventoryCheckView = readFileSync(resolve(root, 'src/views/inventory/checks/index.vue'), 'utf8')
const inventoryCheckPresentation = readFileSync(resolve(root, 'src/composables/useInventoryCheckPresentation.ts'), 'utf8')
const inventoryCheckList = readFileSync(resolve(root, 'src/composables/useInventoryCheckList.ts'), 'utf8')
const inventoryCheckForm = readFileSync(resolve(root, 'src/composables/useInventoryCheckForm.ts'), 'utf8')
const inventoryCheckFeature = [
  inventoryCheckView,
  inventoryCheckPresentation,
  inventoryCheckList,
  inventoryCheckForm
].join('\n')
// 调拨页已按 E-1 拆分为展示/列表/表单 composable，契约仍按整块特性校验
const inventoryTransferFeature = [
  'src/views/inventory/transfers/index.vue',
  'src/composables/useInventoryTransferPresentation.ts',
  'src/composables/useInventoryTransferList.ts',
  'src/composables/useInventoryTransferForm.ts'
].map((path) => readFileSync(resolve(root, path), 'utf8')).join('\n')
// 调整页已按 E-1 拆分为展示/列表/表单 composable，契约仍按整块特性校验
const inventoryAdjustmentFeature = [
  'src/views/inventory/adjustments/index.vue',
  'src/composables/useInventoryAdjustmentPresentation.ts',
  'src/composables/useInventoryAdjustmentList.ts',
  'src/composables/useInventoryAdjustmentForm.ts'
].map((path) => readFileSync(resolve(root, path), 'utf8')).join('\n')
const inventoryOptionViews = [
  {
    name: '库存调整页',
    content: inventoryAdjustmentFeature
  },
  {
    name: '库存调拨页',
    content: inventoryTransferFeature
  },
  {
    name: '库存盘点页',
    content: inventoryCheckFeature
  }
]

const requiredFragments = [
  ':label="t(\'productionOrder.materialWarehouse\')"',
  'prop="materialWarehouseId"',
  'v-model="formData.materialWarehouseId"',
  ':label="t(\'productionOrder.finishedWarehouse\')"',
  'prop="finishedWarehouseId"',
  'v-model="formData.finishedWarehouseId"',
  'const optionPageQuery = { pageNo: 1, pageSize: 200 }',
  'getProducts(optionPageQuery)',
  'getWarehouses(optionPageQuery)',
  'getBOMs(optionPageQuery)'
]

const forbiddenFragments = [
  'label="目标仓库"',
  'prop="warehouseId"',
  'v-model="formData.warehouseId"',
  'warehouseId: [{ required: true',
  'getProducts({ page: 1, size: 1000 })',
  'getWarehouses({ page: 1, size: 1000 })',
  'getBOMs({ page: 1, size: 1000 })'
]

const errors = []

for (const fragment of [
  'documentId: string',
  'partnerId?: string',
  'const normalizeBusinessTrace =',
  'documents: (trace.documents || []).map(normalizeBusinessTraceDocument)',
  'documentId: String(document.documentId)',
  'partnerId: document.partnerId != null ? String(document.partnerId) : undefined'
]) {
  if (!businessTraceApi.includes(fragment)) {
    errors.push(`单据追踪 API 缺少 Long ID 字符串归一化片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface BusinessTimelineEvent {\n  id: string',
  'businessId: string',
  'attachmentId?: string',
  'operatorUserId?: string',
  'export interface BusinessTimelineQuery extends PageQuery',
  'businessId?: string | number',
  'export interface BusinessTimelineCommentRequest',
  'businessId: string | number',
  'export const getBusinessTimeline =',
  "request.get<PageResponse<BusinessTimelineEvent>>('/business-timeline'",
  'export const createBusinessTimelineComment =',
  "request.post<BusinessTimelineEvent>('/business-timeline/comments'",
  'const normalizeBusinessTimelineEvent =',
  'id: String(event.id)',
  'businessId: String(event.businessId)',
  'attachmentId: event.attachmentId != null ? String(event.attachmentId) : undefined',
  'operatorUserId: event.operatorUserId != null ? String(event.operatorUserId) : undefined'
]) {
  if (!businessTimelineApi.includes(fragment)) {
    errors.push(`业务时间线 API 缺少后端真实契约或 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface BusinessTimelineEvent {\n  id: number',
  'businessId: number',
  'attachmentId?: number',
  'operatorUserId?: number',
  'Promise.reject',
  '暂未提供',
  '模拟',
  'fake',
  'mock'
]) {
  if (businessTimelineApi.includes(fragment)) {
    errors.push(`业务时间线 API 仍保留 Long ID 数字风险或假流程片段: ${fragment}`)
  }
}

for (const fragment of [
  'getBusinessTimeline',
  'createBusinessTimelineComment',
  'businessTimelineVisible',
  'selectedTimelineDocument',
  'businessTimelineEvents',
  'timelineCommentForm.content',
  'openBusinessTimeline',
  'loadBusinessTimeline',
  'submitTimelineComment',
  'row.documentType',
  'row.documentId',
  'row.bizNo'
]) {
  if (!businessTraceView.includes(fragment)) {
    errors.push(`业务追踪页缺少业务时间线真实入口片段: ${fragment}`)
  }
}

for (const fragment of [
  'Promise.reject',
  '暂未提供',
  '模拟',
  'fake',
  'mock',
  'documentId: number',
  '<el-input-number'
]) {
  if (businessTraceView.includes(fragment)) {
    errors.push(`业务追踪页仍保留 Long ID 数字输入或假流程片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface UserInfo {\n  id: string',
  'warehouseIds: string[]',
  'const normalizeLoginResponse =',
  'const normalizeUserInfo =',
  'const normalizeUserDataScope =',
  'id: String(user.id)',
  'warehouseIds: dataScope.warehouseIds.map(String)',
  "return request.post<LoginResponse>('/auth/login', data).then(normalizeLoginResponse)",
  "return request.post<LoginResponse>('/auth/refresh', { refreshToken }).then(normalizeLoginResponse)",
  "return request.get<UserInfo>('/auth/user-info').then(normalizeUserInfo)",
  'export const logout = (refreshToken: string) =>',
  "return request.post('/auth/logout', { refreshToken })"
]) {
  if (!authApi.includes(fragment)) {
    errors.push(`认证 API 缺少后端真实契约或 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface UserInfo {\n  id: number',
  "return request.post('/auth/logout')",
  "return request.post<LoginResponse>('/auth/login', data)\n}",
  "return request.get<UserInfo>('/auth/user-info')\n}"
]) {
  if (authApi.includes(fragment)) {
    errors.push(`认证 API 仍保留 Long ID 数字精度风险或旧登出口径片段: ${fragment}`)
  }
}

for (const fragment of [
  "const storedRefreshToken = localStorage.getItem('refreshToken')",
  'if (storedRefreshToken) {',
  'await logout(storedRefreshToken)'
]) {
  if (!userStore.includes(fragment)) {
    errors.push(`用户状态 Store 缺少真实登出 refreshToken 片段: ${fragment}`)
  }
}

for (const fragment of [
  'changePassword,',
  'profileDialogVisible',
  'passwordDialogVisible',
  'passwordFormRef',
  'passwordForm.oldPassword',
  'passwordForm.newPassword',
  'passwordForm.confirmPassword',
  'const openProfileDialog = () =>',
  'const openPasswordDialog = () =>',
  'const submitPasswordChange = async () =>',
  'await changePassword({',
  'oldPassword: passwordForm.oldPassword',
  'newPassword: passwordForm.newPassword',
  'userStore.doLogout()'
]) {
  if (!layoutView.includes(fragment)) {
    errors.push(`顶栏用户菜单缺少真实个人中心/修改密码片段: ${fragment}`)
  }
}

for (const fragment of [
  "case 'profile':\n      // 跳转个人中心\n      break",
  "case 'password':\n      // 打开修改密码对话框\n      break"
]) {
  if (layoutView.includes(fragment)) {
    errors.push(`顶栏用户菜单仍保留空转假入口片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface SystemHealth',
  'status: string',
  'export const getSystemHealth =',
  "request.get<SystemHealth>('/health')"
]) {
  if (!observabilityApi.includes(fragment)) {
    errors.push(`可观测性 API 缺少平台探活真实契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'getSystemHealth',
  'platformHealth',
  'loadPlatformHealth',
  'systemObservability.platformStatus'
]) {
  if (!observabilityView.includes(fragment)) {
    errors.push(`可观测性页面缺少 /api/health 平台状态入口片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface ReadinessRun {\n  id: string',
  'export interface ReadinessItem {\n  id: string',
  'export interface ReadinessEvidence {\n  id: string',
  'businessId?: string',
  'attachmentBusinessId?: string',
  'export const recordReadinessPreflightEvidence = (runId: string | number)',
  '.post<ReadinessPreflight>(`/system/readiness/runs/${runId}/preflight-evidence`)',
  'export const addReadinessItem = (runId: string | number, data: ReadinessItemCreateRequest)',
  'request.post<ReadinessItem>(`/system/readiness/runs/${runId}/items`, data).then(normalizeItem)',
  'export const addReadinessEvidence = (itemId: string | number, data: ReadinessEvidenceCreateRequest)',
  'request.post<ReadinessEvidence>(`/system/readiness/items/${itemId}/evidence`, data).then(normalizeEvidence)',
  'export const markReadinessItemResult = (itemId: string | number, data: ReadinessItemResultRequest)',
  'request.post<ReadinessItem>(`/system/readiness/items/${itemId}/result`, data).then(normalizeItem)',
  'export const decideReadinessRun = (runId: string | number, data: ReadinessDecisionRequest)',
  'request.post<ReadinessRun>(`/system/readiness/runs/${runId}/decision`, data).then(normalizeRun)',
  'const normalizeRun =',
  'id: String(run.id)',
  'const normalizeItem =',
  'runId: String(item.runId)',
  'const normalizeEvidence =',
  'businessId: evidence.businessId != null ? String(evidence.businessId) : undefined',
  'attachmentBusinessId: evidence.attachmentBusinessId != null ? String(evidence.attachmentBusinessId) : undefined'
]) {
  if (!readinessApi.includes(fragment)) {
    errors.push(`预生产验收 API 缺少后端真实契约或 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'id: number',
  'runId: number',
  'businessId?: number',
  'attachmentBusinessId?: number',
  "return Promise.reject",
  '暂未提供',
  '模拟',
  'fake',
  'mock'
]) {
  if (readinessApi.includes(fragment)) {
    errors.push(`预生产验收 API 仍保留 Long ID 数字风险或假流程片段: ${fragment}`)
  }
}

for (const fragment of [
  'systemReadiness.title',
  'recordReadinessPreflightEvidence',
  'addReadinessItem',
  'addReadinessEvidence',
  'markReadinessItemResult',
  'decideReadinessRun',
  'handleRecordPreflight',
  'submitItem',
  'submitEvidence',
  'submitResult',
  'submitDecision',
  'v-model="evidenceForm.businessId"',
  'v-model="evidenceForm.attachmentBusinessType"',
  'v-model="evidenceForm.attachmentBusinessId"',
  'isRunClosed'
]) {
  if (!readinessView.includes(fragment)) {
    errors.push(`预生产验收页面缺少真实子操作入口片段: ${fragment}`)
  }
}

for (const fragment of [
  '<el-input-number\n          v-model="evidenceForm.businessId"',
  '<el-input-number\n          v-model="evidenceForm.attachmentBusinessId"',
  "return Promise.reject",
  '暂未提供',
  '模拟',
  'fake',
  'mock'
]) {
  if (readinessView.includes(fragment)) {
    errors.push(`预生产验收页面仍保留 Long ID 数字输入或假流程片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface PurchaseOrderTrace',
  'export interface PurchaseOrderExecutionInfo',
  'export interface PurchaseOrderRelatedDocs',
  'export interface PurchaseOrderDocumentSummary {\n  id: string',
  'export const cancelPurchaseOrder = (id: string | number)',
  'request.post<PurchaseOrder>(`/purchase/orders/${id}/cancel`).then(normalizePurchaseOrder)',
  'export const closePurchaseOrder = (id: string | number)',
  'request.post<PurchaseOrder>(`/purchase/orders/${id}/close`).then(normalizePurchaseOrder)',
  'export const tracePurchaseOrder = (id: string | number)',
  'request.get<PurchaseOrderTrace>(`/purchase/orders/${id}/trace`).then(normalizePurchaseOrderTrace)',
  'const normalizePurchaseOrderTrace =',
  'const normalizePurchaseOrderDocumentSummary =',
  'id: String(document.id)'
]) {
  if (!purchaseApi.includes(fragment)) {
    errors.push(`采购订单 API 缺少取消/关闭/追踪真实契约或 Long ID 归一化片段: ${fragment}`)
  }
}

for (const fragment of [
  'export const deletePurchaseOrder =',
  "request.post(`/purchase/orders/${id}/cancel`)",
  'return request.get(`/purchase/orders/${id}/trace`)'
]) {
  if (purchaseApi.includes(fragment)) {
    errors.push(`采购订单 API 仍保留删除伪语义或未归一化追踪片段: ${fragment}`)
  }
}

for (const fragment of [
  'cancelPurchaseOrder,',
  'closePurchaseOrder,',
  'tracePurchaseOrder,',
  'traceVisible',
  'purchaseTrace',
  'handleCancelOrder',
  'handleCloseOrder',
  'handleTraceOrder',
  'canCancelOrder(row)',
  'canCloseOrder(row)',
  ':title="t(\'purchaseOrder.traceTitle\')"',
  'purchaseTrace.executionInfo',
  'purchaseTrace.relatedDocs'
]) {
  if (!purchaseOrderView.includes(fragment)) {
    errors.push(`采购订单页缺少取消/关闭/追踪真实入口片段: ${fragment}`)
  }
}

for (const fragment of [
  'handleDelete(row)',
  '确认删除订单',
  '删除成功',
  '删除失败'
]) {
  if (purchaseOrderView.includes(fragment)) {
    errors.push(`采购订单页仍保留取消接口的删除伪语义片段: ${fragment}`)
  }
}

for (const fragment of [
  'sourceInquiryId?: string | number | null',
  'sourceInquiryNo?: string | null',
  'sourceQuoteId?: string | number | null',
  'sourceInquiryLineId?: string | number | null',
  'convertedOrderId?: string | number | null',
  'convertedOrderNo?: string | null',
  "status: 'DRAFT' | 'SUBMITTED' | 'CLOSED' | 'CONVERTED' | 'CANCELLED' | string",
  'export const convertPurchaseInquiryToPurchaseOrder = (id: string | number)',
  '.post<PurchaseOrder>(`/purchase/inquiries/${id}/convert-to-purchase-order`)',
  '.then(normalizePurchaseOrder)',
  'convertedOrderId: inquiry.convertedOrderId != null ? String(inquiry.convertedOrderId) : inquiry.convertedOrderId',
  'sourceInquiryLineId: item.sourceInquiryLineId != null ? String(item.sourceInquiryLineId) : item.sourceInquiryLineId'
]) {
  if (!purchaseApi.includes(fragment)) {
    errors.push(`采购询价原子转换 API 缺少双向来源或 Long ID 归一化片段: ${fragment}`)
  }
}

for (const fragment of [
  'convertPurchaseInquiryToPurchaseOrder,',
  "userStore.hasPermission('purchase:inquiry:manage')",
  "userStore.hasPermission('purchase:order:create')",
  "row.status === 'CLOSED' && canConvertToPurchaseOrder",
  "CONVERTED: t('purchaseInquiryOps.status.converted')",
  'current?.convertedOrderNo',
  'current?.convertedTime',
  'await convertPurchaseInquiryToPurchaseOrder(prefillInquiryId.value)',
  'await convertPurchaseInquiryToPurchaseOrder(row.id)'
]) {
  if (!purchaseInquiryView.includes(fragment)) {
    errors.push(`采购询价页缺少原子转换、双权限或转换结果展示片段: ${fragment}`)
  }
}

for (const fragment of [
  'createPurchaseOrder,',
  'await createPurchaseOrder(',
  'const source = await getPurchaseInquiryPoPrefill(row.id)'
]) {
  if (purchaseInquiryView.includes(fragment)) {
    errors.push(`采购询价页仍保留预填后再次创建采购订单的非原子流程片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface PurchaseInquiryQuoteLine {',
  'inquiryLineId: string | number',
  'lines?: PurchaseInquiryQuoteLine[]',
  'export interface PurchaseInquiryQuoteLineRequest {',
  'lines?: PurchaseInquiryQuoteLineRequest[]',
  'lines: (quote.lines || []).map((line) => ({',
  'id: String(line.id)',
  'inquiryLineId: String(line.inquiryLineId)'
]) {
  if (!purchaseApi.includes(fragment)) {
    errors.push(`采购询价 API 缺少逐行报价类型或 Long ID 归一化片段: ${fragment}`)
  }
}

for (const fragment of [
  'quoteForm.lines = (detail.lines || []).map((line) => ({',
  'inquiryLineId: String(line.id)',
  'lines: quoteForm.lines.map((line) => ({',
  'inquiryLineProductLabel(line.inquiryLineId, selectInquiryLines)',
  "row.lines?.length",
  'purchaseInquiryOps.legacyQuoteSummary'
]) {
  if (!purchaseInquiryView.includes(fragment)) {
    errors.push(`采购询价页缺少逐行报价录入、展示或历史报价回退片段: ${fragment}`)
  }
}

for (const fragment of [
  'unitPrice: quoteForm.unitPrice',
  'taxRate: quoteForm.taxRate'
]) {
  if (purchaseInquiryView.includes(fragment)) {
    errors.push(`采购询价页仍提交旧的整单报价字段: ${fragment}`)
  }
}

const masterdataLongIdContracts = [
  // 产品契约已迁移到版本化 OpenAPI + openapi-typescript，不再做字符串片段检查。
  { name: '客户', type: 'Customer', plural: 'Customers', path: 'customers', normalize: 'normalizeCustomer' },
  { name: '供应商', type: 'Supplier', plural: 'Suppliers', path: 'suppliers', normalize: 'normalizeSupplier' },
  { name: '仓库', type: 'Warehouse', plural: 'Warehouses', path: 'warehouses', normalize: 'normalizeWarehouse' }
]

for (const item of masterdataLongIdContracts) {
  for (const fragment of [
    `export interface ${item.type} {\n  id: string`,
    `export const get${item.plural} = (params: ${item.type}Query) =>`,
    `records: page.records.map(${item.normalize})`,
    `export const get${item.type} = (id: string | number)`,
    `request.get<${item.type}>(\`/masterdata/${item.path}/\${id}\`).then(${item.normalize})`,
    `request.post<${item.type}>('/masterdata/${item.path}', data).then(${item.normalize})`,
    `export const update${item.type} = (id: string | number, data: ${item.type}SaveRequest)`,
    `request.put<${item.type}>(\`/masterdata/${item.path}/\${id}\`, data).then(${item.normalize})`,
    `export const delete${item.type} = (id: string | number)`,
    `request.post<${item.type}>(\`/masterdata/${item.path}/\${id}/disable\`).then(${item.normalize})`,
    `export const enable${item.type} = (id: string | number)`,
    `request.post<${item.type}>(\`/masterdata/${item.path}/\${id}/enable\`).then(${item.normalize})`,
    `const ${item.normalize} =`,
    'id: String('
  ]) {
    if (!masterdataApi.includes(fragment)) {
      errors.push(`主数据 ${item.name} API 缺少 Long ID 字符串兼容片段: ${fragment}`)
    }
  }

  for (const fragment of [
    `export interface ${item.type} {\n  id: number`,
    `export const get${item.type} = (id: number)`,
    `return request.post<number>('/masterdata/${item.path}', data)`,
    `export const update${item.type} = (id: number, data: ${item.type}SaveRequest)`,
    `export const delete${item.type} = (id: number)`
  ]) {
    if (masterdataApi.includes(fragment)) {
      errors.push(`主数据 ${item.name} API 仍保留 Long ID 数字精度或旧返回契约片段: ${fragment}`)
    }
  }
}

for (const view of [
  { name: '产品页', content: productView, detailApi: 'getProduct' },
  { name: '客户页', content: customerView, detailApi: 'getCustomer' },
  { name: '供应商页', content: supplierView, detailApi: 'getSupplier' },
  { name: '仓库页', content: warehouseView, detailApi: 'getWarehouse' }
]) {
  if (!view.content.includes('id?: string')) {
    errors.push(`${view.name}编辑表单缺少 Long ID 字符串类型片段: id?: string`)
  }
  if (!view.content.includes(`${view.detailApi},`)) {
    errors.push(`${view.name}缺少主数据详情 API 导入片段: ${view.detailApi},`)
  }
  if (!view.content.includes(`await ${view.detailApi}(row.id)`)) {
    errors.push(`${view.name}查看详情未调用后端详情接口片段: await ${view.detailApi}(row.id)`)
  }
  if (!view.content.includes('handleEnable(row)')) {
    errors.push(`${view.name}缺少停用后重新启用真实入口片段: handleEnable(row)`)
  }
  if (!view.content.includes('const handleEnable = async')) {
    errors.push(`${view.name}缺少启用处理函数片段: const handleEnable = async`)
  }
  if (view.content.includes('id?: number')) {
    errors.push(`${view.name}编辑表单仍保留 Long ID 数字精度风险片段: id?: number`)
  }
}

for (const fragment of [
  'categoryId?: number',
  'categoryId: undefined',
  'v-model.number="formData.categoryId"',
  'prop="categoryId"'
]) {
  if (masterdataApi.includes(fragment) || productView.includes(fragment)) {
    errors.push(`产品主数据仍保留后端不存在的分类 ID 片段: ${fragment}`)
  }
}

for (const fragment of [
  'deptId?: string               // 所属部门',
  'managerUserId?: string        // 仓库管理员',
  'deptId?: string | number',
  'managerUserId?: string | number',
  'deptId: warehouse.deptId != null ? String(warehouse.deptId) : undefined',
  'managerUserId: warehouse.managerUserId != null ? String(warehouse.managerUserId) : undefined'
]) {
  if (!masterdataApi.includes(fragment)) {
    errors.push(`仓库 API 缺少部门/管理员 Long ID 契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'deptId?: number',
  'managerUserId?: number'
]) {
  if (masterdataApi.includes(fragment)) {
    errors.push(`仓库 API 仍保留部门/管理员 Long ID 数字精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'getDeptTree',
  'getUsers',
  'v-model="formData.deptId"',
  'v-model="formData.managerUserId"',
  'deptId: undefined as string | undefined',
  'managerUserId: undefined as string | undefined',
  'deptId: values.deptId',
  'managerUserId: values.managerUserId'
]) {
  if (!warehouseView.includes(fragment)) {
    errors.push(`仓库页缺少后端必填部门/管理员契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'prop="manager"',
  'v-model="formData.manager"',
  'prop="contact"',
  'v-model="formData.contact"'
]) {
  if (warehouseView.includes(fragment)) {
    errors.push(`仓库页仍保留后端不接收的负责人/联系方式保存片段: ${fragment}`)
  }
}

for (const fragment of [
  'documentId: number',
  'partnerId?: number'
]) {
  if (businessTraceApi.includes(fragment)) {
    errors.push(`单据追踪 API 仍保留 Long ID 精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  "router.push(normalizeTraceRoute(target))",
  'const normalizeTraceRoute ='
]) {
  if (!businessTraceView.includes(fragment)) {
    errors.push(`单据追踪页缺少跳转路由归一化片段: ${fragment}`)
  }
}

const traceRouteTargetViews = [
  { name: '销售订单页', content: salesOrderView, field: 'queryParams.keyword' },
  { name: '销售发货页', content: salesDeliveryView, field: 'queryParams.deliveryNo' },
  { name: '采购订单页', content: purchaseOrderView, field: 'queryForm.orderNo' },
  { name: '采购收货页', content: purchaseReceiptView, field: 'queryForm.receiptNo' },
  { name: '应收账款页', content: financeAccountView, field: 'receivableQuery.receivableNo' },
  { name: '应付账款页', content: financeAccountView, field: 'payableQuery.payableNo' },
  { name: '操作日志页', content: systemLogView, field: 'queryForm.bizNo' }
]

for (const view of traceRouteTargetViews) {
  for (const fragment of [
    'useRoute',
    'const readQueryString =',
    `${view.field} = readQueryString('keyword')`
  ]) {
    if (!view.content.includes(fragment)) {
      errors.push(`${view.name}缺少单据追踪跳转查询参数片段: ${fragment}`)
    }
  }
}

for (const fragment of [
  'ruleId: string',
  'warehouseId: string',
  'productId: string',
  'export interface OperationsDashboardFailedOperation {\n  id: string',
  'return request.get<OperationsDashboard>(\'/dashboard/operations\').then(normalizeOperationsDashboard)',
  'const normalizeOperationsDashboard =',
  'const normalizeOperationsDashboardLowStock =',
  'const normalizeOperationsDashboardFailedOperation =',
  'lowStock: (dashboard.lowStock || []).map(normalizeOperationsDashboardLowStock)',
  'failedOperations: (dashboard.failedOperations || []).map(normalizeOperationsDashboardFailedOperation)',
  'ruleId: String(item.ruleId)',
  'id: String(item.id)'
]) {
  if (!dashboardApi.includes(fragment)) {
    errors.push(`运营看板 API 缺少 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'ruleId: number',
  'warehouseId: number',
  'productId: number',
  'export interface OperationsDashboardFailedOperation {\n  id: number',
  'return request.get<OperationsDashboard>(\'/dashboard/operations\')\n}'
]) {
  if (dashboardApi.includes(fragment)) {
    errors.push(`运营看板 API 仍保留 Long ID 数字精度风险或未归一化片段: ${fragment}`)
  }
}

for (const fragment of [
  'customerId?: string | number'
]) {
  if (!salesApi.includes(fragment)) {
    errors.push(`销售订单 API 缺少客户 Long ID 字符串兼容查询片段: ${fragment}`)
  }
}

for (const fragment of [
  'customerId?: number | string'
]) {
  if (salesApi.includes(fragment)) {
    errors.push(`销售订单 API 仍保留客户 Long ID 非统一类型片段: ${fragment}`)
  }
}

for (const fragment of [
  'export const getFundAccount = (id: string | number)',
  'request.get<FundAccount>(`/finance/fund/accounts/${id}`).then(normalizeFundAccount)',
  'export const getBankStatement = (id: string | number)',
  'request.get<BankStatement>(`/finance/fund/statements/${id}`).then(normalizeBankStatement)',
  'export const matchBankStatement =',
  '/finance/fund/statements/${id}/match',
  'export const unmatchBankStatement =',
  '/finance/fund/statements/${id}/unmatch'
]) {
  if (!fundApi.includes(fragment)) {
    errors.push(`资金对账 API 缺少匹配/取消匹配契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'getFundAccount,',
  'getBankStatement,',
  'accountDetailVisible',
  'statementDetailVisible',
  'selectedAccount',
  'selectedStatementDetail',
  'const handleViewAccount = async (row: FundAccount) =>',
  'const handleViewStatement = async (row: BankStatement) =>',
  'getAccount: getFundAccount',
  'getStatement: getBankStatement',
  'await options.getAccount(row.id)',
  'await options.getStatement(row.id)',
  'financeReportPages.funds.accountDetail',
  'financeReportPages.funds.statementDetail',
  'handleViewAccount(row)',
  'handleViewStatement(row)',
  'openMatchDialog(row)',
  'handleUnmatch(row)',
  'const openMatchDialog = (row: BankStatement) =>',
  'const submitMatch = async () =>',
  'const handleUnmatch = async (row: BankStatement) =>',
  "matchForm.bizType = row.direction === 'IN' ? 'RECEIPT' : 'PAYMENT'",
  'matchStatement: matchBankStatement',
  'unmatchStatement: unmatchBankStatement',
  'await options.matchStatement(selectedStatement.value.id, { ...matchForm })',
  'await options.unmatchStatement(row.id, reason)'
]) {
  if (!fundView.includes(fragment)) {
    errors.push(`资金对账页缺少匹配/取消匹配契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'getFundAccounts({ pageNo: 1, pageSize: 1000',
  'pageSize: 1000, status: \'ENABLED\''
]) {
  if (fundView.includes(fragment)) {
    errors.push(`资金对账页仍保留不稳定账户选项分页片段: ${fragment}`)
  }
}

for (const fragment of [
  'subjectId: string | number',
  'paymentSubjectId: string | number',
  'voucherNo?: string',
  'voucherBalanced?: boolean',
  'amountMatched?: boolean',
  'export interface ExpenseReconciliation {',
  'entries: VoucherEntry[]',
  'reversalEntries: VoucherEntry[]',
  'voucherMissing: boolean',
  'voucherLinkedToExpense: boolean',
  'request.post<Expense>(\'/finance/expenses\', toExpensePayload(data)).then(normalizeExpense)',
  'request.put<Expense>(`/finance/expenses/${id}`, toExpensePayload(data)).then(normalizeExpense)',
  'request.get<ExpenseReconciliation>(`/finance/expenses/${id}/reconciliation`).then(normalizeExpenseReconciliation)',
  'request.post<Expense>(`/finance/expenses/${id}/post`).then(normalizeExpense)'
]) {
  if (!financeApi.includes(fragment)) {
    errors.push(`费用 API 缺少后端契约兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'return request.post<number>(\'/finance/expenses\', data)',
  'expenseType: string',
  'items: ExpenseItem[]',
  'departmentId?: number'
]) {
  if (financeApi.includes(fragment)) {
    errors.push(`费用 API 仍保留旧提交口径片段: ${fragment}`)
  }
}

for (const fragment of [
  'getAccountSubjectTree',
  'financeReportPages.expenses.expenseSubject',
  'prop="subjectId"',
  'v-model="formData.subjectId"',
  'financeReportPages.expenses.paymentSubject',
  'prop="paymentSubjectId"',
  'v-model="formData.paymentSubjectId"',
  'financeReportPages.expenses.expenseAmount',
  'prop="amount"',
  'v-model="formData.amount"',
  'const flattenSubjects =',
  'const subjectLabel =',
  'handleReconciliation(row)',
  'const handleReconciliation = async (row: Expense) =>',
  'reconciliationDialogVisible',
  'reconciliationData',
  'financeReportPages.expenses.originalEntries',
  'financeReportPages.expenses.reversalEntries',
  "row.status === 'POSTED'",
  'viewData.reversalVoucherNo'
]) {
  if (!expenseView.includes(fragment)) {
    errors.push(`费用页缺少后端契约兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'label="费用类型"',
  'v-model="formData.expenseType"',
  'label="申请部门"',
  'label="费用明细"',
  'handleAddItem',
  'ExpenseItem'
]) {
  if (expenseView.includes(fragment)) {
    errors.push(`费用页仍保留旧表单口径片段: ${fragment}`)
  }
}

for (const fragment of [
  'export const getReceivable = (id: string | number)',
  'request.get<Receivable>(`/finance/receivables/${id}`).then(normalizeReceivable)',
  'export const getPayable = (id: string | number)',
  'request.get<Payable>(`/finance/payables/${id}`).then(normalizePayable)',
  'export const getReceipt = (id: string | number)',
  'request.get<Receipt>(`/finance/receipts/${id}`).then(normalizeReceipt)',
  'export const getPayment = (id: string | number)',
  'request.get<Payment>(`/finance/payments/${id}`).then(normalizePayment)',
  'export const getVouchers =',
  'export const getVoucher =',
  'export const getVoucherEntries =',
  'const normalizeVoucher =',
  'id: string',
  'sourceId?: string',
  'expenseId: string',
  'voucherId?: string',
  'subjectId: string',
  'export const getVoucher = (id: string | number)',
  'export const getVoucherEntries = (id: string | number)',
  'return request.get<VoucherEntry[]>(`/finance/vouchers/${id}/entries`).then((entries) => entries.map(normalizeVoucherEntry))',
  'sourceId: voucher.sourceId != null ? String(voucher.sourceId) : undefined',
  'expenseId: String(voucher.expenseSource.expenseId)'
]) {
  if (!financeApi.includes(fragment)) {
    errors.push(`凭证 API 缺少只读查询契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface VoucherCreateRequest',
  'export const createVoucher =',
  'export const approveVoucher =',
  'export const postVoucher =',
  'export const cancelVoucher =',
  '/finance/vouchers/${id}/approve',
  '/finance/vouchers/${id}/post',
  '/finance/vouchers/${id}/cancel'
]) {
  if (financeApi.includes(fragment)) {
    errors.push(`凭证 API 仍暴露后端不存在的写操作片段: ${fragment}`)
  }
}

for (const fragment of [
  'getReceivable,',
  'getPayable,',
  'receivableDetailVisible',
  'payableDetailVisible',
  'selectedReceivable',
  'selectedPayable',
  'const handleViewReceivable = async (row: Receivable) =>',
  'const handleViewPayable = async (row: Payable) =>',
  'await getReceivable(row.id)',
  'await getPayable(row.id)',
  ":title=\"t('financeAccount.dialog.receivable')\"",
  ":title=\"t('financeAccount.dialog.payable')\""
]) {
  if (!financeAccountView.includes(fragment)) {
    errors.push(`应收/应付账款页缺少真实详情接口入口片段: ${fragment}`)
  }
}

for (const [content, fragment] of [
  [financeReceivableView, '<FinanceAccountsPage default-tab="receivables" />'],
  [financePayableView, '<FinanceAccountsPage default-tab="payables" />']
]) {
  if (!content.includes(fragment)) {
    errors.push(`应收/应付账款薄路由页缺少共享组件默认页签片段: ${fragment}`)
  }
}

for (const fragment of [
  'getCustomers({ pageNo: 1000',
  'getSuppliers({ pageNo: 1000'
]) {
  if (financeAccountView.includes(fragment)) {
    errors.push(`应收/应付账款页仍保留错误选项分页片段: ${fragment}`)
  }
}

for (const fragment of [
  'getReceipt,',
  'getPayment,',
  'selectedReceipt',
  'selectedPayment',
  'receiptAllocations',
  'paymentAllocations',
  'const viewReceipt = async (row: Receipt) =>',
  'const viewPayment = async (row: Payment) =>',
  'buildReceiptItems',
  'buildPaymentItems',
  'getReceipt(row.id)',
  'getPayment(row.id)',
  'prop="receivableNo"',
  'prop="payableNo"',
  'prop="allocatedAmount"'
]) {
  if (!financePaymentView.includes(fragment)) {
    errors.push(`收付款页缺少真实详情接口或核销明细片段: ${fragment}`)
  }
}

for (const fragment of [
  "financeReportPages.vouchers.title",
  'getVoucher,',
  'getVoucherEntries',
  'const handleView = async',
  'const [voucher, entries] = await Promise.all([',
  'getVoucher(row.id)',
  'getVoucherEntries(row.id)',
  'currentVoucher.value = voucher',
  'detailEntries.value = entries',
  "financeReportPages.vouchers.sourceValue.expense"
]) {
  if (!voucherView.includes(fragment)) {
    errors.push(`凭证页缺少只读查询契约片段: ${fragment}`)
  }
}

for (const fragment of [
  '新增凭证',
  'createVoucher',
  'approveVoucher',
  'postVoucher',
  'cancelVoucher',
  'handleCreate',
  'handleApprove',
  'handlePost',
  'handleCancel',
  'formData.entries'
]) {
  if (voucherView.includes(fragment)) {
    errors.push(`凭证页仍暴露后端不存在的写操作片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface Voucher {\n  id: number',
  'sourceId?: number',
  'expenseId: number',
  'export interface VoucherEntry {\n  id?: number',
  'voucherId?: number',
  'subjectId: number',
  'export const getVoucher = (id: number)',
  'export const getVoucherEntries = (id: number)'
]) {
  if (financeApi.includes(fragment)) {
    errors.push(`凭证 API 仍保留 Long ID 数字精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'subjectCode: string',
  'subjectName: string',
  'subjectType: string',
  'balanceDirection: string',
  'export interface AccountSubject {\n  id: string',
  'parentId?: string',
  'parentId?: string | number',
  'export const getAccountSubjects = (params: AccountSubjectQuery)',
  'export const getAccountSubject = (id: string | number)',
  'export const updateAccountSubject = (id: string | number, data: AccountSubjectSaveRequest)',
  'export const enableAccountSubject = (id: string | number)',
  'export const disableAccountSubject = (id: string | number)',
  'id: String(subject.id)',
  'parentId: subject.parentId != null ? String(subject.parentId) : undefined',
  'const toAccountSubjectPayload =',
  'request.post<AccountSubject>(\'/finance/account-subjects\', toAccountSubjectPayload(data)).then(normalizeAccountSubject)',
  'request.put<AccountSubject>(`/finance/account-subjects/${id}`, toAccountSubjectPayload(data)).then(normalizeAccountSubject)',
  'request.post<AccountSubject>(`/finance/account-subjects/${id}/enable`).then(normalizeAccountSubject)',
  'request.post<AccountSubject>(`/finance/account-subjects/${id}/disable`).then(normalizeAccountSubject)'
]) {
  if (!financeApi.includes(fragment)) {
    errors.push(`会计科目 API 缺少后端契约兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface AccountSubject {\n  id: number',
  'parentId?: number',
  'parentId?: number',
  'export const getAccountSubject = (id: number)',
  'export const updateAccountSubject = (id: number, data: AccountSubjectSaveRequest)',
  'export const deleteAccountSubject = (id: string | number)',
  'export const deleteAccountSubject = (id: number)',
  'export const enableAccountSubject = (id: number)',
  'export const disableAccountSubject = (id: number)',
  'return request.post<number>(\'/finance/account-subjects\', data)',
  'code: string\n  name: string\n  category: string',
  'return request.put(`/finance/account-subjects/${id}`, data)',
  'return request.post(`/finance/account-subjects/${id}/enable`)',
  'return request.post(`/finance/account-subjects/${id}/disable`)'
]) {
  if (financeApi.includes(fragment)) {
    errors.push(`会计科目 API 仍保留旧字段或未归一化调用片段: ${fragment}`)
  }
}

for (const fragment of [
  'getAccountSubjects,',
  'const hasSubjectQuery = () =>',
  'await getAccountSubjects({',
  'pageNo: 1,',
  'pageSize: 200,',
  `:label="$t('financeReportPages.subjects.code')"`,
  'v-model="formData.subjectCode"',
  `:label="$t('financeReportPages.subjects.name')"`,
  'v-model="formData.subjectName"',
  `:label="$t('financeReportPages.subjects.category')"`,
  'v-model="formData.subjectType"',
  `:label="$t('financeReportPages.subjects.balanceDirection')"`,
  'v-model="formData.balanceDirection"',
  'id: undefined as string | undefined',
  'parentId: undefined as string | undefined'
]) {
  if (!subjectView.includes(fragment)) {
    errors.push(`会计科目页缺少后端契约兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'id: undefined as number | undefined',
  'parentId: undefined as number | undefined',
  'v-model="formData.code"',
  'v-model="formData.name"',
  'v-model="formData.category"',
  'formData.code',
  'formData.name',
  'formData.category'
]) {
  if (subjectView.includes(fragment)) {
    errors.push(`会计科目页仍保留旧保存字段片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface LedgerEntry {\n  id: string',
  'voucherId: string',
  'bizDate: string',
  'lineNo: number',
  'export interface LedgerSummary',
  'debitAmount: number',
  'creditAmount: number',
  'export const getLedgerEntries = (params: LedgerQuery) =>',
  "request.get<LedgerEntry[]>('/finance/ledger/detail'",
  'entries.map(normalizeLedgerEntry)',
  'export const getLedgerSummary = (params: LedgerQuery) =>',
  "request.get<LedgerSummary[]>('/finance/ledger/general'",
  'summaries.map(normalizeLedgerSummary)',
  'const normalizeLedgerEntry =',
  'const normalizeLedgerSummary =',
  'id: String(entry.id)',
  'voucherId: String(entry.voucherId)'
]) {
  if (!financeApi.includes(fragment)) {
    errors.push(`总账 API 缺少后端真实契约兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface LedgerEntry {\n  id: number',
  'voucherDate: string',
  'createdAt: string',
  'return request.get<PageResponse<LedgerEntry>>',
  'return request.get<LedgerSummary>'
]) {
  if (financeApi.includes(fragment)) {
    errors.push(`总账 API 仍保留旧字段、假分页或 Long ID 数字精度风险片段: ${fragment}`)
  }
}

if (interfaceIncludes(financeApi, 'LedgerSummary', 'balance: number')) {
  errors.push('总账 API 仍保留旧字段、假分页或 Long ID 数字精度风险片段: balance: number')
}

for (const fragment of [
  'prop="bizDate"',
  'prop="lineNo"',
  'prop="voucherId"',
  'prop="debitAmount"',
  'prop="creditAmount"',
  'generalLedger.value = res || []',
  'const entries = await getLedgerEntries(buildLedgerQueryParams())',
  'detailLedger.value = entries.slice(start, start + pagination.size)',
  'pagination.total = entries.length'
]) {
  if (!ledgerView.includes(fragment)) {
    errors.push(`总账页缺少后端真实契约展示片段: ${fragment}`)
  }
}

for (const fragment of [
  'prop="voucherDate"',
  'prop="voucherNo"',
  'prop="balance"',
  'res.records',
  'res.total',
  'openingBalance',
  'closingBalance',
  'totalDebit',
  'totalCredit'
]) {
  if (ledgerView.includes(fragment)) {
    errors.push(`总账页仍保留旧字段或假分页片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface AccountPeriod {\n  id: string',
  'lockedBy?: string',
  'closedBy?: string',
  'reopenedBy?: string',
  'periodId: string',
  'export const lockAccountPeriod = (id: string | number)',
  'export const checkAccountPeriodClose = (id: string | number)',
  'export const closeAccountPeriod = (id: string | number)',
  'export const reopenAccountPeriod = (id: string | number)',
  'export const getInventoryFinanceReconciliation = (id: string | number)',
  'id: String(period.id)',
  'periodId: String(result.periodId)',
  'const normalizeAccountPeriod =',
  'const normalizeAccountPeriodCloseCheck =',
  'const normalizeInventoryFinanceReconciliation ='
]) {
  if (!financeApi.includes(fragment)) {
    errors.push(`会计期间 API 缺少 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface AccountPeriod {\n  id: number',
  'lockedBy?: number',
  'closedBy?: number',
  'reopenedBy?: number',
  'periodId: number',
  'export const lockAccountPeriod = (id: number)',
  'export const checkAccountPeriodClose = (id: number)',
  'export const closeAccountPeriod = (id: number)',
  'export const reopenAccountPeriod = (id: number)',
  'export const getInventoryFinanceReconciliation = (id: number)',
  'id: number,\n  params?: InventoryFinanceDifferenceQuery'
]) {
  if (financeApi.includes(fragment)) {
    errors.push(`会计期间 API 仍保留 Long ID 数字精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'financeReportPages.periods.unlockTime',
  'financeReportPages.periods.unlock',
  'financeReportPages.periods.message.unlockConfirm',
  'financeReportPages.periods.message.unlocked',
  'financeReportPages.periods.message.unlockFailed'
]) {
  if (!financePeriodView.includes(fragment)) {
    errors.push(`会计期间页缺少锁定期间解锁语义片段: ${fragment}`)
  }
}

for (const fragment of [
  '反开时间',
  '>反开<',
  '确定反开',
  '反开期间',
  '会计期间已反开',
  '反开会计期间失败'
]) {
  if (financePeriodView.includes(fragment)) {
    errors.push(`会计期间页仍把锁定期间解锁误称为反开片段: ${fragment}`)
  }
}

for (const fragment of [
  'id: string',
  'instanceId?: string',
  'businessId: string',
  'approverUserId?: string',
  'taskId: string | number',
  'const normalizeWorkflowTask =',
  'const normalizeWorkflowRecord =',
  'records: page.records.map(normalizeWorkflowTask)',
  'records: page.records.map(normalizeWorkflowRecord)',
  'request.get<WorkflowTask>(`/workflow/tasks/${id}`).then(normalizeWorkflowTask)',
  'request.post(`/workflow/tasks/${data.taskId}/approve`, { comment: data.comment })',
  'request.post(`/workflow/tasks/${data.taskId}/reject`, { comment: data.reason })',
  'export interface WorkflowWithdrawRequest',
  'businessType: string',
  'businessId: string | number',
  'export const withdrawWorkflow = (data: WorkflowWithdrawRequest)',
  'request.post(`/workflow/${data.businessType}/${data.businessId}/withdraw`, { comment: data.comment })'
]) {
  if (!workflowApi.includes(fragment)) {
    errors.push(`工作流 API 缺少待办/记录真实契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface OrderReportRow {\n  id: string',
  'partnerId: string',
  'export interface InventoryBalanceReportRow {\n  id: string',
  'warehouseId: string',
  'productId: string',
  'export interface InventoryTransactionReportRow {\n  id: string',
  'bizLineId?: string',
  'export interface FinanceSettlementReportRow {\n  id: string',
  'const normalizeOrderReportRow =',
  'const normalizeInventoryBalanceReportRow =',
  'const normalizeInventoryTransactionReportRow =',
  'const normalizeFinanceSettlementReportRow =',
  'id: String(row.id)',
  'partnerId: String(row.partnerId)',
  'warehouseId: String(row.warehouseId)',
  'productId: String(row.productId)',
  'bizLineId: row.bizLineId != null ? String(row.bizLineId) : undefined',
  "request.get<PageResponse<OrderReportRow>>('/reports/purchase-orders', { params }).then((page) => normalizeReportPage(page, normalizeOrderReportRow))",
  "request.get<PageResponse<InventoryBalanceReportRow>>('/reports/inventory-balances', { params }).then((page) => normalizeReportPage(page, normalizeInventoryBalanceReportRow))",
  "request.get<PageResponse<InventoryTransactionReportRow>>('/reports/inventory-transactions', { params }).then((page) => normalizeReportPage(page, normalizeInventoryTransactionReportRow))",
  "request.get<PageResponse<FinanceSettlementReportRow>>('/reports/finance-settlements', { params }).then((page) => normalizeReportPage(page, normalizeFinanceSettlementReportRow))"
]) {
  if (!workflowApi.includes(fragment)) {
    errors.push(`报表 API 缺少 Long ID 字符串归一化片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface OrderReportRow {\n  id: number',
  'partnerId: number',
  'export interface InventoryBalanceReportRow {\n  id: number',
  'warehouseId: number',
  'productId: number',
  'export interface InventoryTransactionReportRow {\n  id: number',
  'bizLineId?: number',
  'export interface FinanceSettlementReportRow {\n  id: number'
]) {
  if (workflowApi.includes(fragment)) {
    errors.push(`报表 API 仍保留 Long ID 数字精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface User {\n  id: string',
  'export const createUser = (data: UserSaveRequest) =>',
  'request.post<User>(\'/system/users\', data).then(normalizeUser)',
  'export const updateUser = (id: string | number, data: UserSaveRequest) =>',
  'request.put<User>(`/system/users/${id}`, data).then(normalizeUser)',
  'export const deleteUser = (id: string | number) =>',
  'request.post<User>(`/system/users/${id}/disable`).then(normalizeUser)',
  'export const enableUser = (id: string | number) =>',
  'request.post<User>(`/system/users/${id}/enable`).then(normalizeUser)',
  'export const resetUserPassword = (id: string | number, newPassword: string) =>',
  'request.post<User>(`/system/users/${id}/reset-password`, { newPassword }).then(normalizeUser)',
  'export interface UserRoleAssignment {',
  'userId: string',
  'roleIds: string[]',
  'export const getAssignedUserRoles = (id: string | number)',
  'request.get<UserRoleAssignment>(`/system/users/${id}/roles`).then(normalizeUserRoleAssignment)',
  'export const assignUserRoles = (id: string | number, roleIds: Array<string | number>)',
  'request.put<UserRoleAssignment>(`/system/users/${id}/roles`, { roleIds }).then(normalizeUserRoleAssignment)',
  'id: String(user.id)',
  'deptId: user.deptId != null ? String(user.deptId) : undefined',
  'postId: user.postId != null ? String(user.postId) : undefined'
]) {
  if (!systemApi.includes(fragment)) {
    errors.push(`系统用户 API 缺少 Long ID 或返回实体归一化片段: ${fragment}`)
  }
}

for (const fragment of [
  'return request.post<number>(\'/system/users\', data)',
  'return request.put(`/system/users/${id}`, data)',
  'return request.post(`/system/users/${id}/disable`)',
  'return request.post(`/system/users/${id}/reset-password`, { newPassword })',
  'export interface User {\n  id: number'
]) {
  if (systemApi.includes(fragment)) {
    errors.push(`系统用户 API 仍保留旧返回口径或 Long ID 精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'getAllRoles,',
  'getAssignedUserRoles,',
  'assignUserRoles,',
  'roleDialogVisible',
  'selectedRoleIds',
  'handleAssignRoles(row)',
  'const handleAssignRoles = async (row: User) =>',
  'const submitRoleAssignment = async () =>',
  'await options.assignUserRoles(currentUserId.value, selectedRoleIds.value)'
]) {
  if (!systemUserView.includes(fragment)) {
    errors.push(`系统用户页缺少真实用户角色分配片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface Role {\n  id: string',
  'const normalizeRole =',
  'id: String(role.id)',
  'code: role.code ?? role.roleCode ?? \'\'',
  'name: role.name ?? role.roleName ?? \'\'',
  'const toRoleCreatePayload =',
  'roleCode: data.code',
  'roleName: data.name',
  'request.post<Role>(\'/system/roles\', toRoleCreatePayload(data)).then(normalizeRole)',
  'request.put<Role>(`/system/roles/${id}`, toRoleUpdatePayload(data)).then(normalizeRole)',
  'request.post<Role>(`/system/roles/${id}/disable`).then(normalizeRole)',
  'export const enableRole = (id: string | number) =>',
  'request.post<Role>(`/system/roles/${id}/enable`).then(normalizeRole)',
  'export const assignRoleMenus = (id: string | number, menuIds: Array<string | number>)',
  'request.put<RoleMenuAssignment>(`/system/roles/${id}/menus`, { menuIds })'
]) {
  if (!systemApi.includes(fragment)) {
    errors.push(`系统角色 API 缺少后端真实契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface Role {\n  id: number',
  'export const getRole = (id: number)',
  'return request.post<number>(\'/system/roles\', data)',
  'return request.put(`/system/roles/${id}`, data)',
  'return request.post(`/system/roles/${id}/disable`)'
]) {
  if (systemApi.includes(fragment)) {
    errors.push(`系统角色 API 仍保留旧返回口径或 Long ID 精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'getMenuTree,',
  'getAssignedRoleMenus,',
  'assignRoleMenus,',
  'node-key="id"',
  'const permissionTree = ref<Menu[]>([])',
  'const [menus, assignment] = await Promise.all',
  'selectedPermissions.value = assignment.menuIds',
  'await assignRoleMenus(currentRoleId.value, menuIds)'
]) {
  if (!roleView.includes(fragment)) {
    errors.push(`系统角色页缺少真实菜单授权片段: ${fragment}`)
  }
}

for (const fragment of [
  'system:user:view',
  'node-key="code"',
  'const permissionTree = ref<PermissionNode[]>',
  'permissions: allPermissions'
]) {
  if (roleView.includes(fragment)) {
    errors.push(`系统角色页仍保留硬编码权限树或假保存片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface Menu {\n  id: string',
  'parentId?: string',
  'const normalizeMenu =',
  'id: String(menu.id)',
  'parentId: menu.parentId != null ? String(menu.parentId) : undefined',
  'name: menu.name ?? menu.menuName ?? \'\'',
  'type: (menu.type ?? menu.menuType ?? \'MENU\') as Menu[\'type\']',
  'orderNum: menu.orderNum ?? menu.sortNo ?? 0',
  'children: menu.children?.map(normalizeMenu)',
  'const toMenuCreatePayload =',
  'menuType: data.type',
  'menuCode: data.code || data.name',
  'menuName: data.name',
  'sortNo: data.orderNum',
  'request.post<Menu>(\'/system/menus\', toMenuCreatePayload(data)).then(normalizeMenu)',
  'request.put<Menu>(`/system/menus/${id}`, toMenuUpdatePayload(data)).then(normalizeMenu)',
  'request.post<Menu>(`/system/menus/${id}/disable`).then(normalizeMenu)',
  'export const enableMenu = (id: string | number) =>',
  'request.post<Menu>(`/system/menus/${id}/enable`).then(normalizeMenu)'
]) {
  if (!systemApi.includes(fragment)) {
    errors.push(`系统菜单 API 缺少后端真实契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface Menu {\n  id: number',
  'parentId?: number',
  'export const getMenu = (id: number)',
  'export const getUserMenus = () =>',
  'return request.post<number>(\'/system/menus\', data)',
  'return request.put(`/system/menus/${id}`, data)',
  'return request.post(`/system/menus/${id}/disable`)'
]) {
  if (systemApi.includes(fragment)) {
    errors.push(`系统菜单 API 仍保留旧返回口径或 Long ID 精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface Dept {\n  id: string',
  'parentId?: string',
  'leaderUserId?: string',
  'const normalizeDept =',
  'id: String(dept.id)',
  'parentId: dept.parentId != null ? String(dept.parentId) : undefined',
  'name: dept.name ?? dept.deptName ?? \'\'',
  'code: dept.code ?? dept.deptCode',
  'manager: dept.manager ?? (dept.leaderUserId != null ? String(dept.leaderUserId) : undefined)',
  'orderNum: dept.orderNum ?? dept.sortNo ?? 0',
  'children: dept.children?.map(normalizeDept)',
  'const toDeptCreatePayload =',
  'deptCode: data.code',
  'deptName: data.name',
  'sortNo: data.orderNum',
  'request.post<Dept>(\'/system/depts\', toDeptCreatePayload(data)).then(normalizeDept)',
  'request.put<Dept>(`/system/depts/${id}`, toDeptUpdatePayload(data)).then(normalizeDept)',
  'request.post<Dept>(`/system/depts/${id}/disable`).then(normalizeDept)',
  'export const enableDept = (id: string | number) =>',
  'request.post<Dept>(`/system/depts/${id}/enable`).then(normalizeDept)'
]) {
  if (!systemApi.includes(fragment)) {
    errors.push(`系统部门 API 缺少后端真实契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface Dept {\n  id: number',
  'parentId?: number',
  'export const getDept = (id: number)',
  'return request.post<number>(\'/system/depts\', data)',
  'return request.put(`/system/depts/${id}`, data)',
  'return request.post(`/system/depts/${id}/disable`)'
]) {
  if (systemApi.includes(fragment)) {
    errors.push(`系统部门 API 仍保留旧返回口径或 Long ID 精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface Post {\n  id: string',
  'deptId?: string',
  'const normalizePost =',
  'id: String(post.id)',
  'deptId: post.deptId != null ? String(post.deptId) : undefined',
  'code: post.code ?? post.postCode ?? \'\'',
  'name: post.name ?? post.postName ?? \'\'',
  'const toPostCreatePayload =',
  'deptId: data.deptId',
  'postCode: data.code',
  'postName: data.name',
  'request.post<Post>(\'/system/posts\', toPostCreatePayload(data)).then(normalizePost)',
  'request.put<Post>(`/system/posts/${id}`, toPostUpdatePayload(data)).then(normalizePost)',
  'request.post<Post>(`/system/posts/${id}/disable`).then(normalizePost)',
  'export const enablePost = (id: string | number) =>',
  'request.post<Post>(`/system/posts/${id}/enable`).then(normalizePost)'
]) {
  if (!systemApi.includes(fragment)) {
    errors.push(`系统岗位 API 缺少后端真实契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface Post {\n  id: number',
  'export const getPost = (id: number)',
  'return request.post<number>(\'/system/posts\', data)',
  'return request.put(`/system/posts/${id}`, data)',
  'return request.post(`/system/posts/${id}/disable`)'
]) {
  if (systemApi.includes(fragment)) {
    errors.push(`系统岗位 API 仍保留旧返回口径或 Long ID 精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'getDeptTree,',
  'deptOptions',
  'systemPost.dept',
  'prop="deptId"',
  'v-model="formData.deptId"',
  `:props="{ label: 'name', value: 'id' }"`,
  'deptId: undefined as string | undefined'
]) {
  if (!postView.includes(fragment)) {
    errors.push(`系统岗位页缺少后端 deptId 必填契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'id: undefined as number | undefined'
]) {
  if (postView.includes(fragment)) {
    errors.push(`系统岗位页仍保留 Long ID 数字表单片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface DictType {\n  id: string',
  'export interface DictItem {\n  id: string',
  'typeId?: string',
  'const normalizeDictType =',
  'code: dict.code ?? dict.dictType ?? \'\'',
  'name: dict.name ?? dict.dictName ?? \'\'',
  'const normalizeDictItem =',
  'typeId: item.typeId != null ? String(item.typeId) : undefined',
  'typeCode: item.typeCode ?? item.dictType ?? \'\'',
  'label: item.label ?? item.itemLabel ?? \'\'',
  'value: item.value ?? item.itemValue ?? \'\'',
  'orderNum: item.orderNum ?? item.sortNo ?? 0',
  'request.post<DictType>(\'/system/dict-types\', toDictTypeCreatePayload(data)).then(normalizeDictType)',
  'request.put<DictType>(`/system/dict-types/${id}`, toDictTypeUpdatePayload(data)).then(normalizeDictType)',
  'request.post<DictType>(`/system/dict-types/${id}/disable`).then(normalizeDictType)',
  'export const enableDictType = (id: string | number) =>',
  'request.post<DictType>(`/system/dict-types/${id}/enable`).then(normalizeDictType)',
  'request.post<DictItem>(\'/system/dict-items\', toDictItemCreatePayload(data)).then(normalizeDictItem)',
  'request.put<DictItem>(`/system/dict-items/${id}`, toDictItemUpdatePayload(data)).then(normalizeDictItem)',
  'request.post<DictItem>(`/system/dict-items/${id}/disable`).then(normalizeDictItem)',
  'export const enableDictItem = (id: string | number) =>',
  'request.post<DictItem>(`/system/dict-items/${id}/enable`).then(normalizeDictItem)'
]) {
  if (!systemApi.includes(fragment)) {
    errors.push(`系统字典 API 缺少后端真实契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface DictType {\n  id: number',
  'export interface DictItem {\n  id: number',
  'return request.post<number>(\'/system/dict-types\', data)',
  'return request.put(`/system/dict-types/${id}`, data)',
  'return request.post(`/system/dict-types/${id}/disable`)',
  'return request.post<number>(\'/system/dict-items\', data)',
  'return request.put(`/system/dict-items/${id}`, data)',
  'return request.post(`/system/dict-items/${id}/disable`)'
]) {
  if (systemApi.includes(fragment)) {
    errors.push(`系统字典 API 仍保留旧返回口径或 Long ID 精度风险片段: ${fragment}`)
  }
}

const systemEnableViewContracts = [
  {
    name: '系统用户页',
    content: systemUserView,
    fragments: ['enableUser,', 'const handleEnable = async (row: User) =>', 'await options.enableUser(row.id)', 'systemUsers.enable']
  },
  {
    name: '系统角色页',
    content: roleView,
    fragments: ['enableRole,', 'const handleEnable = async (row: Role) =>', 'await enableRole(row.id)', 'systemRoles.disable', 'systemRoles.enable']
  },
  {
    name: '系统菜单页',
    content: menuView,
    fragments: ['enableMenu,', 'const handleEnable = async (row: Menu) =>', 'await enableMenu(row.id)', 'systemMenu.disable', 'systemMenu.enable']
  },
  {
    name: '系统部门页',
    content: deptView,
    fragments: ['enableDept,', 'const handleEnable = async (row: Dept) =>', 'await enableDept(row.id)', 'systemDept.disable', 'systemDept.enable']
  },
  {
    name: '系统岗位页',
    content: postView,
    fragments: ['enablePost,', 'const handleEnable = async (row: Post) =>', 'await enablePost(row.id)', 'systemPost.disable', 'systemPost.enable']
  },
  {
    name: '系统字典页',
    content: dictView,
    fragments: [
      'enableDictType,',
      'enableDictItem,',
      'const handleEnableType = async (row: DictType) =>',
      'const handleEnableItem = async (row: DictItem) =>',
      'await enableDictType(row.id)',
      'await enableDictItem(row.id)',
      'systemDicts.disable',
      'systemDicts.enable'
    ]
  },
  {
    name: '系统配置页',
    content: configView,
    fragments: [
      'enableSystemConfig,',
      'disableSystemConfig,',
      'const handleToggleConfigStatus = async (row: SystemConfig) =>',
      'await enableSystemConfig(row.id)',
      'await disableSystemConfig(row.id)',
      "row.status === 'ACTIVE' ? t('systemConfigs.disable') : t('systemConfigs.enable')"
    ]
  }
]

for (const view of systemEnableViewContracts) {
  for (const fragment of view.fragments) {
    if (!view.content.includes(fragment)) {
      errors.push(`${view.name}缺少停用后重新启用真实入口片段: ${fragment}`)
    }
  }
}

for (const fragment of [
  'export interface OperationLog {\n  id: string',
  'userId?: string',
  'operatorId: string',
  'export const getOperationLog = (id: string | number)',
  'id: String(log.id)',
  'userId: log.userId != null ? String(log.userId) : undefined',
  'operatorId: String(log.operatorId ?? log.userId ?? \'\')'
]) {
  if (!systemApi.includes(fragment)) {
    errors.push(`系统操作日志 API 缺少 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface LoginLog {\n  id: string',
  'userId?: string',
  'export interface LoginLogQuery extends PageQuery',
  'loginTimeFrom?: string',
  'loginTimeTo?: string',
  'export const getLoginLogs = (params: LoginLogQuery)',
  "request.get<PageResponse<LoginLog>>('/system/login-logs'",
  'const normalizeLoginLog =',
  'id: String(log.id)',
  'userId: log.userId != null ? String(log.userId) : undefined',
  'export interface AuditLog {\n  id: string',
  'businessId?: string',
  'operatorId?: string',
  'export interface AuditLogQuery extends PageQuery',
  'auditTimeFrom?: string',
  'auditTimeTo?: string',
  'export const getAuditLogs = (params: AuditLogQuery)',
  "request.get<PageResponse<AuditLog>>('/system/audit-logs'",
  'const normalizeAuditLog =',
  'businessId: log.businessId != null ? String(log.businessId) : undefined',
  'operatorId: log.operatorId != null ? String(log.operatorId) : undefined'
]) {
  if (!systemApi.includes(fragment)) {
    errors.push(`系统登录/审计日志 API 缺少后端真实契约或 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface LoginLog {\n  id: number',
  'userId?: number',
  'export interface AuditLog {\n  id: number',
  'businessId?: number',
  'operatorId?: number'
]) {
  if (systemApi.includes(fragment)) {
    errors.push(`系统登录/审计日志 API 仍保留 Long ID 数字精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'name="operation"',
  'name="login"',
  'name="audit"',
  'systemLogs.tabs.login',
  'systemLogs.tabs.audit',
  'getLoginLogs',
  'getAuditLogs',
  'loginQueryForm.username',
  'auditQueryForm.businessNo',
  'auditQueryForm.operatorName',
  'loginTimeFrom',
  'auditTimeFrom'
]) {
  if (!systemLogView.includes(fragment)) {
    errors.push(`系统日志页缺少登录日志/审计日志真实入口片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface OperationLog {\n  id: number',
  'userId?: number',
  'operatorId: number',
  'export const getOperationLog = (id: number)'
]) {
  if (systemApi.includes(fragment)) {
    errors.push(`系统操作日志 API 仍保留 Long ID 数字精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface SystemConfig {\n  id: string',
  'configCode?: string',
  'configName?: string',
  'const normalizeSystemConfig =',
  'id: String(config.id)',
  'configKey: config.configKey ?? config.configCode ?? \'\'',
  'description: config.description ?? config.remark',
  'export const getSystemConfig = (id: string | number)',
  'request.get<SystemConfig>(`/system/configs/${id}`).then(normalizeSystemConfig)',
  'export const updateSystemConfig = (id: string | number, data: SystemConfigSaveRequest)',
  'request.put<SystemConfig>(`/system/configs/${id}`, toSystemConfigUpdatePayload(data))',
  'export const enableSystemConfig = (id: string | number)',
  'request.post<SystemConfig>(`/system/configs/${id}/enable`).then(normalizeSystemConfig)',
  'export const disableSystemConfig = (id: string | number)',
  'request.post<SystemConfig>(`/system/configs/${id}/disable`).then(normalizeSystemConfig)',
  'const toSystemConfigUpdatePayload ='
]) {
  if (!systemApi.includes(fragment)) {
    errors.push(`系统配置 API 缺少后端真实契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface SystemConfig {\n  id: number',
  'export const getSystemConfig = (configKey: string)',
  'request.get<SystemConfig>(`/system/configs/${configKey}`)',
  'export const updateSystemConfig = (configKey: string, data: SystemConfigSaveRequest)',
  'request.put(`/system/configs/${configKey}`, data)'
]) {
  if (systemApi.includes(fragment)) {
    errors.push(`系统配置 API 仍保留旧路由或 Long ID 精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'id: \'\',',
  'configName: \'\',',
  'const res = await getSystemConfig(row.id)',
  'id: res.id',
  'configName: res.configName || res.configKey',
  'await updateSystemConfig(formData.id,',
  'configName: formData.configName || formData.configKey'
]) {
  if (!configView.includes(fragment)) {
    errors.push(`系统配置页缺少按 ID 详情/更新契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'getSystemConfig(row.configKey)',
  'updateSystemConfig(formData.configKey',
  'getConfigTypeLabel',
  'getConfigTypeColor',
  'numericConfigValue',
  'v-model="formData.configType"',
  'JSON预览'
]) {
  if (configView.includes(fragment)) {
    errors.push(`系统配置页仍保留旧路由或后端不存在的类型编辑片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface SequenceRule {\n  id: string',
  'companyId: string',
  'accountBookId: string',
  'currentValue: string',
  'export interface SequenceRuleQuery extends PageQuery',
  'keyword?: string',
  'export interface SequenceRuleSaveRequest',
  'export const getSequenceRules = (params: SequenceRuleQuery)',
  "request.get<PageResponse<SequenceRule>>('/system/sequence-rules'",
  'export const getSequenceRule = (id: string | number)',
  'request.get<SequenceRule>(`/system/sequence-rules/${id}`).then(normalizeSequenceRule)',
  'export const createSequenceRule = (data: SequenceRuleSaveRequest)',
  "request.post<SequenceRule>('/system/sequence-rules', toSequenceRulePayload(data)).then(normalizeSequenceRule)",
  'export const updateSequenceRule = (id: string | number, data: SequenceRuleSaveRequest)',
  'request.put<SequenceRule>(`/system/sequence-rules/${id}`, toSequenceRulePayload(data)).then(normalizeSequenceRule)',
  'export const enableSequenceRule = (id: string | number)',
  'export const disableSequenceRule = (id: string | number)',
  'const normalizeSequenceRule =',
  'id: String(rule.id)',
  'companyId: String(rule.companyId)',
  'accountBookId: String(rule.accountBookId)',
  'currentValue: String(rule.currentValue ?? 0)'
]) {
  if (!systemApi.includes(fragment)) {
    errors.push(`编号规则 API 缺少后端真实契约或 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface SequenceRule {\n  id: number',
  'companyId: number',
  'accountBookId: number',
  'currentValue: number',
  "Promise.reject",
  '暂未提供',
  '模拟'
]) {
  if (systemApi.includes(fragment)) {
    errors.push(`编号规则 API 仍保留 Long ID 数字风险或假流程片段: ${fragment}`)
  }
}

for (const fragment of [
  'v-model="activeTab"',
  'name="configs"',
  'name="sequenceRules"',
  'systemConfigs.sequenceRules',
  'getSequenceRules',
  'getSequenceRule',
  'createSequenceRule',
  'updateSequenceRule',
  'enableSequenceRule',
  'disableSequenceRule',
  'sequenceRuleQuery.keyword',
  'sequenceRuleQuery.status',
  'sequenceRuleForm.bizType',
  'sequenceRuleForm.prefix',
  'sequenceRuleForm.datePattern',
  'sequenceRuleForm.seqLength',
  'sequenceRuleForm.currentValue',
  'handleCreateSequenceRule',
  'handleEditSequenceRule',
  'handleSubmitSequenceRule',
  'handleToggleSequenceRuleStatus'
]) {
  if (!configView.includes(fragment)) {
    errors.push(`系统配置页缺少编号规则真实入口片段: ${fragment}`)
  }
}

for (const fragment of [
  '<el-input-number',
  'id: 0',
  "Promise.reject",
  '暂未提供',
  '模拟'
]) {
  if (configView.includes(fragment)) {
    errors.push(`系统配置页编号规则仍保留 Long ID 数字输入或假流程片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface SystemUserFormState {\n  id?: string',
  'await options.updateUser(formData.id,',
  'await options.createUser({',
  'await options.deleteUser(row.id)',
  'await options.resetUserPassword(row.id, value)'
]) {
  if (!systemUserView.includes(fragment)) {
    errors.push(`系统用户页缺少真实用户 API 调用兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface WorkflowTask {\n  id: number',
  'export interface WorkflowRecord {\n  id: number',
  'businessId: number',
  'taskId: number',
  'approverUserId?: number',
  'getWorkflowTask = (id: number)',
  'reason: data.reason })'
]) {
  if (workflowApi.includes(fragment)) {
    errors.push(`工作流 API 仍保留 Long ID 精度风险或旧驳回 payload 片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface WorkflowApprovalConfig {\n  id?: string',
  'export interface WorkflowApprovalNode {\n  id?: string',
  'export interface WorkflowApprovalApprover {\n  id?: string',
  'approverId: string',
  'export interface WorkflowApprovalConfigRequest',
  'export const getWorkflowApprovalConfig = (businessType: string)',
  'request.get<WorkflowApprovalConfig>(`/workflow/configs/${businessType}`)',
  'export const saveWorkflowApprovalConfig = (businessType: string, data: WorkflowApprovalConfigRequest)',
  'request.put<WorkflowApprovalConfig>(`/workflow/configs/${businessType}`',
  'const normalizeWorkflowApprovalConfig =',
  'const normalizeWorkflowApprovalNode =',
  'const normalizeWorkflowApprovalApprover =',
  'id: config.id != null ? String(config.id) : undefined',
  'id: node.id != null ? String(node.id) : undefined',
  'id: approver.id != null ? String(approver.id) : undefined',
  'approverId: String(approver.approverId)'
]) {
  if (!workflowApi.includes(fragment)) {
    errors.push(`工作流配置 API 缺少后端真实契约或 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface WorkflowApprovalConfig {\n  id?: number',
  'export interface WorkflowApprovalNode {\n  id?: number',
  'export interface WorkflowApprovalApprover {\n  id?: number',
  'approverId: number'
]) {
  if (workflowApi.includes(fragment)) {
    errors.push(`工作流配置 API 仍保留 Long ID 数字风险片段: ${fragment}`)
  }
}

for (const fragment of [
  "import { createWorkflowTaskQueryFromRoute } from './query'",
  'reactive<WorkflowTaskQuery>(createWorkflowTaskQueryFromRoute(route.query))',
  'Object.assign(queryParams, createWorkflowTaskQueryFromRoute(route.query))',
  'approveWorkflowTask({ taskId: currentTask.value.id',
  'rejectWorkflowTask({ taskId: currentTask.value.id'
]) {
  if (!workflowTaskView.includes(fragment)) {
    errors.push(`审批待办页缺少工作流真实操作兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  "businessId: readQueryString(query, 'businessId')",
  "overdueOnly: readQueryBoolean(query, 'overdueOnly')",
  "value === true || (typeof value === 'string' && value.trim().toLowerCase() === 'true')"
]) {
  if (!workflowTaskQuery.includes(fragment)) {
    errors.push(`审批待办查询 helper 缺少 URL 字符串或布尔值兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  "businessId: readQueryNumber('businessId')",
  'function readQueryNumber'
]) {
  if (workflowTaskView.includes(fragment) || workflowTaskQuery.includes(fragment)) {
    errors.push(`审批待办页仍把业务 Long ID 从 URL 转成 number: ${fragment}`)
  }
}

for (const fragment of [
  "businessId: readQueryString('businessId')",
  'const readQueryString =',
  'getBusinessWorkflowRecords,',
  'v-model="queryParams.businessId"',
  'await getBusinessWorkflowRecords(queryParams.businessType, queryParams.businessId)',
  'withdrawWorkflow,',
  'withdrawVisible',
  'withdrawForm.comment',
  'openWithdraw(row)',
  'const openWithdraw = (row: WorkflowRecord) =>',
  'const submitWithdraw = async () =>',
  'await withdrawWorkflow({',
  'businessType: currentRecord.value.businessType',
  'businessId: currentRecord.value.businessId'
]) {
  if (!workflowRecordView.includes(fragment)) {
    errors.push(`审批记录页缺少业务 Long ID 字符串查询兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  "businessId: readQueryNumber('businessId')",
  'function readQueryNumber'
]) {
  if (workflowRecordView.includes(fragment)) {
    errors.push(`审批记录页仍把业务 Long ID 从 URL 转成 number: ${fragment}`)
  }
}

for (const fragment of [
  'workflowConfig.title',
  'getWorkflowApprovalConfig',
  'saveWorkflowApprovalConfig',
  'getUsers',
  'getRoles',
  'businessTypes',
  'activeBusinessType',
  'configForm.configName',
  'configForm.status',
  'configForm.nodes',
  'addNode',
  'removeNode',
  'addApprover',
  'removeApprover',
  'approver.approverType',
  'approver.approverId',
  'submitConfig',
  "approver.approverType === 'USER'"
]) {
  if (!workflowConfigView.includes(fragment)) {
    errors.push(`审批配置页缺少真实工作流配置入口片段: ${fragment}`)
  }
}

for (const fragment of [
  'Promise.reject',
  '暂未提供',
  '模拟',
  'fake',
  'mock',
  'approverId: number',
  '<el-input-number'
]) {
  if (workflowConfigView.includes(fragment)) {
    errors.push(`审批配置页仍保留 Long ID 数字输入或假流程片段: ${fragment}`)
  }
}

for (const fragment of [
  "path: 'configs'",
  "name: 'WorkflowConfigs'",
  "component: () => import('@/views/workflow/configs/index.vue')",
  "permission: 'workflow:config:view'"
]) {
  if (!routerConfig.includes(fragment)) {
    errors.push(`路由缺少审批配置入口片段: ${fragment}`)
  }
}

for (const fragment of [
  'sourceId?: string',
  'assigneeUserId?: string | number',
  'operatorUserId?: string',
  'resolvedBy?: string',
  'createdBy?: string',
  'const normalizeExceptionTicket =',
  'sourceId: ticket.sourceId != null ? String(ticket.sourceId) : undefined',
  'assigneeUserId: ticket.assigneeUserId != null ? String(ticket.assigneeUserId) : undefined',
  'operatorUserId: event.operatorUserId != null ? String(event.operatorUserId) : undefined',
  'records: page.records.map(normalizeExceptionTicket)',
  'request.post<ExceptionTicket>(\'/exception-tickets\', data).then(normalizeExceptionTicket)'
]) {
  if (!exceptionTicketApi.includes(fragment)) {
    errors.push(`异常工单 API 缺少 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'sourceId?: number',
  'assigneeUserId?: number',
  'operatorUserId?: number',
  'resolvedBy?: number',
  'createdBy?: number'
]) {
  if (exceptionTicketApi.includes(fragment)) {
    errors.push(`异常工单 API 仍保留 Long ID 数字精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'v-model="createForm.sourceId"',
  'sourceId: createForm.sourceId?.trim() || undefined',
  'v-model="queryForm.assigneeUserId"',
  'v-model="createForm.assigneeUserId"',
  'v-model="actionForm.assigneeUserId"',
  'const normalizeOptionalId =',
  'assigneeUserId: normalizeOptionalId(queryForm.assigneeUserId)',
  'assigneeUserId: normalizeOptionalId(createForm.assigneeUserId)',
  'assigneeUserId: normalizeOptionalId(actionForm.assigneeUserId)'
]) {
  if (!exceptionTicketView.includes(fragment)) {
    errors.push(`异常工单页缺少 Long ID 字符串输入片段: ${fragment}`)
  }
}

for (const fragment of [
  '<el-input-number v-model="createForm.sourceId"',
  'sourceId: undefined,',
  '<el-input-number\n            v-model="queryForm.assigneeUserId"',
  '<el-input-number\n              v-model="createForm.assigneeUserId"',
  '<el-input-number\n            v-model="actionForm.assigneeUserId"',
  'assigneeUserId: undefined as number | undefined'
]) {
  if (exceptionTicketView.includes(fragment)) {
    errors.push(`异常工单页仍保留 Long ID 数字输入或数字类型片段: ${fragment}`)
  }
}

for (const fragment of [
  'ticketId?: string | number',
  'assigneeUserId?: string | number',
  'assigneeUserId?: string',
  'const normalizeExceptionRule =',
  'const normalizeExceptionRuleHit =',
  'const normalizeExceptionRuleScanResult =',
  'records: page.records.map(normalizeExceptionRule)',
  'records: page.records.map(normalizeExceptionRuleHit)',
  'request.put<ExceptionRule>(`/exception-rules/${id}`, data).then(normalizeExceptionRule)',
  'request.post<ExceptionRule>(`/exception-rules/${id}/enable`).then(normalizeExceptionRule)',
  'request.post<ExceptionRuleScanResult>(`/exception-rules/${id}/scan`).then(normalizeExceptionRuleScanResult)',
  'id: String(rule.id)',
  'ruleId: String(hit.ruleId)',
  'ticketId: hit.ticketId != null ? String(hit.ticketId) : undefined'
]) {
  if (!exceptionRuleApi.includes(fragment)) {
    errors.push(`异常规则 API 缺少 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'ticketId?: number',
  'assigneeUserId?: number',
  'return request.get<PageResponse<ExceptionRule>>(\'/exception-rules\', { params })\n}',
  'return request.get<PageResponse<ExceptionRuleHit>>(\'/exception-rules/hits\', { params })\n}'
]) {
  if (exceptionRuleApi.includes(fragment)) {
    errors.push(`异常规则 API 仍保留 Long ID 数字精度风险或未归一化片段: ${fragment}`)
  }
}

for (const fragment of [
  'v-model="hitQueryForm.ticketId"',
  'v-model="editForm.assigneeUserId"',
  'ticketId: normalizeOptionalId(hitQueryForm.ticketId)',
  'assigneeUserId: normalizeOptionalId(editForm.assigneeUserId)',
  'const normalizeOptionalId ='
]) {
  if (!exceptionRuleView.includes(fragment)) {
    errors.push(`异常规则页缺少 Long ID 字符串输入兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  '<el-input-number\n                v-model="hitQueryForm.ticketId"',
  '<el-input-number\n              v-model="editForm.assigneeUserId"',
  'ticketId: undefined as number | undefined',
  'assigneeUserId: undefined as number | undefined'
]) {
  if (exceptionRuleView.includes(fragment)) {
    errors.push(`异常规则页仍保留 Long ID 数字输入或数字类型片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface ExceptionSlaPolicy {\n  id: string',
  'const normalizeExceptionSlaPolicy =',
  'records: page.records.map(normalizeExceptionSlaPolicy)',
  'request.put<ExceptionSlaPolicy>(`/exception-sla-policies/${id}`, data).then(normalizeExceptionSlaPolicy)',
  'id: String(policy.id)'
]) {
  if (!exceptionSlaPolicyApi.includes(fragment)) {
    errors.push(`异常 SLA 策略 API 缺少 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface ExceptionSlaPolicy {\n  id: number',
  "return request.get<PageResponse<ExceptionSlaPolicy>>('/exception-sla-policies', { params })\n}",
  'return request.put<ExceptionSlaPolicy>(`/exception-sla-policies/${id}`, data)\n}'
]) {
  if (exceptionSlaPolicyApi.includes(fragment)) {
    errors.push(`异常 SLA 策略 API 仍保留 Long ID 数字精度风险或未归一化片段: ${fragment}`)
  }
}

for (const fragment of [
  'getExceptionSlaPolicies',
  'updateExceptionSlaPolicy',
  'openEditDialog',
  'handleSaveEdit',
  'editTarget.value.id'
]) {
  if (!exceptionSlaPolicyView.includes(fragment)) {
    errors.push(`异常 SLA 策略页缺少真实查询或更新入口片段: ${fragment}`)
  }
}

for (const fragment of [
  'businessId?: string | number',
  'const normalizeAttachment =',
  'businessId: String(attachment.businessId)',
  'records: page.records.map(normalizeAttachment)',
  'businessId: string | number'
]) {
  if (!attachmentApi.includes(fragment)) {
    errors.push(`附件 API 缺少业务 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'businessId?: number',
  'businessId: number'
]) {
  if (attachmentApi.includes(fragment)) {
    errors.push(`附件 API 仍保留业务 Long ID 数字精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'v-model="queryForm.businessId"',
  'v-model="uploadForm.businessId"',
  'uploadForm.businessId.trim()',
  'queryForm.businessId = uploadForm.businessId.trim()'
]) {
  if (!attachmentView.includes(fragment)) {
    errors.push(`附件中心页缺少业务 Long ID 字符串输入片段: ${fragment}`)
  }
}

for (const fragment of [
  '<el-input-number',
  'businessId: undefined as number | undefined'
]) {
  if (attachmentView.includes(fragment)) {
    errors.push(`附件中心页仍保留业务 Long ID 数字输入或数字类型片段: ${fragment}`)
  }
}

for (const fragment of [
  'export type ImportType =',
  'export type ImportJobStatus =',
  'export interface ImportJob {\n  jobId: string',
  'createdBy?: string | number',
  'export const downloadImportTemplate = (type: ImportType)',
  "request.get<Blob>(`/import/templates/${type}`",
  'export const previewImportJob = (type: ImportType, file: File)',
  "formData.append('file', file)",
  "request.post<ImportJob>(`/import/jobs/${type}/preview`, formData",
  'export const listImportJobs = (params: ImportJobQuery)',
  "request.get<PageResponse<ImportJob>>('/import/jobs'",
  'export const getImportJob = (jobId: string | number)',
  'export const exportImportErrorRows = (jobId: string | number)',
  'export const commitImportJob = (jobId: string | number)',
  'const normalizeImportJob =',
  'jobId: String(job.jobId)',
  'rows: (job.rows || []).map(normalizeImportRow)'
]) {
  if (!importsApi.includes(fragment)) {
    errors.push(`导入 API 缺少后端真实契约或 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'jobId: number',
  'createdBy?: number',
  "return Promise.reject",
  '暂未提供',
  '模拟',
  'fake',
  'mock'
]) {
  if (importsApi.includes(fragment)) {
    errors.push(`导入 API 仍保留 Long ID 数字风险或假流程片段: ${fragment}`)
  }
}

for (const fragment of [
  'systemImports.jobsTitle',
  'v-model="queryForm.importType"',
  'v-model="queryForm.status"',
  'downloadImportTemplate',
  'previewImportJob',
  'listImportJobs',
  'getImportJob',
  'exportImportErrorRows',
  'commitImportJob',
  'handleDownloadTemplate',
  'handlePreview',
  'handleViewDetail',
  'handleExportErrors',
  'handleCommit',
  'selectedFile.value',
  'row.jobId',
  "row.status === 'VALIDATED'"
]) {
  if (!importsView.includes(fragment)) {
    errors.push(`导入任务页缺少真实导入工作台片段: ${fragment}`)
  }
}

for (const fragment of [
  '<el-input-number',
  'jobId: number',
  "return Promise.reject",
  '暂未提供',
  '模拟',
  'fake',
  'mock'
]) {
  if (importsView.includes(fragment)) {
    errors.push(`导入任务页仍保留 Long ID 数字输入或假流程片段: ${fragment}`)
  }
}

for (const fragment of [
  "path: 'imports'",
  "name: 'SystemImports'",
  "component: () => import('@/views/system/imports/index.vue')",
  "permission: 'import:init:manage'"
]) {
  if (!routerConfig.includes(fragment)) {
    errors.push(`路由缺少导入任务入口片段: ${fragment}`)
  }
}

for (const fragment of requiredFragments) {
  if (!orderView.includes(fragment)) {
    errors.push(`生产订单页缺少双仓库契约片段: ${fragment}`)
  }
}

for (const fragment of forbiddenFragments) {
  if (orderView.includes(fragment)) {
    errors.push(`生产订单页仍保留单目标仓库片段: ${fragment}`)
  }
}

for (const fragment of [
  'export const updateProductionOrder = (id: string | number, data: ProductionOrderUpdateRequest)',
  'request.put<ProductionOrder>(`/production/orders/${id}`, toProductionOrderPayload(data)).then(normalizeProductionOrder)',
  'export interface ProductionCompletionReversalRequest',
  'reversalDate?: string',
  'reversedQty?: number',
  'export interface ProductionReturnRequest',
  'returnDate?: string',
  'lines: {',
  'orderMaterialId: string | number',
  'returnQty: number',
  'export const reverseProductionCompletion = (id: string | number, data: ProductionCompletionReversalRequest)',
  'request.post<ProductionOrder>(`/production/orders/${id}/reverse-completion`, data).then(normalizeProductionOrder)',
  'export const returnProductionMaterials = (id: string | number, data: ProductionReturnRequest)',
  'request.post<ProductionOrder>(`/production/orders/${id}/return-materials`, data).then(normalizeProductionOrder)'
]) {
  if (!productionApi.includes(fragment)) {
    errors.push(`生产订单 API 缺少完工红冲/退料后端真实契约片段: ${fragment}`)
  }
}

for (const fragment of [
  'reverseProductionCompletion = (id: string | number, data: { quantity: number; remark?: string })',
  'materials: {\n    materialId: string | number\n    quantity: number',
  'materialId: material.materialId',
  'quantity: material.quantity'
]) {
  if (productionApi.includes(fragment)) {
    errors.push(`生产订单 API 仍保留完工红冲/退料旧字段错位片段: ${fragment}`)
  }
}

for (const fragment of [
  'updateProductionOrder,',
  '@click="handleEdit(row)"',
  'const handleEdit = async (row: ProductionOrder) =>',
  "dialogTitle.value = t('productionOrder.dialog.edit')",
  'Object.assign(formData, {',
  'if (formData.id) {',
  'await updateProductionOrder(formData.id, formData)',
  'reverseProductionCompletion,',
  'returnProductionMaterials,',
  'reverseDialogVisible',
  'returnDialogVisible',
  'handleReverseCompletion',
  'handleConfirmReverseCompletion',
  'handleReturnMaterials',
  'handleConfirmReturnMaterials',
  'v-model="reverseForm.reversedQty"',
  'v-model="returnForm.returnDate"',
  'row.returnQty',
  'orderMaterialId: material.id',
  'returnQty: material.returnQty'
]) {
  if (!orderView.includes(fragment)) {
    errors.push(`生产订单页缺少完工红冲/退料真实入口片段: ${fragment}`)
  }
}

for (const fragment of [
  "status: 'ACTIVE' | 'DISABLED' | string",
  'baseQty: number',
  'export interface BOMRequest {\n  productId: string | number\n  baseQty: number',
  'baseQty: data.baseQty',
  'quantity: bom.quantity ?? bom.baseQty ?? 0',
  'baseQty: bom.baseQty ?? bom.quantity ?? 0',
  'unit: item.unit || item.materialUnit || \'\''
]) {
  if (!productionApi.includes(fragment)) {
    errors.push(`BOM API 缺少后端真实契约兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  "status: 'DRAFT' | 'APPROVED' | 'DISABLED'",
  'export interface BOMRequest {\n  productId: string | number\n  version: string',
  'version: bom.version ?? bom.status ??',
  'baseQty: data.quantity'
]) {
  if (productionApi.includes(fragment)) {
    errors.push(`BOM API 仍保留前端臆造字段或状态片段: ${fragment}`)
  }
}

for (const fragment of [
  '<el-option :label="t(\'productionBom.status.active\')" value="ACTIVE" />',
  'v-model="formData.baseQty"',
  'baseQty: res.baseQty',
  'baseQty: 1',
  'const optionPageQuery = { pageNo: 1, pageSize: 200, status: \'ACTIVE\' }',
  ':label="productLabel(item)"',
  'materialLabel(row)'
]) {
  if (!bomView.includes(fragment)) {
    errors.push(`BOM页缺少后端真实契约兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'handleApprove',
  '审批成功',
  '调用审批API',
  '<el-option label="草稿" value="DRAFT" />',
  '<el-option label="已审批" value="APPROVED" />',
  'row.status === \'DRAFT\'',
  'label="版本"',
  'prop="version"',
  'v-model="formData.version"',
  'label="单位"',
  'prop="unit"',
  'v-model="formData.unit"',
  'getProducts({ page: 1, size: 1000 })',
  ':label="`${item.productCode} - ${item.productName}`"'
]) {
  if (bomView.includes(fragment)) {
    errors.push(`BOM页仍保留假审批、臆造字段或不稳定选项片段: ${fragment}`)
  }
}

for (const view of inventoryOptionViews) {
  if (!view.content.includes('const optionPageQuery = { pageNo: 1, pageSize: 200, status: \'ACTIVE\' }')) {
    errors.push(`${view.name}缺少库存选项分页契约: const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }`)
  }
  if (!view.content.includes('getWarehouses(optionPageQuery)')) {
    errors.push(`${view.name}仓库选项未使用统一分页契约: getWarehouses(optionPageQuery)`)
  }
  if (!view.content.includes('getProducts(optionPageQuery)')) {
    errors.push(`${view.name}产品选项未使用统一分页契约: getProducts(optionPageQuery)`)
  }
  for (const fragment of [
    'getWarehouses({ pageNo: 1000',
    'getProducts({ pageNo: 1000',
    'getWarehouses({ pageNo: 1, pageSize: 1000',
    'getProducts({ pageNo: 1, pageSize: 1000'
  ]) {
    if (view.content.includes(fragment)) {
      errors.push(`${view.name}仍保留不稳定库存选项分页片段: ${fragment}`)
    }
  }
}

const inventoryAdjustmentRequiredFragments = [
  'const normalizeInventoryAdjustment =',
  'const toAdjustmentPayload =',
  'const adjustmentDirection =',
  'id: string | number',
  'warehouseId: string | number',
  'productId: string | number',
  'warehouseId?: string | number',
  'export const getInventoryAdjustment = (id: string | number)',
  'export const completeInventoryAdjustment = (id: string | number)',
  'export const cancelInventoryAdjustment = (id: string | number)',
  'request.post<InventoryAdjustment>(\'/inventory/adjustments\', toAdjustmentPayload(data))',
  'const items = (adjustment.items ?? adjustment.lines ?? []).map(normalizeInventoryAdjustmentItem)',
  'id: adjustment.id,',
  'warehouseId: String(adjustment.warehouseId)',
  'productId: String(item.productId)',
  'status: adjustment.status === \'POSTED\' ? \'COMPLETED\' : adjustment.status',
  'direction: item.direction ?? adjustmentDirection(data.type)',
  'qty: item.quantity',
  'unitCost: item.unitCost ?? 0'
]

for (const fragment of inventoryAdjustmentRequiredFragments) {
  if (!inventoryApi.includes(fragment)) {
    errors.push(`库存调整 API 缺少前后端契约映射片段: ${fragment}`)
  }
}

for (const fragment of [
  'return request.get<PageResponse<InventoryAdjustment>>(\'/inventory/adjustments\', { params })\n}',
  'return request.get<InventoryAdjustment>(`/inventory/adjustments/${id}`)\n}',
  'return request.post<number>(\'/inventory/adjustments\', data)',
  'return request.post(`/inventory/adjustments/${id}/post`)\n}',
  'id: Number(adjustment.id)',
  'warehouseId: number',
  'productId: number',
  'warehouseId?: number',
  'productId?: number',
  'warehouseId: Number(adjustment.warehouseId)',
  'productId: Number(item.productId)'
]) {
  if (inventoryApi.includes(fragment)) {
    errors.push(`库存调整 API 仍保留未归一化调用片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface InventoryAlertQuery extends PageQuery {\n  warehouseId?: string | number',
  'productId?: string | number',
  'warehouseId: params.warehouseId',
  'productId: params.productId',
  'export interface InventoryAlertRule {',
  'export interface InventoryAlertRuleCreateRequest {',
  'export const createInventoryAlertRule = (data: InventoryAlertRuleCreateRequest)',
  "request.post<InventoryAlertRule>('/inventory/alert-rules', data).then(normalizeInventoryAlertRule)",
  'warehouseId: String(rule.warehouseId)',
  'productId: String(rule.productId)',
  'warehouseId: String(alert.warehouseId)',
  'productId: String(alert.productId)'
]) {
  if (!inventoryApi.includes(fragment)) {
    errors.push(`库存预警 API 缺少 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface InventoryAlertQuery extends PageQuery {\n  warehouseId?: number',
  'productId?: number'
]) {
  if (inventoryApi.includes(fragment)) {
    errors.push(`库存预警 API 仍保留 Long ID 数字精度风险片段: ${fragment}`)
  }
}

for (const fragment of [
  'handleCreateRule',
  'v-permission="\'inventory:alert:create\'"',
  'ruleDialogVisible',
  'ruleForm.warehouseId',
  'ruleForm.productId',
  'ruleForm.minQty',
  'createInventoryAlertRule(ruleForm)',
  'const submitRule = async () =>'
]) {
  if (!inventoryAlertView.includes(fragment)) {
    errors.push(`库存预警页缺少低库存规则创建入口片段: ${fragment}`)
  }
}

for (const fragment of [
  'export const getInventoryStock = (id: string | number)',
  'request.get<InventoryStock>(`/inventory/balances/${id}`).then(normalizeInventoryStock)',
  'export const getInventoryLotBalance = (id: string | number)',
  'request.get<InventoryLotBalance>(`/inventory/lot-balances/${id}`).then(normalizeInventoryLotBalance)',
  'export const getInventoryTransaction = (id: string | number)',
  'request.get<InventoryTransaction>(`/inventory/transactions/${id}`).then(normalizeInventoryTransaction)',
  'export interface InventoryLotBalance {\n  locationId?: string\n\n  id: string',
  'export interface InventoryLotTrace {\n  locationId?: string\n\n  id: string',
  'export interface InventoryLotExpiryAlert {\n  id: string',
  'export interface InventoryTransaction {\n  locationId?: string\n\n  id: string',
  'warehouseId: string',
  'productId: string',
  'bizLineId?: string',
  'export interface InventoryLotBalanceQuery extends PageQuery',
  'export interface InventoryLotTraceQuery extends PageQuery',
  'export interface InventoryLotExpiryAlertQuery extends PageQuery',
  'export interface InventoryTransactionQuery extends PageQuery',
  'export const getInventoryLotBalances =',
  "request.get<PageResponse<InventoryLotBalance>>('/inventory/lot-balances'",
  'export const getInventoryLotTrace =',
  "request.get<PageResponse<InventoryLotTrace>>('/inventory/lots/trace'",
  'export const getInventoryLotExpiryAlerts =',
  "request.get<PageResponse<InventoryLotExpiryAlert>>('/inventory/lots/alerts'",
  'export const getInventoryTransactions =',
  "request.get<PageResponse<InventoryTransaction>>('/inventory/transactions'",
  'const normalizeInventoryLotBalance =',
  'const normalizeInventoryLotTrace =',
  'const normalizeInventoryLotExpiryAlert =',
  'const normalizeInventoryTransaction =',
  'id: String(item.id)',
  'warehouseId: String(item.warehouseId)',
  'productId: String(item.productId)',
  'bizLineId: item.bizLineId != null ? String(item.bizLineId) : undefined'
]) {
  if (!inventoryApi.includes(fragment)) {
    errors.push(`库存批次/流水 API 缺少后端真实契约或 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface InventoryLotBalance {\n  id: number',
  'export interface InventoryLotTrace {\n  id: number',
  'export interface InventoryLotExpiryAlert {\n  id: number',
  'export interface InventoryTransaction {\n  id: number',
  'warehouseId: number',
  'productId: number',
  'bizLineId?: number',
  "return Promise.reject",
  '暂未提供',
  '模拟',
  'fake',
  'mock'
]) {
  if (inventoryApi.includes(fragment)) {
    errors.push(`库存批次/流水 API 仍保留 Long ID 数字风险或假流程片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface InventoryReservation {\n  id: string',
  'warehouseId: string',
  'productId: string',
  'sourceId?: string',
  'sourceLineId?: string',
  'export interface InventoryReservationEvent {\n  id: string',
  'reservationId: string',
  'createdBy?: string',
  'export interface InventoryReservationDetail',
  'export interface InventoryReservationSummary',
  'export interface InventoryReservationCheckIssue',
  'export interface InventoryReservationQuery extends PageQuery',
  'export const getInventoryReservations =',
  "request.get<PageResponse<InventoryReservation>>('/inventory/reservations'",
  'export const getInventoryReservation = (id: string | number)',
  'export const getInventoryReservationSummary =',
  "request.get<InventoryReservationSummary[]>('/inventory/reservations/summary'",
  'export const getInventoryReservationSource =',
  "request.get<InventoryReservationSource>('/inventory/reservations/source'",
  'export const checkInventoryReservations =',
  "request.get<InventoryReservationCheckIssue[]>('/inventory/reservations/checks'",
  'export const manualReleaseInventoryReservation =',
  "request.post<InventoryReservationDetail>(`/inventory/reservations/${id}/manual-release`",
  'const normalizeInventoryReservation =',
  'const normalizeInventoryReservationEvent =',
  'const normalizeInventoryReservationDetail =',
  'id: String(reservation.id)',
  'warehouseId: String(reservation.warehouseId)',
  'productId: String(reservation.productId)',
  'sourceId: reservation.sourceId != null ? String(reservation.sourceId) : undefined',
  'sourceLineId: reservation.sourceLineId != null ? String(reservation.sourceLineId) : undefined',
  'reservationId: String(event.reservationId)',
  'createdBy: event.createdBy != null ? String(event.createdBy) : undefined'
]) {
  if (!inventoryApi.includes(fragment)) {
    errors.push(`库存预留 API 缺少后端真实契约或 Long ID 字符串兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export interface InventoryReservation {\n  id: number',
  'warehouseId: number',
  'productId: number',
  'sourceId?: number',
  'sourceLineId?: number',
  'reservationId: number',
  'createdBy?: number',
  "return Promise.reject",
  '暂未提供',
  '模拟',
  'fake',
  'mock'
]) {
  if (inventoryApi.includes(fragment)) {
    errors.push(`库存预留 API 仍保留 Long ID 数字风险或假流程片段: ${fragment}`)
  }
}

for (const fragment of [
  'inventoryStocks.reservationDetails',
  'getInventoryReservations',
  'getInventoryReservation',
  'manualReleaseInventoryReservation',
  'checkInventoryReservations',
  'handleOpenReservations',
  'handleViewReservation',
  'openReleaseDialog',
  'submitManualRelease',
  'releaseForm.qty',
  'releaseForm.reason',
  'reservationDetail.events',
  'getInventoryReservationSummary',
  'getInventoryReservationSource',
  'reservationSummaryData',
  'reservationSourceDetail',
  'loadReservationSummary',
  'handleViewReservationSource',
  "row.status === 'ACTIVE' && Number(row.remainingQty) > 0",
  'warehouseId: row.warehouseId',
  'productId: row.productId'
]) {
  if (!inventoryStockFeature.includes(fragment)) {
    errors.push(`库存查询页缺少库存预留真实操作入口片段: ${fragment}`)
  }
}

for (const fragment of [
  'getWarehouses({ pageNo: 1, pageSize: 1000',
  'getProducts({ pageNo: 1, pageSize: 1000',
  "return Promise.reject",
  '暂未提供',
  '模拟',
  'fake',
  'mock'
]) {
  if (inventoryStockView.includes(fragment)) {
    errors.push(`库存查询页仍保留不稳定选项分页、假流程或占位片段: ${fragment}`)
  }
}

for (const fragment of [
  'inventoryStocks.lotStock',
  'inventoryStocks.transactions',
  'inventoryStocks.expiryAlerts',
  'inventoryStocks.lotTrace',
  'getInventoryLotBalances',
  'getInventoryLotTrace',
  'getInventoryLotExpiryAlerts',
  'getInventoryTransactions',
  'getInventoryStock',
  'getInventoryLotBalance',
  'getInventoryTransaction',
  'handleOpenLotBalances',
  'handleOpenTransactions',
  'handleOpenLotAlerts',
  'handleOpenLotTrace',
  'handleViewStock',
  'handleViewLotBalance',
  'handleViewTransaction',
  'stockDetailVisible',
  'lotBalanceDetailVisible',
  'transactionDetailVisible',
  'selectedStock',
  'selectedLotBalance',
  'selectedTransaction',
  'await dependencies.getStock(row.id)',
  'await dependencies.getLotBalance(row.id)',
  'await dependencies.getTransaction(row.id)',
  'lotBalanceData',
  'transactionData',
  'lotAlertData',
  'lotTraceData',
  'warehouseId: row.warehouseId',
  'productId: row.productId'
]) {
  if (!inventoryStockFeature.includes(fragment)) {
    errors.push(`库存查询页缺少批次库存/流水真实入口片段: ${fragment}`)
  }
}

const adjustmentView = inventoryOptionViews.find((view) => view.name === '库存调整页')?.content || ''
for (const fragment of [
  '<el-option label="其他" value="OTHER" />',
  ':label="`${product.code} - ${product.name}`"',
  'item.productCode = product.code\n',
  'item.productName = product.name\n'
]) {
  if (adjustmentView.includes(fragment)) {
    errors.push(`库存调整页仍保留与后端契约不一致的片段: ${fragment}`)
  }
}
for (const fragment of [
  ':label="productLabel(product)"',
  'const productLabel = (product: Product) =>',
  'item.productCode = product.code || product.productCode || \'\'',
  'item.productName = product.name || product.productName || \'\''
]) {
  if (!adjustmentView.includes(fragment)) {
    errors.push(`库存调整页缺少字段兼容片段: ${fragment}`)
  }
}

const inventoryTransferRequiredFragments = [
  'const normalizeInventoryTransfer =',
  'const toTransferPayload =',
  'id: string | number',
  'lines?: InventoryTransferLineResponse[]',
  'export const getInventoryTransfer = (id: string | number)',
  'export const shipInventoryTransfer = (id: string | number)',
  'export const cancelInventoryTransfer = (id: string | number)',
  'request.post<InventoryTransfer>(\'/inventory/transfers\', toTransferPayload(data))',
  'const items = (transfer.items ?? transfer.lines ?? []).map(normalizeInventoryTransferItem)',
  'id: transfer.id,',
  'status: transfer.status === \'POSTED\' ? \'COMPLETED\' : transfer.status',
  'qty: item.quantity',
  'unitCost: item.unitCost ?? 0'
]

for (const fragment of inventoryTransferRequiredFragments) {
  if (!inventoryApi.includes(fragment)) {
    errors.push(`库存调拨 API 缺少前后端契约映射片段: ${fragment}`)
  }
}

for (const fragment of [
  'return request.get<PageResponse<InventoryTransfer>>(\'/inventory/transfers\', { params })\n}',
  'return request.get<InventoryTransfer>(`/inventory/transfers/${id}`)\n}',
  'return request.post<number>(\'/inventory/transfers\', data)',
  'return request.post(`/inventory/transfers/${id}/post`)\n}',
  'id: Number(transfer.id)'
]) {
  if (inventoryApi.includes(fragment)) {
    errors.push(`库存调拨 API 仍保留未归一化调用片段: ${fragment}`)
  }
}

const transferView = inventoryOptionViews.find((view) => view.name === '库存调拨页')?.content || ''
for (const fragment of [
  '<el-option label="在途" value="IN_TRANSIT" />',
  'row.status === \'IN_TRANSIT\'',
  'receiveInventoryTransfer',
  '确认发货此调拨单吗？发货后将扣减调出仓库库存。',
  ':label="`${product.code} - ${product.name}`"',
  'item.productCode = product.code\n',
  'item.productName = product.name\n'
]) {
  if (transferView.includes(fragment)) {
    errors.push(`库存调拨页仍保留与后端契约不一致的片段: ${fragment}`)
  }
}
for (const fragment of [
  // 过账入口按 i18n 键校验，不再依赖已清除的中文硬编码/注释
  'inventoryTransfers.action.post',
  'shipInventoryTransfer',
  'inventoryTransfers.message.postConfirm',
  ':label="productLabel(product)"',
  'const productLabel = (product: Product) =>',
  'item.productCode = product.code || product.productCode || \'\'',
  'item.productName = product.name || product.productName || \'\'',
  'item.unitCost = product.purchasePrice ?? item.unitCost ?? 0'
]) {
  if (!transferView.includes(fragment)) {
    errors.push(`库存调拨页缺少后端契约兼容片段: ${fragment}`)
  }
}

const inventoryCheckRequiredFragments = [
  'export interface InventoryCheck {\n  id: string | number',
  "status: 'COUNTED' | 'ADJUSTED' | 'CANCELLED'",
  'generatedAdjustmentId?: string | number',
  'export const getInventoryCheck = (id: string | number)',
  'export const updateInventoryCheck = (id: string | number, data: InventoryCheckUpdateRequest)',
  'export const completeInventoryCheck = (id: string | number)',
  'export const cancelInventoryCheck = (id: string | number)',
  'request.post<InventoryCheck>(\'/inventory/checks\', toStockCheckCreatePayload(data))',
  'lines: data.items.map(toStockCheckLinePayload)',
  'items: data.items.map(toStockCheckLinePayload)',
  'actualQty: item.actualQuantity ?? item.bookQuantity ?? 0'
]

for (const fragment of inventoryCheckRequiredFragments) {
  if (!inventoryApi.includes(fragment)) {
    errors.push(`库存盘点 API 缺少前后端契约映射片段: ${fragment}`)
  }
}

for (const fragment of [
  'export const getInventoryCheck = (id: number)',
  'export const updateInventoryCheck = (id: number, data: InventoryCheckUpdateRequest)',
  'export const completeInventoryCheck = (id: number)',
  'export const cancelInventoryCheck = (id: number)',
  'id: number\n  checkNo',
  'generatedAdjustmentId?: number'
]) {
  if (inventoryApi.includes(fragment)) {
    errors.push(`库存盘点 API 仍保留 Long ID 数字精度风险片段: ${fragment}`)
  }
}

const checkView = inventoryOptionViews.find((view) => view.name === '库存盘点页')?.content || ''
for (const fragment of [
  '<el-option label="草稿" value="DRAFT" />',
  '<el-option label="盘点中" value="CHECKING" />',
  '<el-option label="已完成" value="COMPLETED" />',
  "row.status === 'DRAFT'",
  "row.status === 'CHECKING'",
  "row.status === 'COMPLETED'",
  '确认完成此库存盘点吗？完成后将根据差异自动调整库存。',
  'const currentId = ref(0)'
]) {
  if (checkView.includes(fragment)) {
    errors.push(`库存盘点页仍保留与后端契约不一致的片段: ${fragment}`)
  }
}
for (const fragment of [
  'inventoryChecks.message.adjustConfirm',
  "const currentId = ref<string | number>('')",
  ':disabled="isView"',
  "v-if=\"row.status === 'COUNTED'\"",
  "v-if=\"row.status === 'COUNTED'\"",
  "v-if=\"row.status === 'COUNTED'\""
]) {
  if (!checkView.includes(fragment)) {
    errors.push(`库存盘点页缺少后端契约兼容片段: ${fragment}`)
  }
}

const salesReturnRequiredFragments = [
  'const normalizeSalesReturn =',
  'const toSalesReturnPayload =',
  "status: 'DRAFT' | 'POSTED' | 'COMPLETED' | 'CANCELLED'",
  'deliveryId: string | number',
  'lines?: SalesReturnLineResponse[]',
  'deliveryLineId?: string | number',
  'export const postSalesReturn = (id: string | number)',
  'request.post<SalesReturn>(\'/sales/returns\', toSalesReturnPayload(data))',
  'request.post<SalesReturn>(`/sales/returns/${id}/post`).then(normalizeSalesReturn)',
  'deliveryId: data.deliveryId',
  'lines: data.items.map((item) => ({',
  'deliveryLineId: item.deliveryLineId',
  'qty: item.quantity'
]

for (const fragment of salesReturnRequiredFragments) {
  if (!salesApi.includes(fragment)) {
    errors.push(`销售退货 API 缺少前后端契约映射片段: ${fragment}`)
  }
}

for (const fragment of [
  'return request.post<number>(\'/sales/returns\', data)',
  'export interface SalesReturnCreateRequest {\n  orderId',
  "status: 'DRAFT' | 'COMPLETED' | 'CANCELLED'"
]) {
  if (salesApi.includes(fragment)) {
    errors.push(`销售退货 API 仍保留与后端契约不一致的片段: ${fragment}`)
  }
}

for (const fragment of [
  'getSalesDeliveries',
  'postSalesReturn',
  'salesReturnOps.salesDelivery',
  'prop="deliveryId"',
  'v-model="formData.deliveryId"',
  '@change="handleDeliveryChange"',
  "v-if=\"row.status === 'DRAFT'\"",
  '过账',
  'salesReturnOps.message.postConfirm',
  'const handleDeliveryChange = async () =>',
  'formData.items = delivery.items.map(item => ({',
  'deliveryLineId: item.id',
  'quantity: item.quantity - (item.returnedQty || 0)',
  'const deliveryPageQuery = { pageNo: 1, pageSize: 200, status: \'POSTED\' }'
]) {
  if (!salesReturnView.includes(fragment)) {
    errors.push(`销售退货页缺少后端契约兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'placeholder="请选择销售订单（可选）"',
  '@change="handleOrderChange"',
  'const handleOrderChange = async () =>',
  'getSalesOrder',
  'getSalesOrders({ pageNo: 1000',
  'getCustomers({ pageNo: 1000',
  'getWarehouses({ pageNo: 1000',
  'getProducts({ pageNo: 1000',
  'handleAddItem',
  'handleProductChange',
  ':label="`${product.code} - ${product.name}`"'
]) {
  if (salesReturnView.includes(fragment)) {
    errors.push(`销售退货页仍保留与后端契约不一致的片段: ${fragment}`)
  }
}

const purchaseReturnRequiredFragments = [
  'const normalizePurchaseReturn =',
  'const toPurchaseReturnPayload =',
  "status: 'DRAFT' | 'POSTED' | 'COMPLETED' | 'CANCELLED'",
  'receiptId: string | number',
  'lines?: PurchaseReturnLineResponse[]',
  'receiptLineId?: string | number',
  'export const getPurchaseReturn = (id: string | number)',
  'request.get<PurchaseReturn>(`/purchase/returns/${id}`).then(normalizePurchaseReturn)',
  'export const postPurchaseReturn = (id: string | number)',
  'request.post<PurchaseReturn>(\'/purchase/returns\', toPurchaseReturnPayload(data))',
  'request.post<PurchaseReturn>(`/purchase/returns/${id}/post`).then(normalizePurchaseReturn)',
  'receiptId: data.receiptId',
  'lines: data.items.map((item) => ({',
  'receiptLineId: item.receiptLineId',
  'qty: item.quantity'
]

for (const fragment of [
  'linkedOrderVisible',
  'const linkedOrder = ref<PurchaseOrder>()',
  'await getPurchaseOrder(orderId)',
  ':title="t(\'purchaseOrder.detailTitle\')"',
  'v-if="linkedOrder"'
]) {
  if (!purchaseReceiptView.includes(fragment)) {
    errors.push(`采购收货页缺少采购订单真实查看片段: ${fragment}`)
  }
}

for (const fragment of [
  'ElMessage.info(`查看订单 ID',
  '实际项目中跳转到订单详情页',
  'v-model.number="queryForm.orderId"',
  'getPurchaseOrder(orderId as number)'
]) {
  if (purchaseReceiptView.includes(fragment)) {
    errors.push(`采购收货页仍保留采购订单假查看片段: ${fragment}`)
  }
}

for (const fragment of purchaseReturnRequiredFragments) {
  if (!purchaseApi.includes(fragment)) {
    errors.push(`采购退货 API 缺少前后端契约映射片段: ${fragment}`)
  }
}

for (const fragment of [
  'return request.post<number>(\'/purchase/returns\', data)',
  'export interface PurchaseReturnCreateRequest {\n  orderId',
  "status: 'DRAFT' | 'COMPLETED' | 'CANCELLED'",
  'completePurchaseReturn'
]) {
  if (purchaseApi.includes(fragment)) {
    errors.push(`采购退货 API 仍保留与后端契约不一致的片段: ${fragment}`)
  }
}

for (const fragment of [
  'getPurchaseReceipts',
  'getPurchaseReceipt',
  'getPurchaseReturn',
  'postPurchaseReturn',
  ':label="t(\'purchaseReturn.receipt\')"',
  'prop="receiptId"',
  'v-model="form.receiptId"',
  '@change="handleReceiptChange"',
  "v-if=\"row.status === 'DRAFT'\"",
  "t('purchaseReturn.post')",
  "t('purchaseReturn.message.postConfirm')",
  'const handleReceiptChange = async () =>',
  'const handleView = async (row: PurchaseReturn) =>',
  'currentRow.value = await getPurchaseReturn(row.id)',
  'form.items = receipt.items.map(item => ({',
  'receiptLineId: item.id',
  'quantity: item.quantity - (item.returnedQty || 0)',
  'const receiptPageQuery = { pageNo: 1, pageSize: 200, status: \'POSTED\' }'
]) {
  if (!purchaseReturnView.includes(fragment)) {
    errors.push(`采购退货页缺少后端契约兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'label="采购订单"',
  'prop="orderId"',
  'v-model="form.orderId"',
  '@change="handleOrderChange"',
  'const handleOrderChange =',
  'getPurchaseOrders',
  'getPurchaseOrders({',
  'form.warehouseId',
  'handleProductChange',
  ':label="`${product.code} - ${product.name}`"',
  'v-model.number="queryForm.receiptId"',
  'completePurchaseReturn'
]) {
  if (purchaseReturnView.includes(fragment)) {
    errors.push(`采购退货页仍保留与后端契约不一致的片段: ${fragment}`)
  }
}

for (const fragment of [
  'linkedReceiptVisible',
  'const linkedReceipt = ref<PurchaseReceipt>()',
  'await getPurchaseReceipt(receiptId)',
  ':title="t(\'purchaseReturn.linkedReceiptTitle\')"',
  'v-if="linkedReceipt"'
]) {
  if (!purchaseReturnView.includes(fragment)) {
    errors.push(`采购退货页缺少采购收货单真实查看片段: ${fragment}`)
  }
}

for (const fragment of [
  'ElMessage.info(`查看收货单 ID',
  '实际项目中跳转到收货单详情页'
]) {
  if (purchaseReturnView.includes(fragment)) {
    errors.push(`采购退货页仍保留采购收货单假查看片段: ${fragment}`)
  }
}

for (const fragment of [
  'aria-label="打开摄像头扫码"',
  'navigator.mediaDevices.getUserMedia',
  'BarcodeDetector',
  "emit('cameraState', state)",
  'stream?.getTracks().forEach',
  'onBeforeUnmount(stopCamera)'
]) {
  if (!barcodeScanField.includes(fragment)) {
    errors.push(`扫码组件缺少摄像头/扫码枪兼容片段: ${fragment}`)
  }
}

for (const fragment of [
  'export const incrementScannedLine',
  'String(line.productId) === String(scannedProductId)',
  "status: 'not-found'",
  "status: 'at-maximum'"
]) {
  if (!barcodeUtils.includes(fragment)) {
    errors.push(`扫码数量工具缺少安全匹配或上限片段: ${fragment}`)
  }
}

for (const [name, content] of [
  ['采购收货页', purchaseReceiptView],
  ['销售发货页', salesDeliveryView]
]) {
  for (const fragment of [
    '<BarcodeScanField',
    '@scan="handleBarcodeScan"',
    'getProductByBarcode',
    'incrementScannedLine',
    'hydrateProductLineLabels',
    'const resetScanQuantities = async () =>',
    'const handleBarcodeScan = async (barcode: string) =>'
  ]) {
    if (!content.includes(fragment)) {
      errors.push(`${name}缺少 Web 扫码录入片段: ${fragment}`)
    }
  }
}

if (errors.length > 0) {
  console.error(errors.join('\n'))
  process.exit(1)
}

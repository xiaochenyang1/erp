import { createI18n } from 'vue-i18n'
import { adminWorkflowPageMessages } from './admin-workflow-pages'
import { financeReportPageMessages } from './finance-report-pages'
import { operationsPageMessages } from './operations-pages'
import { platformPageMessages } from './platform-pages'
import { salesCommercialPageMessages } from './sales-commercial-pages'

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const
export type SupportedLocale = typeof SUPPORTED_LOCALES[number]
export const DEFAULT_LOCALE: SupportedLocale = 'zh-CN'

export const isSupportedLocale = (value: unknown): value is SupportedLocale =>
  typeof value === 'string' && SUPPORTED_LOCALES.includes(value as SupportedLocale)

export const readStoredLocale = (): SupportedLocale => {
  const stored = localStorage.getItem('locale')
  return isSupportedLocale(stored) ? stored : DEFAULT_LOCALE
}

const coreMessages = {
  'zh-CN': {
    common: { cancel: '取消', save: '保存', confirm: '确定' },
    app: { name: 'ERP管理系统', shortName: 'ERP系统' },
    settings: {
      title: '语言与时区', language: '界面语言', timezone: '显示时区',
      zhCN: '简体中文', enUS: 'English', shanghai: '中国标准时间', utc: '协调世界时',
      newYork: '纽约时间', london: '伦敦时间'
    },
    user: {
      profile: '个人中心', password: '修改密码', logout: '退出登录', logoutConfirm: '确定要退出登录吗？',
      saved: '个人资料已保存', saveFailed: '保存个人资料失败', preferencesSaveFailed: '保存语言与时区偏好失败', passwordChanged: '密码修改成功，请重新登录'
    },
    login: {
      welcome: '欢迎登录', subtitle: '请输入您的账号密码', username: '请输入用户名', password: '请输入密码',
      forgot: '忘记密码？', passwordResetHint: '请联系系统管理员重置密码', submit: '登录', submitting: '登录中...', testAccount: '本地测试账号',
      prefilledTestAccount: 'admin / LocalAdmin123（已填充）',
      feature1Title: '集成管理', feature1Desc: '采购、销售、库存、财务一体化管理',
      feature2Title: '数据分析', feature2Desc: '实时报表，智能决策支持',
      feature3Title: '流程审批', feature3Desc: '多级审批，权限精细控制',
      usernameRequired: '请输入用户名', passwordRequired: '请输入密码', passwordMin: '密码至少6位'
    },
    dashboard: {
      welcome: '欢迎回来，{name}', user: '用户', source: '工作台数据来自当前账套',
      onboarding: '新手路径：主数据 → 销售/采购订单 → 出入库 → 财务收付；扩展能力见 账龄/MRP/报价/询价',
      newPurchase: '新增采购', newSales: '新增销售', stockQuery: '库存查询',
      todayPurchaseOrders: '今日采购订单', todaySalesAmount: '今日销售金额', stockAlerts: '库存预警',
      pendingApprovals: '待审批单据', overdueApprovals: '超时 {count}', openReceivables: '未结应收', openPayables: '未结应付',
      today: '今日', attention: '需关注', pending: '待处理', receivableAging: '应收账龄', payableAging: '应付账龄',
      details: '详情', bucket: '账龄段', count: '笔数', amount: '金额', total: '合计', overdueTop: '逾期 TOP',
      topSkus: '畅销商品 TOP 5', last30Days: '最近30天已过账发货', rank: '排名', productCode: '商品编码', productName: '商品名称', salesQuantity: '销售数量', salesAmount: '销售金额',
      operationsOverview: '待处理概览', operationsCount: '待处理数量', fundExposure: '资金占用概览',
      updatedAt: '更新于 {time}', waitingData: '等待数据', refresh: '刷新',
      myTodos: '我的待办', viewApprovals: '查看审批', noTodos: '暂无待办事项', process: '处理',
      quickActionsTitle: '快捷操作',
      quickPurchaseOrders: '采购订单', quickSalesOrders: '销售订单', quickInventoryStocks: '库存查询',
      quickFinanceVouchers: '财务凭证', quickFinancePayments: '收付款', quickProductionOrders: '生产订单',
      viewAllAlerts: '查看全部 {count} 项', noInventoryAlerts: '暂无库存预警',
      lowStockItem: '商品 {productId} / 仓库 {warehouseId}', shortage: '缺口 {count}',
      failedOperationsTitle: '最近失败操作', failedOperationsShort: '失败操作', viewLogs: '查看日志',
      noFailedOperations: '暂无失败操作', loadFailed: '加载工作台数据失败',
      priority: { high: '紧急', medium: '重要', low: '普通' }
    },
    workflow: {
      businessType: '业务类型', businessNo: '业务单号', status: '状态', selectBusinessType: '请选择业务类型', inputBusinessNo: '请输入业务单号', selectStatus: '请选择状态',
      purchaseOrder: '采购订单', salesOrder: '销售订单', expense: '费用单', pending: '待审批', approved: '已通过', rejected: '已驳回', cancelled: '已取消',
      search: '查询', reset: '重置', tasks: '审批待办', refresh: '刷新', title: '任务标题', createdTime: '创建时间', updatedTime: '更新时间', dueTime: '审批时限', overdue: '已超时', overdueOnly: '仅看超时', actions: '操作',
      view: '查看', approve: '通过', reject: '驳回', transfer: '转签', escalate: '升级', detail: '审批任务详情', businessId: '业务ID', deadline: '审批截止', escalationCount: '升级次数', close: '关闭', approveAction: '审批通过',
      escalationTitle: '超时审批升级', escalateTo: '升级给', selectAssignee: '选择新处理人', escalationComment: '升级说明', confirmEscalation: '确认升级',
      approvalComment: '审批意见', rejectionReason: '驳回原因', inputApprovalComment: '请输入审批意见', inputRejectionReason: '请输入驳回原因',
      transferTitle: '转签任务', transferTo: '转签给', selectUser: '选择用户', comment: '备注', confirmTransfer: '确定转签',
      selectTransferUser: '请选择转签用户', transferSuccess: '转签成功', transferFailed: '转签失败', selectEscalationUser: '请选择升级目标用户', escalationSuccess: '超时审批已升级', approvalSuccess: '审批通过', rejectedSuccess: '已驳回'
    },
    financeAccount: {
      tabs: { receivables: '应收账款', payables: '应付账款' },
      receivableNo: '应收单号', receivableNoPlaceholder: '请输入应收单号', payableNo: '应付单号', payableNoPlaceholder: '请输入应付单号',
      customer: '客户', selectCustomer: '请选择客户', supplier: '供应商', selectSupplier: '请选择供应商', statusLabel: '状态', selectStatus: '请选择状态',
      search: '查询', export: '导出', sourceNo: '来源单号', bizDate: '业务日期', createdTime: '创建时间', updatedTime: '更新时间', actions: '操作', view: '查看',
      receivableAmount: '应收金额', receivedAmount: '已收金额', unreceivedAmount: '未收金额', payableAmount: '应付金额', paidAmount: '已付金额', unpaidAmount: '未付金额',
      status: { unsettled: '未结算', partiallySettled: '部分结算', settled: '已结算', offset: '已冲销' },
      dialog: { receivable: '应收账款详情', payable: '应付账款详情' },
      file: { receivables: '应收账款_{timestamp}.csv', payables: '应付账款_{timestamp}.csv' },
      message: { receivablesLoadFailed: '加载应收账款失败', payablesLoadFailed: '加载应付账款失败', exported: '导出成功', exportFailed: '导出失败', receivableDetailLoadFailed: '加载应收账款详情失败', payableDetailLoadFailed: '加载应付账款详情失败', customersLoadFailed: '加载客户列表失败', suppliersLoadFailed: '加载供应商列表失败' }
    },
    financeAging: {
      asOfDate: '基准日', todayPlaceholder: '默认今天', search: '查询', reset: '重置', receivableTotal: '应收未结总额', payableTotal: '应付未结总额', asOfDateValue: '基准日 {date}', outstandingOnly: '仅统计剩余金额大于 0 的未结单据',
      receivableBuckets: '应收账龄分段', payableBuckets: '应付账龄分段', bucket: '账龄段', count: '笔数', amount: '金额', overdueReceivables: '应收逾期 TOP20', overduePayables: '应付逾期 TOP20', receivablesLedger: '去应收台账', payablesLedger: '去应付台账',
      receivableNo: '应收单号', payableNo: '应付单号', customer: '客户', supplier: '供应商', bizDate: '业务日期', agingDays: '账龄天数', outstandingAmount: '未结金额',
      bucketLabel: { d0_30: '0-30 天', d31_60: '31-60 天', d61_90: '61-90 天', d90Plus: '90 天以上' },
      message: { loadFailed: '加载账龄分析失败' }
    },
    financeGrossMargin: {
      period: '期间', startDate: '开始日期', endDate: '结束日期', search: '查询', salesAmount: '销售额', costApprox: '出库成本', grossMargin: '毛利', marginRate: '毛利率',
      costNotice: '成本取已过账销售发货对应的库存出库流水金额（真实出库成本）。', productCode: '商品编码', productName: '商品名称', salesQuantity: '销售数量', costAmount: '成本',
      message: { selectRange: '请选择日期区间', loadFailed: '加载毛利分析失败' }
    },
    financeStatement: {
      partnerType: '往来类型', customer: '客户', supplier: '供应商', selectPartner: '请选择往来单位', period: '期间', startDate: '开始日期', endDate: '结束日期', search: '查询',
      partnerTypeValue: '（{type}）', periodValue: '{from} 至 {to}', openingValue: '期初：{amount}', increaseValue: '增加：{amount}', decreaseValue: '减少：{amount}', closingValue: '期末：{amount}',
      date: '日期', docType: '单据类型', docNo: '单号', direction: '方向', amount: '金额', balance: '余额', remark: '备注',
      document: { receivable: '应收单', receipt: '收款单', payable: '应付单', payment: '付款单' },
      directionValue: { increase: '增加', decrease: '减少' },
      message: { selectPartnerAndRange: '请选择往来单位和日期区间', loadFailed: '查询对账单失败', optionsLoadFailed: '加载往来单位失败' }
    },
    salesOrder: {
      title: '销售订单', keyword: '关键词', orderNo: '订单号', customer: '客户', orderStatus: '订单状态', approvalStatus: '审批状态', deliveryStatus: '发货状态',
      selectCustomer: '请选择客户', selectStatus: '请选择状态', selectApprovalStatus: '请选择审批状态', search: '查询', reset: '重置', create: '新增订单',
      orderDate: '订单日期', deliveryDate: '交付日期', quantity: '数量', amount: '金额', remark: '备注', actions: '操作', view: '查看', print: '打印', copy: '复制', edit: '编辑', submit: '提交', approve: '通过', reject: '驳回', unapprove: '反审核', cancel: '取消',
      warehouse: '发货仓库', selectWarehouse: '请选择仓库', remarkPlaceholder: '请输入备注', details: '订单明细', addLine: '添加明细', product: '产品', selectProduct: '请选择产品', unitPrice: '单价', taxRate: '税率', delete: '删除', close: '关闭', save: '保存',
      creditPreview: '客户授信预览', creditFormula: '未结应收 + 已审批未发货订单 + 本单含税金额', unlimitedCustomer: '不限额客户', exceededAfterSubmit: '提交后超限', sufficientCredit: '额度充足', creditLimit: '信用额度', unlimited: '不限额', outstandingReceivable: '未结应收', openOrderExposure: '在途订单敞口', currentExposure: '当前敞口', orderTaxAmount: '本单含税金额', availableAfterSubmit: '提交后可用额度',
      expectedExceeded: '预计超限 {amount}', exceededDescription: '当前敞口 {current} + 本单 {order} = {projected}，已超过信用额度 {limit}', noCreditLimit: '该客户未设置授信额度', unlimitedDescription: '当前敞口 {current}，本单可继续提交审批', projectedExposure: '提交后敞口 {amount}', availableDescription: '提交后仍有可用额度 {amount}',
      minimumPrice: '最低 {amount}', customerPrice: '客户价', generalPrice: '通用价',
      status: { draft: '草稿', submitted: '审批中', approved: '已通过', rejected: '已驳回', confirmed: '已确认', cancelled: '已取消', closed: '已关闭', notSubmitted: '未提交', notDelivered: '未发货', partial: '部分发货', delivered: '已发货' },
      dialog: { create: '新增销售订单', edit: '编辑销售订单', copy: '复制销售订单', view: '销售订单详情', copiedFrom: '复制自 {orderNo}' },
      validation: { customer: '请选择客户', warehouse: '请选择发货仓库', orderDate: '请选择订单日期', lineRequired: '请至少维护一条有效订单明细', belowMinimum: '第 {line} 行单价低于最低价 {amount}' },
      message: { loadFailed: '加载销售订单失败', printLoadFailed: '加载打印数据失败', saveFailed: '保存销售订单失败', created: '创建成功', updated: '更新成功', submitConfirm: '确定提交该销售订单吗？', submitted: '提交成功', approveConfirm: '确定审批通过该销售订单吗？', approved: '审批成功', unapproveConfirm: '确定反审核该销售订单吗？', unapproved: '反审核成功', rejectReason: '请输入驳回原因', rejectTitle: '驳回销售订单', rejected: '驳回成功', rejectFailed: '驳回失败', cancelConfirm: '确定取消该销售订单吗？', cancelled: '取消成功', prompt: '提示', actionFailed: '操作失败', optionsLoadFailed: '加载销售订单选项失败' }
    },
    salesDelivery: {
      title: '销售发货', deliveryNo: '发货单号', deliveryNoPlaceholder: '请输入发货单号', salesOrder: '销售订单', salesOrderNo: '销售订单号', orderIdPlaceholder: '请输入订单ID', customer: '客户', selectCustomer: '请选择客户', statusLabel: '状态', selectStatus: '请选择状态', dateRange: '日期范围', rangeSeparator: '至', startDate: '开始日期', endDate: '结束日期', search: '查询', reset: '重置', create: '新增发货',
      warehouse: '发货仓库', deliveryDate: '发货日期', remark: '备注', createdBy: '创建人', createdAt: '创建时间', actions: '操作', view: '查看', print: '打印', edit: '编辑', post: '过账', cancel: '取消', selectOrder: '请选择销售订单', selectWarehouse: '请选择发货仓库', selectDeliveryDate: '请选择发货日期', remarkPlaceholder: '请输入备注', details: '发货明细', clearQuantity: '清零数量', currentQuantity: '本次数量', productCode: '产品编码', productName: '产品名称', orderedQuantity: '订单数量', deliveredQuantity: '已发货数量', currentDeliveryQuantity: '本次发货数量', confirm: '确定',
      status: { draft: '草稿', posted: '已过账', cancelled: '已取消' },
      dialog: { create: '新增销售发货', view: '查看销售发货', edit: '编辑销售发货' },
      validation: { order: '请选择销售订单', warehouse: '请选择发货仓库', date: '请选择发货日期', lineRequired: '请至少添加一条发货明细', quantityRequired: '请输入发货数量' },
      scan: { resetConfirm: '确认清零当前发货数量吗？', title: '扫码计数', reset: '清零', resetDone: '数量已清零', resetFailed: '清零数量失败', selectOrderFirst: '请先选择销售订单', notInOrder: '商品 {code} 不在当前销售订单中', atMaximum: '商品 {code} 已达到可发货数量', lookupFailed: '条码查询失败' },
      message: { loadFailed: '加载数据失败', customersLoadFailed: '加载客户列表失败', warehousesLoadFailed: '加载仓库列表失败', ordersLoadFailed: '加载订单列表失败', printLoadFailed: '加载打印数据失败', detailLoadFailed: '加载详情失败', deliveryLoadFailed: '加载发货单失败', orderDetailLoadFailed: '加载订单详情失败', cancelConfirm: '确认取消此发货单吗？', cancelled: '取消成功', cancelFailed: '取消失败', postConfirm: '确定过账该销售发货单吗？', posted: '过账成功', postFailed: '过账失败', created: '创建成功', updated: '更新成功', createFailed: '创建失败', updateFailed: '更新失败', prompt: '提示' }
    },
    purchaseOrder: {
      title: '采购订单管理', subtitle: '管理采购订单全生命周期，优化供应链效率', totalOrders: '订单总数', pendingApproval: '待审核', approved: '已审核', orderNo: '订单编号', orderNoPlaceholder: '请输入订单编号', supplier: '供应商', selectSupplier: '请选择供应商', maximumPrice: '最高价 {amount}', supplierPrice: '供应商专价', generalPrice: '商品通用价', allSuppliers: '全部供应商', orderStatus: '订单状态', selectStatus: '请选择状态', orderDate: '订单日期', dateRangeSeparator: '至', startDate: '开始日期', endDate: '结束日期', create: '新增订单', expectedArrival: '预计到货', expectedArrivalDate: '预计到货日期', orderAmount: '订单金额', createdBy: '创建人', createdAt: '创建时间', actions: '操作', view: '查看', print: '打印', copy: '复制', edit: '编辑', submit: '提交', approve: '审核', reject: '驳回', unapprove: '反审核', close: '关闭', cancel: '取消', trace: '追踪',
      basicInfo: '基本信息', selectDate: '选择日期', details: '订单明细', addProduct: '添加商品', sequence: '序号', productName: '商品名称', selectProduct: '请选择商品', quantity: '数量', unitPriceCny: '单价（元）', amountCny: '金额（元）', unitPrice: '单价', amount: '金额', remark: '备注', optional: '选填', totalAmount: '订单总金额：', remarkPlaceholder: '请输入备注信息（选填）', confirm: '确定', detailTitle: '采购订单详情', orderInfo: '订单信息', otherInfo: '其他信息', traceTitle: '采购订单追踪', orderedQuantity: '订购数量', receivedQuantity: '已收数量', remainingQuantity: '待收数量', receiptStatus: '收货状态', documentNo: '单据编号', type: '类型', date: '日期', statusLabel: '状态',
      status: { draft: '草稿', submitted: '审批中', approved: '已审核', rejected: '已驳回', closed: '已关闭', cancelled: '已取消' },
      dialog: { create: '新增采购订单', edit: '编辑采购订单', copiedFrom: '复制自 {orderNo}' },
      traceSections: { receipts: '采购收货', returns: '采购退货', payables: '应付账款', payments: '付款记录', vouchers: '财务凭证' },
      validation: { supplier: '请选择供应商', orderDate: '请选择订单日期', details: '请添加订单明细', rejectReason: '请输入驳回原因' },
      message: { loadFailed: '加载数据失败', copyFailed: '复制失败', printLoadFailed: '加载打印数据失败', cancelConfirm: '确认取消订单“{orderNo}”吗？', cancelled: '取消成功', cancelFailed: '取消失败', closeConfirm: '确认关闭订单“{orderNo}”吗？关闭后不能继续收货。', closed: '关闭成功', closeFailed: '关闭失败', traceLoadFailed: '加载采购订单追踪失败', submitConfirm: '确认提交订单“{orderNo}”审批吗？', submitted: '提交成功', submitFailed: '提交失败', approveConfirm: '确认审核通过订单“{orderNo}”吗？', approved: '审核成功', approveFailed: '审核失败', unapproveConfirm: '确认反审核订单“{orderNo}”吗？', unapproved: '反审核成功', unapproveFailed: '反审核失败', rejectTitle: '驳回订单', rejected: '已驳回', actionFailed: '操作失败', exported: '导出成功', exportFailed: '导出失败', exportFile: '采购订单_{timestamp}.csv', updated: '更新成功', created: '创建成功', updateFailed: '更新失败', createFailed: '创建失败', suppliersLoadFailed: '加载供应商失败', productsLoadFailed: '加载商品失败', prompt: '提示' }
    },
    purchaseReceipt: {
      title: '采购收货管理', subtitle: '管理采购收货入库，确保物料按时到位', totalReceipts: '收货总数', pending: '待完成', completed: '已完成', receiptNo: '收货单号', receiptNoPlaceholder: '请输入收货单号', purchaseOrder: '采购订单', purchaseOrderNo: '采购订单号', orderIdPlaceholder: '请输入订单ID', supplier: '供应商', selectSupplier: '请选择供应商', allSuppliers: '全部供应商', statusLabel: '状态', selectStatus: '请选择状态', receiptDate: '收货日期', rangeSeparator: '至', startDate: '开始日期', endDate: '结束日期', create: '新增收货', warehouse: '入库仓库', createdBy: '创建人', createdAt: '创建时间', actions: '操作', view: '查看', print: '打印', edit: '编辑', post: '过账', cancel: '取消', basicInfo: '基本信息', selectOrder: '请选择采购订单', selectDate: '选择日期', selectWarehouse: '请选择仓库', details: '收货明细', clearQuantity: '清零数量', currentQuantity: '本次数量', sequence: '序号', productName: '商品名称', orderedQuantity: '订单数量', actualReceipt: '实际收货', receivedQuantity: '实收数量', remark: '备注', optional: '选填', remarkPlaceholder: '请输入备注信息（选填）', confirm: '确定', detailTitle: '收货单详情', receiptInfo: '收货信息', otherInfo: '其他信息', loadingOrder: '加载采购订单详情...',
      status: { draft: '草稿', posted: '已过账', cancelled: '已取消' },
      dialog: { create: '新增采购收货', edit: '编辑采购收货' },
      validation: { order: '请选择采购订单', warehouse: '请选择入库仓库', date: '请选择收货日期' },
      scan: { resetConfirm: '确认清零当前收货数量吗？', title: '扫码计数', reset: '清零', resetDone: '数量已清零', resetFailed: '清零数量失败', selectOrderFirst: '请先选择采购订单', notInOrder: '商品 {code} 不在当前采购订单中', atMaximum: '商品 {code} 已达到可收货数量', lookupFailed: '条码查询失败' },
      message: { loadFailed: '加载数据失败', noAvailableOrders: '暂无可收货的采购订单', ordersLoadFailed: '加载订单失败', receiptLoadFailed: '加载收货单失败', printLoadFailed: '加载打印数据失败', missingOrderId: '缺少采购订单ID', orderDetailLoadFailed: '加载采购订单详情失败', postConfirm: '确认过账收货单“{receiptNo}”吗？过账后将入库并生成应付。', posted: '过账成功', postFailed: '过账失败', cancelConfirm: '确认取消收货单“{receiptNo}”吗？', cancelled: '已取消', cancelFailed: '取消失败', exported: '导出成功', exportFailed: '导出失败', exportFile: '采购收货_{timestamp}.csv', updated: '更新成功', created: '创建成功', updateFailed: '更新失败', createFailed: '创建失败', warehousesLoadFailed: '加载仓库失败', prompt: '提示' }
    },
    purchaseReturn: {
      title: '采购退货管理', subtitle: '处理采购退货，确保质量问题及时反馈', totalReturns: '退货总数', pending: '待过账', completed: '已过账', returnNo: '退货单号', returnNoPlaceholder: '请输入退货单号', receipt: '采购收货单', receiptIdPlaceholder: '请输入收货ID', receiptNo: '采购收货单号', sourceOrder: '来源订单', warehouse: '退货仓库', returnDate: '退货日期', rangeSeparator: '至', startDate: '开始日期', endDate: '结束日期', statusLabel: '状态', selectStatus: '请选择状态', create: '新增退货', createdBy: '创建人', createdAt: '创建时间', actions: '操作', view: '查看', edit: '编辑', post: '过账', cancel: '取消', basicInfo: '基本信息', selectReceipt: '请选择采购收货单', selectDate: '选择日期', details: '退货明细', sequence: '序号', productName: '商品名称', receiptQuantity: '收货数量', availableQuantity: '可退数量', actualReturn: '实际退货', remark: '备注', optional: '选填', remarkPlaceholder: '请输入备注信息（选填）', confirm: '确定', detailTitle: '退货单详情', returnInfo: '退货信息', otherInfo: '其他信息', linkedReceiptTitle: '采购收货单详情', loadingReceipt: '加载采购收货单详情...',
      status: { draft: '草稿', posted: '已过账', cancelled: '已取消' },
      dialog: { create: '新增采购退货', edit: '编辑采购退货' },
      validation: { receipt: '请选择采购收货单', date: '请选择退货日期' },
      message: { loadFailed: '加载数据失败', noAvailableReceipts: '暂无可退货的采购收货单', ordersLoadFailed: '加载订单失败', returnLoadFailed: '加载退货单失败', detailLoadFailed: '加载采购退货详情失败', missingReceiptId: '缺少采购收货单ID', receiptDetailLoadFailed: '加载采购收货单详情失败', postConfirm: '确认过账此采购退货单吗？过账后将扣减库存并冲减应付。', posted: '退货已过账', postFailed: '过账失败', cancelConfirm: '确认取消退货单“{returnNo}”吗？', cancelled: '已取消', cancelFailed: '取消失败', exported: '导出成功', exportFailed: '导出失败', exportFile: '采购退货_{timestamp}.csv', updated: '更新成功', created: '创建成功', updateFailed: '更新失败', createFailed: '创建失败', prompt: '提示' }
    },
    qcInspection: {
      inspectionNo: '检验单号', statusLabel: '状态', all: '全部', typeLabel: '类型', search: '查询', reset: '重置', create: '新建检验单', export: '导出', sourceDocument: '来源单', inspectionDate: '检验日期', inspectedQuantity: '检验数量', qualifiedQuantity: '合格数量', unqualifiedQuantity: '不合格数量', actions: '操作', detail: '详情', edit: '编辑', submit: '提交', judge: '判定', cancelInspection: '作废', inspectionType: '检验类型', purchaseReceipt: '采购入库单', salesDelivery: '销售出库单', productionOrderId: '生产工单ID', selectDraftReceipt: '选择草稿状态的采购入库单', selectDraftDelivery: '选择草稿状态的销售出库单', productionOrderPlaceholder: '已下达/已领料的生产工单ID', sourceOption: '{no}（数量 {quantity}）', remark: '备注', cancel: '取消', confirm: '确定', save: '保存', editHint: '检验单号：{no}。来源单据不可改；仅草稿可编辑检验日期、备注与行检验数量', line: '行', productId: '商品ID', defectReason: '不合格原因', lineRemark: '行备注', optional: '选填', judgeHint: '检验单号：{no}。合格+不合格数量之和须等于检验数量', confirmJudge: '确认判定', qualifiedUnqualified: '合格 / 不合格', sourceInbound: '入库 {id}', sourceOutbound: '出库 {id}', sourceProduction: '工单 {id}',
      type: { iqc: '来料 IQC', oqc: '出库 OQC', ipqc: '过程 IPQC' },
      status: { draft: '草稿', submitted: '已提交', judged: '已判定', cancelled: '已作废' },
      dialog: { create: '新建检验单', editIqc: '编辑来料检验单', editOqc: '编辑出库检验单', editIpqc: '编辑过程检验单', judge: '判定检验单', detail: '检验单详情' },
      validation: { delivery: '请选择销售出库单', receipt: '请选择采购入库单', productionOrder: '请输入生产工单ID', date: '请选择检验日期', draftOnly: '仅草稿状态可编辑', editableMissing: '未加载到可编辑的检验单', negativeQuantity: '第 {line} 行：检验数量不能为负数', judgeQuantity: '第 {line} 行：合格数量 + 不合格数量必须等于检验数量' },
      message: { loadFailed: '加载检验单失败', sourcesLoadFailed: '加载来源单据失败', detailLoadFailed: '加载检验单详情失败', created: '创建成功', submitConfirm: '确认提交检验单“{no}”吗？', submitted: '已提交', saved: '保存成功', judged: '判定完成', cancelConfirm: '确认作废检验单“{no}”吗？', cancelled: '已作废', exported: '导出成功', exportFailed: '导出失败', exportFile: 'quality_inspections_{date}.csv', prompt: '提示' }
    },
    purchaseRequisition: {
      keyword: '单号', status: '状态', search: '查询', create: '新建请购', no: '请购单号', date: '请购日期', neededDate: '需求日期', supplier: '供应商', convertedPo: '采购订单', remark: '备注', actions: '操作', view: '查看', edit: '编辑', submit: '提交', approve: '审批', reject: '驳回', convert: '转采购订单', cancel: '作废', editTitle: '编辑请购', createTitle: '新建请购', lines: '明细', addLine: '加行', product: '商品', qty: '数量', delete: '删', close: '关闭', save: '保存',
      validation: { required: '请填写日期和明细' },
      message: { created: '创建成功', saved: '保存成功', done: '操作成功' }
    },
    purchaseRequisition: {
      keyword: 'No.', status: 'Status', search: 'Search', create: 'New requisition', no: 'Requisition no.', date: 'Date', neededDate: 'Needed date', supplier: 'Supplier', convertedPo: 'PO', remark: 'Remark', actions: 'Actions', view: 'View', edit: 'Edit', submit: 'Submit', approve: 'Approve', reject: 'Reject', convert: 'Convert to PO', cancel: 'Cancel', editTitle: 'Edit requisition', createTitle: 'New requisition', lines: 'Lines', addLine: 'Add line', product: 'Product', qty: 'Qty', delete: 'Del', close: 'Close', save: 'Save',
      validation: { required: 'Date and lines are required' },
      message: { created: 'Created', saved: 'Saved', done: 'Done' }
    },
    inventorySerial: {
      keyword: '关键字', status: '状态', search: '查询', create: '登记序列号', serialNo: '序列号', productCode: '商品编码', productName: '商品名称', inboundBizNo: '入库单号', outboundBizNo: '出库单号', actions: '操作', issue: '出库', scrap: '报废', createTitle: '登记序列号', product: '商品', cancel: '取消', save: '保存',
      validation: { required: '请填写商品和序列号' },
      message: { created: '登记成功', issued: '已出库', scrapped: '已报废' }
    },
    inventorySerial: {
      keyword: 'Keyword', status: 'Status', search: 'Search', create: 'Register serial', serialNo: 'Serial no.', productCode: 'Product code', productName: 'Product name', inboundBizNo: 'Inbound no.', outboundBizNo: 'Outbound no.', actions: 'Actions', issue: 'Issue', scrap: 'Scrap', createTitle: 'Register serial', product: 'Product', cancel: 'Cancel', save: 'Save',
      validation: { required: 'Product and serial no. are required' },
      message: { created: 'Created', issued: 'Issued', scrapped: 'Scrapped' }
    },
    warehouseLocation: {
      warehouse: '仓库', selectWarehouse: '选择仓库', keyword: '关键字', keywordPlaceholder: '库位编码/名称', status: '状态', search: '查询', create: '新建库位',
      code: '库位编码', name: '库位名称', default: '默认', yes: '是', no: '否', active: '启用', inactive: '停用', remark: '备注', actions: '操作', edit: '编辑', enable: '启用', disable: '停用',
      editTitle: '编辑库位', createTitle: '新建库位', cancel: '取消', save: '保存',
      validation: { required: '请完整填写仓库、编码和名称' },
      message: { saved: '保存成功', created: '创建成功', enabled: '已启用', disabled: '已停用' }
    },
    warehouseLocation: {
      warehouse: 'Warehouse', selectWarehouse: 'Select warehouse', keyword: 'Keyword', keywordPlaceholder: 'Location code / name', status: 'Status', search: 'Search', create: 'New location',
      code: 'Location code', name: 'Location name', default: 'Default', yes: 'Yes', no: 'No', active: 'Active', inactive: 'Inactive', remark: 'Remark', actions: 'Actions', edit: 'Edit', enable: 'Enable', disable: 'Disable',
      editTitle: 'Edit location', createTitle: 'New location', cancel: 'Cancel', save: 'Save',
      validation: { required: 'Warehouse, code and name are required' },
      message: { saved: 'Saved', created: 'Created', enabled: 'Enabled', disabled: 'Disabled' }
    },
    inventoryMrp: {
      title: '轻量 MRP', history: '刷新历史', historyTitle: 'MRP运行历史', runNo: '运行号', asOfDate: '业务日', status: '状态', purchaseCount: '采购建议数', productionCount: '生产建议数', createdTime: '创建时间', open: '打开', lineStatus: '行状态', convertedDoc: '已转单据', actions: '操作', convertPo: '转采购', convertMo: '转生产', description: '独立需求=销售未发货+安全库存；供应=现存量+在途采购+在制；有 BOM 建议生产并展开材料采购', run: '运行计划',
      summary: '运行日 {date} · 采购建议 {purchaseCount} · 生产建议 {productionCount}', productionSuggestions: '生产建议', purchaseSuggestions: '采购建议',
      productCode: '编码', productName: '品名', demandQty: '需求', onHandQty: '现存量', openSupplyQty: '在途/在制', netQty: '净需求', reason: '原因',
      message: { succeeded: 'MRP 运行完成', failed: 'MRP 运行失败', loadFailed: '加载计划失败', convertFailed: '转单失败', convertedPo: '已转采购订单 {orderNo}', convertedMo: '已转生产订单 {orderNo}' }
    },
    inventoryReplenishment: {
      suggestionNo: '建议编号', suggestionNoPlaceholder: '请输入建议编号', statusLabel: '状态', all: '全部', warehouse: '仓库', selectWarehouse: '请选择仓库', product: '产品', selectProduct: '请选择产品', supplier: '供应商', selectSupplier: '请选择供应商', createdAt: '创建时间', rangeSeparator: '至', startTime: '开始时间', endTime: '结束时间', search: '查询', reset: '重置',
      fulfillmentStatus: '履约状态', productCode: '产品编码', productName: '产品名称', suggestedQty: '建议数量', shortageSnapshot: '缺口快照', expectedArrival: '预计到货', purchaseOrder: '采购订单', remark: '备注', actions: '操作', edit: '编辑', convert: '转采购订单', cancel: '取消', editTitle: '编辑补货建议', expectedArrivalPlaceholder: '请选择预计到货日期', save: '保存',
      status: { draft: '草稿', converted: '已转单', cancelled: '已取消' },
      fulfillment: { suggested: '待转采购', purchaseCreated: '已生成采购', partialReceived: '部分到货', replenished: '已补足', purchaseClosed: '采购关闭', cancelled: '已取消' },
      validation: { quantityRequired: '请输入建议数量', quantityPositive: '建议数量必须大于 0' },
      message: { loadFailed: '加载补货建议失败', updated: '补货建议已更新', saveFailed: '保存补货建议失败', cancelConfirm: '确认取消补货建议 {no} 吗？', cancelTitle: '取消补货建议', cancelReason: '取消原因（选填）', confirm: '确定', cancelled: '已取消', cancelFailed: '取消失败', supplierRequired: '请先为补货建议选择供应商', convertConfirm: '确认将补货建议 {no} 转为采购订单吗？', convertTitle: '转采购订单', converted: '已生成采购订单 {no}', convertFailed: '转采购订单失败', optionsLoadFailed: '加载筛选选项失败' }
    },
    productionRouting: {
      title: '工艺路线', keyword: '关键字', keywordPlaceholder: '工艺路线编码/名称', statusLabel: '状态', all: '全部', search: '查询', reset: '重置', create: '新增工艺路线', code: '编码', name: '名称', bom: 'BOM', operationCount: '工序数', remark: '备注', actions: '操作', view: '查看', edit: '编辑', enable: '启用', disable: '停用', routingCode: '路线编码', routingName: '路线名称', codePlaceholder: '请输入工艺路线编码', namePlaceholder: '请输入工艺路线名称', selectBom: '请选择BOM', operationList: '工序清单', operationDetails: '工序明细', sequence: '序', operationCode: '工序编码', operationName: '工序名称', workCenter: '工作中心', selectWorkCenter: '选择工作中心', standardMinutes: '标准工时(分)', optional: '选填', delete: '删除', addOperation: '添加工序', remarkPlaceholder: '请输入备注', cancel: '取消', save: '保存', close: '关闭', detailTitle: '工艺路线详情',
      status: { active: '启用', disabled: '已停用' },
      dialog: { create: '新增工艺路线', edit: '编辑工艺路线' },
      validation: { code: '请输入工艺路线编码', name: '请输入工艺路线名称', bom: '请选择BOM', operations: '请至少添加一道工序', operationRequired: '第 {line} 道工序：编码、名称、工作中心和标准工时均必填' },
      message: { optionsLoadFailed: '加载工作中心/BOM选项失败', loadFailed: '加载工艺路线失败', detailLoadFailed: '加载工艺路线详情失败', enableConfirm: '确认启用工艺路线“{name}”吗？', disableConfirm: '确认停用工艺路线“{name}”吗？', enabled: '已启用', disabled: '已停用', updated: '更新成功', created: '创建成功', prompt: '提示' }
    },
    productionWorkCenter: {
      title: '工作中心', keyword: '关键字', keywordPlaceholder: '工作中心编码/名称', statusLabel: '状态', all: '全部', search: '查询', reset: '重置', create: '新增工作中心', code: '编码', name: '名称', remark: '备注', actions: '操作', edit: '编辑', enable: '启用', disable: '停用', codePlaceholder: '请输入工作中心编码', namePlaceholder: '请输入工作中心名称', remarkPlaceholder: '请输入备注', cancel: '取消', save: '保存',
      status: { active: '启用', disabled: '已停用' },
      dialog: { create: '新增工作中心', edit: '编辑工作中心' },
      validation: { code: '请输入工作中心编码', name: '请输入工作中心名称' },
      message: { loadFailed: '加载工作中心失败', enableConfirm: '确认启用工作中心“{name}”吗？', disableConfirm: '确认停用工作中心“{name}”吗？', enabled: '已启用', disabled: '已停用', updated: '更新成功', created: '创建成功', prompt: '提示' }
    },
    productionBom: {
      title: 'BOM管理', bomCode: 'BOM编码', bomCodePlaceholder: '请输入BOM编码', product: '产品', selectProduct: '请选择产品', statusLabel: '状态', select: '请选择', search: '查询', reset: '重置', create: '新增BOM', baseQuantity: '基准数量', remark: '备注', createdAt: '创建时间', createdBy: '创建人', actions: '操作', view: '查看', edit: '编辑', materialList: '物料清单', materialDetails: '物料明细', material: '物料', selectMaterial: '请选择物料', quantity: '用量', scrapRatePercent: '损耗率(%)', scrapRate: '损耗率', delete: '删除', addMaterial: '添加物料', remarkPlaceholder: '请输入备注', cancel: '取消', save: '保存', close: '关闭', detailTitle: 'BOM详情', productFallback: '产品{id}',
      status: { active: '启用', disabled: '已停用' },
      dialog: { create: '新增BOM', edit: '编辑BOM' },
      validation: { product: '请选择产品', baseQuantity: '请输入基准数量', materials: '请添加物料明细' },
      message: { productsLoadFailed: '加载产品列表失败', loadFailed: '加载数据失败', listLoadFailed: '加载BOM列表失败', detailLoadFailed: '加载BOM详情失败', updated: '更新成功', created: '创建成功', actionFailed: '操作失败' }
    },
    productionOrder: {
      title: '生产订单管理', orderNo: '订单号', orderNoPlaceholder: '请输入订单号', product: '产品', selectProduct: '请选择产品', statusLabel: '状态', select: '请选择', priorityLabel: '优先级', search: '查询', reset: '重置', create: '新增订单', productCode: '产品编码', productName: '产品名称', plannedQuantity: '计划数量', completedQuantity: '完成数量', completionRate: '完成率', materialWarehouse: '材料出库仓', finishedWarehouse: '成品入库仓', plannedStart: '计划开始', plannedEnd: '计划结束', actions: '操作', view: '查看', edit: '编辑', release: '下达', issue: '领料', operationReport: '工序报工', complete: '完工', reverse: '红冲', returnMaterials: '退料', cancel: '取消', bom: 'BOM', bomCode: 'BOM编码', selectBom: '请选择BOM', selectMaterialWarehouse: '请选择材料出库仓', selectFinishedWarehouse: '请选择成品入库仓', plannedStartDate: '计划开始日期', plannedEndDate: '计划结束日期', selectDate: '请选择日期', remark: '备注', remarkPlaceholder: '请输入备注', save: '保存', close: '关闭', detailTitle: '生产订单详情', scrapQuantity: '报废数量', actualStart: '实际开始', actualEnd: '实际结束', createdBy: '创建人', createdAt: '创建时间', materialUsage: '物料使用情况', materialCode: '物料编码', materialName: '物料名称', requiredQuantity: '需求数量', issuedQuantity: '已领数量', returnedQuantity: '已退数量', unit: '单位', completionTitle: '生产完工', completionQuantity: '完工数量', maxCompletion: '最大可完工: {quantity}', completionDate: '完工日期', confirmCompletion: '确定完工', reversalTitle: '完工红冲', reversalQuantity: '红冲数量', maxReversal: '最大可红冲: {quantity}', reversalDate: '红冲日期', confirmReversal: '确定红冲', materialReturnTitle: '生产退料', returnDate: '退料日期', returnableQuantity: '可退数量', currentReturn: '本次退料', lotNo: '批次号', confirmReturn: '确定退料', operationsTitle: '工序报工 · {orderNo}', operationsHelp: '若工单 BOM 绑定了启用中的工艺路线，下达后会自动生成工序。无工序时完工不校验；有工序时须全部报工完成且合格量足够。', sequence: '序号', operationCode: '工序码', operationName: '工序名称', workCenter: '工作中心', planned: '计划', reported: '已报', qualified: '合格', scrap: '报废', report: '报工', noOperations: '暂无工序（未绑定工艺路线或下达前无快照）', refresh: '刷新', reportQuantity: '报工数量', qualifiedQuantity: '合格数量', confirmReport: '确认报工', baseQuantity: '基准数量 {quantity}', warehouseFallback: '仓库 {id}', issueRemark: '生产订单页确认领料',
      status: { draft: '草稿', released: '已下达', materialIssued: '已领料', inProgress: '生产中', completed: '已完成', cancelled: '已取消' },
      priority: { low: '低', normal: '普通', high: '高', urgent: '紧急' },
      operationStatus: { pending: '待报工', inProgress: '进行中', done: '已完成' },
      dialog: { create: '新增生产订单', edit: '编辑生产订单' },
      validation: { product: '请选择产品', bom: '请选择BOM', quantity: '请输入计划数量', materialWarehouse: '请选择材料出库仓', finishedWarehouse: '请选择成品入库仓', startDate: '请选择开始日期', endDate: '请选择结束日期', completedQuantity: '请输入完工数量', reversalQuantity: '请输入红冲数量', returnQuantity: '请输入本次退料数量', qualifiedExceedsReported: '合格数量不能大于报工数量' },
      message: { optionsLoadFailed: '加载选项数据失败', loadFailed: '加载数据失败', orderLoadFailed: '加载生产订单失败', detailLoadFailed: '加载订单详情失败', releaseConfirm: '确定要下达生产订单“{orderNo}”吗？', released: '下达成功', releaseFailed: '下达失败', issueConfirm: '确定按剩余需求领料生产订单“{orderNo}”吗？', issued: '领料成功', issueFailed: '领料失败', operationsLoadFailed: '加载工序失败', reported: '报工成功', completed: '完工成功', completeFailed: '完工失败', reversed: '红冲成功', reverseFailed: '红冲失败', noReturnableMaterials: '当前工单没有可退物料', returnableLoadFailed: '加载可退物料失败', returned: '退料成功', returnFailed: '退料失败', cancelConfirm: '确定要取消生产订单“{orderNo}”吗？', cancelled: '取消成功', cancelFailed: '取消失败', updated: '更新成功', created: '创建成功', updateFailed: '更新失败', createFailed: '创建失败', prompt: '提示' }
    }
  },
  'en-US': {
    common: { cancel: 'Cancel', save: 'Save', confirm: 'Confirm' },
    app: { name: 'ERP Management System', shortName: 'ERP System' },
    settings: {
      title: 'Language & Time Zone', language: 'Language', timezone: 'Display time zone',
      zhCN: '简体中文', enUS: 'English', shanghai: 'China Standard Time', utc: 'UTC',
      newYork: 'New York', london: 'London'
    },
    user: {
      profile: 'Profile', password: 'Change password', logout: 'Sign out', logoutConfirm: 'Are you sure you want to sign out?',
      saved: 'Profile saved', saveFailed: 'Failed to save profile', preferencesSaveFailed: 'Failed to save language and time-zone preferences', passwordChanged: 'Password changed. Please sign in again.'
    },
    login: {
      welcome: 'Welcome back', subtitle: 'Enter your username and password', username: 'Username', password: 'Password',
      forgot: 'Forgot password?', passwordResetHint: 'Contact your system administrator to reset your password', submit: 'Sign in', submitting: 'Signing in...', testAccount: 'Local test account',
      prefilledTestAccount: 'admin / LocalAdmin123 (prefilled)',
      feature1Title: 'Integrated operations', feature1Desc: 'Purchasing, sales, inventory, and finance in one place',
      feature2Title: 'Data analytics', feature2Desc: 'Real-time reports for better decisions',
      feature3Title: 'Approval workflows', feature3Desc: 'Multi-level approvals with fine-grained access control',
      usernameRequired: 'Username is required', passwordRequired: 'Password is required', passwordMin: 'Password must contain at least 6 characters'
    },
    dashboard: {
      welcome: 'Welcome back, {name}', user: 'User', source: 'Dashboard data is from the current account set',
      onboarding: 'Getting started: master data → sales/purchase orders → inventory → payments; explore aging, MRP, quotes, and inquiries',
      newPurchase: 'New purchase', newSales: 'New sale', stockQuery: 'Stock lookup',
      todayPurchaseOrders: "Today's purchase orders", todaySalesAmount: "Today's sales", stockAlerts: 'Stock alerts',
      pendingApprovals: 'Pending approvals', overdueApprovals: '{count} overdue', openReceivables: 'Open receivables', openPayables: 'Open payables',
      today: 'Today', attention: 'Attention', pending: 'Pending', receivableAging: 'Receivable aging', payableAging: 'Payable aging',
      details: 'Details', bucket: 'Aging bucket', count: 'Count', amount: 'Amount', total: 'Total', overdueTop: 'Overdue TOP',
      topSkus: 'Top 5 products', last30Days: 'Posted deliveries in the last 30 days', rank: 'Rank', productCode: 'Product code', productName: 'Product name', salesQuantity: 'Quantity sold', salesAmount: 'Sales amount',
      operationsOverview: 'Pending work overview', operationsCount: 'Work items', fundExposure: 'Cash exposure overview',
      updatedAt: 'Updated {time}', waitingData: 'Waiting for data', refresh: 'Refresh',
      myTodos: 'My tasks', viewApprovals: 'View approvals', noTodos: 'No pending items', process: 'Handle',
      quickActionsTitle: 'Quick actions',
      quickPurchaseOrders: 'Purchase orders', quickSalesOrders: 'Sales orders', quickInventoryStocks: 'Stock lookup',
      quickFinanceVouchers: 'Financial vouchers', quickFinancePayments: 'Receipts & payments', quickProductionOrders: 'Production orders',
      viewAllAlerts: 'View all {count}', noInventoryAlerts: 'No stock alerts',
      lowStockItem: 'Product {productId} / Warehouse {warehouseId}', shortage: 'Shortage {count}',
      failedOperationsTitle: 'Recent failed operations', failedOperationsShort: 'Failed ops', viewLogs: 'View logs',
      noFailedOperations: 'No failed operations', loadFailed: 'Failed to load dashboard data',
      priority: { high: 'High', medium: 'Medium', low: 'Normal' }
    },
    workflow: {
      businessType: 'Business type', businessNo: 'Document no.', status: 'Status', selectBusinessType: 'Select business type', inputBusinessNo: 'Enter document no.', selectStatus: 'Select status',
      purchaseOrder: 'Purchase order', salesOrder: 'Sales order', expense: 'Expense', pending: 'Pending', approved: 'Approved', rejected: 'Rejected', cancelled: 'Cancelled',
      search: 'Search', reset: 'Reset', tasks: 'Approval tasks', refresh: 'Refresh', title: 'Task title', createdTime: 'Created', updatedTime: 'Updated', dueTime: 'Due', overdue: 'Overdue', overdueOnly: 'Overdue only', actions: 'Actions',
      view: 'View', approve: 'Approve', reject: 'Reject', transfer: 'Transfer', escalate: 'Escalate', detail: 'Approval task details', businessId: 'Business ID', deadline: 'Deadline', escalationCount: 'Escalations', close: 'Close', approveAction: 'Approve',
      escalationTitle: 'Escalate overdue task', escalateTo: 'Escalate to', selectAssignee: 'Select a new assignee', escalationComment: 'Reason', confirmEscalation: 'Confirm escalation',
      approvalComment: 'Approval comment', rejectionReason: 'Rejection reason', inputApprovalComment: 'Enter an approval comment', inputRejectionReason: 'Enter a rejection reason',
      transferTitle: 'Transfer task', transferTo: 'Transfer to', selectUser: 'Select a user', comment: 'Comment', confirmTransfer: 'Confirm transfer',
      selectTransferUser: 'Select a transfer user', transferSuccess: 'Task transferred', transferFailed: 'Failed to transfer task', selectEscalationUser: 'Select an escalation target', escalationSuccess: 'Overdue task escalated', approvalSuccess: 'Task approved', rejectedSuccess: 'Task rejected'
    },
    financeAccount: {
      tabs: { receivables: 'Receivables', payables: 'Payables' },
      receivableNo: 'Receivable no.', receivableNoPlaceholder: 'Enter a receivable no.', payableNo: 'Payable no.', payableNoPlaceholder: 'Enter a payable no.',
      customer: 'Customer', selectCustomer: 'Select a customer', supplier: 'Supplier', selectSupplier: 'Select a supplier', statusLabel: 'Status', selectStatus: 'Select a status',
      search: 'Search', export: 'Export', sourceNo: 'Source no.', bizDate: 'Business date', createdTime: 'Created', updatedTime: 'Updated', actions: 'Actions', view: 'View',
      receivableAmount: 'Receivable amount', receivedAmount: 'Received amount', unreceivedAmount: 'Unreceived amount', payableAmount: 'Payable amount', paidAmount: 'Paid amount', unpaidAmount: 'Unpaid amount',
      status: { unsettled: 'Unsettled', partiallySettled: 'Partially settled', settled: 'Settled', offset: 'Offset' },
      dialog: { receivable: 'Receivable details', payable: 'Payable details' },
      file: { receivables: 'receivables_{timestamp}.csv', payables: 'payables_{timestamp}.csv' },
      message: { receivablesLoadFailed: 'Failed to load receivables', payablesLoadFailed: 'Failed to load payables', exported: 'Export complete', exportFailed: 'Export failed', receivableDetailLoadFailed: 'Failed to load receivable details', payableDetailLoadFailed: 'Failed to load payable details', customersLoadFailed: 'Failed to load customers', suppliersLoadFailed: 'Failed to load suppliers' }
    },
    financeAging: {
      asOfDate: 'As-of date', todayPlaceholder: 'Defaults to today', search: 'Search', reset: 'Reset', receivableTotal: 'Open receivables', payableTotal: 'Open payables', asOfDateValue: 'As of {date}', outstandingOnly: 'Includes only open documents with a remaining amount greater than 0',
      receivableBuckets: 'Receivable aging buckets', payableBuckets: 'Payable aging buckets', bucket: 'Aging bucket', count: 'Count', amount: 'Amount', overdueReceivables: 'Top 20 overdue receivables', overduePayables: 'Top 20 overdue payables', receivablesLedger: 'Open receivables', payablesLedger: 'Open payables',
      receivableNo: 'Receivable no.', payableNo: 'Payable no.', customer: 'Customer', supplier: 'Supplier', bizDate: 'Business date', agingDays: 'Aging days', outstandingAmount: 'Outstanding amount',
      bucketLabel: { d0_30: '0-30 days', d31_60: '31-60 days', d61_90: '61-90 days', d90Plus: 'Over 90 days' },
      message: { loadFailed: 'Failed to load aging analysis' }
    },
    financeGrossMargin: {
      period: 'Period', startDate: 'Start date', endDate: 'End date', search: 'Search', salesAmount: 'Sales amount', costApprox: 'Outbound cost', grossMargin: 'Gross margin', marginRate: 'Gross margin rate',
      costNotice: 'Cost uses inventory outbound transaction amounts from posted sales deliveries.', productCode: 'Product code', productName: 'Product name', salesQuantity: 'Sales quantity', costAmount: 'Cost',
      message: { selectRange: 'Select a date range', loadFailed: 'Failed to load gross margin analysis' }
    },
    financeStatement: {
      partnerType: 'Partner type', customer: 'Customer', supplier: 'Supplier', selectPartner: 'Select a partner', period: 'Period', startDate: 'Start date', endDate: 'End date', search: 'Search',
      partnerTypeValue: '({type})', periodValue: '{from} to {to}', openingValue: 'Opening: {amount}', increaseValue: 'Increase: {amount}', decreaseValue: 'Decrease: {amount}', closingValue: 'Closing: {amount}',
      date: 'Date', docType: 'Document type', docNo: 'Document no.', direction: 'Direction', amount: 'Amount', balance: 'Balance', remark: 'Remark',
      document: { receivable: 'Receivable', receipt: 'Receipt', payable: 'Payable', payment: 'Payment' },
      directionValue: { increase: 'Increase', decrease: 'Decrease' },
      message: { selectPartnerAndRange: 'Select a partner and date range', loadFailed: 'Failed to load the statement', optionsLoadFailed: 'Failed to load partners' }
    },
    salesOrder: {
      title: 'Sales orders', keyword: 'Keyword', orderNo: 'Order no.', customer: 'Customer', orderStatus: 'Order status', approvalStatus: 'Approval status', deliveryStatus: 'Delivery status',
      selectCustomer: 'Select a customer', selectStatus: 'Select a status', selectApprovalStatus: 'Select an approval status', search: 'Search', reset: 'Reset', create: 'New order',
      orderDate: 'Order date', deliveryDate: 'Delivery date', quantity: 'Quantity', amount: 'Amount', remark: 'Remark', actions: 'Actions', view: 'View', print: 'Print', copy: 'Copy', edit: 'Edit', submit: 'Submit', approve: 'Approve', reject: 'Reject', unapprove: 'Unapprove', cancel: 'Cancel',
      warehouse: 'Delivery warehouse', selectWarehouse: 'Select a warehouse', remarkPlaceholder: 'Enter a remark', details: 'Order lines', addLine: 'Add line', product: 'Product', selectProduct: 'Select a product', unitPrice: 'Unit price', taxRate: 'Tax rate', delete: 'Delete', close: 'Close', save: 'Save',
      creditPreview: 'Customer credit preview', creditFormula: 'Open receivables + approved undelivered orders + this order including tax', unlimitedCustomer: 'Unlimited customer', exceededAfterSubmit: 'Over limit after submission', sufficientCredit: 'Credit available', creditLimit: 'Credit limit', unlimited: 'Unlimited', outstandingReceivable: 'Open receivables', openOrderExposure: 'Open order exposure', currentExposure: 'Current exposure', orderTaxAmount: 'This order incl. tax', availableAfterSubmit: 'Available after submission',
      expectedExceeded: 'Expected overage {amount}', exceededDescription: 'Current exposure {current} + this order {order} = {projected}, exceeding the credit limit {limit}', noCreditLimit: 'No credit limit is set for this customer', unlimitedDescription: 'Current exposure is {current}; this order can be submitted', projectedExposure: 'Exposure after submission: {amount}', availableDescription: 'Available credit after submission: {amount}',
      minimumPrice: 'Minimum {amount}', customerPrice: 'Customer price', generalPrice: 'General price',
      status: { draft: 'Draft', submitted: 'In approval', approved: 'Approved', rejected: 'Rejected', confirmed: 'Confirmed', cancelled: 'Cancelled', closed: 'Closed', notSubmitted: 'Not submitted', notDelivered: 'Not delivered', partial: 'Partially delivered', delivered: 'Delivered' },
      dialog: { create: 'New sales order', edit: 'Edit sales order', copy: 'Copy sales order', view: 'Sales order details', copiedFrom: 'Copied from {orderNo}' },
      validation: { customer: 'Select a customer', warehouse: 'Select a delivery warehouse', orderDate: 'Select an order date', lineRequired: 'Add at least one valid order line', belowMinimum: 'Line {line} price is below the minimum {amount}' },
      message: { loadFailed: 'Failed to load sales orders', printLoadFailed: 'Failed to load print data', saveFailed: 'Failed to save the sales order', created: 'Sales order created', updated: 'Sales order updated', submitConfirm: 'Submit this sales order?', submitted: 'Sales order submitted', approveConfirm: 'Approve this sales order?', approved: 'Sales order approved', unapproveConfirm: 'Unapprove this sales order?', unapproved: 'Sales order unapproved', rejectReason: 'Enter a rejection reason', rejectTitle: 'Reject sales order', rejected: 'Sales order rejected', rejectFailed: 'Failed to reject the sales order', cancelConfirm: 'Cancel this sales order?', cancelled: 'Sales order cancelled', prompt: 'Confirmation', actionFailed: 'Operation failed', optionsLoadFailed: 'Failed to load sales order options' }
    },
    salesDelivery: {
      title: 'Sales deliveries', deliveryNo: 'Delivery no.', deliveryNoPlaceholder: 'Enter a delivery no.', salesOrder: 'Sales order', salesOrderNo: 'Sales order no.', orderIdPlaceholder: 'Enter an order ID', customer: 'Customer', selectCustomer: 'Select a customer', statusLabel: 'Status', selectStatus: 'Select a status', dateRange: 'Date range', rangeSeparator: 'to', startDate: 'Start date', endDate: 'End date', search: 'Search', reset: 'Reset', create: 'New delivery',
      warehouse: 'Delivery warehouse', deliveryDate: 'Delivery date', remark: 'Remark', createdBy: 'Created by', createdAt: 'Created', actions: 'Actions', view: 'View', print: 'Print', edit: 'Edit', post: 'Post', cancel: 'Cancel', selectOrder: 'Select a sales order', selectWarehouse: 'Select a delivery warehouse', selectDeliveryDate: 'Select a delivery date', remarkPlaceholder: 'Enter a remark', details: 'Delivery lines', clearQuantity: 'Clear quantities', currentQuantity: 'Current quantity', productCode: 'Product code', productName: 'Product name', orderedQuantity: 'Ordered quantity', deliveredQuantity: 'Delivered quantity', currentDeliveryQuantity: 'Delivery quantity', confirm: 'Confirm',
      status: { draft: 'Draft', posted: 'Posted', cancelled: 'Cancelled' },
      dialog: { create: 'New sales delivery', view: 'View sales delivery', edit: 'Edit sales delivery' },
      validation: { order: 'Select a sales order', warehouse: 'Select a delivery warehouse', date: 'Select a delivery date', lineRequired: 'Add at least one delivery line', quantityRequired: 'Enter a delivery quantity' },
      scan: { resetConfirm: 'Clear all current delivery quantities?', title: 'Scan count', reset: 'Clear', resetDone: 'Quantities cleared', resetFailed: 'Failed to clear quantities', selectOrderFirst: 'Select a sales order first', notInOrder: 'Product {code} is not in this sales order', atMaximum: 'Product {code} has reached its deliverable quantity', lookupFailed: 'Barcode lookup failed' },
      message: { loadFailed: 'Failed to load deliveries', customersLoadFailed: 'Failed to load customers', warehousesLoadFailed: 'Failed to load warehouses', ordersLoadFailed: 'Failed to load sales orders', printLoadFailed: 'Failed to load print data', detailLoadFailed: 'Failed to load delivery details', deliveryLoadFailed: 'Failed to load the delivery', orderDetailLoadFailed: 'Failed to load order details', cancelConfirm: 'Cancel this delivery?', cancelled: 'Delivery cancelled', cancelFailed: 'Failed to cancel the delivery', postConfirm: 'Post this sales delivery?', posted: 'Delivery posted', postFailed: 'Failed to post the delivery', created: 'Delivery created', updated: 'Delivery updated', createFailed: 'Failed to create the delivery', updateFailed: 'Failed to update the delivery', prompt: 'Confirmation' }
    },
    purchaseOrder: {
      title: 'Purchase order management', subtitle: 'Manage the full purchase order lifecycle and improve supply-chain efficiency', totalOrders: 'Total orders', pendingApproval: 'Pending approval', approved: 'Approved', orderNo: 'Order no.', orderNoPlaceholder: 'Enter an order no.', supplier: 'Supplier', selectSupplier: 'Select a supplier', maximumPrice: 'Max price {amount}', supplierPrice: 'Supplier price', generalPrice: 'General price', allSuppliers: 'All suppliers', orderStatus: 'Order status', selectStatus: 'Select a status', orderDate: 'Order date', dateRangeSeparator: 'to', startDate: 'Start date', endDate: 'End date', create: 'New order', expectedArrival: 'Expected arrival', expectedArrivalDate: 'Expected arrival date', orderAmount: 'Order amount', createdBy: 'Created by', createdAt: 'Created', actions: 'Actions', view: 'View', print: 'Print', copy: 'Copy', edit: 'Edit', submit: 'Submit', approve: 'Approve', reject: 'Reject', unapprove: 'Unapprove', close: 'Close', cancel: 'Cancel', trace: 'Trace',
      basicInfo: 'Basic information', selectDate: 'Select a date', details: 'Order lines', addProduct: 'Add product', sequence: 'No.', productName: 'Product name', selectProduct: 'Select a product', quantity: 'Quantity', unitPriceCny: 'Unit price (CNY)', amountCny: 'Amount (CNY)', unitPrice: 'Unit price', amount: 'Amount', remark: 'Remark', optional: 'Optional', totalAmount: 'Order total:', remarkPlaceholder: 'Enter an optional remark', confirm: 'Confirm', detailTitle: 'Purchase order details', orderInfo: 'Order information', otherInfo: 'Other information', traceTitle: 'Purchase order trace', orderedQuantity: 'Ordered quantity', receivedQuantity: 'Received quantity', remainingQuantity: 'Remaining quantity', receiptStatus: 'Receipt status', documentNo: 'Document no.', type: 'Type', date: 'Date', statusLabel: 'Status',
      status: { draft: 'Draft', submitted: 'In approval', approved: 'Approved', rejected: 'Rejected', closed: 'Closed', cancelled: 'Cancelled' },
      dialog: { create: 'New purchase order', edit: 'Edit purchase order', copiedFrom: 'Copied from {orderNo}' },
      traceSections: { receipts: 'Purchase receipts', returns: 'Purchase returns', payables: 'Payables', payments: 'Payments', vouchers: 'Financial vouchers' },
      validation: { supplier: 'Select a supplier', orderDate: 'Select an order date', details: 'Add at least one order line', rejectReason: 'Enter a rejection reason' },
      message: { loadFailed: 'Failed to load purchase orders', copyFailed: 'Failed to copy the order', printLoadFailed: 'Failed to load print data', cancelConfirm: 'Cancel order “{orderNo}”?', cancelled: 'Order cancelled', cancelFailed: 'Failed to cancel the order', closeConfirm: 'Close order “{orderNo}”? No further receipts can be created.', closed: 'Order closed', closeFailed: 'Failed to close the order', traceLoadFailed: 'Failed to load the purchase order trace', submitConfirm: 'Submit order “{orderNo}” for approval?', submitted: 'Order submitted', submitFailed: 'Failed to submit the order', approveConfirm: 'Approve order “{orderNo}”?', approved: 'Order approved', approveFailed: 'Failed to approve the order', unapproveConfirm: 'Unapprove order “{orderNo}”?', unapproved: 'Order unapproved', unapproveFailed: 'Failed to unapprove the order', rejectTitle: 'Reject order', rejected: 'Order rejected', actionFailed: 'Operation failed', exported: 'Export complete', exportFailed: 'Export failed', exportFile: 'purchase_orders_{timestamp}.csv', updated: 'Order updated', created: 'Order created', updateFailed: 'Failed to update the order', createFailed: 'Failed to create the order', suppliersLoadFailed: 'Failed to load suppliers', productsLoadFailed: 'Failed to load products', prompt: 'Confirmation' }
    },
    purchaseReceipt: {
      title: 'Purchase receipt management', subtitle: 'Manage incoming receipts and keep materials arriving on schedule', totalReceipts: 'Total receipts', pending: 'Pending', completed: 'Completed', receiptNo: 'Receipt no.', receiptNoPlaceholder: 'Enter a receipt no.', purchaseOrder: 'Purchase order', purchaseOrderNo: 'Purchase order no.', orderIdPlaceholder: 'Enter an order ID', supplier: 'Supplier', selectSupplier: 'Select a supplier', allSuppliers: 'All suppliers', statusLabel: 'Status', selectStatus: 'Select a status', receiptDate: 'Receipt date', rangeSeparator: 'to', startDate: 'Start date', endDate: 'End date', create: 'New receipt', warehouse: 'Receiving warehouse', createdBy: 'Created by', createdAt: 'Created', actions: 'Actions', view: 'View', print: 'Print', edit: 'Edit', post: 'Post', cancel: 'Cancel', basicInfo: 'Basic information', selectOrder: 'Select a purchase order', selectDate: 'Select a date', selectWarehouse: 'Select a warehouse', details: 'Receipt lines', clearQuantity: 'Clear quantities', currentQuantity: 'Current quantity', sequence: 'No.', productName: 'Product name', orderedQuantity: 'Ordered quantity', actualReceipt: 'Received now', receivedQuantity: 'Received quantity', remark: 'Remark', optional: 'Optional', remarkPlaceholder: 'Enter an optional remark', confirm: 'Confirm', detailTitle: 'Purchase receipt details', receiptInfo: 'Receipt information', otherInfo: 'Other information', loadingOrder: 'Loading purchase order details...',
      status: { draft: 'Draft', posted: 'Posted', cancelled: 'Cancelled' },
      dialog: { create: 'New purchase receipt', edit: 'Edit purchase receipt' },
      validation: { order: 'Select a purchase order', warehouse: 'Select a receiving warehouse', date: 'Select a receipt date' },
      scan: { resetConfirm: 'Clear all current receipt quantities?', title: 'Scan count', reset: 'Clear', resetDone: 'Quantities cleared', resetFailed: 'Failed to clear quantities', selectOrderFirst: 'Select a purchase order first', notInOrder: 'Product {code} is not in this purchase order', atMaximum: 'Product {code} has reached its receivable quantity', lookupFailed: 'Barcode lookup failed' },
      message: { loadFailed: 'Failed to load purchase receipts', noAvailableOrders: 'No purchase orders are available for receipt', ordersLoadFailed: 'Failed to load purchase orders', receiptLoadFailed: 'Failed to load the receipt', printLoadFailed: 'Failed to load print data', missingOrderId: 'Purchase order ID is missing', orderDetailLoadFailed: 'Failed to load purchase order details', postConfirm: 'Post receipt “{receiptNo}”? Inventory and a payable will be created.', posted: 'Receipt posted', postFailed: 'Failed to post the receipt', cancelConfirm: 'Cancel receipt “{receiptNo}”?', cancelled: 'Receipt cancelled', cancelFailed: 'Failed to cancel the receipt', exported: 'Export complete', exportFailed: 'Export failed', exportFile: 'purchase_receipts_{timestamp}.csv', updated: 'Receipt updated', created: 'Receipt created', updateFailed: 'Failed to update the receipt', createFailed: 'Failed to create the receipt', warehousesLoadFailed: 'Failed to load warehouses', prompt: 'Confirmation' }
    },
    purchaseReturn: {
      title: 'Purchase return management', subtitle: 'Process purchase returns and respond to quality issues promptly', totalReturns: 'Total returns', pending: 'Pending posting', completed: 'Posted', returnNo: 'Return no.', returnNoPlaceholder: 'Enter a return no.', receipt: 'Purchase receipt', receiptIdPlaceholder: 'Enter a receipt ID', receiptNo: 'Purchase receipt no.', sourceOrder: 'Source order', warehouse: 'Return warehouse', returnDate: 'Return date', rangeSeparator: 'to', startDate: 'Start date', endDate: 'End date', statusLabel: 'Status', selectStatus: 'Select a status', create: 'New return', createdBy: 'Created by', createdAt: 'Created', actions: 'Actions', view: 'View', edit: 'Edit', post: 'Post', cancel: 'Cancel', basicInfo: 'Basic information', selectReceipt: 'Select a purchase receipt', selectDate: 'Select a date', details: 'Return lines', sequence: 'No.', productName: 'Product name', receiptQuantity: 'Received quantity', availableQuantity: 'Available to return', actualReturn: 'Return quantity', remark: 'Remark', optional: 'Optional', remarkPlaceholder: 'Enter an optional remark', confirm: 'Confirm', detailTitle: 'Purchase return details', returnInfo: 'Return information', otherInfo: 'Other information', linkedReceiptTitle: 'Purchase receipt details', loadingReceipt: 'Loading purchase receipt details...',
      status: { draft: 'Draft', posted: 'Posted', cancelled: 'Cancelled' },
      dialog: { create: 'New purchase return', edit: 'Edit purchase return' },
      validation: { receipt: 'Select a purchase receipt', date: 'Select a return date' },
      message: { loadFailed: 'Failed to load purchase returns', noAvailableReceipts: 'No purchase receipts are available for return', ordersLoadFailed: 'Failed to load purchase receipts', returnLoadFailed: 'Failed to load the return', detailLoadFailed: 'Failed to load purchase return details', missingReceiptId: 'Purchase receipt ID is missing', receiptDetailLoadFailed: 'Failed to load purchase receipt details', postConfirm: 'Post this purchase return? Inventory will be reduced and the payable will be reversed.', posted: 'Return posted', postFailed: 'Failed to post the return', cancelConfirm: 'Cancel return “{returnNo}”?', cancelled: 'Return cancelled', cancelFailed: 'Failed to cancel the return', exported: 'Export complete', exportFailed: 'Export failed', exportFile: 'purchase_returns_{timestamp}.csv', updated: 'Return updated', created: 'Return created', updateFailed: 'Failed to update the return', createFailed: 'Failed to create the return', prompt: 'Confirmation' }
    },
    qcInspection: {
      inspectionNo: 'Inspection no.', statusLabel: 'Status', all: 'All', typeLabel: 'Type', search: 'Search', reset: 'Reset', create: 'New inspection', export: 'Export', sourceDocument: 'Source document', inspectionDate: 'Inspection date', inspectedQuantity: 'Inspected quantity', qualifiedQuantity: 'Qualified quantity', unqualifiedQuantity: 'Unqualified quantity', actions: 'Actions', detail: 'Details', edit: 'Edit', submit: 'Submit', judge: 'Judge', cancelInspection: 'Cancel', inspectionType: 'Inspection type', purchaseReceipt: 'Purchase receipt', salesDelivery: 'Sales delivery', productionOrderId: 'Production order ID', selectDraftReceipt: 'Select a draft purchase receipt', selectDraftDelivery: 'Select a draft sales delivery', productionOrderPlaceholder: 'Released or material-issued production order ID', sourceOption: '{no} (quantity {quantity})', remark: 'Remark', cancel: 'Cancel', confirm: 'Confirm', save: 'Save', editHint: 'Inspection {no}. The source document cannot be changed; only the date, remark, and inspected line quantities can be edited in draft.', line: 'Line', productId: 'Product ID', defectReason: 'Defect reason', lineRemark: 'Line remark', optional: 'Optional', judgeHint: 'Inspection {no}. Qualified plus unqualified quantity must equal inspected quantity.', confirmJudge: 'Confirm judgment', qualifiedUnqualified: 'Qualified / unqualified', sourceInbound: 'Receipt {id}', sourceOutbound: 'Delivery {id}', sourceProduction: 'Order {id}',
      type: { iqc: 'Incoming IQC', oqc: 'Outbound OQC', ipqc: 'In-process IPQC' },
      status: { draft: 'Draft', submitted: 'Submitted', judged: 'Judged', cancelled: 'Cancelled' },
      dialog: { create: 'New inspection', editIqc: 'Edit incoming inspection', editOqc: 'Edit outbound inspection', editIpqc: 'Edit in-process inspection', judge: 'Judge inspection', detail: 'Inspection details' },
      validation: { delivery: 'Select a sales delivery', receipt: 'Select a purchase receipt', productionOrder: 'Enter a production order ID', date: 'Select an inspection date', draftOnly: 'Only draft inspections can be edited', editableMissing: 'No editable inspection was loaded', negativeQuantity: 'Line {line}: inspected quantity cannot be negative', judgeQuantity: 'Line {line}: qualified plus unqualified quantity must equal inspected quantity' },
      message: { loadFailed: 'Failed to load inspections', sourcesLoadFailed: 'Failed to load source documents', detailLoadFailed: 'Failed to load inspection details', created: 'Inspection created', submitConfirm: 'Submit inspection “{no}”?', submitted: 'Inspection submitted', saved: 'Inspection saved', judged: 'Inspection judged', cancelConfirm: 'Cancel inspection “{no}”?', cancelled: 'Inspection cancelled', exported: 'Export complete', exportFailed: 'Export failed', exportFile: 'quality_inspections_{date}.csv', prompt: 'Confirmation' }
    },
    inventoryMrp: {
      title: 'MRP Lite', history: 'Refresh history', historyTitle: 'MRP run history', runNo: 'Run no.', asOfDate: 'As-of date', status: 'Status', purchaseCount: 'Purchase lines', productionCount: 'Production lines', createdTime: 'Created', open: 'Open', lineStatus: 'Line status', convertedDoc: 'Converted doc', actions: 'Actions', convertPo: 'To PO', convertMo: 'To MO', description: 'Independent demand = undelivered sales + safety stock; supply = on hand + open purchases + work in progress. Products with a BOM generate production suggestions and expanded material purchases.', run: 'Run plan',
      summary: 'As of {date} · {purchaseCount} purchase suggestions · {productionCount} production suggestions', productionSuggestions: 'Production suggestions', purchaseSuggestions: 'Purchase suggestions',
      productCode: 'Code', productName: 'Product', demandQty: 'Demand', onHandQty: 'On hand', openSupplyQty: 'Open supply', netQty: 'Net demand', reason: 'Reason',
      message: { succeeded: 'MRP run completed', failed: 'MRP run failed', loadFailed: 'Failed to load plan', convertFailed: 'Conversion failed', convertedPo: 'Converted to purchase order {orderNo}', convertedMo: 'Converted to production order {orderNo}' }
    },
    inventoryReplenishment: {
      suggestionNo: 'Suggestion no.', suggestionNoPlaceholder: 'Enter a suggestion no.', statusLabel: 'Status', all: 'All', warehouse: 'Warehouse', selectWarehouse: 'Select a warehouse', product: 'Product', selectProduct: 'Select a product', supplier: 'Supplier', selectSupplier: 'Select a supplier', createdAt: 'Created', rangeSeparator: 'to', startTime: 'Start time', endTime: 'End time', search: 'Search', reset: 'Reset',
      fulfillmentStatus: 'Fulfillment', productCode: 'Product code', productName: 'Product name', suggestedQty: 'Suggested quantity', shortageSnapshot: 'Shortage snapshot', expectedArrival: 'Expected arrival', purchaseOrder: 'Purchase order', remark: 'Remark', actions: 'Actions', edit: 'Edit', convert: 'Create purchase order', cancel: 'Cancel', editTitle: 'Edit replenishment suggestion', expectedArrivalPlaceholder: 'Select an expected arrival date', save: 'Save',
      status: { draft: 'Draft', converted: 'Converted', cancelled: 'Cancelled' },
      fulfillment: { suggested: 'Awaiting conversion', purchaseCreated: 'Purchase created', partialReceived: 'Partially received', replenished: 'Replenished', purchaseClosed: 'Purchase closed', cancelled: 'Cancelled' },
      validation: { quantityRequired: 'Enter a suggested quantity', quantityPositive: 'Suggested quantity must be greater than 0' },
      message: { loadFailed: 'Failed to load replenishment suggestions', updated: 'Replenishment suggestion updated', saveFailed: 'Failed to save the replenishment suggestion', cancelConfirm: 'Cancel replenishment suggestion {no}?', cancelTitle: 'Cancel replenishment suggestion', cancelReason: 'Optional cancellation reason', confirm: 'Confirm', cancelled: 'Suggestion cancelled', cancelFailed: 'Failed to cancel the suggestion', supplierRequired: 'Select a supplier before conversion', convertConfirm: 'Convert replenishment suggestion {no} to a purchase order?', convertTitle: 'Create purchase order', converted: 'Purchase order {no} created', convertFailed: 'Failed to create the purchase order', optionsLoadFailed: 'Failed to load filter options' }
    },
    productionRouting: {
      title: 'Routings', keyword: 'Keyword', keywordPlaceholder: 'Routing code or name', statusLabel: 'Status', all: 'All', search: 'Search', reset: 'Reset', create: 'New routing', code: 'Code', name: 'Name', bom: 'BOM', operationCount: 'Operations', remark: 'Remark', actions: 'Actions', view: 'View', edit: 'Edit', enable: 'Enable', disable: 'Disable', routingCode: 'Routing code', routingName: 'Routing name', codePlaceholder: 'Enter a routing code', namePlaceholder: 'Enter a routing name', selectBom: 'Select a BOM', operationList: 'Operation list', operationDetails: 'Operation details', sequence: 'No.', operationCode: 'Operation code', operationName: 'Operation name', workCenter: 'Work center', selectWorkCenter: 'Select a work center', standardMinutes: 'Standard time (min)', optional: 'Optional', delete: 'Delete', addOperation: 'Add operation', remarkPlaceholder: 'Enter a remark', cancel: 'Cancel', save: 'Save', close: 'Close', detailTitle: 'Routing details',
      status: { active: 'Active', disabled: 'Disabled' },
      dialog: { create: 'New routing', edit: 'Edit routing' },
      validation: { code: 'Enter a routing code', name: 'Enter a routing name', bom: 'Select a BOM', operations: 'Add at least one operation', operationRequired: 'Operation {line}: code, name, work center, and standard time are required' },
      message: { optionsLoadFailed: 'Failed to load work center and BOM options', loadFailed: 'Failed to load routings', detailLoadFailed: 'Failed to load routing details', enableConfirm: 'Enable routing “{name}”?', disableConfirm: 'Disable routing “{name}”?', enabled: 'Routing enabled', disabled: 'Routing disabled', updated: 'Routing updated', created: 'Routing created', prompt: 'Confirmation' }
    },
    productionWorkCenter: {
      title: 'Work centers', keyword: 'Keyword', keywordPlaceholder: 'Work center code or name', statusLabel: 'Status', all: 'All', search: 'Search', reset: 'Reset', create: 'New work center', code: 'Code', name: 'Name', remark: 'Remark', actions: 'Actions', edit: 'Edit', enable: 'Enable', disable: 'Disable', codePlaceholder: 'Enter a work center code', namePlaceholder: 'Enter a work center name', remarkPlaceholder: 'Enter a remark', cancel: 'Cancel', save: 'Save',
      status: { active: 'Active', disabled: 'Disabled' },
      dialog: { create: 'New work center', edit: 'Edit work center' },
      validation: { code: 'Enter a work center code', name: 'Enter a work center name' },
      message: { loadFailed: 'Failed to load work centers', enableConfirm: 'Enable work center “{name}”?', disableConfirm: 'Disable work center “{name}”?', enabled: 'Work center enabled', disabled: 'Work center disabled', updated: 'Work center updated', created: 'Work center created', prompt: 'Confirmation' }
    },
    productionBom: {
      title: 'BOM management', bomCode: 'BOM code', bomCodePlaceholder: 'Enter a BOM code', product: 'Product', selectProduct: 'Select a product', statusLabel: 'Status', select: 'Select', search: 'Search', reset: 'Reset', create: 'New BOM', baseQuantity: 'Base quantity', remark: 'Remark', createdAt: 'Created', createdBy: 'Created by', actions: 'Actions', view: 'View', edit: 'Edit', materialList: 'Material list', materialDetails: 'Material details', material: 'Material', selectMaterial: 'Select a material', quantity: 'Quantity', scrapRatePercent: 'Scrap rate (%)', scrapRate: 'Scrap rate', delete: 'Delete', addMaterial: 'Add material', remarkPlaceholder: 'Enter a remark', cancel: 'Cancel', save: 'Save', close: 'Close', detailTitle: 'BOM details', productFallback: 'Product {id}',
      status: { active: 'Active', disabled: 'Disabled' },
      dialog: { create: 'New BOM', edit: 'Edit BOM' },
      validation: { product: 'Select a product', baseQuantity: 'Enter a base quantity', materials: 'Add at least one material line' },
      message: { productsLoadFailed: 'Failed to load products', loadFailed: 'Failed to load BOMs', listLoadFailed: 'Failed to load the BOM list', detailLoadFailed: 'Failed to load BOM details', updated: 'BOM updated', created: 'BOM created', actionFailed: 'Operation failed' }
    },
    productionOrder: {
      title: 'Production order management', orderNo: 'Order no.', orderNoPlaceholder: 'Enter an order no.', product: 'Product', selectProduct: 'Select a product', statusLabel: 'Status', select: 'Select', priorityLabel: 'Priority', search: 'Search', reset: 'Reset', create: 'New order', productCode: 'Product code', productName: 'Product name', plannedQuantity: 'Planned quantity', completedQuantity: 'Completed quantity', completionRate: 'Completion', materialWarehouse: 'Material warehouse', finishedWarehouse: 'Finished goods warehouse', plannedStart: 'Planned start', plannedEnd: 'Planned end', actions: 'Actions', view: 'View', edit: 'Edit', release: 'Release', issue: 'Issue materials', operationReport: 'Operation reporting', complete: 'Complete', reverse: 'Reverse', returnMaterials: 'Return materials', cancel: 'Cancel', bom: 'BOM', bomCode: 'BOM code', selectBom: 'Select a BOM', selectMaterialWarehouse: 'Select a material warehouse', selectFinishedWarehouse: 'Select a finished goods warehouse', plannedStartDate: 'Planned start date', plannedEndDate: 'Planned end date', selectDate: 'Select a date', remark: 'Remark', remarkPlaceholder: 'Enter a remark', save: 'Save', close: 'Close', detailTitle: 'Production order details', scrapQuantity: 'Scrap quantity', actualStart: 'Actual start', actualEnd: 'Actual end', createdBy: 'Created by', createdAt: 'Created', materialUsage: 'Material usage', materialCode: 'Material code', materialName: 'Material name', requiredQuantity: 'Required quantity', issuedQuantity: 'Issued quantity', returnedQuantity: 'Returned quantity', unit: 'Unit', completionTitle: 'Production completion', completionQuantity: 'Completion quantity', maxCompletion: 'Maximum: {quantity}', completionDate: 'Completion date', confirmCompletion: 'Confirm completion', reversalTitle: 'Reverse completion', reversalQuantity: 'Reversal quantity', maxReversal: 'Maximum: {quantity}', reversalDate: 'Reversal date', confirmReversal: 'Confirm reversal', materialReturnTitle: 'Return production materials', returnDate: 'Return date', returnableQuantity: 'Returnable quantity', currentReturn: 'Return now', lotNo: 'Lot no.', confirmReturn: 'Confirm return', operationsTitle: 'Operation reporting · {orderNo}', operationsHelp: 'When the BOM has an active routing, operations are generated on release. Completion is unrestricted without operations; otherwise all operations must be done with sufficient qualified quantity.', sequence: 'No.', operationCode: 'Operation code', operationName: 'Operation name', workCenter: 'Work center', planned: 'Planned', reported: 'Reported', qualified: 'Qualified', scrap: 'Scrap', report: 'Report', noOperations: 'No operations (no routing or no snapshot before release)', refresh: 'Refresh', reportQuantity: 'Reported quantity', qualifiedQuantity: 'Qualified quantity', confirmReport: 'Confirm report', baseQuantity: 'Base quantity {quantity}', warehouseFallback: 'Warehouse {id}', issueRemark: 'Issued from production order page',
      status: { draft: 'Draft', released: 'Released', materialIssued: 'Materials issued', inProgress: 'In progress', completed: 'Completed', cancelled: 'Cancelled' },
      priority: { low: 'Low', normal: 'Normal', high: 'High', urgent: 'Urgent' },
      operationStatus: { pending: 'Pending', inProgress: 'In progress', done: 'Done' },
      dialog: { create: 'New production order', edit: 'Edit production order' },
      validation: { product: 'Select a product', bom: 'Select a BOM', quantity: 'Enter a planned quantity', materialWarehouse: 'Select a material warehouse', finishedWarehouse: 'Select a finished goods warehouse', startDate: 'Select a start date', endDate: 'Select an end date', completedQuantity: 'Enter a completion quantity', reversalQuantity: 'Enter a reversal quantity', returnQuantity: 'Enter a material return quantity', qualifiedExceedsReported: 'Qualified quantity cannot exceed reported quantity' },
      message: { optionsLoadFailed: 'Failed to load options', loadFailed: 'Failed to load production orders', orderLoadFailed: 'Failed to load the production order', detailLoadFailed: 'Failed to load order details', releaseConfirm: 'Release production order “{orderNo}”?', released: 'Order released', releaseFailed: 'Failed to release the order', issueConfirm: 'Issue the remaining material demand for production order “{orderNo}”?', issued: 'Materials issued', issueFailed: 'Failed to issue materials', operationsLoadFailed: 'Failed to load operations', reported: 'Operation reported', completed: 'Production completed', completeFailed: 'Failed to complete production', reversed: 'Completion reversed', reverseFailed: 'Failed to reverse completion', noReturnableMaterials: 'This order has no returnable materials', returnableLoadFailed: 'Failed to load returnable materials', returned: 'Materials returned', returnFailed: 'Failed to return materials', cancelConfirm: 'Cancel production order “{orderNo}”?', cancelled: 'Order cancelled', cancelFailed: 'Failed to cancel the order', updated: 'Order updated', created: 'Order created', updateFailed: 'Failed to update the order', createFailed: 'Failed to create the order', prompt: 'Confirmation' }
    }
  }
} as const

const messages = {
  'zh-CN': {
    ...coreMessages['zh-CN'],
    ...operationsPageMessages['zh-CN'],
    ...financeReportPageMessages['zh-CN'],
    ...adminWorkflowPageMessages['zh-CN'],
    ...platformPageMessages['zh-CN'],
    ...salesCommercialPageMessages['zh-CN']
  },
  'en-US': {
    ...coreMessages['en-US'],
    ...operationsPageMessages['en-US'],
    ...financeReportPageMessages['en-US'],
    ...adminWorkflowPageMessages['en-US'],
    ...platformPageMessages['en-US'],
    ...salesCommercialPageMessages['en-US']
  }
} as const

export const i18n = createI18n({
  legacy: false,
  locale: readStoredLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  messages
})

export const setI18nLocale = (locale: SupportedLocale) => {
  i18n.global.locale.value = locale
  document.documentElement.lang = locale
}

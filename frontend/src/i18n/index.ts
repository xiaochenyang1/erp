import { createI18n } from 'vue-i18n'

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const
export type SupportedLocale = typeof SUPPORTED_LOCALES[number]
export const DEFAULT_LOCALE: SupportedLocale = 'zh-CN'

export const isSupportedLocale = (value: unknown): value is SupportedLocale =>
  typeof value === 'string' && SUPPORTED_LOCALES.includes(value as SupportedLocale)

export const readStoredLocale = (): SupportedLocale => {
  const stored = localStorage.getItem('locale')
  return isSupportedLocale(stored) ? stored : DEFAULT_LOCALE
}

const messages = {
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
      remember: '记住密码', forgot: '忘记密码？', submit: '登录', submitting: '登录中...', testAccount: '测试账号',
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
      remember: 'Remember me', forgot: 'Forgot password?', submit: 'Sign in', submitting: 'Signing in...', testAccount: 'Test account',
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
    }
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

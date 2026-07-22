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
      saved: '个人资料已保存', saveFailed: '保存个人资料失败', passwordChanged: '密码修改成功，请重新登录'
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
      pendingApprovals: '待审批单据', openReceivables: '未结应收', openPayables: '未结应付',
      today: '今日', attention: '需关注', pending: '待处理', receivableAging: '应收账龄', payableAging: '应付账龄',
      details: '详情', bucket: '账龄段', count: '笔数', amount: '金额', total: '合计', overdueTop: '逾期 TOP'
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
      saved: 'Profile saved', saveFailed: 'Failed to save profile', passwordChanged: 'Password changed. Please sign in again.'
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
      pendingApprovals: 'Pending approvals', openReceivables: 'Open receivables', openPayables: 'Open payables',
      today: 'Today', attention: 'Attention', pending: 'Pending', receivableAging: 'Receivable aging', payableAging: 'Payable aging',
      details: 'Details', bucket: 'Aging bucket', count: 'Count', amount: 'Amount', total: 'Total', overdueTop: 'Overdue TOP'
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

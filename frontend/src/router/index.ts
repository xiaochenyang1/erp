import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

// 路由配置
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
      requiresAuth: false
    }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    meta: {
      requiresAuth: true
    },
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: {
          title: '首页',
          icon: 'House'
        }
      },
      // 主数据管理
      {
        path: '/masterdata',
        name: 'Masterdata',
        meta: {
          title: '主数据',
          icon: 'Menu'
        },
        children: [
          {
            path: 'products',
            name: 'Products',
            component: () => import('@/views/masterdata/products/index.vue'),
            meta: {
              title: '产品管理',
              icon: 'Box',
              permission: 'masterdata:product:view'
            }
          },
          {
            path: 'customers',
            name: 'Customers',
            component: () => import('@/views/masterdata/customers/index.vue'),
            meta: {
              title: '客户管理',
              icon: 'User',
              permission: 'masterdata:customer:view'
            }
          },
          {
            path: 'suppliers',
            name: 'Suppliers',
            component: () => import('@/views/masterdata/suppliers/index.vue'),
            meta: {
              title: '供应商管理',
              icon: 'OfficeBuilding',
              permission: 'masterdata:supplier:view'
            }
          },
          {
            path: 'warehouses',
            name: 'Warehouses',
            component: () => import('@/views/masterdata/warehouses/index.vue'),
            meta: {
              title: '仓库管理',
              icon: 'House',
              permission: 'masterdata:warehouse:view'
            }
          },
          {
            path: 'locations',
            name: 'MasterdataLocations',
            component: () => import('@/views/masterdata/locations/index.vue'),
            meta: {
              title: '库位管理',
              icon: 'Place',
              permission: 'masterdata:location:view'
            }
          }
        ]
      },
      // 采购管理
      {
        path: '/purchase',
        name: 'Purchase',
        meta: {
          title: '采购管理',
          icon: 'ShoppingCart'
        },
        children: [
          {
            path: 'orders',
            name: 'PurchaseOrders',
            component: () => import('@/views/purchase/orders/index.vue'),
            meta: {
              title: '采购订单',
              icon: 'Document',
              permission: 'purchase:order:view'
            }
          },
          {
            path: 'receipts',
            name: 'PurchaseReceipts',
            component: () => import('@/views/purchase/receipts/index.vue'),
            meta: {
              title: '采购收货',
              icon: 'Box',
              permission: 'purchase:receipt:view'
            }
          },
          {
            path: 'returns',
            name: 'PurchaseReturns',
            component: () => import('@/views/purchase/returns/index.vue'),
            meta: {
              title: '采购退货',
              icon: 'RefreshLeft',
              permission: 'purchase:return:view'
            }
          },
          {
            path: 'inquiries',
            name: 'PurchaseInquiries',
            component: () => import('@/views/purchase/inquiries/index.vue'),
            meta: {
              title: '采购询价',
              icon: 'Tickets',
              permission: 'purchase:inquiry:view'
            }
          },
          {
            path: 'requisitions',
            name: 'PurchaseRequisitions',
            component: () => import('@/views/purchase/requisitions/index.vue'),
            meta: { title: '采购请购', icon: 'Document', permission: 'purchase:requisition:view' }
          },
          {
            path: 'prices',
            name: 'PurchasePrices',
            component: () => import('@/views/purchase/prices/index.vue'),
            meta: {
              title: '采购价目',
              icon: 'PriceTag',
              permission: 'purchase:price:view'
            }
          }
        ]
      },
      // 销售管理
      {
        path: '/sales',
        name: 'Sales',
        meta: {
          title: '销售管理',
          icon: 'Sell'
        },
        children: [
          {
            path: 'orders',
            name: 'SalesOrders',
            component: () => import('@/views/sales/orders/index.vue'),
            meta: {
              title: '销售订单',
              icon: 'Document',
              permission: 'sales:order:view'
            }
          },
          {
            path: 'deliveries',
            name: 'SalesDeliveries',
            component: () => import('@/views/sales/deliveries/index.vue'),
            meta: {
              title: '销售发货',
              icon: 'Box',
              permission: 'sales:delivery:view'
            }
          },
          {
            path: 'returns',
            name: 'SalesReturns',
            component: () => import('@/views/sales/returns/index.vue'),
            meta: {
              title: '销售退货',
              icon: 'RefreshLeft',
              permission: 'sales:return:view'
            }
          },
          {
            path: 'prices',
            name: 'SalesPrices',
            component: () => import('@/views/sales/prices/index.vue'),
            meta: {
              title: '销售价目',
              icon: 'PriceTag',
              permission: 'sales:price:view'
            }
          },
          {
            path: 'quotes',
            name: 'SalesQuotes',
            component: () => import('@/views/sales/quotes/index.vue'),
            meta: {
              title: '销售报价',
              icon: 'Ticket',
              permission: 'sales:quote:view'
            }
          }
        ]
      },
      // 库存管理
      {
        path: '/inventory',
        name: 'Inventory',
        meta: {
          title: '库存管理',
          icon: 'Box'
        },
        children: [
          {
            path: 'stocks',
            name: 'InventoryStocks',
            component: () => import('@/views/inventory/stocks/index.vue'),
            meta: {
              title: '库存查询',
              icon: 'List',
              permission: 'inventory:stock:view'
            }
          },
          {
            path: 'adjustments',
            name: 'InventoryAdjustments',
            component: () => import('@/views/inventory/adjustments/index.vue'),
            meta: {
              title: '库存调整',
              icon: 'EditPen',
              permission: 'inventory:adjustment:view'
            }
          },
          {
            path: 'checks',
            name: 'InventoryChecks',
            component: () => import('@/views/inventory/checks/index.vue'),
            meta: {
              title: '库存盘点',
              icon: 'Document',
              permission: 'inventory:check:view'
            }
          },
          {
            path: 'transfers',
            name: 'InventoryTransfers',
            component: () => import('@/views/inventory/transfers/index.vue'),
            meta: {
              title: '库存调拨',
              icon: 'Switch',
              permission: 'inventory:transfer:view'
            }
          },
          {
            path: 'alerts',
            name: 'InventoryAlerts',
            component: () => import('@/views/inventory/alerts/index.vue'),
            meta: {
              title: '库存预警',
              icon: 'Warning',
              permission: 'inventory:alert:view'
            }
          },
          {
            path: 'serials',
            name: 'InventorySerials',
            component: () => import('@/views/inventory/serials/index.vue'),
            meta: {
              title: '序列号台账',
              icon: 'Ticket',
              permission: 'inventory:serial:view'
            }
          },
          {
            path: 'mrp',
            name: 'InventoryMrp',
            component: () => import('@/views/inventory/mrp/index.vue'),
            meta: {
              title: 'MRP计划',
              icon: 'SetUp',
              permission: 'inventory:mrp:view'
            }
          },
          {
            path: 'replenishment-suggestions',
            name: 'InventoryReplenishmentSuggestions',
            component: () => import('@/views/inventory/replenishment-suggestions/index.vue'),
            meta: {
              title: '补货建议',
              icon: 'ShoppingCart',
              permission: 'inventory:replenishment:view'
            }
          },
          {
            path: 'lot-genealogy',
            name: 'InventoryLotGenealogy',
            component: () => import('@/views/inventory/lot-genealogy/index.vue'),
            meta: {
              title: '批次谱系',
              icon: 'Share',
              permission: 'inventory:lot:genealogy'
            }
          }
        ]
      },
      // 质量管理
      {
        path: '/qc',
        name: 'Qc',
        meta: {
          title: '质量管理',
          icon: 'Stamp'
        },
        children: [
          {
            path: 'inspections',
            name: 'QcInspections',
            component: () => import('@/views/qc/inspection/index.vue'),
            meta: {
              title: '来料检验',
              icon: 'DocumentChecked',
              permission: 'qc:inspection:view'
            }
          }
        ]
      },
      // 系统管理
      {
        path: '/system',
        name: 'System',
        meta: {
          title: '系统管理',
          icon: 'Setting'
        },
        children: [
          {
            path: 'users',
            name: 'SystemUsers',
            component: () => import('@/views/system/users/index.vue'),
            meta: {
              title: '用户管理',
              icon: 'User',
              permission: 'system:user:view'
            }
          },
          {
            path: 'roles',
            name: 'SystemRoles',
            component: () => import('@/views/system/roles/index.vue'),
            meta: {
              title: '角色管理',
              icon: 'UserFilled',
              permission: 'system:role:view'
            }
          },
          {
            path: 'menus',
            name: 'SystemMenus',
            component: () => import('@/views/system/menus/index.vue'),
            meta: {
              title: '菜单管理',
              icon: 'Menu',
              permission: 'system:menu:view'
            }
          },
          {
            path: 'depts',
            name: 'SystemDepts',
            component: () => import('@/views/system/depts/index.vue'),
            meta: {
              title: '部门管理',
              icon: 'OfficeBuilding',
              permission: 'system:dept:view'
            }
          },
          {
            path: 'posts',
            name: 'SystemPosts',
            component: () => import('@/views/system/posts/index.vue'),
            meta: {
              title: '岗位管理',
              icon: 'Briefcase',
              permission: 'system:post:view'
            }
          },
          {
            path: 'dicts',
            name: 'SystemDicts',
            component: () => import('@/views/system/dicts/index.vue'),
            meta: {
              title: '字典管理',
              icon: 'Collection',
              permission: 'system:dict:view'
            }
          },
          {
            path: 'configs',
            name: 'SystemConfigs',
            component: () => import('@/views/system/configs/index.vue'),
            meta: {
              title: '系统配置',
              icon: 'Tools',
              permission: 'system:config:view'
            }
          },
          {
            path: 'logs',
            name: 'SystemLogs',
            component: () => import('@/views/system/logs/index.vue'),
            meta: {
              title: '操作日志',
              icon: 'Document',
              permission: 'system:log:view'
            }
          },
          {
            path: 'attachments',
            name: 'SystemAttachments',
            component: () => import('@/views/system/attachments/index.vue'),
            meta: {
              title: '附件中心',
              icon: 'Paperclip',
              permission: 'system:attachment:view'
            }
          },
          {
            path: 'imports',
            name: 'SystemImports',
            component: () => import('@/views/system/imports/index.vue'),
            meta: {
              title: '导入任务',
              icon: 'Upload',
              permission: 'import:init:manage'
            }
          },
          {
            path: 'document-state-rules',
            name: 'SystemDocumentStateRules',
            component: () => import('@/views/system/document-state-rules/index.vue'),
            meta: {
              title: '单据状态规则',
              icon: 'SetUp',
              permission: 'system:config:view'
            }
          },
          {
            path: 'user-sessions',
            name: 'SystemUserSessions',
            component: () => import('@/views/system/user-sessions/index.vue'),
            meta: {
              title: '在线会话',
              icon: 'Monitor',
              permission: 'system:user-session:view'
            }
          },
          {
            path: 'notifications',
            name: 'SystemNotifications',
            component: () => import('@/views/system/notifications/index.vue'),
            meta: {
              title: '通知中心',
              icon: 'Bell',
              permission: 'system:notification:view'
            }
          },
          {
            path: 'observability',
            name: 'SystemObservability',
            component: () => import('@/views/system/observability/index.vue'),
            meta: {
              title: '可观测性',
              icon: 'TrendCharts',
              permission: 'system:observability:view'
            }
          },
          {
            path: 'readiness',
            name: 'SystemReadiness',
            component: () => import('@/views/system/readiness/index.vue'),
            meta: {
              title: '预生产验收',
              icon: 'DocumentChecked',
              permission: 'system:readiness:view'
            }
          }
        ]
      },
      // 财务管理
      {
        path: '/finance',
        name: 'Finance',
        meta: {
          title: '财务管理',
          icon: 'Money'
        },
        children: [
          {
            path: 'subjects',
            name: 'FinanceSubjects',
            component: () => import('@/views/finance/subjects/index.vue'),
            meta: {
              title: '会计科目',
              icon: 'Notebook',
              permission: 'finance:subject:manage'
            }
          },
          {
            path: 'periods',
            name: 'FinancePeriods',
            component: () => import('@/views/finance/periods/index.vue'),
            meta: {
              title: '会计期间',
              icon: 'Calendar',
              permission: 'finance:period:view'
            }
          },
          {
            path: 'vouchers',
            name: 'FinanceVouchers',
            component: () => import('@/views/finance/vouchers/index.vue'),
            meta: {
              title: '凭证管理',
              icon: 'Tickets',
              permission: 'finance:voucher:view'
            }
          },
          {
            path: 'vouchers/manual',
            name: 'FinanceManualVouchers',
            component: () => import('@/views/finance/vouchers/manual/index.vue'),
            meta: {
              title: '手工凭证',
              icon: 'EditPen',
              permission: 'finance:voucher:view'
            }
          },
          {
            path: 'ledger',
            name: 'FinanceLedger',
            component: () => import('@/views/finance/ledger/index.vue'),
            meta: {
              title: '总账查询',
              icon: 'DataLine',
              permission: 'finance:ledger:view'
            }
          },
          {
            path: 'receivables',
            name: 'FinanceReceivables',
            component: () => import('@/views/finance/receivables/index.vue'),
            meta: {
              title: '应收账款',
              icon: 'Document',
              permission: 'finance:receivable:view'
            }
          },
          {
            path: 'payables',
            name: 'FinancePayables',
            component: () => import('@/views/finance/payables/index.vue'),
            meta: {
              title: '应付账款',
              icon: 'Document',
              permission: 'finance:payable:view'
            }
          },
          {
            path: 'aging',
            name: 'FinanceAging',
            component: () => import('@/views/finance/aging/index.vue'),
            meta: {
              title: '账龄分析',
              icon: 'Histogram',
              permission: 'finance:aging:view'
            }
          },
          {
            path: 'statements',
            name: 'FinanceStatements',
            component: () => import('@/views/finance/statements/index.vue'),
            meta: {
              title: '往来对账',
              icon: 'Notebook',
              permission: 'finance:statement:view'
            }
          },
          {
            path: 'gross-margin',
            name: 'FinanceGrossMargin',
            component: () => import('@/views/finance/gross-margin/index.vue'),
            meta: {
              title: '毛利简报',
              icon: 'TrendCharts',
              permission: 'finance:margin:view'
            }
          },
          {
            path: 'payments',
            name: 'FinancePayments',
            component: () => import('@/views/finance/payments/index.vue'),
            meta: {
              title: '收付款管理',
              icon: 'Money',
              permission: 'finance:payment:view'
            }
          },
          {
            path: 'funds',
            name: 'FinanceFunds',
            component: () => import('@/views/finance/funds/index.vue'),
            meta: {
              title: '资金对账',
              icon: 'CreditCard',
              permission: 'finance:fund:view'
            }
          },
          {
            path: 'expenses',
            name: 'FinanceExpenses',
            component: () => import('@/views/finance/expenses/index.vue'),
            meta: {
              title: '费用管理',
              icon: 'Wallet',
              permission: 'finance:expense:manage'
            }
          },
          {
            path: 'invoices',
            name: 'FinanceInvoices',
            component: () => import('@/views/finance/invoices/index.vue'),
            meta: {
              title: '发票登记',
              icon: 'Tickets',
              permission: 'finance:invoice:view'
            }
          }
        ]
      },
      // 审批中心
      {
        path: '/workflow',
        name: 'Workflow',
        meta: {
          title: '审批中心',
          icon: 'Finished'
        },
        children: [
          {
            path: 'tasks',
            name: 'WorkflowTasks',
            component: () => import('@/views/workflow/tasks/index.vue'),
            meta: {
              title: '审批待办',
              icon: 'Memo',
              permission: 'workflow:view'
            }
          },
          {
            path: 'records',
            name: 'WorkflowRecords',
            component: () => import('@/views/workflow/records/index.vue'),
            meta: {
              title: '审批记录',
              icon: 'Clock',
              permission: 'workflow:view'
            }
          },
          {
            path: 'configs',
            name: 'WorkflowConfigs',
            component: () => import('@/views/workflow/configs/index.vue'),
            meta: {
              title: '审批配置',
              icon: 'Setting',
              permission: 'workflow:config:view'
            }
          }
        ]
      },
      // 生产管理
      {
        path: '/production',
        name: 'Production',
        meta: {
          title: '生产管理',
          icon: 'Box'
        },
        children: [
          {
            path: 'boms',
            name: 'ProductionBOMs',
            component: () => import('@/views/production/boms/index.vue'),
            meta: {
              title: 'BOM管理',
              icon: 'List',
              permission: 'production:bom:view'
            }
          },
          {
            path: 'orders',
            name: 'ProductionOrders',
            component: () => import('@/views/production/orders/index.vue'),
            meta: {
              title: '生产订单',
              icon: 'Document',
              permission: 'production:order:view'
            }
          },
          {
            path: 'work-centers',
            name: 'ProductionWorkCenters',
            component: () => import('@/views/production/work-centers/index.vue'),
            meta: {
              title: '工作中心',
              icon: 'OfficeBuilding',
              permission: 'production:work-center:view'
            }
          },
          {
            path: 'routings',
            name: 'ProductionRoutings',
            component: () => import('@/views/production/routings/index.vue'),
            meta: {
              title: '工艺路线',
              icon: 'Guide',
              permission: 'production:routing:view'
            }
          }
        ]
      },
      // 报表中心
      {
        path: '/reports',
        name: 'Reports',
        component: () => import('@/views/reports/index.vue'),
        meta: {
          title: '报表中心',
          icon: 'DataAnalysis',
          permission: 'report:view'
        }
      },
      {
        path: '/reports/traces',
        name: 'BusinessTraces',
        component: () => import('@/views/reports/traces/index.vue'),
        meta: {
          title: '单据追踪',
          icon: 'Search',
          permission: 'report:view'
        }
      },
      {
        path: '/exception-tickets',
        name: 'ExceptionTickets',
        component: () => import('@/views/exception-tickets/index.vue'),
        meta: {
          title: '异常处理',
          icon: 'Warning',
          permission: 'exception-ticket:view'
        }
      },
      {
        path: '/exception-rules',
        name: 'ExceptionRules',
        component: () => import('@/views/exception-rules/index.vue'),
        meta: {
          title: '异常规则',
          icon: 'AlarmClock',
          permission: 'exception-rule:view'
        }
      },
      {
        path: '/exception-sla-policies',
        name: 'ExceptionSlaPolicies',
        component: () => import('@/views/exception-sla-policies/index.vue'),
        meta: {
          title: '异常SLA策略',
          icon: 'TrendCharts',
          permission: 'exception-sla-policy:view'
        }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: {
      title: '404',
      requiresAuth: false
    }
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  NProgress.start()

  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - ERP系统` : 'ERP系统'

  const requiresAuth = to.meta.requiresAuth !== false
  const token = localStorage.getItem('token')

  // 无需登录的页面
  if (!requiresAuth) {
    if (to.path === '/login' && token) {
      next('/')
    } else {
      next()
    }
    return
  }

  // 需要登录但没有 token
  if (!token) {
    next('/login')
    return
  }

  const userStore = useUserStore()

  // 刷新页面后用户信息/权限尚未加载：先拉取，保证后续权限校验有依据（消除竞态）
  if (!userStore.userInfo) {
    try {
      await userStore.getUserInfo()
    } catch {
      // getUserInfo 会同步清理持久化登录态、用户权限与运行时菜单
      next('/login')
      return
    }
  }

  // 已登录访问登录页
  if (to.path === '/login') {
    next('/')
    return
  }

  // 页面级权限校验
  const required = to.meta.permission as string | undefined
  if (required && !userStore.hasPermission(required)) {
    ElMessage.warning('您没有访问该页面的权限')
    next('/dashboard')
    return
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router

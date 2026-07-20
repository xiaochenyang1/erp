/**
 * 系统常量定义
 */

/**
 * 响应状态码
 */
export const RESPONSE_CODE = {
  SUCCESS: '0',           // 成功
  UNAUTHORIZED: '401',    // 未授权
  FORBIDDEN: '403',       // 禁止访问
  NOT_FOUND: '404',       // 未找到
  ERROR: '500'            // 服务器错误
} as const

/**
 * HTTP状态码
 */
export const HTTP_STATUS = {
  OK: 200,
  CREATED: 201,
  NO_CONTENT: 204,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  TOO_MANY_REQUESTS: 429,
  INTERNAL_SERVER_ERROR: 500,
  BAD_GATEWAY: 502,
  SERVICE_UNAVAILABLE: 503
} as const

/**
 * 订单状态
 */
export const ORDER_STATUS = {
  DRAFT: 'DRAFT',
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  DELIVERING: 'DELIVERING',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED'
} as const

/**
 * 通用状态
 */
export const COMMON_STATUS = {
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE'
} as const

/**
 * 支付状态
 */
export const PAYMENT_STATUS = {
  UNPAID: 'UNPAID',
  PARTIAL: 'PARTIAL',
  PAID: 'PAID',
  OVERDUE: 'OVERDUE'
} as const

/**
 * 凭证状态
 */
export const VOUCHER_STATUS = {
  DRAFT: 'DRAFT',
  APPROVED: 'APPROVED',
  POSTED: 'POSTED',
  CANCELLED: 'CANCELLED'
} as const

/**
 * 收付款方式
 */
export const PAYMENT_METHOD = {
  CASH: { value: 'CASH', label: '现金' },
  BANK_TRANSFER: { value: 'BANK_TRANSFER', label: '银行转账' },
  CHECK: { value: 'CHECK', label: '支票' },
  OTHER: { value: 'OTHER', label: '其他' }
} as const

/**
 * 获取收付款方式选项
 */
export const PAYMENT_METHOD_OPTIONS = Object.values(PAYMENT_METHOD)

/**
 * 会计科目类别
 */
export const ACCOUNT_CATEGORY = {
  ASSET: { value: 'ASSET', label: '资产' },
  LIABILITY: { value: 'LIABILITY', label: '负债' },
  EQUITY: { value: 'EQUITY', label: '所有者权益' },
  REVENUE: { value: 'REVENUE', label: '收入' },
  EXPENSE: { value: 'EXPENSE', label: '费用' }
} as const

/**
 * 获取会计科目类别选项
 */
export const ACCOUNT_CATEGORY_OPTIONS = Object.values(ACCOUNT_CATEGORY)

/**
 * 凭证类型
 */
export const VOUCHER_TYPE = {
  RECEIPT: { value: 'RECEIPT', label: '收款凭证' },
  PAYMENT: { value: 'PAYMENT', label: '付款凭证' },
  TRANSFER: { value: 'TRANSFER', label: '转账凭证' },
  ADJUST: { value: 'ADJUST', label: '调整凭证' }
} as const

/**
 * 获取凭证类型选项
 */
export const VOUCHER_TYPE_OPTIONS = Object.values(VOUCHER_TYPE)

/**
 * 单据类型
 */
export const BIZ_TYPE = {
  PURCHASE_ORDER: { value: 'PURCHASE_ORDER', label: '采购订单' },
  PURCHASE_RECEIPT: { value: 'PURCHASE_RECEIPT', label: '采购收货' },
  PURCHASE_RETURN: { value: 'PURCHASE_RETURN', label: '采购退货' },
  SALES_ORDER: { value: 'SALES_ORDER', label: '销售订单' },
  SALES_DELIVERY: { value: 'SALES_DELIVERY', label: '销售发货' },
  SALES_RETURN: { value: 'SALES_RETURN', label: '销售退货' },
  INVENTORY_ADJUSTMENT: { value: 'INVENTORY_ADJUSTMENT', label: '库存调整' },
  INVENTORY_CHECK: { value: 'INVENTORY_CHECK', label: '库存盘点' },
  INVENTORY_TRANSFER: { value: 'INVENTORY_TRANSFER', label: '库存调拨' }
} as const

/**
 * 库存调整类型
 */
export const ADJUSTMENT_TYPE = {
  IN: { value: 'IN', label: '调增' },
  OUT: { value: 'OUT', label: '调减' }
} as const

/**
 * 获取库存调整类型选项
 */
export const ADJUSTMENT_TYPE_OPTIONS = Object.values(ADJUSTMENT_TYPE)

/**
 * 分页默认配置
 */
export const PAGINATION = {
  DEFAULT_PAGE: 1,
  DEFAULT_SIZE: 10,
  PAGE_SIZES: [10, 20, 50, 100]
} as const

/**
 * 日期格式
 */
export const DATE_FORMAT = {
  DATE: 'YYYY-MM-DD',
  DATETIME: 'YYYY-MM-DD HH:mm:ss',
  TIME: 'HH:mm:ss',
  MONTH: 'YYYY-MM'
} as const

/**
 * 文件上传限制
 */
export const UPLOAD = {
  MAX_SIZE: 10 * 1024 * 1024, // 10MB
  ACCEPT_IMAGE: 'image/jpeg,image/png,image/gif',
  ACCEPT_EXCEL: 'application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  ACCEPT_DOC: 'application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  ACCEPT_PDF: 'application/pdf'
} as const

/**
 * 正则表达式
 */
export const REGEX = {
  MOBILE: /^1[3-9]\d{9}$/,
  EMAIL: /^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/,
  ID_CARD: /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/,
  URL: /^(https?:\/\/)?([\da-z.-]+)\.([a-z.]{2,6})([/\w .-]*)*\/?$/,
  NUMBER: /^-?\d+(\.\d+)?$/,
  INTEGER: /^-?\d+$/,
  POSITIVE_INTEGER: /^[1-9]\d*$/,
  MONEY: /^(0|[1-9]\d*)(\.\d{1,2})?$/
} as const

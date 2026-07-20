/**
 * 格式化金额
 * @param amount 金额
 * @param decimals 小数位数，默认2位
 * @returns 格式化后的金额字符串
 */
export function formatMoney(amount: number | string, decimals: number = 2): string {
  if (amount === null || amount === undefined) return '0.00'
  const num = typeof amount === 'string' ? parseFloat(amount) : amount
  if (isNaN(num)) return '0.00'
  return num.toFixed(decimals).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/**
 * 格式化日期
 * @param date 日期对象或字符串
 * @param format 格式，默认 'YYYY-MM-DD'
 * @returns 格式化后的日期字符串
 */
export function formatDate(date: Date | string | number, format: string = 'YYYY-MM-DD'): string {
  if (!date) return ''

  const d = typeof date === 'string' || typeof date === 'number' ? new Date(date) : date
  if (isNaN(d.getTime())) return ''

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', String(year))
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 格式化日期时间
 * @param date 日期对象或字符串
 * @returns 格式化后的日期时间字符串
 */
export function formatDateTime(date: Date | string | number): string {
  return formatDate(date, 'YYYY-MM-DD HH:mm:ss')
}

/**
 * 格式化文件大小
 * @param bytes 字节数
 * @returns 格式化后的文件大小字符串
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'

  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))

  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/**
 * 防抖函数
 * @param fn 要执行的函数
 * @param delay 延迟时间（毫秒）
 * @returns 防抖后的函数
 */
export function debounce<T extends (...args: any[]) => any>(
  fn: T,
  delay: number = 300
): (...args: Parameters<T>) => void {
  let timer: ReturnType<typeof setTimeout> | null = null

  return function(this: any, ...args: Parameters<T>) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn.apply(this, args)
    }, delay)
  }
}

/**
 * 节流函数
 * @param fn 要执行的函数
 * @param delay 延迟时间（毫秒）
 * @returns 节流后的函数
 */
export function throttle<T extends (...args: any[]) => any>(
  fn: T,
  delay: number = 300
): (...args: Parameters<T>) => void {
  let timer: ReturnType<typeof setTimeout> | null = null
  let lastTime = 0

  return function(this: any, ...args: Parameters<T>) {
    const now = Date.now()

    if (now - lastTime >= delay) {
      fn.apply(this, args)
      lastTime = now
    } else {
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => {
        fn.apply(this, args)
        lastTime = Date.now()
      }, delay - (now - lastTime))
    }
  }
}

/**
 * 深拷贝
 * @param obj 要拷贝的对象
 * @returns 拷贝后的新对象
 */
export function deepClone<T>(obj: T): T {
  if (obj === null || typeof obj !== 'object') return obj

  if (obj instanceof Date) return new Date(obj.getTime()) as any
  if (obj instanceof Array) return obj.map(item => deepClone(item)) as any
  if (obj instanceof Object) {
    const clonedObj = {} as T
    for (const key in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, key)) {
        clonedObj[key] = deepClone(obj[key])
      }
    }
    return clonedObj
  }

  return obj
}

/**
 * 下载文件
 * @param blob 文件blob
 * @param filename 文件名
 */
export function downloadFile(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/**
 * 获取状态标签类型
 * @param status 状态值
 * @returns Element Plus Tag 类型
 */
export function getStatusType(status: string): 'success' | 'warning' | 'danger' | 'info' | '' {
  const statusMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    'ACTIVE': 'success',
    'COMPLETED': 'success',
    'APPROVED': 'success',
    'POSTED': 'success',
    'PAID': 'success',
    'PENDING': 'warning',
    'DRAFT': 'info',
    'PARTIAL': 'warning',
    'REJECTED': 'danger',
    'CANCELLED': 'danger',
    'INACTIVE': 'info',
    'OVERDUE': 'danger',
    'UNPAID': 'warning'
  }

  return statusMap[status] || ''
}

/**
 * 获取状态文本
 * @param status 状态值
 * @returns 状态中文文本
 */
export function getStatusText(status: string): string {
  const statusMap: Record<string, string> = {
    'ACTIVE': '启用',
    'INACTIVE': '停用',
    'DRAFT': '草稿',
    'PENDING': '待审批',
    'APPROVED': '已审批',
    'REJECTED': '已驳回',
    'COMPLETED': '已完成',
    'CANCELLED': '已取消',
    'POSTED': '已过账',
    'PAID': '已支付',
    'UNPAID': '未支付',
    'PARTIAL': '部分支付',
    'OVERDUE': '已逾期',
    'DELIVERING': '发货中'
  }

  return statusMap[status] || status
}

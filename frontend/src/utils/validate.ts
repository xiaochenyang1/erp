/**
 * 表单验证规则
 */

type ValidationCallback = (error?: Error) => void

/**
 * 必填验证
 */
export const required = (message: string = '此项为必填项') => ({
  required: true,
  message,
  trigger: 'blur'
})

/**
 * 手机号验证
 */
export const mobile = () => ({
  pattern: /^1[3-9]\d{9}$/,
  message: '请输入正确的手机号',
  trigger: 'blur'
})

/**
 * 邮箱验证
 */
export const email = () => ({
  type: 'email' as const,
  message: '请输入正确的邮箱地址',
  trigger: 'blur'
})

/**
 * 密码验证（至少8位，包含大小写字母和数字）
 */
export const password = () => ({
  pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d@$!%*?&]{8,}$/,
  message: '密码至少8位，需包含大小写字母和数字',
  trigger: 'blur'
})

/**
 * 身份证验证
 */
export const idCard = () => ({
  pattern: /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/,
  message: '请输入正确的身份证号',
  trigger: 'blur'
})

/**
 * 数字验证
 */
export const number = () => ({
  type: 'number' as const,
  message: '请输入数字',
  trigger: 'blur'
})

/**
 * 整数验证
 */
export const integer = () => ({
  pattern: /^-?\d+$/,
  message: '请输入整数',
  trigger: 'blur'
})

/**
 * 正整数验证
 */
export const positiveInteger = () => ({
  pattern: /^[1-9]\d*$/,
  message: '请输入正整数',
  trigger: 'blur'
})

/**
 * 金额验证（最多两位小数）
 */
export const money = () => ({
  pattern: /^(0|[1-9]\d*)(\.\d{1,2})?$/,
  message: '请输入正确的金额（最多两位小数）',
  trigger: 'blur'
})

/**
 * URL验证
 */
export const url = () => ({
  type: 'url' as const,
  message: '请输入正确的URL地址',
  trigger: 'blur'
})

/**
 * 长度范围验证
 */
export const length = (min: number, max: number, message?: string) => ({
  min,
  max,
  message: message || `长度在 ${min} 到 ${max} 个字符`,
  trigger: 'blur'
})

/**
 * 最小长度验证
 */
export const minLength = (min: number, message?: string) => ({
  min,
  message: message || `最少 ${min} 个字符`,
  trigger: 'blur'
})

/**
 * 最大长度验证
 */
export const maxLength = (max: number, message?: string) => ({
  max,
  message: message || `最多 ${max} 个字符`,
  trigger: 'blur'
})

/**
 * 数值范围验证
 */
export const range = (min: number, max: number, message?: string) => ({
  validator: (rule: any, value: any, callback: ValidationCallback) => {
    if (value === '' || value === null || value === undefined) {
      callback()
      return
    }
    const num = Number(value)
    if (isNaN(num)) {
      callback(new Error('请输入数字'))
    } else if (num < min || num > max) {
      callback(new Error(message || `数值范围为 ${min} 到 ${max}`))
    } else {
      callback()
    }
  },
  trigger: 'blur'
})

/**
 * 自定义验证
 */
export const custom = (validator: (rule: any, value: any, callback: ValidationCallback) => void) => ({
  validator,
  trigger: 'blur'
})

/**
 * 确认密码验证
 */
export const confirmPassword = (passwordField: string) => ({
  validator: (rule: any, value: any, callback: ValidationCallback) => {
    if (value === '') {
      callback(new Error('请再次输入密码'))
    } else if (value !== rule.form[passwordField]) {
      callback(new Error('两次输入密码不一致'))
    } else {
      callback()
    }
  },
  trigger: 'blur'
})

/**
 * 用户名验证（字母开头，允许字母数字下划线，4-20位）
 */
export const username = () => ({
  pattern: /^[a-zA-Z][a-zA-Z0-9_]{3,19}$/,
  message: '用户名以字母开头，4-20位字母数字下划线',
  trigger: 'blur'
})

/**
 * 编码验证（字母、数字、下划线、中划线）
 */
export const code = () => ({
  pattern: /^[a-zA-Z0-9_-]+$/,
  message: '只能包含字母、数字、下划线和中划线',
  trigger: 'blur'
})

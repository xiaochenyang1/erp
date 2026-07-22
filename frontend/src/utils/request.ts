import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { readDisplayPreferences } from '@/utils/locale'

// API响应数据结构
export interface ApiResponse<T = any> {
  code: string
  message: string
  data: T
}

const normalizeParams = (params: unknown) => {
  if (!params || typeof params !== 'object' || Array.isArray(params)) {
    return params
  }

  const normalized = { ...(params as Record<string, unknown>) }
  if (normalized.page !== undefined && normalized.pageNo === undefined) {
    normalized.pageNo = normalized.page
  }
  if (normalized.size !== undefined && normalized.pageSize === undefined) {
    normalized.pageSize = normalized.size
  }
  delete normalized.page
  delete normalized.size
  return normalized
}

export const resolveAcceptLanguage = () => readDisplayPreferences().locale

const toNumber = (value: unknown, fallback = 0) => {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : fallback
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : fallback
  }
  return fallback
}

const normalizeResponseData = <T>(data: T): T => {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return data
  }

  const record = data as Record<string, unknown>
  if (Array.isArray(record.records)) {
    record.pageNo = toNumber(record.pageNo, 1)
    record.pageSize = toNumber(record.pageSize, 20)
    record.total = toNumber(record.total, 0)
  }
  return data
}

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    config.params = normalizeParams(config.params)

    // 添加Token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    config.headers['Accept-Language'] = resolveAcceptLanguage()
    return config
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 业务成功码
const SUCCESS_CODE = '0'

// ===== Token 静默续期（避免并发重复刷新）=====
let isRefreshing = false
let pendingQueue: Array<(token: string | null) => void> = []

const flushQueue = (token: string | null) => {
  pendingQueue.forEach((cb) => cb(token))
  pendingQueue = []
}

const clearAuthAndRedirect = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  flushQueue(null)
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

// 用裸 axios 调刷新接口：不走业务拦截器，也避免与 auth.ts 形成循环依赖
const doRefreshToken = (refreshToken: string): Promise<string> => {
  const baseURL = import.meta.env.VITE_APP_BASE_API || '/api'
  return axios
    .post(`${baseURL}/auth/refresh`, { refreshToken })
    .then((resp) => {
      const data = resp.data?.data
      const newToken = data?.accessToken as string | undefined
      if (!newToken) {
        throw new Error('refresh response missing accessToken')
      }
      localStorage.setItem('token', newToken)
      if (data?.refreshToken) {
        localStorage.setItem('refreshToken', data.refreshToken)
      }
      return newToken
    })
}

// 处理 401：尝试用 refreshToken 静默续期并重放原请求，失败则登出
const handleUnauthorized = (error: any): Promise<any> => {
  const originalConfig = error.config
  const refreshToken = localStorage.getItem('refreshToken')

  // 刷新接口自身 401 / 已重试过 / 无 refreshToken → 直接登出
  if (
    !originalConfig ||
    (originalConfig as any)._retried ||
    originalConfig.url?.includes('/auth/refresh') ||
    !refreshToken
  ) {
    ElMessage.error('登录已过期，请重新登录')
    clearAuthAndRedirect()
    return Promise.reject(error)
  }

  (originalConfig as any)._retried = true

  // 已有刷新在进行：排队，刷新完成后用新 token 重放
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      pendingQueue.push((token) => {
        if (token) {
          originalConfig.headers.Authorization = `Bearer ${token}`
          resolve(service(originalConfig))
        } else {
          reject(error)
        }
      })
    })
  }

  isRefreshing = true
  return doRefreshToken(refreshToken)
    .then((newToken) => {
      flushQueue(newToken)
      originalConfig.headers.Authorization = `Bearer ${newToken}`
      return service(originalConfig)
    })
    .catch((refreshErr) => {
      ElMessage.error('登录已过期，请重新登录')
      clearAuthAndRedirect()
      return Promise.reject(refreshErr)
    })
    .finally(() => {
      isRefreshing = false
    })
}

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse | Blob>) => {
    if (response.config.responseType === 'blob') {
      return response.data
    }

    const res = response.data as ApiResponse

    // 判断响应状态
    if (res.code === SUCCESS_CODE) {
      return normalizeResponseData(res.data)
    }

    // 业务错误
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // 401：走静默续期流程（成功则透明重放，调用方无感知）
    if (error.response?.status === 401) {
      return handleUnauthorized(error)
    }

    console.error('响应错误:', error)

    // 处理其它 HTTP 错误状态码
    if (error.response) {
      const status = error.response.status
      const message = error.response.data?.message || error.message

      switch (status) {
        case 403:
          ElMessage.error('权限不足')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 429:
          ElMessage.error('请求过于频繁，请稍后再试')
          break
        case 500:
          ElMessage.error('服务器内部错误')
          break
        default:
          ElMessage.error(message || '请求失败')
      }
    } else if (error.request) {
      ElMessage.error('网络请求失败，请检查网络连接')
    } else {
      ElMessage.error('请求配置错误')
    }

    return Promise.reject(error)
  }
)

// 导出请求方法
export default service

// 封装常用请求方法
export const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config) as Promise<T>
  },

  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config) as Promise<T>
  },

  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config) as Promise<T>
  },

  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config) as Promise<T>
  },

  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.patch(url, data, config) as Promise<T>
  }
}

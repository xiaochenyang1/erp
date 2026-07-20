/**
 * 通用类型定义
 */

/**
 * 分页响应
 */
export interface PageResponse<T> {
  records: T[]      // 数据列表
  total: number     // 总记录数
  pageNo: number    // 当前页码
  pageSize: number  // 每页大小
}

/**
 * 分页查询参数
 */
export interface PageQuery {
  pageNo?: number
  pageSize?: number
  page?: number
  size?: number
}

/**
 * API响应包装
 */
export interface ApiResponse<T = any> {
  code: string
  message: string
  data: T
}

/**
 * 空响应
 */
export type VoidResponse = ApiResponse<null>

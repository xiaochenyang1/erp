import { describe, it, expect, vi, beforeEach } from 'vitest'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('@/utils/request', () => ({
  request: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
    put: (...args: unknown[]) => put(...args),
    delete: vi.fn()
  }
}))

import {
  getWorkCenters,
  getRouting,
  getRoutings
} from '@/api/production'

beforeEach(() => {
  get.mockReset()
  post.mockReset()
  put.mockReset()
})

describe('production API 归一化', () => {
  it('getWorkCenters 归一化分页 records 的 id 为字符串', async () => {
    // 后端 Long ID 以 JSON 字符串下发,归一化须原样保留精度。
    get.mockResolvedValue({
      records: [{ id: '9007199254740993', workCenterCode: 'WC01', workCenterName: '装配', status: 'ACTIVE' }],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })
    const page = await getWorkCenters({ pageNo: 1, pageSize: 20 })
    expect(page.records[0].id).toBe('9007199254740993')
    expect(typeof page.records[0].id).toBe('string')
  })

  it('getRouting 归一化 id/bomId 及工序 workCenterId 为字符串、standardMinutes 为数值', async () => {
    get.mockResolvedValue({
      id: '9007199254740993',
      routingCode: 'RT01',
      routingName: '标准工艺',
      bomId: '9007199254740995',
      status: 'ACTIVE',
      operations: [
        { id: '1', lineNo: 1, operationCode: 'OP1', operationName: '切割', workCenterId: '9007199254740997', standardMinutes: '12.50' }
      ]
    })
    const routing = await getRouting('9007199254740993')
    expect(routing.id).toBe('9007199254740993')
    expect(routing.bomId).toBe('9007199254740995')
    expect(routing.operations[0].workCenterId).toBe('9007199254740997')
    expect(routing.operations[0].standardMinutes).toBe(12.5)
    expect(typeof routing.operations[0].standardMinutes).toBe('number')
  })

  it('getRoutings 对空 operations 归一化为数组', async () => {
    get.mockResolvedValue({
      records: [{ id: 1, routingCode: 'RT', routingName: 'x', bomId: 2, status: 'ACTIVE' }],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })
    const page = await getRoutings({ pageNo: 1, pageSize: 20 })
    expect(page.records[0].operations).toEqual([])
  })
})

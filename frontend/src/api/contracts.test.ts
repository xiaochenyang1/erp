import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('@/utils/request', () => ({
  request: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
    put: (...args: unknown[]) => put(...args)
  }
}))

import { approveContract, createContract, getContract, getContracts } from '@/api/contracts'

beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset()
})

describe('合同 API 归一化', () => {
  it('分页和详情把雪花 id、数量和金额归一化', async () => {
    get.mockResolvedValue({
      pageNo: 1, pageSize: 20, total: 1,
      records: [{ id: '9007199254740993', contractNo: 'CT1', contractType: 'SALES', customerId: '9007199254740995', contractName: '合同', signedDate: '2026-08-26', effectiveFrom: '2026-08-26', status: 'DRAFT', totalAmount: '20.00', lines: [{ productId: '9007199254740997', quantity: '2', unitPrice: '10', amount: '20' }] }]
    })
    const page = await getContracts({ pageNo: 1, pageSize: 20 })
    expect(page.records[0].id).toBe('9007199254740993')
    expect(page.records[0].customerId).toBe('9007199254740995')
    expect(page.records[0].lines[0].productId).toBe('9007199254740997')
    expect(page.records[0].lines[0].quantity).toBe(2)
    expect(page.records[0].totalAmount).toBe(20)
  })

  it('创建和审批使用合同生命周期路由', async () => {
    const record = { id: '1', contractNo: 'CT1', contractType: 'SALES', contractName: '合同', signedDate: '2026-08-26', effectiveFrom: '2026-08-26', status: 'DRAFT', totalAmount: 0, lines: [] }
    post.mockResolvedValue(record)
    await createContract({ contractType: 'SALES', customerId: '2', contractName: '合同', signedDate: '2026-08-26', effectiveFrom: '2026-08-26', lines: [] })
    await approveContract('1')
    expect(post).toHaveBeenNthCalledWith(1, '/contracts', expect.any(Object))
    expect(post).toHaveBeenNthCalledWith(2, '/contracts/1/approve')
  })

  it('详情调用带路径参数', async () => {
    get.mockResolvedValue({ id: '7', contractNo: 'CT7', contractType: 'PURCHASE', contractName: '采购合同', signedDate: '2026-08-26', effectiveFrom: '2026-08-26', status: 'ACTIVE', totalAmount: 0, lines: [] })
    const result = await getContract('7')
    expect(get).toHaveBeenCalledWith('/contracts/7')
    expect(result.id).toBe('7')
  })
})

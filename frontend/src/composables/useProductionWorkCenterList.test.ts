import { describe, expect, it, vi } from 'vitest'

import type { WorkCenter } from '@/api/production'
import { useProductionWorkCenterList } from './useProductionWorkCenterList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.name != null ? `${key}:${params.name}` : key

const row = (overrides: Partial<WorkCenter> = {}) =>
  ({
    id: 'wc1',
    workCenterCode: 'WC01',
    workCenterName: '装配',
    status: 'ACTIVE',
    ...overrides
  }) as WorkCenter

const createList = (overrides: Partial<Parameters<typeof useProductionWorkCenterList>[1]> = {}) =>
  useProductionWorkCenterList(t, {
    getWorkCenters: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getWorkCenter: vi.fn(async () => row({ remark: 'detail' })),
    enableWorkCenter: vi.fn(async () => ({})),
    disableWorkCenter: vi.fn(async () => ({})),
    printWorkCenter: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('production work center list', () => {
  it('sends filled filters and resets paging on query', async () => {
    const getWorkCenters = vi.fn(async () => ({ records: [], total: 2 } as any))
    const list = createList({ getWorkCenters })

    list.pagination.page = 3
    list.queryForm.keyword = '装配'
    list.queryForm.status = 'ACTIVE'
    await list.handleQuery()

    expect(getWorkCenters).toHaveBeenCalledWith({
      keyword: '装配',
      status: 'ACTIVE',
      pageNo: 1,
      pageSize: 20
    })
  })

  it('pages and resets independently', async () => {
    const getWorkCenters = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getWorkCenters })

    await list.handlePageChange(2)
    expect(list.pagination.page).toBe(2)
    await list.handleSizeChange(50)
    expect(list.pagination.size).toBe(50)
    expect(list.pagination.page).toBe(1)

    list.queryForm.keyword = 'x'
    await list.handleReset()
    expect(list.queryForm.keyword).toBe('')
  })

  it('prints, enables and disables work centers', async () => {
    const printWorkCenter = vi.fn()
    const enableWorkCenter = vi.fn(async () => ({}))
    const disableWorkCenter = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ printWorkCenter, enableWorkCenter, disableWorkCenter, onSuccess })

    expect(await list.handlePrint(row())).toBe(true)
    expect(printWorkCenter).toHaveBeenCalledWith(expect.objectContaining({ remark: 'detail' }))

    expect(await list.handleEnable(row({ status: 'DISABLED' }))).toBe(true)
    expect(enableWorkCenter).toHaveBeenCalledWith('wc1')
    expect(onSuccess).toHaveBeenCalledWith('productionWorkCenter.message.enabled')

    expect(await list.handleDisable(row())).toBe(true)
    expect(disableWorkCenter).toHaveBeenCalledWith('wc1')
  })
})

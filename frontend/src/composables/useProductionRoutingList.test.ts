import { describe, expect, it, vi } from 'vitest'

import type { Routing } from '@/api/production'
import { useProductionRoutingList } from './useProductionRoutingList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.name != null ? `${key}:${params.name}` : key

const row = (overrides: Partial<Routing> = {}) =>
  ({
    id: 'r1',
    routingCode: 'RT001',
    routingName: '装配路线',
    status: 'ACTIVE',
    operations: [],
    ...overrides
  }) as Routing

const createList = (overrides: Partial<Parameters<typeof useProductionRoutingList>[1]> = {}) =>
  useProductionRoutingList(t, {
    getRoutings: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getRouting: vi.fn(async () => row({ remark: 'detail' })),
    getWorkCenters: vi.fn(async () => ({ records: [{ id: 'wc1' }], total: 1 } as any)),
    getBOMs: vi.fn(async () => ({ records: [{ id: 'b1' }], total: 1 } as any)),
    enableRouting: vi.fn(async () => ({})),
    disableRouting: vi.fn(async () => ({})),
    printRouting: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('production routing list', () => {
  it('sends filled filters and resets paging on query', async () => {
    const getRoutings = vi.fn(async () => ({ records: [], total: 3 } as any))
    const list = createList({ getRoutings })

    list.pagination.page = 5
    list.queryForm.keyword = 'RT'
    list.queryForm.status = 'ACTIVE'
    await list.handleQuery()

    expect(getRoutings).toHaveBeenCalledWith({
      keyword: 'RT',
      status: 'ACTIVE',
      pageNo: 1,
      pageSize: 20
    })
    expect(list.pagination.total).toBe(3)

    list.queryForm.keyword = ''
    list.queryForm.status = ''
    await list.handleQuery()
    expect(getRoutings).toHaveBeenLastCalledWith({
      keyword: undefined,
      status: undefined,
      pageNo: 1,
      pageSize: 20
    })
  })

  it('keeps the page when paging and returns to page 1 on size change or reset', async () => {
    const getRoutings = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getRoutings })

    await list.handlePageChange(4)
    expect(list.pagination.page).toBe(4)
    expect(getRoutings).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 4 }))

    await list.handleSizeChange(50)
    expect(list.pagination.size).toBe(50)
    expect(list.pagination.page).toBe(1)

    list.queryForm.keyword = 'X'
    list.pagination.page = 3
    await list.handleReset()
    expect(list.queryForm.keyword).toBe('')
    expect(list.pagination.page).toBe(1)
  })

  it('loads work-center and BOM options with ACTIVE page contracts', async () => {
    const getWorkCenters = vi.fn(async () => ({ records: [{ id: 'wc1' }], total: 1 } as any))
    const getBOMs = vi.fn(async () => ({ records: [{ id: 'b1' }], total: 1 } as any))
    const list = createList({ getWorkCenters, getBOMs })

    await list.loadOptions()
    expect(getWorkCenters).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(getBOMs).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(list.workCenterOptions.value).toEqual([{ id: 'wc1' }])
    expect(list.bomOptions.value).toEqual([{ id: 'b1' }])
  })

  it('reports option and list load failures', async () => {
    const onError = vi.fn()
    const list = createList({
      getWorkCenters: vi.fn(async () => { throw new Error('boom') }),
      getRoutings: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await list.loadOptions()
    expect(onError).toHaveBeenCalledWith('productionRouting.message.optionsLoadFailed')

    await list.loadData()
    expect(onError).toHaveBeenCalledWith('productionRouting.message.loadFailed')
    expect(list.loading.value).toBe(false)
  })

  it('opens detail and prints decorated routing details', async () => {
    const printRouting = vi.fn()
    const decoratePrint = vi.fn((doc: Routing) => ({ ...doc, routingName: '打印名' }))
    const list = createList({ printRouting, decoratePrint })

    expect(await list.handleView(row())).toBe(true)
    expect(list.viewDialogVisible.value).toBe(true)
    expect(list.viewData.value.remark).toBe('detail')

    expect(await list.handlePrint(row())).toBe(true)
    expect(printRouting).toHaveBeenCalledWith(expect.objectContaining({ routingName: '打印名' }))
  })

  it('enables and disables after confirmation, and aborts when cancelled', async () => {
    const enableRouting = vi.fn(async () => ({}))
    const disableRouting = vi.fn(async () => ({}))
    const confirm = vi.fn(async () => true)
    const onSuccess = vi.fn()
    const list = createList({ enableRouting, disableRouting, confirm, onSuccess })

    expect(await list.handleEnable(row())).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'productionRouting.message.enableConfirm:装配路线',
      'productionRouting.message.prompt',
      { type: 'warning' }
    )
    expect(enableRouting).toHaveBeenCalledWith('r1')
    expect(onSuccess).toHaveBeenCalledWith('productionRouting.message.enabled')

    expect(await list.handleDisable(row())).toBe(true)
    expect(disableRouting).toHaveBeenCalledWith('r1')
    expect(onSuccess).toHaveBeenCalledWith('productionRouting.message.disabled')

    const cancelled = createList({
      confirm: vi.fn(async () => { throw new Error('cancel') }),
      enableRouting
    })
    enableRouting.mockClear()
    expect(await cancelled.handleEnable(row())).toBe(false)
    expect(enableRouting).not.toHaveBeenCalled()
  })
})

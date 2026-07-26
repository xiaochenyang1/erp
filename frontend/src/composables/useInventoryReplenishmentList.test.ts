import { describe, expect, it, vi } from 'vitest'

import type { InventoryReplenishmentSuggestion } from '@/api/inventory'
import { useInventoryReplenishmentList } from './useInventoryReplenishmentList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.no != null ? `${key}:${params.no}` : key

const row = (overrides: Partial<InventoryReplenishmentSuggestion> = {}) =>
  ({
    id: 's1',
    suggestionNo: 'RS001',
    status: 'DRAFT',
    supplierId: 'sup1',
    suggestedQty: 10,
    shortageQtySnapshot: 5,
    ...overrides
  }) as InventoryReplenishmentSuggestion

const createList = (overrides: Partial<Parameters<typeof useInventoryReplenishmentList>[1]> = {}) =>
  useInventoryReplenishmentList(t, {
    getSuggestions: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getWarehouses: vi.fn(async () => ({ records: [{ id: 'w1' }], total: 1 } as any)),
    getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
    getSuppliers: vi.fn(async () => ({ records: [{ id: 's1' }], total: 1 } as any)),
    cancelSuggestion: vi.fn(async () => ({})),
    convertSuggestion: vi.fn(async () => row({ purchaseOrderNo: 'PO1' })),
    confirm: vi.fn(async () => true),
    prompt: vi.fn(async () => ({ value: '原因' })),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('inventory replenishment list', () => {
  it('sends filled filters and resets paging on query', async () => {
    const getSuggestions = vi.fn(async () => ({ records: [], total: 2 } as any))
    const list = createList({ getSuggestions })

    list.queryParams.pageNo = 4
    list.queryParams.suggestionNo = 'RS'
    list.queryParams.status = 'DRAFT'
    list.queryParams.warehouseId = 'w1'
    await list.handleQuery()

    expect(getSuggestions).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 20,
      suggestionNo: 'RS',
      status: 'DRAFT',
      warehouseId: 'w1',
      productId: undefined,
      supplierId: undefined,
      createdTimeFrom: undefined,
      createdTimeTo: undefined
    })
  })

  it('loads options with ACTIVE page contracts and pages independently', async () => {
    const getWarehouses = vi.fn(async () => ({ records: [{ id: 'w1' }], total: 1 } as any))
    const getProducts = vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any))
    const getSuppliers = vi.fn(async () => ({ records: [{ id: 's1' }], total: 1 } as any))
    const getSuggestions = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getWarehouses, getProducts, getSuppliers, getSuggestions })

    await list.loadOptions()
    expect(getWarehouses).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(list.warehouses.value).toEqual([{ id: 'w1' }])

    await list.handlePageChange(3)
    expect(list.queryParams.pageNo).toBe(3)
    await list.handleSizeChange(50)
    expect(list.queryParams.pageSize).toBe(50)
    expect(list.queryParams.pageNo).toBe(1)

    list.handleCreatedRangeChange(['2026-07-01T00:00:00', '2026-07-02T00:00:00'])
    expect(list.queryParams.createdTimeFrom).toBe('2026-07-01T00:00:00')
  })

  it('cancels and converts after confirmation, and blocks convert without supplier', async () => {
    const cancelSuggestion = vi.fn(async () => ({}))
    const convertSuggestion = vi.fn(async () => row({ purchaseOrderNo: 'PO9' }))
    const onSuccess = vi.fn()
    const onWarning = vi.fn()
    const list = createList({ cancelSuggestion, convertSuggestion, onSuccess, onWarning })

    expect(await list.handleCancel(row())).toBe(true)
    expect(cancelSuggestion).toHaveBeenCalledWith('s1', '原因')
    expect(onSuccess).toHaveBeenCalledWith('inventoryReplenishment.message.cancelled')

    expect(await list.handleConvert(row({ supplierId: undefined }))).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('inventoryReplenishment.message.supplierRequired')

    expect(await list.handleConvert(row())).toBe(true)
    expect(convertSuggestion).toHaveBeenCalledWith('s1')
    expect(onSuccess).toHaveBeenCalledWith('inventoryReplenishment.message.converted:PO9')
  })
})

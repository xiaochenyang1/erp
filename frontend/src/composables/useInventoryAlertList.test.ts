import { describe, expect, it, vi } from 'vitest'

import type { InventoryAlert, InventoryAlertRule } from '@/api/inventory'
import { useInventoryAlertList } from './useInventoryAlertList'

const t = (key: string) => key

describe('inventory alert list', () => {
  const createList = (overrides: Partial<Parameters<typeof useInventoryAlertList>[1]> = {}) =>
    useInventoryAlertList(t, {
      getAlerts: vi.fn(async () => ({
        records: [
          { id: 'a1', alertType: 'OUT_OF_STOCK', shortageQty: 2, status: 'ACTIVE', warehouseId: 'w1', productId: 'p1' },
          { id: 'a2', alertType: 'LOW_STOCK', shortageQty: 3, status: 'ACTIVE', warehouseId: 'w1', productId: 'p2' }
        ],
        total: 2
      } as any)),
      getRules: vi.fn(async () => ([
        { id: 'r1', warehouseId: 'w1', productId: 'p1', minQty: 5 }
      ] as any)),
      createRule: vi.fn(async () => ({})),
      updateRule: vi.fn(async () => ({})),
      enableRule: vi.fn(async () => ({})),
      disableRule: vi.fn(async () => ({})),
      ignoreAlert: vi.fn(async () => ({})),
      resolveAlert: vi.fn(async () => ({})),
      reactivateAlert: vi.fn(async () => ({})),
      createSuggestion: vi.fn(async () => ({})),
      getWarehouses: vi.fn(async () => ({ records: [{ id: 'w1' }], total: 1 } as any)),
      getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
      getSuppliers: vi.fn(async () => ({ records: [{ id: 's1' }], total: 1 } as any)),
      confirm: vi.fn(async () => true),
      prompt: vi.fn(async () => ({ value: 'note' })),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onWarning: vi.fn(),
      ...overrides
    })

  it('loads alerts/statistics and supports query reset', async () => {
    const list = createList()
    await list.loadData()
    expect(list.tableData.value).toHaveLength(2)
    expect(list.statistics.outOfStock).toBe(1)
    expect(list.statistics.lowStock).toBe(1)
    expect(list.statistics.shortageQty).toBe(5)

    list.queryParams.alertType = 'LOW_STOCK'
    list.handleQuery()
    await Promise.resolve()
    expect(list.queryParams.pageNo).toBe(1)

    list.handleReset()
    await Promise.resolve()
    expect(list.queryParams.alertType).toBe('')
    expect(list.queryParams.status).toBe('ACTIVE')
  })

  it('manages rules and alert disposition actions', async () => {
    const enableRule = vi.fn(async () => ({}))
    const ignoreAlert = vi.fn(async () => ({}))
    const resolveAlert = vi.fn(async () => ({}))
    const reactivateAlert = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({
      enableRule,
      ignoreAlert,
      resolveAlert,
      reactivateAlert,
      onSuccess
    })

    await list.openRulesDrawer()
    expect(list.ruleRows.value).toHaveLength(1)

    await list.handleToggleRule({ id: 'r1' } as InventoryAlertRule, true)
    await list.handleDispose({
      id: 'a1',
      warehouseId: 'w1',
      productId: 'p1',
      warehouseName: 'Main',
      productName: 'Good'
    } as InventoryAlert, 'IGNORED')
    await list.handleDispose({
      id: 'a1',
      warehouseId: 'w1',
      productId: 'p1',
      warehouseName: 'Main',
      productName: 'Good'
    } as InventoryAlert, 'RESOLVED')
    await list.handleReactivate({
      id: 'a1',
      warehouseId: 'w1',
      productId: 'p1',
      warehouseName: 'Main',
      productName: 'Good'
    } as InventoryAlert)

    expect(enableRule).toHaveBeenCalledWith('r1')
    expect(ignoreAlert).toHaveBeenCalledWith(expect.objectContaining({ warehouseId: 'w1', productId: 'p1' }))
    expect(resolveAlert).toHaveBeenCalledWith(expect.objectContaining({ warehouseId: 'w1', productId: 'p1' }))
    expect(reactivateAlert).toHaveBeenCalledWith(expect.objectContaining({ warehouseId: 'w1', productId: 'p1' }))
    expect(onSuccess).toHaveBeenCalledWith('inventoryAlerts.message.ruleEnabled')
    expect(onSuccess).toHaveBeenCalledWith('inventoryAlerts.message.ignored')
    expect(onSuccess).toHaveBeenCalledWith('inventoryAlerts.message.resolved')
    expect(onSuccess).toHaveBeenCalledWith('inventoryAlerts.message.reactivated')
  })
})

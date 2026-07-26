import { computed } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Warehouse } from '@/api/masterdata'
import { useWarehouseList } from './useWarehouseList'

const texts = computed(() => ({
  loadFailed: 'load failed',
  loadOptionsFailed: 'options failed',
  loadDetailFailed: 'detail failed',
  confirmTitle: 'Confirm',
  confirmDelete: 'Delete {name}?',
  confirmEnable: 'Enable {name}?',
  delete: 'Delete',
  enable: 'Enable',
  cancel: 'Cancel',
  deleteSuccess: 'deleted',
  deleteFailed: 'delete failed',
  enableSuccess: 'enabled',
  enableFailed: 'enable failed',
  exportSuccess: 'exported',
  exportFailed: 'export failed',
  exportFilename: 'warehouses'
}))

describe('warehouse list', () => {
  const createList = (overrides: Partial<Parameters<typeof useWarehouseList>[1]> = {}) =>
    useWarehouseList(texts, {
      getWarehouses: vi.fn(async () => ({
        records: [{
          id: '1',
          warehouseCode: 'W001',
          warehouseName: 'Main',
          status: 'ACTIVE'
        }],
        total: 1
      } as any)),
      getWarehouse: vi.fn(async () => ({
        id: '1',
        warehouseCode: 'W001',
        warehouseName: 'Main',
        status: 'ACTIVE'
      } as Warehouse)),
      getStockSummary: vi.fn(async () => ({
        warehouseId: '1',
        skuCount: 3,
        qtyOnHand: 10,
        qtyReserved: 2,
        qtyAvailable: 8,
        amountOnHand: 100
      } as any)),
      enableWarehouse: vi.fn(async () => ({})),
      deleteWarehouse: vi.fn(async () => ({})),
      exportWarehouses: vi.fn(async () => new Blob(['csv'])),
      getDeptTree: vi.fn(async () => ([{ id: 'd1', name: 'Ops', children: [] }] as any)),
      getUsers: vi.fn(async () => ({
        records: [{ id: 'u1', username: 'alice', realName: 'Alice' }],
        total: 1
      } as any)),
      confirm: vi.fn(async () => true),
      interpolate: (template, params) => template.replace(/\{(\w+)\}/g, (_, key) => String(params[key] ?? '')),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      ...overrides
    })

  it('loads normalized warehouses and supports search/reset/pagination', async () => {
    const list = createList()
    await list.loadData()

    expect(list.tableData.value[0]).toEqual(expect.objectContaining({
      code: 'W001',
      name: 'Main'
    }))
    expect(list.total.value).toBe(1)

    list.searchForm.code = 'W001'
    list.handleSearch()
    await Promise.resolve()
    expect(list.searchForm.pageNo).toBe(1)

    list.handlePageChange(2, 40)
    await Promise.resolve()
    expect(list.searchForm.pageNo).toBe(2)
    expect(list.searchForm.pageSize).toBe(40)

    list.handleReset()
    await Promise.resolve()
    expect(list.searchForm.code).toBe('')
  })

  it('loads options, views stock summary, and enables/deletes warehouses', async () => {
    const enableWarehouse = vi.fn(async () => ({}))
    const deleteWarehouse = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ enableWarehouse, deleteWarehouse, onSuccess })

    await list.loadOptions()
    expect(list.deptOptions.value).toHaveLength(1)
    expect(list.userOptions.value).toHaveLength(1)

    await list.handleView({ id: '1' } as Warehouse)
    expect(list.detailVisible.value).toBe(true)
    expect(list.currentRow.value?.code).toBe('W001')
    expect(list.stockSummary.value?.qtyAvailable).toBe(8)

    await list.handleEnable({ id: '1', name: 'Main' } as Warehouse)
    await list.handleDelete({ id: '1', name: 'Main' } as Warehouse)
    expect(enableWarehouse).toHaveBeenCalledWith('1')
    expect(deleteWarehouse).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('enabled')
    expect(onSuccess).toHaveBeenCalledWith('deleted')
  })

  it('exports warehouses', async () => {
    const exportWarehouses = vi.fn(async () => new Blob(['csv']))
    const onSuccess = vi.fn()
    const list = createList({ exportWarehouses, onSuccess })

    await list.handleExport()
    expect(exportWarehouses).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('exported')
  })
})

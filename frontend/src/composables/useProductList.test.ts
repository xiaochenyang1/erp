import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Product } from '@/api/masterdata'
import { useProductList } from './useProductList'

const texts = computed(() => ({
  confirm: 'Confirm',
  cancel: 'Cancel',
  loadFailed: 'load failed',
  loadDetailFailed: 'detail failed',
  confirmTitle: 'Confirm',
  confirmDelete: 'Delete {name}?',
  confirmEnable: 'Enable {name}?',
  deleteSuccess: 'deleted',
  deleteFailed: 'delete failed',
  enableSuccess: 'enabled',
  enableFailed: 'enable failed',
  exportSuccess: 'exported',
  exportFailed: 'export failed',
  exportFilename: 'products',
  selectedExportFilename: 'selected-{count}',
  batchEnableTitle: 'Batch enable',
  batchDisableTitle: 'Batch disable',
  batchEnableConfirm: 'Enable {count}?',
  batchDisableConfirm: 'Disable {count}?',
  batchEnableSuccess: 'Enabled {count}',
  batchDisableSuccess: 'Disabled {count}',
  batchEnablePartial: 'Enabled {success}, failed {failedCount}: {failed}',
  batchDisablePartial: 'Disabled {success}, failed {failedCount}: {failed}',
  productCode: 'Code',
  productName: 'Name',
  productCategory: 'Category',
  specification: 'Spec',
  unit: 'Unit',
  auxUnit: 'Aux',
  conversionFactor: 'Factor',
  salePrice: 'Sale',
  costPrice: 'Cost',
  status: 'Status',
  active: 'Active',
  inactive: 'Inactive'
}))

describe('product list', () => {
  const createList = (overrides: Partial<Parameters<typeof useProductList>[1]> = {}) =>
    useProductList(texts, {
      getProducts: vi.fn(async () => ({
        records: [{
          id: '1',
          productCode: 'P001',
          productName: 'Item',
          specification: 'S1',
          unitName: 'PCS',
          salePrice: 10,
          purchasePrice: 8,
          status: 'ACTIVE'
        }],
        total: 1
      } as any)),
      getProduct: vi.fn(async () => ({
        id: '1',
        productCode: 'P001',
        productName: 'Item',
        status: 'ACTIVE'
      } as Product)),
      getStockSummary: vi.fn(async () => ({
        productId: '1',
        qtyOnHand: 5,
        qtyReserved: 1,
        qtyAvailable: 4,
        warehouseCount: 2,
        amountOnHand: 40
      } as any)),
      enableProduct: vi.fn(async () => ({})),
      deleteProduct: vi.fn(async () => ({})),
      exportProducts: vi.fn(async () => new Blob(['csv'])),
      confirm: vi.fn(async () => true),
      interpolate: (template, params) => Object.entries(params).reduce(
        (result, [key, value]) => result.replace(`{${key}}`, String(value)),
        template
      ),
      joinNames: (items) => items.join(', '),
      formatUnit: (value) => value || '-',
      formatCurrency: (value) => String(value ?? ''),
      locale: ref('en-US'),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onWarning: vi.fn(),
      ...overrides
    })

  it('loads normalized products and supports search/reset/pagination', async () => {
    const list = createList()
    await list.loadData()

    expect(list.tableData.value[0]).toEqual(expect.objectContaining({
      code: 'P001',
      name: 'Item',
      specifications: 'S1',
      unit: 'PCS',
      unitPrice: 10,
      costPrice: 8
    }))
    expect(list.total.value).toBe(1)

    list.searchForm.code = 'P001'
    list.handleSearch()
    await Promise.resolve()
    expect(list.searchForm.pageNo).toBe(1)

    list.handlePageChange(2, 50)
    await Promise.resolve()
    expect(list.searchForm.pageNo).toBe(2)
    expect(list.searchForm.pageSize).toBe(50)

    list.handleReset()
    await Promise.resolve()
    expect(list.searchForm.code).toBe('')
  })

  it('views product detail with stock summary and enables/deletes rows', async () => {
    const enableProduct = vi.fn(async () => ({}))
    const deleteProduct = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ enableProduct, deleteProduct, onSuccess })

    await list.handleView({ id: '1' } as Product)
    expect(list.detailVisible.value).toBe(true)
    expect(list.currentRow.value?.code).toBe('P001')
    expect(list.stockSummary.value?.qtyAvailable).toBe(4)

    await list.handleEnable({ id: '1', name: 'Item' } as Product)
    await list.handleDelete({ id: '1', name: 'Item' } as Product)
    expect(enableProduct).toHaveBeenCalledWith('1')
    expect(deleteProduct).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('enabled')
    expect(onSuccess).toHaveBeenCalledWith('deleted')
  })

  it('runs batch enable and export actions', async () => {
    const enableProduct = vi.fn(async () => ({}))
    const exportProducts = vi.fn(async () => new Blob(['csv']))
    const onSuccess = vi.fn()
    const list = createList({ enableProduct, exportProducts, onSuccess })
    list.selectedRows.value = [
      { id: '1', name: 'A', status: 'INACTIVE' } as Product,
      { id: '2', name: 'B', status: 'INACTIVE' } as Product
    ]

    await list.handleBatchEnable()
    expect(enableProduct).toHaveBeenCalledTimes(2)

    await list.handleExport()
    expect(exportProducts).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('exported')
  })
})

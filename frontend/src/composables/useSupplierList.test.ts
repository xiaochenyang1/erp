import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Supplier } from '@/api/masterdata'
import { useSupplierList } from './useSupplierList'

const texts = computed(() => ({
  loadFailed: 'load failed',
  loadDetailFailed: 'detail failed',
  confirmTitle: 'Confirm',
  confirmDelete: 'Delete {name}?',
  confirmEnable: 'Enable {name}?',
  delete: 'Delete',
  enable: 'Enable',
  cancel: 'Cancel',
  confirm: 'OK',
  deleteSuccess: 'deleted',
  deleteFailed: 'delete failed',
  enableSuccess: 'enabled',
  enableFailed: 'enable failed',
  exportSuccess: 'exported',
  exportFailed: 'export failed',
  exportFilename: 'suppliers',
  selectedExportFilename: 'suppliers-{count}',
  batchEnable: 'Batch enable',
  batchDisable: 'Batch disable',
  exportSelected: 'Export selected',
  batchEnableTitle: 'Batch enable',
  batchDisableTitle: 'Batch disable',
  batchEnableConfirm: 'Enable {count}?',
  batchDisableConfirm: 'Disable {count}?',
  batchEnableSuccess: 'enabled {count}',
  batchDisableSuccess: 'disabled {count}',
  batchEnablePartial: 'enabled {success}, failed {failedCount}: {failed}',
  batchDisablePartial: 'disabled {success}, failed {failedCount}: {failed}',
  supplierCode: 'Code',
  supplierName: 'Name',
  contact: 'Contact',
  phone: 'Phone',
  email: 'Email',
  settlementMethod: 'Settlement',
  creditPeriod: 'Credit period',
  status: 'Status',
  active: 'Active',
  inactive: 'Inactive'
}))

describe('supplier list', () => {
  const createList = (overrides: Partial<Parameters<typeof useSupplierList>[1]> = {}) =>
    useSupplierList(texts, {
      getSuppliers: vi.fn(async () => ({
        records: [{
          id: '1',
          supplierCode: 'S001',
          supplierName: 'Vendor',
          contactName: 'Bob',
          contactPhone: '456',
          status: 'ACTIVE'
        }],
        total: 1
      } as any)),
      getSupplier: vi.fn(async () => ({
        id: '1',
        supplierCode: 'S001',
        supplierName: 'Vendor',
        status: 'ACTIVE'
      } as Supplier)),
      getPayableExposure: vi.fn(async () => ({
        supplierId: '1',
        outstandingPayable: 100,
        openPurchaseOrderAmount: 50,
        totalExposure: 150
      } as any)),
      enableSupplier: vi.fn(async () => ({})),
      deleteSupplier: vi.fn(async () => ({})),
      exportSuppliers: vi.fn(async () => new Blob(['csv'])),
      confirm: vi.fn(async () => true),
      interpolate: (template, params) => template.replace(/\{(\w+)\}/g, (_, key) => String(params[key] ?? '')),
      joinNames: (items: string[]) => items.join(', '),
      locale: ref('en-US'),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onWarning: vi.fn(),
      ...overrides
    })

  it('loads normalized suppliers and supports search/reset/pagination', async () => {
    const list = createList()
    await list.loadData()

    expect(list.tableData.value[0]).toEqual(expect.objectContaining({
      code: 'S001',
      name: 'Vendor',
      contact: 'Bob',
      mobile: '456'
    }))
    expect(list.total.value).toBe(1)

    list.searchForm.code = 'S001'
    list.handleSearch()
    await Promise.resolve()
    expect(list.searchForm.pageNo).toBe(1)

    list.handlePageChange(3, 30)
    await Promise.resolve()
    expect(list.searchForm.pageNo).toBe(3)
    expect(list.searchForm.pageSize).toBe(30)

    list.handleReset()
    await Promise.resolve()
    expect(list.searchForm.code).toBe('')
  })

  it('views payable exposure and enables/deletes suppliers', async () => {
    const enableSupplier = vi.fn(async () => ({}))
    const deleteSupplier = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ enableSupplier, deleteSupplier, onSuccess })

    await list.handleView({ id: '1' } as Supplier)
    expect(list.detailVisible.value).toBe(true)
    expect(list.currentRow.value?.code).toBe('S001')
    expect(list.payableExposure.value?.totalExposure).toBe(150)

    await list.handleEnable({ id: '1', name: 'Vendor' } as Supplier)
    await list.handleDelete({ id: '1', name: 'Vendor' } as Supplier)
    expect(enableSupplier).toHaveBeenCalledWith('1')
    expect(deleteSupplier).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('enabled')
    expect(onSuccess).toHaveBeenCalledWith('deleted')
  })

  it('exports suppliers', async () => {
    const exportSuppliers = vi.fn(async () => new Blob(['csv']))
    const onSuccess = vi.fn()
    const list = createList({ exportSuppliers, onSuccess })

    await list.handleExport()
    expect(exportSuppliers).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('exported')
  })

  it('runs batch enable and export-selected actions', async () => {
    const enableSupplier = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ enableSupplier, onSuccess })

    list.selectedRows.value = [
      { id: '1', name: 'Vendor', code: 'S001' } as Supplier,
      { id: '2', name: 'Acme Co', code: 'S002' } as Supplier
    ]

    await list.handleBatchEnable()
    expect(enableSupplier).toHaveBeenCalledWith('1')
    expect(enableSupplier).toHaveBeenCalledWith('2')
    expect(onSuccess).toHaveBeenCalledWith('enabled 2')
  })
})

import { computed } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Customer } from '@/api/masterdata'
import { useCustomerList } from './useCustomerList'

const texts = computed(() => ({
  loadFailed: 'load failed',
  loadDetailFailed: 'detail failed',
  confirmTitle: 'Confirm',
  confirmDelete: 'Delete {name}?',
  confirmEnable: 'Enable {name}?',
  delete: 'Delete',
  enable: 'Enable',
  deleteSuccess: 'deleted',
  deleteFailed: 'delete failed',
  enableSuccess: 'enabled',
  enableFailed: 'enable failed',
  exportSuccess: 'exported',
  exportFailed: 'export failed',
  exportFilename: 'customers'
}))

describe('customer list', () => {
  const createList = (overrides: Partial<Parameters<typeof useCustomerList>[1]> = {}) =>
    useCustomerList(texts, {
      getCustomers: vi.fn(async () => ({
        records: [{
          id: '1',
          customerCode: 'C001',
          customerName: 'Acme',
          contactName: 'Ann',
          contactPhone: '123',
          type: 'COMPANY',
          status: 'ACTIVE'
        }],
        total: 1
      } as any)),
      getCustomer: vi.fn(async () => ({
        id: '1',
        customerCode: 'C001',
        customerName: 'Acme',
        status: 'ACTIVE'
      } as Customer)),
      getCreditExposure: vi.fn(async () => ({
        customerId: '1',
        creditLimit: 1000,
        outstandingReceivable: 200,
        openOrderExposure: 100,
        totalExposure: 300,
        availableCredit: 700,
        unlimited: false,
        exceeded: false
      })),
      enableCustomer: vi.fn(async () => ({})),
      deleteCustomer: vi.fn(async () => ({})),
      exportCustomers: vi.fn(async () => new Blob(['csv'])),
      confirm: vi.fn(async () => true),
      cancelLabel: () => 'Cancel',
      interpolate: (template, params) => template.replace(/\{(\w+)\}/g, (_, key) => String(params[key] ?? '')),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      ...overrides
    })

  it('loads normalized customers and supports search/reset/pagination', async () => {
    const list = createList()
    await list.loadData()

    expect(list.tableData.value[0]).toEqual(expect.objectContaining({
      code: 'C001',
      name: 'Acme',
      contact: 'Ann',
      mobile: '123'
    }))
    expect(list.total.value).toBe(1)

    list.searchForm.code = 'C001'
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

  it('views credit exposure and enables/deletes customers', async () => {
    const enableCustomer = vi.fn(async () => ({}))
    const deleteCustomer = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ enableCustomer, deleteCustomer, onSuccess })

    await list.handleView({ id: '1' } as Customer)
    expect(list.detailVisible.value).toBe(true)
    expect(list.currentRow.value?.code).toBe('C001')
    expect(list.creditExposure.value?.availableCredit).toBe(700)

    await list.handleEnable({ id: '1', name: 'Acme' } as Customer)
    await list.handleDelete({ id: '1', name: 'Acme' } as Customer)
    expect(enableCustomer).toHaveBeenCalledWith('1')
    expect(deleteCustomer).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('enabled')
    expect(onSuccess).toHaveBeenCalledWith('deleted')
  })

  it('exports customers', async () => {
    const exportCustomers = vi.fn(async () => new Blob(['csv']))
    const onSuccess = vi.fn()
    const list = createList({ exportCustomers, onSuccess })

    await list.handleExport()
    expect(exportCustomers).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('exported')
  })
})

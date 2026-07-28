import { describe, expect, it, vi } from 'vitest'

import type { Customer, Supplier } from '@/api/masterdata'
import { useFinanceAccountResources } from './useFinanceAccountResources'

const t = (key: string) => key
const page = <T>(records: T[]) => ({
  records,
  total: records.length,
  pageNo: 1,
  pageSize: 200
})

const customer = { id: 'c1', name: 'Acme' } as Customer
const supplier = { id: 's1', name: 'Supply Co' } as Supplier

const createResources = (
  overrides: Partial<Parameters<typeof useFinanceAccountResources>[1]> = {}
) => useFinanceAccountResources(t, {
  canLoadCustomers: () => true,
  canLoadSuppliers: () => true,
  getCustomers: vi.fn(async () => page([customer])),
  getSuppliers: vi.fn(async () => page([supplier])),
  reportError: vi.fn(),
  ...overrides
})

describe('finance account resources', () => {
  it('loads active customer and supplier options with bounded paging', async () => {
    const getCustomers = vi.fn(async () => page([customer]))
    const getSuppliers = vi.fn(async () => page([supplier]))
    const resources = createResources({ getCustomers, getSuppliers })

    expect(await resources.loadCustomers()).toBe(true)
    expect(await resources.loadSuppliers()).toBe(true)
    expect(getCustomers).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(getSuppliers).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(resources.customers.value).toEqual([customer])
    expect(resources.suppliers.value).toEqual([supplier])
  })

  it('does not load options without the corresponding combined permission', async () => {
    const getCustomers = vi.fn(async () => page([customer]))
    const getSuppliers = vi.fn(async () => page([supplier]))
    const resources = createResources({
      canLoadCustomers: () => false,
      canLoadSuppliers: () => false,
      getCustomers,
      getSuppliers
    })

    expect(await resources.loadCustomers()).toBe(false)
    expect(await resources.loadSuppliers()).toBe(false)
    expect(getCustomers).not.toHaveBeenCalled()
    expect(getSuppliers).not.toHaveBeenCalled()
  })

  it('reports side-specific option failures without leaking partial state', async () => {
    const reportError = vi.fn()
    const resources = createResources({
      getCustomers: vi.fn(async () => { throw new Error('customers') }),
      getSuppliers: vi.fn(async () => { throw new Error('suppliers') }),
      reportError
    })

    expect(await resources.loadCustomers()).toBe(false)
    expect(await resources.loadSuppliers()).toBe(false)
    expect(reportError).toHaveBeenNthCalledWith(
      1,
      'financeAccount.message.customersLoadFailed',
      expect.any(Error)
    )
    expect(reportError).toHaveBeenNthCalledWith(
      2,
      'financeAccount.message.suppliersLoadFailed',
      expect.any(Error)
    )
    expect(resources.customers.value).toEqual([])
    expect(resources.suppliers.value).toEqual([])
  })
})

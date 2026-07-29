import { describe, expect, it, vi } from 'vitest'

import type { PartnerStatement } from '@/api/finance'
import type { Customer, Supplier } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'
import { useFinanceStatementQuery } from './useFinanceStatementQuery'

const t = (key: string) => key

const customer = (overrides: Partial<Customer> = {}): Customer => ({
  id: 'customer-1',
  customerCode: 'C001',
  customerName: 'Acme',
  status: 'ACTIVE',
  ...overrides
})

const supplier = (overrides: Partial<Supplier> = {}): Supplier => ({
  id: 'supplier-1',
  supplierCode: 'S001',
  supplierName: 'Parts Co',
  status: 'ACTIVE',
  ...overrides
})

const statement = (overrides: Partial<PartnerStatement> = {}): PartnerStatement => ({
  partnerType: 'CUSTOMER',
  partnerId: 'customer-1',
  partnerName: 'Acme',
  dateFrom: '2026-07-01',
  dateTo: '2026-07-29',
  openingBalance: 10,
  totalIncrease: 20,
  totalDecrease: 5,
  closingBalance: 25,
  lines: [],
  ...overrides
})

const page = <T>(records: T[]): PageResponse<T> => ({
  records,
  total: records.length,
  pageNo: 1,
  pageSize: 200
})

const createQuery = (
  overrides: Partial<Parameters<typeof useFinanceStatementQuery>[1]> = {}
) => useFinanceStatementQuery(t, {
  getCustomers: vi.fn(async () => page([customer()])),
  getSuppliers: vi.fn(async () => page([supplier()])),
  getPartnerStatement: vi.fn(async () => statement()),
  getBusinessMonthDateRange: () => ['2026-07-01', '2026-07-29'],
  onError: vi.fn(),
  onWarning: vi.fn(),
  ...overrides
})

describe('finance statement query', () => {
  it('starts with the business month and loads ACTIVE customers by default', async () => {
    const getCustomers = vi.fn(async () => page([customer()]))
    const getSuppliers = vi.fn(async () => page([supplier()]))
    const query = createQuery({ getCustomers, getSuppliers })

    expect(query.range.value).toEqual(['2026-07-01', '2026-07-29'])
    expect(await query.loadPartners()).toBe(true)
    expect(getCustomers).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 200,
      status: 'ACTIVE'
    })
    expect(getSuppliers).not.toHaveBeenCalled()
    expect(query.partners.value[0]).toEqual(expect.objectContaining({
      id: 'customer-1',
      name: 'Acme'
    }))
  })

  it('clears stale state immediately and loads suppliers after a type change', async () => {
    let resolveSuppliers!: (value: PageResponse<Supplier>) => void
    const getCustomers = vi.fn(async () => page([customer()]))
    const getSuppliers = vi.fn(() => new Promise<PageResponse<Supplier>>((resolve) => {
      resolveSuppliers = resolve
    }))
    const query = createQuery({ getCustomers, getSuppliers })
    query.partnerId.value = 'customer-1'
    query.partners.value = [customer()]
    query.statement.value = statement()

    const pending = query.handlePartnerTypeChange('SUPPLIER')

    expect(query.partnerType.value).toBe('SUPPLIER')
    expect(query.partnerId.value).toBe('')
    expect(query.partners.value).toEqual([])
    expect(query.statement.value).toBeUndefined()
    expect(getCustomers).not.toHaveBeenCalled()
    expect(getSuppliers).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 200,
      status: 'ACTIVE'
    })

    resolveSuppliers(page([supplier()]))
    expect(await pending).toBe(true)
    expect(query.partners.value[0]).toEqual(expect.objectContaining({
      id: 'supplier-1',
      name: 'Parts Co'
    }))
  })

  it('rejects a missing partner or incomplete date range without requesting', async () => {
    const getPartnerStatement = vi.fn(async () => statement())
    const onWarning = vi.fn()
    const query = createQuery({ getPartnerStatement, onWarning })

    expect(await query.loadData()).toBe(false)
    query.partnerId.value = 'customer-1'
    query.range.value = ['2026-07-01']
    expect(await query.loadData()).toBe(false)
    query.range.value = ['2026-07-01', '']
    expect(await query.loadData()).toBe(false)
    query.range.value = ['2026-07-31', '2026-07-01']
    expect(await query.loadData()).toBe(false)

    expect(getPartnerStatement).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledTimes(4)
    expect(onWarning).toHaveBeenLastCalledWith(
      'financeStatement.message.selectPartnerAndRange'
    )
  })

  it('queries with exact parameters and preserves a long partner id string', async () => {
    const longId = '2072561615605100546'
    const getPartnerStatement = vi.fn(async () => statement({ partnerId: longId }))
    const query = createQuery({ getPartnerStatement })
    query.partnerType.value = 'SUPPLIER'
    query.partnerId.value = longId
    query.range.value = ['2026-06-01', '2026-06-30']

    expect(await query.loadData()).toBe(true)
    expect(getPartnerStatement).toHaveBeenCalledWith({
      partnerType: 'SUPPLIER',
      partnerId: longId,
      dateFrom: '2026-06-01',
      dateTo: '2026-06-30'
    })
    expect(query.statement.value?.partnerId).toBe(longId)
  })

  it('clears the displayed statement and invalidates an in-flight query after a filter change', async () => {
    let resolveStatement!: (value: PartnerStatement) => void
    const query = createQuery({
      getPartnerStatement: vi.fn(() => new Promise<PartnerStatement>((resolve) => {
        resolveStatement = resolve
      }))
    })
    query.partnerId.value = 'customer-1'
    query.statement.value = statement()

    const staleRequest = query.loadData()
    expect(query.loading.value).toBe(true)
    query.clearStatement()

    expect(query.statement.value).toBeUndefined()
    expect(query.loading.value).toBe(false)

    resolveStatement(statement({ partnerId: 'customer-stale' }))
    expect(await staleRequest).toBe(false)
    expect(query.statement.value).toBeUndefined()
    expect(query.loading.value).toBe(false)
  })

  it('reports partner option failures and clears options', async () => {
    const onError = vi.fn()
    const query = createQuery({
      getCustomers: vi.fn(async () => { throw new Error('options failed') }),
      onError
    })
    query.partners.value = [customer()]

    expect(await query.loadPartners()).toBe(false)
    expect(query.partners.value).toEqual([])
    expect(onError).toHaveBeenCalledWith('financeStatement.message.optionsLoadFailed')
  })

  it('reports statement failures and always restores loading', async () => {
    let rejectStatement!: (reason?: unknown) => void
    const getPartnerStatement = vi.fn(() => new Promise<PartnerStatement>((_, reject) => {
      rejectStatement = reject
    }))
    const onError = vi.fn()
    const query = createQuery({ getPartnerStatement, onError })
    query.partnerId.value = 'customer-1'

    const pending = query.loadData()
    expect(query.loading.value).toBe(true)
    rejectStatement(new Error('statement failed'))

    expect(await pending).toBe(false)
    expect(query.loading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeStatement.message.loadFailed')
  })

  it('ignores stale partner options when the partner type changes quickly', async () => {
    let resolveCustomers!: (value: PageResponse<Customer>) => void
    let resolveSuppliers!: (value: PageResponse<Supplier>) => void
    const query = createQuery({
      getCustomers: vi.fn(() => new Promise<PageResponse<Customer>>((resolve) => {
        resolveCustomers = resolve
      })),
      getSuppliers: vi.fn(() => new Promise<PageResponse<Supplier>>((resolve) => {
        resolveSuppliers = resolve
      }))
    })

    const staleCustomerRequest = query.loadPartners()
    const currentSupplierRequest = query.handlePartnerTypeChange('SUPPLIER')
    resolveSuppliers(page([supplier({ id: 'supplier-current' })]))
    expect(await currentSupplierRequest).toBe(true)
    expect(query.partners.value[0]?.id).toBe('supplier-current')

    resolveCustomers(page([customer({ id: 'customer-stale' })]))
    expect(await staleCustomerRequest).toBe(false)
    expect(query.partners.value[0]?.id).toBe('supplier-current')
  })

  it('does not restore a stale statement after switching partner type', async () => {
    let resolveStatement!: (value: PartnerStatement) => void
    const onError = vi.fn()
    const query = createQuery({
      getPartnerStatement: vi.fn(() => new Promise<PartnerStatement>((resolve) => {
        resolveStatement = resolve
      })),
      onError
    })
    query.partnerId.value = 'customer-1'

    const staleRequest = query.loadData()
    expect(query.loading.value).toBe(true)
    await query.handlePartnerTypeChange('SUPPLIER')
    expect(query.loading.value).toBe(false)
    expect(query.statement.value).toBeUndefined()

    resolveStatement(statement({ partnerId: 'customer-stale' }))
    expect(await staleRequest).toBe(false)
    expect(query.statement.value).toBeUndefined()
    expect(onError).not.toHaveBeenCalled()
  })
})

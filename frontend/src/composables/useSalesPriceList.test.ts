import { describe, expect, it, vi } from 'vitest'

import type { SalesPrice } from '@/api/sales'
import { useSalesPriceList } from './useSalesPriceList'

const t = (key: string) => key

const price = (overrides: Partial<SalesPrice> = {}): SalesPrice => ({
  id: '1',
  productId: 'p1',
  productCode: 'SKU',
  productName: 'Item',
  listPrice: 100,
  minPrice: 80,
  effectiveFrom: '2026-01-01',
  status: 'ACTIVE',
  ...overrides
})

const createList = (overrides: Partial<Parameters<typeof useSalesPriceList>[1]> = {}) =>
  useSalesPriceList(t, {
    getSalesPrices: vi.fn(async () => ({ records: [price()], total: 1 })),
    getSalesPrice: vi.fn(async () => price({ listPrice: 120 })),
    createSalesPrice: vi.fn(async () => ({})),
    updateSalesPrice: vi.fn(async () => ({})),
    enableSalesPrice: vi.fn(async () => ({})),
    disableSalesPrice: vi.fn(async () => ({})),
    getCustomers: vi.fn(async () => ({ records: [{ id: 'c1', customerName: 'Acme' } as any], total: 1 })),
    getProducts: vi.fn(async () => ({ records: [{ id: 'p1', productCode: 'SKU', productName: 'Item' } as any], total: 1 })),
    printSalesPrice: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('sales price list', () => {
  it('loads, pages and resets filters', async () => {
    const list = createList()
    expect(await list.loadOptions()).toBe(true)
    expect(list.customers.value).toHaveLength(1)
    expect(await list.loadData()).toBe(true)
    expect(list.tableData.value).toHaveLength(1)

    await list.handleSizeChange(50)
    expect(list.searchForm.pageSize).toBe(50)
    expect(list.searchForm.pageNo).toBe(1)
    await list.handlePageChange(2)
    expect(list.searchForm.pageNo).toBe(2)

    list.searchForm.keyword = 'x'
    list.searchForm.customerId = 'c1'
    await list.handleReset()
    expect(list.searchForm.keyword).toBe('')
    expect(list.searchForm.customerId).toBe('')
    expect(list.searchForm.pageNo).toBe(1)
  })

  it('creates, edits and saves with business rules', async () => {
    const createSalesPrice = vi.fn(async () => ({}))
    const updateSalesPrice = vi.fn(async () => ({}))
    const onWarning = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({ createSalesPrice, updateSalesPrice, onWarning, onSuccess })

    await list.handleCreate()
    expect(list.dialogVisible.value).toBe(true)
    expect(list.form.effectiveFrom).toBeTruthy()

    list.scopeType.value = 'CUSTOMER'
    list.form.customerId = ''
    expect(list.validateBusinessRules()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('salesPrice.validation.customer')

    list.form.customerId = 'c1'
    list.form.productId = 'p1'
    list.form.listPrice = 10
    list.form.minPrice = 20
    expect(list.validateBusinessRules()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('salesPrice.validation.minAboveList')

    list.form.minPrice = 8
    expect(await list.submitSave()).toBe(true)
    expect(createSalesPrice).toHaveBeenCalledWith(expect.objectContaining({
      customerId: 'c1',
      productId: 'p1',
      listPrice: 10,
      minPrice: 8
    }))
    expect(onSuccess).toHaveBeenCalledWith('salesPrice.message.created')

    await list.handleEdit(price({ id: '9', customerId: 'c1', customerName: 'Acme' }))
    expect(list.editingId.value).toBe('9')
    expect(list.scopeType.value).toBe('CUSTOMER')
    list.form.listPrice = 50
    list.form.minPrice = 40
    expect(await list.submitSave()).toBe(true)
    expect(updateSalesPrice).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('salesPrice.message.saved')
  })

  it('prints, enables and disables rows', async () => {
    const printSalesPrice = vi.fn()
    const enableSalesPrice = vi.fn(async () => ({}))
    const disableSalesPrice = vi.fn(async () => ({}))
    const confirm = vi.fn(async () => true)
    const onSuccess = vi.fn()
    const list = createList({
      printSalesPrice,
      enableSalesPrice,
      disableSalesPrice,
      confirm,
      onSuccess
    })

    expect(await list.handlePrint(price())).toBe(true)
    expect(printSalesPrice).toHaveBeenCalledWith(expect.objectContaining({ listPrice: 120 }))

    expect(await list.handleEnable(price())).toBe(true)
    expect(enableSalesPrice).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('salesPrice.message.enabled')

    expect(await list.handleDisable(price())).toBe(true)
    expect(confirm).toHaveBeenCalled()
    expect(disableSalesPrice).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('salesPrice.message.disabled')
  })
})

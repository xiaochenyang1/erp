import { describe, expect, it, vi } from 'vitest'

import type { PurchasePrice } from '@/api/purchase'
import { usePurchasePriceList } from './usePurchasePriceList'

const t = (key: string) => key

const price = (overrides: Partial<PurchasePrice> = {}): PurchasePrice => ({
  id: '1',
  productId: 'p1',
  productCode: 'SKU',
  productName: 'Item',
  listPrice: 100,
  maxPrice: 120,
  effectiveFrom: '2026-01-01',
  status: 'ACTIVE',
  ...overrides
})

const createList = (overrides: Partial<Parameters<typeof usePurchasePriceList>[1]> = {}) =>
  usePurchasePriceList(t, {
    getPurchasePrices: vi.fn(async () => ({
      records: [price()],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })),
    getPurchasePrice: vi.fn(async () => price({ listPrice: 90 })),
    createPurchasePrice: vi.fn(async () => ({})),
    updatePurchasePrice: vi.fn(async () => ({})),
    enablePurchasePrice: vi.fn(async () => ({})),
    disablePurchasePrice: vi.fn(async () => ({})),
    getSuppliers: vi.fn(async () => ({
      records: [{ id: 's1', supplierName: 'Vendor' } as any],
      total: 1,
      pageNo: 1,
      pageSize: 200
    })),
    getProducts: vi.fn(async () => ({
      records: [{ id: 'p1', productCode: 'SKU', productName: 'Item' } as any],
      total: 1,
      pageNo: 1,
      pageSize: 200
    })),
    printPurchasePrice: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('purchase price list', () => {
  it('loads, validates max price and saves', async () => {
    const createPurchasePrice = vi.fn(async () => ({}))
    const onWarning = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({ createPurchasePrice, onWarning, onSuccess })

    expect(await list.loadData()).toBe(true)
    await list.handleCreate()
    list.scopeType.value = 'SUPPLIER'
    list.form.supplierId = ''
    expect(list.validateBusinessRules()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('purchasePrice.validation.supplier')

    list.form.supplierId = 's1'
    list.form.productId = 'p1'
    list.form.listPrice = 100
    list.form.maxPrice = 50
    expect(list.validateBusinessRules()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('purchasePrice.validation.maxBelowList')

    list.form.maxPrice = 120
    expect(await list.submitSave()).toBe(true)
    expect(createPurchasePrice).toHaveBeenCalledWith(expect.objectContaining({
      supplierId: 's1',
      maxPrice: 120
    }))
    expect(onSuccess).toHaveBeenCalledWith('purchasePrice.message.created')
  })

  it('prints and toggles status', async () => {
    const printPurchasePrice = vi.fn()
    const enablePurchasePrice = vi.fn(async () => ({}))
    const disablePurchasePrice = vi.fn(async () => ({}))
    const list = createList({ printPurchasePrice, enablePurchasePrice, disablePurchasePrice })
    expect(await list.handlePrint(price())).toBe(true)
    expect(printPurchasePrice).toHaveBeenCalled()
    expect(await list.handleEnable(price())).toBe(true)
    expect(await list.handleDisable(price())).toBe(true)
    expect(disablePurchasePrice).toHaveBeenCalledWith('1')
  })
})

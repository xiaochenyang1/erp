import { effectScope, nextTick, ref, type EffectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { Product } from '@/api/masterdata'
import { getSalesOrder, updateSalesOrder, type SalesOrder, type SalesOrderCreditPreview } from '@/api/sales'
import { request } from '@/utils/request'
import { useSalesOrderForm } from './useSalesOrderForm'

vi.mock('@/utils/request', () => ({
  request: { get: vi.fn(), put: vi.fn(), post: vi.fn() }
}))

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.orderNo != null) return `${key}:${params.orderNo}`
  if (params?.line != null) return `${key}:${params.line}`
  return key
}

describe('sales order form', () => {
  const scopes: EffectScope[] = []
  const products = ref<Product[]>([
    { id: 'p-1', productCode: 'FG-1', productName: 'Good', salePrice: 12 } as Product
  ])
  const foreignOrder = {
    id: 'so-1',
    orderNo: 'SO001',
    customerId: 'c-1',
    warehouseId: 'w-1',
    orderDate: '2026-07-20',
    currencyCode: 'USD',
    exchangeRate: 7,
    items: [{
      productId: 'p-1',
      quantity: 2,
      price: 10,
      taxRate: 0.13
    }]
  } as SalesOrder

  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
  })

  afterEach(() => {
    scopes.splice(0).forEach((scope) => scope.stop())
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  const createForm = (overrides: Partial<Parameters<typeof useSalesOrderForm>[1]> = {}) => {
    const scope = effectScope()
    scopes.push(scope)
    return scope.run(() => useSalesOrderForm(t, {
      products,
      getOrder: vi.fn(async () => foreignOrder),
      createOrder: vi.fn(async () => ({})),
      updateOrder: vi.fn(async () => ({})),
      previewCredit: vi.fn(async () => ({
        projectedAvailableCredit: 100
      } as any)),
      resolvePrice: vi.fn(async () => ({
        matched: true,
        listPrice: 15,
        minPrice: 12,
        matchLevel: 'CUSTOMER'
      })),
      formatBusinessDate: () => '2026-07-26',
      formatMoney: (value) => String(value ?? 0),
      lineAmount: (row) => Number(row.quantity || 0) * Number(row.price || 0),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onWarning: vi.fn(),
      onCompleted: vi.fn(),
      ...overrides
    }))!
  }

  const validForm = (form: ReturnType<typeof createForm>) => {
    form.formRef.value = {
      clearValidate: vi.fn(),
      validate: (cb: (valid: boolean) => void | Promise<void>) => cb(true)
    } as any
  }

  const previewResult = (available: number) => ({ projectedAvailableCredit: available } as SalesOrderCreditPreview)
  const pendingPreview = () => {
    let resolve!: (value: SalesOrderCreditPreview) => void
    const promise = new Promise<SalesOrderCreditPreview>((resolvePromise) => { resolve = resolvePromise })
    return { promise, resolve }
  }

  const flushPreview = async () => {
    await nextTick()
    await vi.advanceTimersByTimeAsync(250)
  }

  it('creates form and applies resolved price', async () => {
    const form = createForm()
    form.handleCreate()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.formData.items).toHaveLength(1)

    form.formData.items[0].productId = 'p-1'
    form.formData.customerId = 'c-1'
    form.formData.orderDate = '2026-07-26'
    await form.onProductChange(form.formData.items[0] as any)
    expect(form.formData.items[0].price).toBe(15)
    expect((form.formData.items[0] as any).minPrice).toBe(12)
  })

  it('converts base prices and checks the minimum in base currency after a rate change', async () => {
    const updateOrder = vi.fn(async () => ({}))
    const onWarning = vi.fn()
    const form = createForm({
      updateOrder, onWarning,
      resolvePrice: vi.fn(async () => ({ matched: true, listPrice: 100, minPrice: 80 }))
    })
    await form.handleEdit({ id: 'so-1' } as SalesOrder)
    form.formData.currencyCode = 'USD'
    form.formData.exchangeRate = 2
    await form.onProductChange(form.formData.items[0])
    expect(form.formData.items[0].price).toBe(50)
    expect(form.formData.items[0].minPrice).toBe(40)
    form.formRef.value = { validate: (cb: (valid: boolean) => Promise<void>) => cb(true) } as any
    await form.handleSave()
    expect(updateOrder).toHaveBeenCalledTimes(1)

    form.formData.exchangeRate = 1
    await nextTick()
    expect(form.formData.items[0].price).toBe(50)
    expect(form.formData.items[0].minPrice).toBe(80)
    await form.handleSave()
    expect(updateOrder).toHaveBeenCalledTimes(1)
    expect(onWarning).toHaveBeenCalled()
  })

  it('converts the product master sale price when no price list matches', async () => {
    const form = createForm({ resolvePrice: vi.fn(async () => ({ matched: false })) })
    form.handleCreate()
    form.formData.exchangeRate = 2
    form.formData.items[0].productId = 'p-1'
    await form.onProductChange(form.formData.items[0])
    expect(form.formData.items[0].price).toBe(6)
  })

  it('loads edit/copy form and submits create payload', async () => {
    const createOrder = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const form = createForm({ createOrder, onSuccess, onCompleted })

    await form.handleEdit({ id: 'so-1' } as SalesOrder)
    expect(form.formData.customerId).toBe('c-1')

    await form.handleCopy({ id: 'so-1', orderNo: 'SO001' } as SalesOrder)
    expect(form.formData.id).toBeUndefined()
    expect(form.formData.orderDate).toBe('2026-07-26')

    form.formRef.value = {
      validate: (cb: (valid: boolean) => void | Promise<void>) => cb(true)
    } as any
    form.formData.customerId = 'c-1'
    form.formData.warehouseId = 'w-1'
    form.formData.orderDate = '2026-07-26'
    form.formData.items = [{ productId: 'p-1', quantity: 2, price: 10, taxRate: 0 } as any]
    form.dialogVisible.value = true
    await form.handleSave()

    expect(createOrder).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('salesOrder.message.created')
    expect(onCompleted).toHaveBeenCalled()
  })

  it('preserves the stored USD rate through load, edit and the actual update request', async () => {
    vi.mocked(request.get).mockResolvedValue(foreignOrder)
    vi.mocked(request.put).mockResolvedValue(foreignOrder)
    const form = createForm({ getOrder: getSalesOrder, updateOrder: updateSalesOrder })

    await form.handleEdit(foreignOrder)
    form.formData.remark = 'Updated remark only'
    validForm(form)
    await form.handleSave()

    expect(request.put).toHaveBeenCalledWith('/sales/orders/so-1', expect.objectContaining({
      currencyCode: 'USD',
      exchangeRate: 7,
      orderDate: '2026-07-20',
      remark: 'Updated remark only',
      lines: [expect.objectContaining({ productId: 'p-1', qty: 2, price: 10 })]
    }))
  })

  it('shows the stored currency and exchange rate when viewing an order', async () => {
    const previewCredit = vi.fn(async () => previewResult(100))
    const form = createForm({ previewCredit })
    await form.handleView(foreignOrder)
    await flushPreview()

    expect(form.isView.value).toBe(true)
    expect(form.formData).toMatchObject({ currencyCode: 'USD', exchangeRate: 7 })
    expect(previewCredit).not.toHaveBeenCalled()
  })

  it('copies the source currency and rate, then clears them for a new order', async () => {
    const createOrder = vi.fn(async () => ({}))
    const form = createForm({ createOrder })
    await form.handleCopy(foreignOrder)
    validForm(form)
    await form.handleSave()

    expect(createOrder).toHaveBeenCalledWith(expect.objectContaining({
      id: undefined,
      orderDate: '2026-07-26',
      currencyCode: 'USD',
      exchangeRate: 7
    }))

    form.handleCreate()
    expect(form.formData.currencyCode).toBeUndefined()
    expect(form.formData.exchangeRate).toBeUndefined()
  })

  it('does not inherit currency when the next loaded order has no currency fields', async () => {
    const getOrder = vi.fn()
      .mockResolvedValueOnce(foreignOrder)
      .mockResolvedValueOnce({ ...foreignOrder, currencyCode: undefined, exchangeRate: undefined })
    const form = createForm({ getOrder })
    await form.handleEdit(foreignOrder)
    await form.handleEdit({ id: 'legacy-order' } as SalesOrder)

    expect(form.formData.currencyCode).toBeUndefined()
    expect(form.formData.exchangeRate).toBeUndefined()
  })

  it('previews credit with the order date, currency and rate and reloads when each changes', async () => {
    const previewCredit = vi.fn(async () => previewResult(100))
    const form = createForm({ previewCredit })
    await form.handleEdit(foreignOrder)
    await flushPreview()

    expect(previewCredit).toHaveBeenLastCalledWith('c-1', [expect.objectContaining({
      productId: 'p-1', quantity: 2, price: 10, amount: 20
    })], { orderDate: '2026-07-20', currencyCode: 'USD', exchangeRate: 7 })

    form.formData.orderDate = '2026-07-21'
    await flushPreview()
    expect(previewCredit).toHaveBeenLastCalledWith('c-1', expect.any(Array), {
      orderDate: '2026-07-21', currencyCode: 'USD', exchangeRate: 7
    })

    form.formData.currencyCode = 'EUR'
    await flushPreview()
    expect(previewCredit).toHaveBeenLastCalledWith('c-1', expect.any(Array), {
      orderDate: '2026-07-21', currencyCode: 'EUR', exchangeRate: 7
    })

    form.formData.exchangeRate = 8
    await flushPreview()
    expect(previewCredit).toHaveBeenLastCalledWith('c-1', expect.any(Array), {
      orderDate: '2026-07-21', currencyCode: 'EUR', exchangeRate: 8
    })
    expect(previewCredit).toHaveBeenCalledTimes(4)
  })

  it('discards the old preview as soon as currency context changes, before the debounce finishes', async () => {
    const old = pendingPreview()
    const previewCredit = vi.fn()
      .mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce(previewResult(200))
    const form = createForm({ previewCredit })
    await form.handleEdit(foreignOrder)
    await flushPreview()

    form.formData.exchangeRate = 8
    old.resolve(previewResult(10))
    await nextTick()
    expect(previewCredit).toHaveBeenCalledTimes(1)
    expect(form.creditPreview.value).toBeUndefined()

    await flushPreview()
    expect(form.creditPreview.value?.projectedAvailableCredit).toBe(200)
  })

  it.each([true, false])('keeps the newest credit preview when old response finishes first: %s', async (oldFirst) => {
    const old = pendingPreview()
    const current = pendingPreview()
    const previewCredit = vi.fn()
      .mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(current.promise)
    const form = createForm({ previewCredit })
    await form.handleEdit(foreignOrder)
    await flushPreview()
    form.formData.currencyCode = 'EUR'
    await flushPreview()

    if (oldFirst) {
      old.resolve(previewResult(10))
      await nextTick()
      expect(form.creditPreviewLoading.value).toBe(true)
      expect(form.creditPreview.value).toBeUndefined()
    }
    current.resolve(previewResult(200))
    await nextTick()
    if (!oldFirst) {
      old.resolve(previewResult(10))
      await nextTick()
    }

    expect(form.creditPreview.value?.projectedAvailableCredit).toBe(200)
    expect(form.creditPreviewLoading.value).toBe(false)
  })

  it.each(['close', 'reset', 'view'])('ignores an in-flight credit preview after %s', async (action) => {
    const pending = pendingPreview()
    const previewCredit = vi.fn(() => pending.promise)
    const form = createForm({ previewCredit })
    await form.handleEdit(foreignOrder)
    await flushPreview()

    if (action === 'close') form.dialogVisible.value = false
    if (action === 'reset') form.resetForm()
    if (action === 'view') await form.handleView(foreignOrder)
    pending.resolve(previewResult(10))
    await nextTick()

    expect(form.creditPreview.value).toBeUndefined()
    expect(form.creditPreviewLoading.value).toBe(false)
  })
})

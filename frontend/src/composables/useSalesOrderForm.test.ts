import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Product } from '@/api/masterdata'
import type { SalesOrder } from '@/api/sales'
import { useSalesOrderForm } from './useSalesOrderForm'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.orderNo != null) return `${key}:${params.orderNo}`
  if (params?.line != null) return `${key}:${params.line}`
  return key
}

describe('sales order form', () => {
  const products = ref<Product[]>([
    { id: 'p-1', productCode: 'FG-1', productName: 'Good', salePrice: 12 } as Product
  ])

  const createForm = (overrides: Partial<Parameters<typeof useSalesOrderForm>[1]> = {}) =>
    useSalesOrderForm(t, {
      products,
      getOrder: vi.fn(async () => ({
        id: 'so-1',
        orderNo: 'SO001',
        customerId: 'c-1',
        warehouseId: 'w-1',
        orderDate: '2026-07-20',
        items: [{
          productId: 'p-1',
          quantity: 2,
          price: 10,
          taxRate: 0.13
        }]
      } as SalesOrder)),
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
    })

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
})

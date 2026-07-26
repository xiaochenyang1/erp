import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { SalesDelivery, SalesOrder } from '@/api/sales'
import { useSalesDeliveryForm } from './useSalesDeliveryForm'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.code != null) return `${key}:${params.code}`
  if (params?.line != null) return `${key}:${params.line}`
  return key
}

vi.mock('@/utils/productLines', () => ({
  hydrateProductLineLabels: vi.fn(async (items: any[]) => items),
  validateProductControlLines: vi.fn(() => [])
}))

vi.mock('@/utils/barcode', () => ({
  incrementScannedLine: vi.fn(() => ({
    status: 'ok',
    index: 0,
    quantity: 2
  }))
}))

describe('sales delivery form', () => {
  const orders = ref<SalesOrder[]>([])

  const createForm = (overrides: Partial<Parameters<typeof useSalesDeliveryForm>[1]> = {}) =>
    useSalesDeliveryForm(t, {
      orders,
      getDelivery: vi.fn(async () => ({
        id: 'sd-1',
        orderId: 'so-1',
        orderNo: 'SO001',
        customerName: 'Acme',
        warehouseId: 'w-1',
        deliveryDate: '2026-07-26',
        items: [{
          orderLineId: 'line-1',
          productId: 'p-1',
          quantity: 3
        }]
      } as any)),
      getOrder: vi.fn(async () => ({
        id: 'so-1',
        orderNo: 'SO001',
        items: [{
          id: 'line-1',
          productId: 'p-1',
          productCode: 'FG-1',
          productName: 'Good',
          quantity: 10,
          deliveredQuantity: 2
        }]
      } as any)),
      createDelivery: vi.fn(async () => ({})),
      updateDelivery: vi.fn(async () => ({})),
      loadProduct: vi.fn(async (id) => ({ id })),
      loadProductByBarcode: vi.fn(async () => ({ id: 'p-1', productCode: 'FG-1' })),
      loadLocations: vi.fn(async () => undefined),
      formatBusinessDate: () => '2026-07-26',
      confirm: vi.fn(async () => true),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onWarning: vi.fn(),
      onCompleted: vi.fn(),
      ...overrides
    })

  it('creates form and fills items from sales order', async () => {
    const form = createForm()
    form.handleCreate()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.formData.deliveryDate).toBe('2026-07-26')

    form.formData.orderId = 'so-1'
    await form.handleOrderChange()
    expect(form.formData.items[0].quantity).toBe(8)
  })

  it('edits draft delivery and supports barcode scan/submit', async () => {
    const createDelivery = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const form = createForm({ createDelivery, onSuccess, onCompleted })

    await form.handleEdit({ id: 'sd-1' } as SalesDelivery)
    expect(form.editingId.value).toBe('sd-1')
    expect(form.formData.items).toHaveLength(1)

    form.formData.orderId = 'so-1'
    form.formData.items = [{
      productId: 'p-1',
      orderedQuantity: 10,
      deliveredQuantity: 0,
      quantity: 1
    } as any]
    await form.handleBarcodeScan('BC-1')
    expect(form.scanFeedback.value).toContain('FG-1')

    form.editingId.value = ''
    form.formRef.value = {
      validate: (cb: (valid: boolean) => void | Promise<void>) => cb(true)
    } as any
    form.formData.warehouseId = 'w-1'
    form.formData.deliveryDate = '2026-07-26'
    form.dialogVisible.value = true
    await form.handleSubmit()
    expect(createDelivery).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('salesDelivery.message.created')
    expect(onCompleted).toHaveBeenCalled()
  })
})

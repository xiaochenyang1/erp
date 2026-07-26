import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { SalesDelivery, SalesReturn } from '@/api/sales'
import { useSalesReturnForm } from './useSalesReturnForm'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.line != null) return `${key}:${params.line}`
  return key
}

vi.mock('@/utils/productLines', () => ({
  hydrateProductLineLabels: vi.fn(async (items: any[]) => items),
  validateProductControlLines: vi.fn(() => [])
}))

describe('sales return form', () => {
  const products = ref([{ id: 'p-1', productCode: 'FG-1', productName: 'Good' }] as any)
  const deliveries = ref<SalesDelivery[]>([])

  const createForm = (overrides: Partial<Parameters<typeof useSalesReturnForm>[1]> = {}) =>
    useSalesReturnForm(t, {
      products,
      deliveries,
      getDelivery: vi.fn(async () => ({
        id: 'd1',
        items: [{
          id: 'line-1',
          productId: 'p-1',
          quantity: 10,
          returnedQty: 2,
          price: 8
        }]
      } as any)),
      getReturn: vi.fn(async () => ({
        id: 'sr-1',
        deliveryId: 'd1',
        returnDate: '2026-07-26',
        remark: 'draft',
        items: [{
          deliveryLineId: 'line-1',
          productId: 'p-1',
          productCode: 'FG-1',
          productName: 'Good',
          quantity: 3,
          price: 8,
          amount: 24
        }]
      } as SalesReturn)),
      createReturn: vi.fn(async () => ({})),
      updateReturn: vi.fn(async () => ({})),
      formatBusinessDate: () => '2026-07-26',
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onWarning: vi.fn(),
      onCompleted: vi.fn(),
      ...overrides
    })

  it('creates empty form and fills items from delivery', async () => {
    const form = createForm()
    form.handleCreate()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.formData.returnDate).toBe('2026-07-26')

    form.formData.deliveryId = 'd1'
    await form.handleDeliveryChange()
    expect(form.formData.items).toHaveLength(1)
    expect(form.formData.items[0].quantity).toBe(8)
  })

  it('loads edit form and submits create payload', async () => {
    const createReturn = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const form = createForm({ createReturn, onSuccess, onCompleted })

    await form.handleEdit({ id: 'sr-1' } as SalesReturn)
    expect(form.editingId.value).toBe('sr-1')
    expect(form.formData.items[0].quantity).toBe(3)

    form.editingId.value = ''
    form.formRef.value = {
      validate: (cb: (valid: boolean) => void | Promise<void>) => cb(true)
    } as any
    form.formData.deliveryId = 'd1'
    form.formData.returnDate = '2026-07-26'
    form.formData.items = [{ productId: 'p-1', quantity: 2, price: 8, amount: 16 } as any]
    form.dialogVisible.value = true
    await form.handleSubmit()

    expect(createReturn).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('salesReturnOps.message.success')
    expect(form.dialogVisible.value).toBe(false)
    expect(onCompleted).toHaveBeenCalled()
  })
})

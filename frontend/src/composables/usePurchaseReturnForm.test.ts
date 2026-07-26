import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { PurchaseReceipt, PurchaseReturn } from '@/api/purchase'
import { usePurchaseReturnForm } from './usePurchaseReturnForm'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.line != null) return `${key}:${params.line}`
  return key
}

vi.mock('@/utils/productLines', () => ({
  hydrateProductLineLabels: vi.fn(async (items: any[]) => items),
  validateProductControlLines: vi.fn(() => [])
}))

describe('purchase return form', () => {
  const products = ref([
    { id: 'p-1', productCode: 'RM-1', productName: 'Steel' }
  ] as any)

  const availableReceipts = ref<PurchaseReceipt[]>([])

  const createForm = (overrides: Partial<Parameters<typeof usePurchaseReturnForm>[1]> = {}) =>
    usePurchaseReturnForm(t, {
      products,
      availableReceipts,
      loadCreateOptions: vi.fn(async () => {
        availableReceipts.value = [{
          id: 'r1',
          receiptNo: 'PR001',
          items: [{
            id: 'line-1',
            productId: 'p-1',
            quantity: 10,
            returnedQty: 2,
            price: 5
          }]
        } as any]
        return availableReceipts.value
      }),
      getReceipt: vi.fn(async () => ({
        id: 'r1',
        receiptNo: 'PR001',
        items: [{
          id: 'line-1',
          productId: 'p-1',
          quantity: 10,
          returnedQty: 2,
          price: 5
        }]
      } as any)),
      getReturn: vi.fn(async () => ({
        id: 'prt-1',
        receiptId: 'r1',
        returnDate: '2026-07-26',
        remark: 'draft',
        items: [{
          receiptLineId: 'line-1',
          productId: 'p-1',
          productCode: 'RM-1',
          productName: 'Steel',
          receiptQty: 10,
          returnedQty: 0,
          availableReturnQty: 8,
          quantity: 3,
          price: 5
        }]
      } as PurchaseReturn)),
      createReturn: vi.fn(async () => ({})),
      updateReturn: vi.fn(async () => ({})),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onWarning: vi.fn(),
      onCompleted: vi.fn(),
      ...overrides
    })

  it('opens create dialog after loading receipts', async () => {
    const form = createForm()
    await form.handleAdd()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.editingId.value).toBe('')
  })

  it('fills items when receipt changes and edits draft return', async () => {
    const form = createForm()
    await form.handleAdd()
    form.form.receiptId = 'r1'
    await form.handleReceiptChange()
    expect(form.form.items).toHaveLength(1)
    expect(form.form.items[0].quantity).toBe(8)

    await form.handleEdit({ id: 'prt-1' } as PurchaseReturn)
    expect(form.editingId.value).toBe('prt-1')
    expect(form.form.items[0].quantity).toBe(3)
    expect(form.dialogVisible.value).toBe(true)
  })

  it('creates return after validation succeeds', async () => {
    const createReturn = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const form = createForm({ createReturn, onSuccess, onCompleted })
    form.formRef.value = {
      validate: (cb: (valid: boolean) => void | Promise<void>) => cb(true)
    } as any
    form.form.receiptId = 'r1'
    form.form.returnDate = '2026-07-26'
    form.form.items = [{ productId: 'p-1', quantity: 2 } as any]
    form.dialogVisible.value = true

    await form.handleSubmitForm()
    expect(createReturn).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('purchaseReturn.message.created')
    expect(form.dialogVisible.value).toBe(false)
    expect(onCompleted).toHaveBeenCalled()
  })
})

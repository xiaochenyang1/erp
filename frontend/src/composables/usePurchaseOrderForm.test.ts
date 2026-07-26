import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Product } from '@/api/masterdata'
import type { PurchaseOrder } from '@/api/purchase'
import { usePurchaseOrderForm } from './usePurchaseOrderForm'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.orderNo != null) return `${key}:${params.orderNo}`
  return key
}

describe('purchase order form', () => {
  const products = ref<Product[]>([
    {
      id: 'p-1',
      productCode: 'RM-1',
      productName: 'Steel',
      purchasePrice: 12,
      taxRate: 13
    } as Product
  ])

  const createForm = (overrides: Partial<Parameters<typeof usePurchaseOrderForm>[1]> = {}) =>
    usePurchaseOrderForm(t, {
      products,
      getOrder: vi.fn(async () => ({
        id: 'po-1',
        orderNo: 'PO001',
        supplierId: 's-1',
        expectedDate: '2026-08-01',
        remark: 'source',
        items: [{
          productId: 'p-1',
          quantity: 2,
          qty: 2,
          price: 10,
          taxRate: 0.13,
          amount: 20
        }]
      } as PurchaseOrder)),
      createOrder: vi.fn(async () => ({})),
      updateOrder: vi.fn(async () => ({})),
      resolvePrice: vi.fn(async () => ({
        matched: true,
        listPrice: 15,
        maxPrice: 18,
        matchLevel: 'SUPPLIER'
      })),
      formatBusinessDate: () => '2026-07-26',
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onWarning: vi.fn(),
      onCompleted: vi.fn(),
      ...overrides
    })

  it('opens create dialog with empty form', () => {
    const form = createForm()
    form.form.supplierId = 's-9'
    form.handleAdd()

    expect(form.dialogVisible.value).toBe(true)
    expect(form.editId.value).toBeUndefined()
    expect(form.form.supplierId).toBe('')
    expect(form.form.items).toEqual([])
  })

  it('loads edit and copy data into the dialog', async () => {
    const form = createForm()
    form.handleEdit({
      id: 'po-2',
      supplierId: 's-2',
      orderDate: '2026-07-20',
      expectedDate: '2026-07-30',
      remark: 'edit me',
      items: [{ productId: 'p-1', quantity: 1, price: 8, amount: 8 }]
    } as PurchaseOrder)

    expect(form.editId.value).toBe('po-2')
    expect(form.form.supplierId).toBe('s-2')
    expect(form.dialogVisible.value).toBe(true)

    await form.handleCopy({ id: 'po-1', orderNo: 'PO001' } as PurchaseOrder)
    expect(form.editId.value).toBeUndefined()
    expect(form.form.orderDate).toBe('2026-07-26')
    expect(form.form.remark).toContain('purchaseOrder.dialog.copiedFrom:PO001')
    expect(form.form.items[0].quantity).toBe(2)
  })

  it('fills product price and applies resolved purchase price', async () => {
    const resolvePrice = vi.fn(async () => ({
      matched: true,
      listPrice: 15,
      maxPrice: 18,
      matchLevel: 'SUPPLIER'
    }))
    const form = createForm({ resolvePrice })
    form.handleAddItem()
    form.form.items[0].productId = 'p-1'
    form.form.supplierId = 's-1'
    form.form.orderDate = '2026-07-26'

    await form.handleProductChange(0)

    expect(form.form.items[0].productCode).toBe('RM-1')
    expect(form.form.items[0].price).toBe(15)
    expect((form.form.items[0] as any).maxPrice).toBe(18)
    expect((form.form.items[0] as any).priceLevel).toBe('SUPPLIER')
    expect(form.form.items[0].amount).toBe(15)
    expect(resolvePrice).toHaveBeenCalledWith({
      productId: 'p-1',
      supplierId: 's-1',
      bizDate: '2026-07-26'
    })
  })

  it('converts aux quantity and submits create payload', async () => {
    const createOrder = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const form = createForm({ createOrder, onSuccess, onCompleted })
    form.formRef.value = {
      validate: (cb: (valid: boolean) => void | Promise<void>) => cb(true)
    } as any
    form.handleAddItem()
    form.form.supplierId = 's-1'
    form.form.orderDate = '2026-07-26'
    form.form.items[0].productId = 'p-1'
    form.form.items[0].auxUnitName = 'BOX'
    form.form.items[0].conversionFactor = 10
    ;(form.form.items[0] as any).auxQty = 2
    form.handleAuxQtyChange(0)
    form.form.items[0].price = 3
    form.calculateAmount(form.form.items[0])
    form.dialogVisible.value = true

    await form.handleSubmitForm()

    expect(form.form.items[0].quantity).toBe(20)
    expect(form.form.items[0].amount).toBe(60)
    expect(createOrder).toHaveBeenCalledWith(expect.objectContaining({
      supplierId: 's-1',
      orderDate: '2026-07-26'
    }))
    expect(onSuccess).toHaveBeenCalledWith('purchaseOrder.message.created')
    expect(form.dialogVisible.value).toBe(false)
    expect(onCompleted).toHaveBeenCalled()
  })
})

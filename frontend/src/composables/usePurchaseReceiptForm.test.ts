import { describe, expect, it, vi } from 'vitest'

import type { PurchaseOrder, PurchaseReceipt } from '@/api/purchase'
import { usePurchaseReceiptForm } from './usePurchaseReceiptForm'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.code != null) return `${key}:${params.code}`
  if (params?.line != null) return `${key}:${params.line}`
  return key
}

vi.mock('@/utils/productLines', () => ({
  hydrateProductLineLabels: vi.fn(async (items: any[]) => items.map((item) => ({
    ...item,
    lotControlled: false,
    serialControlled: false
  }))),
  validateProductControlLines: vi.fn(() => [])
}))

vi.mock('@/utils/barcode', () => ({
  incrementScannedLine: vi.fn(() => ({
    status: 'ok',
    index: 0,
    quantity: 2
  }))
}))

describe('purchase receipt form', () => {
  const baseOptions = () => ({
    getApprovedOrders: vi.fn(async () => ({
      records: [{ id: 'po-1', orderNo: 'PO001', supplierName: 'Acme', items: [] }],
      total: 1
    } as any)),
    getOrder: vi.fn(async () => ({
      id: 'po-1',
      orderNo: 'PO001',
      items: [{
        id: 'line-1',
        productId: 'p-1',
        productCode: 'RM-1',
        productName: 'Steel',
        quantity: 10,
        receivedQty: 4,
        price: 1,
        amount: 10
      }]
    } as PurchaseOrder)),
    getReceipt: vi.fn(async () => ({
      id: 'pr-1',
      orderId: 'po-1',
      orderNo: 'PO001',
      supplierName: 'Acme',
      warehouseId: 'w-1',
      receiptDate: '2026-07-26',
      remark: 'draft',
      items: [{
        orderItemId: 'line-1',
        productId: 'p-1',
        productCode: 'RM-1',
        productName: 'Steel',
        orderedQuantity: 10,
        quantity: 3,
        qty: 3
      }]
    } as PurchaseReceipt)),
    createReceipt: vi.fn(async () => ({})),
    updateReceipt: vi.fn(async () => ({})),
    loadProduct: vi.fn(async (id) => ({ id, productCode: 'RM-1', productName: 'Steel' })),
    loadProductByBarcode: vi.fn(async () => ({ id: 'p-1', productCode: 'RM-1' })),
    loadLocations: vi.fn(async () => undefined),
    confirm: vi.fn(async () => true),
    onSuccess: vi.fn(),
    onError: vi.fn(),
    onWarning: vi.fn(),
    onCompleted: vi.fn()
  })

  it('opens create dialog with approved orders', async () => {
    const deps = baseOptions()
    const form = usePurchaseReceiptForm(t, deps)

    await form.handleAdd()

    expect(deps.getApprovedOrders).toHaveBeenCalled()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.availableOrders.value).toHaveLength(1)
    expect(form.form.orderId).toBe('')
  })

  it('loads draft receipt into edit dialog', async () => {
    const deps = baseOptions()
    const form = usePurchaseReceiptForm(t, deps)

    await form.handleEdit({ id: 'pr-1' } as PurchaseReceipt)

    expect(deps.getReceipt).toHaveBeenCalledWith('pr-1')
    expect(form.editingId.value).toBe('pr-1')
    expect(form.form.warehouseId).toBe('w-1')
    expect(form.form.items).toHaveLength(1)
    expect(deps.loadLocations).toHaveBeenCalledWith('w-1')
    expect(form.dialogVisible.value).toBe(true)
  })

  it('fills items when order changes and supports barcode scan', async () => {
    const deps = baseOptions()
    const form = usePurchaseReceiptForm(t, deps)

    await form.handleOrderChange('po-1')
    expect(form.form.items[0]).toEqual(expect.objectContaining({
      productId: 'p-1',
      orderedQuantity: 10,
      receivedQuantity: 4,
      quantity: 6
    }))

    form.form.orderId = 'po-1'
    await form.handleBarcodeScan('BC-1')
    expect(deps.loadProductByBarcode).toHaveBeenCalledWith('BC-1')
    expect(form.form.items[0].qty).toBe(2)
    expect(form.scanFeedback.value).toContain('RM-1')
  })

  it('creates receipt after validation succeeds', async () => {
    const deps = baseOptions()
    const form = usePurchaseReceiptForm(t, deps)
    form.formRef.value = {
      validate: (cb: (valid: boolean) => void | Promise<void>) => cb(true)
    } as any
    form.form.orderId = 'po-1'
    form.form.warehouseId = 'w-1'
    form.form.receiptDate = '2026-07-26'
    form.form.items = [{
      productId: 'p-1',
      quantity: 2
    } as any]
    form.dialogVisible.value = true

    await form.handleSubmitForm()

    expect(deps.createReceipt).toHaveBeenCalledWith(expect.objectContaining({
      orderId: 'po-1',
      warehouseId: 'w-1'
    }))
    expect(deps.onSuccess).toHaveBeenCalledWith('purchaseReceipt.message.created')
    expect(form.dialogVisible.value).toBe(false)
    expect(deps.onCompleted).toHaveBeenCalled()
  })

  it('resets scan quantities after confirmation', async () => {
    const deps = baseOptions()
    const form = usePurchaseReceiptForm(t, deps)
    form.form.items = [{
      productId: 'p-1',
      quantity: 5,
      qty: 5
    } as any]

    await form.resetScanQuantities()

    expect(deps.confirm).toHaveBeenCalled()
    expect(form.form.items[0].quantity).toBe(0)
    expect(form.form.items[0].qty).toBe(0)
    expect(form.scanFeedback.value).toBe('purchaseReceipt.scan.resetDone')
  })
})

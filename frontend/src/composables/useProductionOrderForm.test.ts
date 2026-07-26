import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { BOM, ProductionOrder } from '@/api/production'
import { useProductionOrderForm } from './useProductionOrderForm'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.quantity != null) return `${key}:${params.quantity}`
  if (params?.orderNo != null) return `${key}:${params.orderNo}`
  return key
}

describe('production order form', () => {
  const allBomOptions = ref<BOM[]>([
    { id: 'b1', productId: 'p1', bomCode: 'BOM-1', baseQty: 1, status: 'ACTIVE', items: [] },
    { id: 'b2', productId: 'p2', bomCode: 'BOM-2', baseQty: 2, status: 'ACTIVE', items: [] }
  ] as BOM[])

  it('opens create dialog with reset form data', () => {
    const form = useProductionOrderForm(t, {
      allBomOptions,
      loadOrder: vi.fn(),
      createOrder: vi.fn(),
      updateOrder: vi.fn(),
      releaseOrder: vi.fn(),
      cancelOrder: vi.fn(),
      confirm: vi.fn()
    })

    form.formData.productId = 'p9'
    form.handleAdd()

    expect(form.dialogVisible.value).toBe(true)
    expect(form.dialogTitle.value).toBe('productionOrder.dialog.create')
    expect(form.formData.productId).toBeUndefined()
    expect(form.formData.planQuantity).toBe(1)
    expect(form.bomOptions.value).toEqual(allBomOptions.value)
  })

  it('loads order into edit dialog and filters bom options by product', async () => {
    const loadOrder = vi.fn(async () => ({
      id: 'mo-1',
      productId: 'p1',
      bomId: 'b1',
      planQuantity: 8,
      materialWarehouseId: 'mw-1',
      finishedWarehouseId: 'fw-1',
      planStartDate: '2026-07-20',
      planEndDate: '2026-07-25',
      priority: 'HIGH',
      remark: 'rush'
    } as ProductionOrder))
    const form = useProductionOrderForm(t, {
      allBomOptions,
      loadOrder,
      createOrder: vi.fn(),
      updateOrder: vi.fn(),
      releaseOrder: vi.fn(),
      cancelOrder: vi.fn(),
      confirm: vi.fn()
    })

    await form.handleEdit({ id: 'mo-1' } as ProductionOrder)

    expect(loadOrder).toHaveBeenCalledWith('mo-1')
    expect(form.dialogVisible.value).toBe(true)
    expect(form.dialogTitle.value).toBe('productionOrder.dialog.edit')
    expect(form.formData.planQuantity).toBe(8)
    expect(form.formData.priority).toBe('HIGH')
    expect(form.bomOptions.value.map((bom) => bom.id)).toEqual(['b1'])
  })

  it('creates a new order and closes dialog on success', async () => {
    const createOrder = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const form = useProductionOrderForm(t, {
      allBomOptions,
      loadOrder: vi.fn(),
      createOrder,
      updateOrder: vi.fn(),
      releaseOrder: vi.fn(),
      cancelOrder: vi.fn(),
      confirm: vi.fn(),
      onSuccess,
      onCompleted
    })
    form.formRef.value = {
      validate: (cb: (valid: boolean) => void | Promise<void>) => cb(true)
    } as any
    form.formData.productId = 'p1'
    form.formData.bomId = 'b1'
    form.formData.planQuantity = 3
    form.formData.materialWarehouseId = 'mw-1'
    form.formData.finishedWarehouseId = 'fw-1'
    form.formData.planStartDate = '2026-07-26'
    form.formData.planEndDate = '2026-07-30'
    form.dialogVisible.value = true

    await form.handleSubmit()

    expect(createOrder).toHaveBeenCalledWith(expect.objectContaining({
      productId: 'p1',
      bomId: 'b1',
      planQuantity: 3
    }))
    expect(onSuccess).toHaveBeenCalledWith('productionOrder.message.created')
    expect(form.dialogVisible.value).toBe(false)
    expect(onCompleted).toHaveBeenCalled()
  })

  it('releases and cancels orders after confirmation', async () => {
    const releaseOrder = vi.fn(async () => ({}))
    const cancelOrder = vi.fn(async () => ({}))
    const confirm = vi.fn(async () => true)
    const onSuccess = vi.fn()
    const form = useProductionOrderForm(t, {
      allBomOptions,
      loadOrder: vi.fn(),
      createOrder: vi.fn(),
      updateOrder: vi.fn(),
      releaseOrder,
      cancelOrder,
      confirm,
      onSuccess
    })

    await form.handleRelease({ id: 'mo-2', orderNo: 'MO002' } as ProductionOrder)
    await form.handleCancel({ id: 'mo-3', orderNo: 'MO003' } as ProductionOrder)

    expect(confirm).toHaveBeenCalledTimes(2)
    expect(releaseOrder).toHaveBeenCalledWith('mo-2')
    expect(cancelOrder).toHaveBeenCalledWith('mo-3')
    expect(onSuccess).toHaveBeenCalledWith('productionOrder.message.released')
    expect(onSuccess).toHaveBeenCalledWith('productionOrder.message.cancelled')
  })

  it('filters bom options when product changes', () => {
    const form = useProductionOrderForm(t, {
      allBomOptions,
      loadOrder: vi.fn(),
      createOrder: vi.fn(),
      updateOrder: vi.fn(),
      releaseOrder: vi.fn(),
      cancelOrder: vi.fn(),
      confirm: vi.fn()
    })
    form.formData.bomId = 'b1'

    form.handleProductChange('p2')

    expect(form.formData.bomId).toBeUndefined()
    expect(form.bomOptions.value.map((bom) => bom.id)).toEqual(['b2'])
  })
})

import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useInventorySerialForm } from './useInventorySerialForm'

const t = (key: string) => key

const createForm = (overrides: Partial<Parameters<typeof useInventorySerialForm>[1]> = {}) =>
  useInventorySerialForm(t, {
    createInventorySerial: vi.fn(async () => ({})),
    getProducts: vi.fn(async () => ({
      records: [
        { id: 'p1', productCode: 'S1', productName: 'Serial', serialControlled: true } as any,
        { id: 'p2', productCode: 'N1', productName: 'Normal', serialControlled: false } as any
      ],
      total: 2,
      pageNo: 1,
      pageSize: 200
    })),
    locationsForWarehouse: (warehouseId, all = []) =>
      warehouseId
        ? all.filter((location) => String(location.warehouseId) === String(warehouseId))
        : all,
    allLocations: ref([
      { id: 'l1', warehouseId: 'w1', locationCode: 'A1', locationName: 'Bin' } as any,
      { id: 'l2', warehouseId: 'w2', locationCode: 'B1', locationName: 'Bin B' } as any
    ]),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    onSubmitted: vi.fn(),
    ...overrides
  })

describe('inventory serial form', () => {
  it('opens create with serial-controlled products only', async () => {
    const form = createForm()
    await form.openCreate()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.products.value).toHaveLength(1)
    expect(form.products.value[0].id).toBe('p1')
  })

  it('validates and creates a serial', async () => {
    const createInventorySerial = vi.fn(async () => ({}))
    const onWarning = vi.fn()
    const onSuccess = vi.fn()
    const onSubmitted = vi.fn()
    const form = createForm({ createInventorySerial, onWarning, onSuccess, onSubmitted })

    await form.openCreate()
    expect(form.validateForm()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('inventorySerial.validation.required')

    form.form.productId = 'p1'
    form.form.serialNo = 'SN9'
    form.form.warehouseId = 'w1'
    form.form.locationId = 'l1'
    form.form.inboundBizNo = 'R1'
    expect(await form.save()).toBe(true)
    expect(createInventorySerial).toHaveBeenCalledWith({
      productId: 'p1',
      warehouseId: 'w1',
      locationId: 'l1',
      serialNo: 'SN9',
      inboundBizNo: 'R1',
      remark: undefined
    })
    expect(onSuccess).toHaveBeenCalledWith('inventorySerial.message.created')
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('clears location when warehouse changes and surfaces product load failure', async () => {
    const onError = vi.fn()
    const form = createForm({
      getProducts: vi.fn(async () => {
        throw new Error('boom')
      }),
      onError
    })
    form.form.warehouseId = 'w1'
    form.form.locationId = 'l1'
    form.handleFormWarehouseChange()
    expect(form.form.locationId).toBe('')

    await form.openCreate()
    expect(form.products.value).toEqual([])
    expect(onError).toHaveBeenCalledWith('inventorySerial.message.productsLoadFailed')
  })
})

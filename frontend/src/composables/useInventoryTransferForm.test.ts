import { describe, expect, it, vi } from 'vitest'

import type { InventoryTransfer } from '@/api/inventory'
import type { Product } from '@/api/masterdata'
import { useInventoryTransferForm } from './useInventoryTransferForm'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.expected != null) return `${key}:${params.expected}/${params.actual}`
  return key
}

const products: Product[] = [
  {
    id: 'p1',
    code: 'P-001',
    name: '普通件',
    purchasePrice: 12.5,
    lotControlled: false,
    shelfLifeControlled: false,
    serialControlled: false
  } as any,
  {
    id: 'p2',
    code: 'P-002',
    name: '批次件',
    lotControlled: true,
    shelfLifeControlled: false,
    serialControlled: false
  } as any,
  {
    id: 'p3',
    code: 'P-003',
    name: '序列号件',
    lotControlled: false,
    shelfLifeControlled: false,
    serialControlled: true
  } as any
]

const createForm = (overrides: Partial<Parameters<typeof useInventoryTransferForm>[1]> = {}) =>
  useInventoryTransferForm(t, {
    getTransfer: vi.fn(async () => ({
      id: 't1',
      transferNo: 'TR001',
      fromWarehouseId: 1,
      toWarehouseId: 2,
      transferDate: '2026-07-20',
      status: 'DRAFT',
      items: [{ productId: 'p2', quantity: 5, lotNo: 'L1' }]
    } as InventoryTransfer)),
    createTransfer: vi.fn(async () => ({})),
    findProduct: (productId) => products.find((item) => String(item.id) === String(productId)),
    onSuccess: vi.fn(),
    onError: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('inventory transfer form', () => {
  it('opens create mode with a business-date default and empty lines', () => {
    const form = createForm()
    form.handleCreate()

    expect(form.dialogVisible.value).toBe(true)
    expect(form.isView.value).toBe(false)
    expect(form.formData.items).toEqual([])
    expect(form.formData.transferDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(form.selectedFromWarehouseId.value).toBeUndefined()
    expect(form.selectedToWarehouseId.value).toBeUndefined()
  })

  it('loads detail in view mode, hydrating control flags', async () => {
    const form = createForm()

    await form.handleView({ id: 't1' } as InventoryTransfer)

    expect(form.isView.value).toBe(true)
    expect(form.dialogVisible.value).toBe(true)
    expect(form.formData.items[0].lotControlled).toBe(true)
    expect(form.selectedFromWarehouseId.value).toBe(1)
    expect(form.selectedToWarehouseId.value).toBe(2)
  })

  it('reports detail load failures without opening the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      getTransfer: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await form.handleView({ id: 't1' } as InventoryTransfer)
    expect(form.dialogVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('inventoryTransfers.message.detailLoadFailed')
  })

  it('adds, removes and hydrates lines from the selected product', async () => {
    const form = createForm()
    form.handleCreate()

    form.handleAddItem()
    form.handleAddItem()
    expect(form.formData.items).toHaveLength(2)

    form.handleDeleteItem(1)
    expect(form.formData.items).toHaveLength(1)

    form.formData.items[0].productId = 'p1'
    await form.handleProductChange(0)
    expect(form.formData.items[0]).toMatchObject({
      productCode: 'P-001',
      productName: '普通件',
      unitCost: 12.5,
      lotControlled: false,
      serialControlled: false
    })

    form.formData.items[0].productId = 'p3'
    await form.handleProductChange(0)
    expect(form.formData.items[0].serialControlled).toBe(true)
  })

  it('rejects transfers where source and target warehouse are the same', async () => {
    const onWarning = vi.fn()
    const createTransfer = vi.fn(async () => ({}))
    const form = createForm({ onWarning, createTransfer })

    form.handleCreate()
    form.formData.fromWarehouseId = 4
    form.formData.toWarehouseId = '4'
    form.formData.items = [{ productId: 'p1', quantity: 1 } as any]

    expect(await form.handleSubmit()).toBe(false)
    expect(createTransfer).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('inventoryTransfers.validation.warehousesDifferent')
  })

  it('requires at least one line before submitting', async () => {
    const onWarning = vi.fn()
    const createTransfer = vi.fn(async () => ({}))
    const form = createForm({ onWarning, createTransfer })

    form.handleCreate()
    form.formData.fromWarehouseId = 1
    form.formData.toWarehouseId = 2

    expect(await form.handleSubmit()).toBe(false)
    expect(createTransfer).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('inventoryTransfers.validation.itemRequired')
  })

  it('validates lot and serial capture against the transfer quantity', async () => {
    const onWarning = vi.fn()
    const createTransfer = vi.fn(async () => ({}))
    const form = createForm({ onWarning, createTransfer })

    form.handleCreate()
    form.formData.fromWarehouseId = 1
    form.formData.toWarehouseId = 2
    form.formData.items = [{
      productId: 'p2',
      quantity: 3,
      lotNo: '',
      lotControlled: true
    } as any]

    expect(await form.handleSubmit()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('inventoryTransfers.validation.lotRequired')

    form.formData.items = [{
      productId: 'p3',
      quantity: 2,
      serialNos: 'SN1',
      serialControlled: true
    } as any]
    expect(await form.handleSubmit()).toBe(false)
    expect(onWarning).toHaveBeenLastCalledWith('inventoryTransfers.validation.serialCountMismatch:2/1')

    form.formData.items[0].serialNos = 'SN1 SN2'
    expect(await form.handleSubmit()).toBe(true)
    expect(createTransfer).toHaveBeenCalled()
  })

  it('creates the transfer, closes the dialog and refreshes the list', async () => {
    const createTransfer = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const onSuccess = vi.fn()
    const form = createForm({ createTransfer, onSubmitted, onSuccess })

    form.handleCreate()
    form.formData.fromWarehouseId = 1
    form.formData.toWarehouseId = 2
    form.formData.items = [{ productId: 'p1', quantity: 4 } as any]

    expect(await form.handleSubmit()).toBe(true)
    expect(createTransfer).toHaveBeenCalledWith(expect.objectContaining({
      fromWarehouseId: 1,
      toWarehouseId: 2
    }))
    expect(form.dialogVisible.value).toBe(false)
    expect(onSubmitted).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('inventoryTransfers.message.success')
  })

  it('keeps the dialog open and clears loading when submit fails', async () => {
    const onError = vi.fn()
    const form = createForm({
      createTransfer: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    form.handleCreate()
    form.formData.fromWarehouseId = 1
    form.formData.toWarehouseId = 2
    form.formData.items = [{ productId: 'p1', quantity: 1 } as any]

    expect(await form.handleSubmit()).toBe(false)
    expect(form.dialogVisible.value).toBe(true)
    expect(form.submitLoading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('inventoryTransfers.message.failed')
  })
})

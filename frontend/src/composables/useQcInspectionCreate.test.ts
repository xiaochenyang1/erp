import { describe, expect, it, vi } from 'vitest'

import { useQcInspectionCreate } from './useQcInspectionCreate'

const t = (key: string) => key

const createDialog = (overrides: Partial<Parameters<typeof useQcInspectionCreate>[1]> = {}) =>
  useQcInspectionCreate(t, {
    createInspection: vi.fn(async () => ({})),
    getReceipts: vi.fn(async () => ({
      records: [{ id: 'r1', receiptNo: 'RC001' }],
      total: 1
    } as any)),
    getDeliveries: vi.fn(async () => ({
      records: [{ id: 'd1', deliveryNo: 'DL001' }],
      total: 1
    } as any)),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    onSubmitted: vi.fn(),
    ...overrides
  })

describe('qc inspection create', () => {
  it('opens as IQC with a business date and loads draft receipts only', async () => {
    const getReceipts = vi.fn(async () => ({ records: [{ id: 'r1' }], total: 1 } as any))
    const getDeliveries = vi.fn(async () => ({ records: [], total: 0 } as any))
    const dialog = createDialog({ getReceipts, getDeliveries })

    await dialog.handleCreate()

    expect(dialog.createVisible.value).toBe(true)
    expect(dialog.createForm.inspectionType).toBe('IQC')
    expect(dialog.createForm.inspectionDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(getReceipts).toHaveBeenCalledWith({ pageNo: 1, pageSize: 100, status: 'DRAFT' })
    expect(getDeliveries).not.toHaveBeenCalled()
    expect(dialog.draftReceipts.value).toHaveLength(1)
    expect(dialog.sourceLoading.value).toBe(false)
  })

  it('swaps the source list and clears the selection when the type changes', async () => {
    const getReceipts = vi.fn(async () => ({ records: [{ id: 'r1' }], total: 1 } as any))
    const getDeliveries = vi.fn(async () => ({ records: [{ id: 'd1' }], total: 1 } as any))
    const dialog = createDialog({ getReceipts, getDeliveries })

    await dialog.handleCreate()
    dialog.createForm.receiptId = 'r1'

    dialog.createForm.inspectionType = 'OQC'
    await dialog.onCreateTypeChange()
    expect(dialog.createForm.receiptId).toBe('')
    expect(getDeliveries).toHaveBeenCalledWith({ pageNo: 1, pageSize: 100, status: 'DRAFT' })
    expect(dialog.draftDeliveries.value).toHaveLength(1)

    dialog.createForm.inspectionType = 'IPQC'
    await dialog.onCreateTypeChange()
    expect(dialog.draftReceipts.value).toEqual([])
    expect(dialog.draftDeliveries.value).toEqual([])
    expect(getDeliveries).toHaveBeenCalledTimes(1)
  })

  it('reports source load failures', async () => {
    const onError = vi.fn()
    const dialog = createDialog({
      getReceipts: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await dialog.handleCreate()
    expect(onError).toHaveBeenCalledWith('qcInspection.message.sourcesLoadFailed')
    expect(dialog.sourceLoading.value).toBe(false)
  })

  it('requires the source document matching the selected type', async () => {
    const onWarning = vi.fn()
    const createInspection = vi.fn(async () => ({}))
    const dialog = createDialog({ onWarning, createInspection })
    await dialog.handleCreate()

    expect(await dialog.confirmCreate()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('qcInspection.validation.receipt')

    dialog.createForm.inspectionType = 'OQC'
    expect(await dialog.confirmCreate()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('qcInspection.validation.delivery')

    dialog.createForm.inspectionType = 'IPQC'
    dialog.createForm.productionOrderId = '   '
    expect(await dialog.confirmCreate()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('qcInspection.validation.productionOrder')

    expect(createInspection).not.toHaveBeenCalled()
  })

  it('requires an inspection date', async () => {
    const onWarning = vi.fn()
    const createInspection = vi.fn(async () => ({}))
    const dialog = createDialog({ onWarning, createInspection })
    await dialog.handleCreate()

    dialog.createForm.receiptId = 'r1'
    dialog.createForm.inspectionDate = ''
    expect(await dialog.confirmCreate()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('qcInspection.validation.date')
    expect(createInspection).not.toHaveBeenCalled()
  })

  it('sends only the source field for the chosen type', async () => {
    const createInspection = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const dialog = createDialog({ createInspection, onSubmitted })

    await dialog.handleCreate()
    dialog.createForm.receiptId = 'r1'
    dialog.createForm.remark = 'note'
    expect(await dialog.confirmCreate()).toBe(true)
    expect(createInspection).toHaveBeenLastCalledWith(expect.objectContaining({
      inspectionType: 'IQC',
      receiptId: 'r1',
      deliveryId: undefined,
      productionOrderId: undefined,
      remark: 'note'
    }))
    expect(dialog.createVisible.value).toBe(false)
    expect(onSubmitted).toHaveBeenCalled()

    await dialog.handleCreate()
    dialog.createForm.inspectionType = 'OQC'
    dialog.createForm.deliveryId = 'd1'
    expect(await dialog.confirmCreate()).toBe(true)
    expect(createInspection).toHaveBeenLastCalledWith(expect.objectContaining({
      inspectionType: 'OQC',
      deliveryId: 'd1',
      receiptId: undefined,
      productionOrderId: undefined
    }))

    await dialog.handleCreate()
    dialog.createForm.inspectionType = 'IPQC'
    dialog.createForm.productionOrderId = 'PO-9'
    expect(await dialog.confirmCreate()).toBe(true)
    expect(createInspection).toHaveBeenLastCalledWith(expect.objectContaining({
      inspectionType: 'IPQC',
      productionOrderId: 'PO-9',
      receiptId: undefined,
      deliveryId: undefined
    }))
  })

  it('keeps the dialog open and clears submitting when creation fails', async () => {
    const onSubmitted = vi.fn()
    const dialog = createDialog({
      createInspection: vi.fn(async () => { throw new Error('boom') }),
      onSubmitted
    })

    await dialog.handleCreate()
    dialog.createForm.receiptId = 'r1'

    expect(await dialog.confirmCreate()).toBe(false)
    expect(dialog.createVisible.value).toBe(true)
    expect(dialog.submitting.value).toBe(false)
    expect(onSubmitted).not.toHaveBeenCalled()
  })
})

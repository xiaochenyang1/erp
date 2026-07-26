import { describe, expect, it, vi } from 'vitest'

import type { PurchaseRequisition } from '@/api/purchase'
import { usePurchaseRequisitionForm } from './usePurchaseRequisitionForm'

const t = (key: string) => key

const detail = {
  id: 'rq1',
  requisitionNo: 'PRQ001',
  requisitionDate: '2026-07-20',
  neededDate: '2026-07-25',
  supplierId: 9,
  remark: '备注',
  lines: [
    { productId: 11, qty: 3, remark: '行1' }
  ]
} as unknown as PurchaseRequisition

const createForm = (overrides: Partial<Parameters<typeof usePurchaseRequisitionForm>[1]> = {}) =>
  usePurchaseRequisitionForm(t, {
    getRequisition: vi.fn(async () => detail),
    createRequisition: vi.fn(async () => ({})),
    updateRequisition: vi.fn(async () => ({})),
    ensureOptions: vi.fn(async () => {}),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('purchase requisition form', () => {
  it('opens a blank create form dated in the business timezone', async () => {
    const ensureOptions = vi.fn(async () => {})
    const form = createForm({ ensureOptions })
    await form.openCreate()

    expect(ensureOptions).toHaveBeenCalled()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.editingId.value).toBeNull()
    expect(form.form.requisitionDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(form.form.lines).toEqual([{ productId: '', qty: 1, remark: '' }])
  })

  it('loads the detail for edit and stringifies ids', async () => {
    const getRequisition = vi.fn(async () => detail)
    const form = createForm({ getRequisition })

    expect(await form.openEdit({ id: 'rq1' } as PurchaseRequisition)).toBe(true)
    expect(getRequisition).toHaveBeenCalledWith('rq1')
    expect(form.editingId.value).toBe('rq1')
    expect(form.form).toMatchObject({
      requisitionDate: '2026-07-20',
      neededDate: '2026-07-25',
      supplierId: '9',
      remark: '备注'
    })
    expect(form.form.lines).toEqual([{ productId: '11', qty: 3, remark: '行1' }])
  })

  it('reports detail failures without opening the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      getRequisition: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await form.openEdit({ id: 'rq1' } as PurchaseRequisition)).toBe(false)
    expect(form.dialogVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('purchaseRequisition.message.detailLoadFailed')
  })

  it('adds and removes lines', async () => {
    const form = createForm()
    await form.openCreate()
    form.addLine()
    expect(form.form.lines).toHaveLength(2)
    form.removeLine(0)
    expect(form.form.lines).toHaveLength(1)
  })

  it('blocks save without required fields', async () => {
    const onWarning = vi.fn()
    const form = createForm({ onWarning })
    await form.openCreate()
    form.form.requisitionDate = ''
    expect(await form.save()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('purchaseRequisition.validation.required')
  })

  it('creates when no id is present and updates when editing', async () => {
    const createRequisition = vi.fn(async () => ({}))
    const updateRequisition = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createRequisition, updateRequisition, onSubmitted })

    await form.openCreate()
    form.form.lines[0] = { productId: 'p1', qty: 2, remark: '' }
    expect(await form.save()).toBe(true)
    expect(createRequisition).toHaveBeenCalledWith({
      requisitionDate: form.form.requisitionDate,
      neededDate: null,
      supplierId: null,
      remark: undefined,
      lines: [{ productId: 'p1', qty: 2, remark: undefined }]
    })
    expect(onSubmitted).toHaveBeenCalled()

    await form.openEdit({ id: 'rq1' } as PurchaseRequisition)
    form.form.remark = '改'
    expect(await form.save()).toBe(true)
    expect(updateRequisition).toHaveBeenCalledWith('rq1', expect.objectContaining({
      remark: '改',
      supplierId: '9',
      neededDate: '2026-07-25'
    }))
  })

  it('reports save failures without closing the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      createRequisition: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await form.openCreate()
    form.form.lines[0] = { productId: 'p1', qty: 1, remark: '' }
    expect(await form.save()).toBe(false)
    expect(form.dialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('purchaseRequisition.message.saveFailed')
    expect(form.saving.value).toBe(false)
  })
})

import { describe, expect, it, vi } from 'vitest'

import type { PurchaseInquiry } from '@/api/purchase'
import { usePurchaseInquiryForm } from './usePurchaseInquiryForm'

const t = (key: string) => key

describe('purchase inquiry form', () => {
  it('creates, edits and saves inquiry form state', async () => {
    const loadOptions = vi.fn(async () => {})
    const createInquiry = vi.fn(async () => ({}))
    const updateInquiry = vi.fn(async () => ({}))
    const getInquiry = vi.fn(async () => ({
      id: 'inq-1',
      inquiryDate: '2026-07-01',
      title: 'Steel RFQ',
      remark: 'urgent',
      lines: [{ productId: 'p1', qty: 12, remark: 'A' }]
    } as PurchaseInquiry))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const form = usePurchaseInquiryForm(t, {
      loadOptions,
      getInquiry,
      createInquiry,
      updateInquiry,
      formatBusinessDate: () => '2026-07-26',
      onSuccess,
      onCompleted
    })

    await form.handleCreate()
    expect(loadOptions).toHaveBeenCalled()
    expect(form.formVisible.value).toBe(true)
    expect(form.form.inquiryDate).toBe('2026-07-26')
    expect(form.form.lines).toHaveLength(1)

    form.addLine()
    expect(form.form.lines).toHaveLength(2)
    form.removeLine(1)
    expect(form.form.lines).toHaveLength(1)

    form.form.lines[0].productId = 'p1'
    form.form.lines[0].qty = 5
    await form.confirmSave()
    expect(createInquiry).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('purchaseInquiryOps.message.created')
    expect(onCompleted).toHaveBeenCalled()

    await form.handleEdit({ id: 'inq-1' } as PurchaseInquiry)
    expect(form.editingId.value).toBe('inq-1')
    expect(form.form.title).toBe('Steel RFQ')
    form.form.lines[0].qty = 20
    await form.confirmSave()
    expect(updateInquiry).toHaveBeenCalledWith('inq-1', expect.objectContaining({
      inquiryDate: '2026-07-01',
      lines: [expect.objectContaining({ productId: 'p1', qty: 20 })]
    }))
    expect(onSuccess).toHaveBeenCalledWith('purchaseInquiryOps.message.saved')
  })

  it('warns when saving without valid lines', async () => {
    const onWarning = vi.fn()
    const form = usePurchaseInquiryForm(t, {
      loadOptions: vi.fn(async () => {}),
      getInquiry: vi.fn(async () => ({} as PurchaseInquiry)),
      createInquiry: vi.fn(async () => ({})),
      updateInquiry: vi.fn(async () => ({})),
      onWarning
    })

    await form.handleCreate()
    form.form.lines = [{ productId: '', qty: 1, remark: '' }]
    await form.confirmSave()
    expect(onWarning).toHaveBeenCalledWith('purchaseInquiryOps.validation.lineRequired')
  })
})

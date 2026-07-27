import { describe, expect, it, vi } from 'vitest'

import type { FinanceInvoice } from '@/api/finance'
import { useFinanceInvoiceForm } from './useFinanceInvoiceForm'

const t = (key: string) => key

const invoice = (overrides: Partial<FinanceInvoice> = {}): FinanceInvoice => ({
  id: '1',
  invoiceNo: 'INV1',
  invoiceType: 'OUTPUT',
  partnerName: 'Acme',
  amount: 100,
  taxAmount: 13,
  invoiceDate: '2026-07-01',
  status: 'DRAFT',
  relatedBizType: 'SALES_ORDER',
  relatedBizId: '9',
  remark: 'note',
  ...overrides
})

const createForm = (overrides: Partial<Parameters<typeof useFinanceInvoiceForm>[1]> = {}) =>
  useFinanceInvoiceForm(t, {
    getFinanceInvoice: vi.fn(async () => invoice()),
    createFinanceInvoice: vi.fn(async () => ({})),
    updateFinanceInvoice: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onSubmitted: vi.fn(),
    ...overrides
  })

describe('finance invoice form', () => {
  it('opens create with today and creates invoices', async () => {
    const createFinanceInvoice = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onSubmitted = vi.fn()
    const form = createForm({ createFinanceInvoice, onSuccess, onSubmitted })

    form.handleAdd()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.dialogMode.value).toBe('create')
    expect(form.formData.invoiceDate).toBeTruthy()
    expect(form.dialogTitle.value).toBe('financeReportPages.invoices.dialog.create')

    form.formData.invoiceType = 'INPUT'
    form.formData.partnerName = 'Vendor'
    form.formData.amount = 50
    form.formData.taxAmount = 6.5
    form.formData.relatedBizType = 'PURCHASE_ORDER'
    form.formData.relatedBizId = '3'
    expect(await form.submitSave()).toBe(true)
    expect(createFinanceInvoice).toHaveBeenCalledWith({
      invoiceType: 'INPUT',
      partnerName: 'Vendor',
      invoiceDate: form.formData.invoiceDate,
      amount: 50,
      taxAmount: 6.5,
      relatedBizType: 'PURCHASE_ORDER',
      relatedBizId: '3',
      remark: undefined
    })
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.invoices.message.saved')
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('loads detail for edit and updates', async () => {
    const updateFinanceInvoice = vi.fn(async () => ({}))
    const form = createForm({ updateFinanceInvoice })
    expect(await form.handleEdit({ id: '1' })).toBe(true)
    expect(form.dialogMode.value).toBe('edit')
    expect(form.formData.partnerName).toBe('Acme')
    expect(form.formData.relatedBizId).toBe('9')
    form.formData.remark = 'updated'
    expect(await form.submitSave()).toBe(true)
    expect(updateFinanceInvoice).toHaveBeenCalledWith(
      '1',
      expect.objectContaining({ remark: 'updated', partnerName: 'Acme' })
    )
  })

  it('surfaces detail load failure and resets on close', async () => {
    const onError = vi.fn()
    const form = createForm({
      getFinanceInvoice: vi.fn(async () => {
        throw new Error('missing')
      }),
      onError
    })
    expect(await form.handleEdit({ id: 'x' })).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.invoices.message.detailLoadFailed')

    form.handleAdd()
    form.formData.partnerName = 'tmp'
    form.handleDialogClose()
    expect(form.formData.partnerName).toBe('')
  })
})

import { describe, expect, it, vi } from 'vitest'

import type { QcInspection } from '@/api/qc'
import { useQcInspectionEdit } from './useQcInspectionEdit'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.line != null ? `${key}:${params.line}` : key

const draft = (overrides: Partial<QcInspection> = {}): QcInspection => ({
  id: 'q1',
  inspectionNo: 'QC001',
  inspectionType: 'IQC',
  receiptId: 'r1',
  inspectionDate: '2026-07-20',
  status: 'DRAFT',
  totalQty: 10,
  qualifiedQty: 0,
  unqualifiedQty: 0,
  remark: 'note',
  lines: [
    {
      id: 'l1',
      lineNo: 1,
      productId: 'p1',
      inspectedQty: 10,
      qualifiedQty: 0,
      unqualifiedQty: 0,
      defectReason: '',
      remark: ''
    }
  ],
  ...overrides
} as QcInspection)

const createEdit = (overrides: Partial<Parameters<typeof useQcInspectionEdit>[1]> = {}) =>
  useQcInspectionEdit(t, {
    getInspection: vi.fn(async () => draft()),
    updateInspection: vi.fn(async () => ({})),
    judgeInspection: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    onSubmitted: vi.fn(),
    ...overrides
  })

describe('qc inspection edit', () => {
  it('loads a draft into the edit form with its lines', async () => {
    const edit = createEdit()

    expect(await edit.handleEdit(draft())).toBe(true)
    expect(edit.editVisible.value).toBe(true)
    expect(edit.editingInspectionNo.value).toBe('QC001')
    expect(edit.editForm.inspectionDate).toBe('2026-07-20')
    expect(edit.editForm.remark).toBe('note')
    expect(edit.editLines.value).toEqual([{
      lineId: 'l1',
      lineNo: 1,
      productId: 'p1',
      inspectedQty: 10,
      defectReason: '',
      remark: ''
    }])
  })

  it('refuses non-draft rows and re-checks the fetched status', async () => {
    const onWarning = vi.fn()
    const getInspection = vi.fn(async () => draft())
    const edit = createEdit({ onWarning, getInspection })

    expect(await edit.handleEdit(draft({ status: 'SUBMITTED' }))).toBe(false)
    expect(getInspection).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('qcInspection.validation.draftOnly')

    const stale = createEdit({
      onWarning,
      getInspection: vi.fn(async () => draft({ status: 'JUDGED' }))
    })
    expect(await stale.handleEdit(draft())).toBe(false)
    expect(stale.editVisible.value).toBe(false)
  })

  it('reports detail load failures for edit', async () => {
    const onError = vi.fn()
    const edit = createEdit({
      getInspection: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await edit.handleEdit(draft())).toBe(false)
    expect(edit.editVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('qcInspection.message.detailLoadFailed')
  })

  it('titles the edit dialog and resolves the source id per inspection type', async () => {
    const edit = createEdit()
    await edit.handleEdit(draft())
    expect(edit.editDialogTitle.value).toBe('qcInspection.dialog.editIqc')
    expect(edit.editSourceDocumentId.value).toBe('r1')

    edit.editForm.inspectionType = 'OQC'
    edit.editForm.deliveryId = 'd7'
    expect(edit.editDialogTitle.value).toBe('qcInspection.dialog.editOqc')
    expect(edit.editSourceDocumentId.value).toBe('d7')

    edit.editForm.inspectionType = 'IPQC'
    edit.editForm.productionOrderId = 'PO-3'
    expect(edit.editDialogTitle.value).toBe('qcInspection.dialog.editIpqc')
    expect(edit.editSourceDocumentId.value).toBe('PO-3')
  })

  it('rejects a missing date or negative inspected quantity', async () => {
    const onWarning = vi.fn()
    const updateInspection = vi.fn(async () => ({}))
    const edit = createEdit({ onWarning, updateInspection })
    await edit.handleEdit(draft())

    edit.editForm.inspectionDate = ''
    expect(await edit.confirmEdit()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('qcInspection.validation.date')

    edit.editForm.inspectionDate = '2026-07-21'
    edit.editLines.value[0].inspectedQty = -1
    expect(await edit.confirmEdit()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('qcInspection.validation.negativeQuantity:1')
    expect(updateInspection).not.toHaveBeenCalled()
  })

  it('warns when no editable inspection is loaded', async () => {
    const onWarning = vi.fn()
    const edit = createEdit({ onWarning })

    expect(await edit.confirmEdit()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('qcInspection.validation.editableMissing')
  })

  it('saves trimmed line edits and resets the form', async () => {
    const updateInspection = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const edit = createEdit({ updateInspection, onSubmitted })
    await edit.handleEdit(draft())

    edit.editForm.remark = '  updated  '
    edit.editLines.value[0].inspectedQty = 8
    edit.editLines.value[0].defectReason = '  scratch  '
    edit.editLines.value[0].remark = '   '

    expect(await edit.confirmEdit()).toBe(true)
    expect(updateInspection).toHaveBeenCalledWith('q1', {
      inspectionDate: '2026-07-20',
      remark: 'updated',
      lines: [{
        lineId: 'l1',
        inspectedQty: 8,
        defectReason: 'scratch',
        remark: undefined
      }]
    })
    expect(edit.editVisible.value).toBe(false)
    expect(edit.editingId.value).toBeNull()
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('defaults judge lines to fully qualified', async () => {
    const edit = createEdit()

    const detail = await edit.handleJudge(draft())
    expect(detail?.inspectionNo).toBe('QC001')
    expect(edit.judgeVisible.value).toBe(true)
    expect(edit.judgingInspectionNo.value).toBe('QC001')
    expect(edit.judgeLines.value).toEqual([{
      lineId: 'l1',
      productId: 'p1',
      lineNo: 1,
      inspectedQty: 10,
      qualifiedQty: 10,
      unqualifiedQty: 0,
      defectReason: ''
    }])
  })

  it('requires qualified plus unqualified to equal the inspected quantity', async () => {
    const onWarning = vi.fn()
    const judgeInspection = vi.fn(async () => ({}))
    const edit = createEdit({ onWarning, judgeInspection })
    await edit.handleJudge(draft())

    edit.judgeLines.value[0].qualifiedQty = 4
    edit.judgeLines.value[0].unqualifiedQty = 4
    expect(await edit.confirmJudge()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('qcInspection.validation.judgeQuantity:1')
    expect(judgeInspection).not.toHaveBeenCalled()

    edit.judgeLines.value[0].unqualifiedQty = 6
    edit.judgeLines.value[0].defectReason = 'dent'
    expect(await edit.confirmJudge()).toBe(true)
    expect(judgeInspection).toHaveBeenCalledWith('q1', {
      lines: [{
        lineId: 'l1',
        qualifiedQty: 4,
        unqualifiedQty: 6,
        defectReason: 'dent'
      }]
    })
    expect(edit.judgeVisible.value).toBe(false)
  })

  it('reports detail load failures for judge and keeps dialogs closed on API failure', async () => {
    const onError = vi.fn()
    const failingDetail = createEdit({
      getInspection: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    expect(await failingDetail.handleJudge(draft())).toBeUndefined()
    expect(failingDetail.judgeVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('qcInspection.message.detailLoadFailed')

    const onSubmitted = vi.fn()
    const failingJudge = createEdit({
      judgeInspection: vi.fn(async () => { throw new Error('boom') }),
      onSubmitted
    })
    await failingJudge.handleJudge(draft())
    expect(await failingJudge.confirmJudge()).toBe(false)
    expect(failingJudge.judgeVisible.value).toBe(true)
    expect(failingJudge.submitting.value).toBe(false)
    expect(onSubmitted).not.toHaveBeenCalled()
  })
})

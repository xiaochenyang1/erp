import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import {
  usePurchaseOrderPresentation,
  usePurchaseOrderSummary
} from './usePurchaseOrderPresentation'
import type { PurchaseOrderLike } from './usePurchaseOrderPresentation'

describe('purchase order presentation', () => {
  it('formats money and follows the backend lifecycle action matrix', () => {
    const presentation = usePurchaseOrderPresentation()

    expect(presentation.formatMoney(12.3)).toMatch(/12\.30/)

    const actionCases: Array<[
      keyof Pick<typeof presentation, 'canEdit' | 'canSubmit' | 'canApprove' | 'canReject' | 'canUnapprove' | 'canCancel' | 'canClose'>,
      PurchaseOrderLike,
      boolean
    ]> = [
      ['canEdit', { status: 'DRAFT' }, true],
      ['canEdit', { status: 'REJECTED' }, true],
      ['canEdit', { status: 'SUBMITTED' }, false],
      ['canSubmit', { status: 'DRAFT' }, true],
      ['canSubmit', { status: 'REJECTED' }, true],
      ['canSubmit', { status: 'APPROVED' }, false],
      ['canApprove', { status: 'SUBMITTED', approvalStatus: 'IN_APPROVAL' }, true],
      ['canApprove', { status: 'PENDING', approvalStatus: 'IN_APPROVAL' }, false],
      ['canApprove', { status: 'SUBMITTED', approvalStatus: 'APPROVED' }, false],
      ['canReject', { status: 'SUBMITTED', approvalStatus: 'IN_APPROVAL' }, true],
      ['canReject', { status: 'SUBMITTED' }, false],
      ['canUnapprove', {
        status: 'APPROVED',
        approvalStatus: 'APPROVED',
        receiptStatus: 'NOT_RECEIVED'
      }, true],
      ['canUnapprove', {
        status: 'APPROVED',
        approvalStatus: 'APPROVED',
        receiptStatus: 'PARTIAL_RECEIVED'
      }, false],
      ['canUnapprove', { status: 'APPROVED', receiptStatus: 'NOT_RECEIVED' }, false],
      ['canCancel', { status: 'DRAFT' }, true],
      ['canCancel', { status: 'REJECTED' }, true],
      ['canCancel', { status: 'SUBMITTED' }, true],
      ['canCancel', { status: 'APPROVED' }, false],
      ['canClose', { status: 'APPROVED', receiptStatus: 'NOT_RECEIVED' }, true],
      ['canClose', { status: 'APPROVED', receiptStatus: 'PARTIAL_RECEIVED' }, true],
      ['canClose', { status: 'APPROVED', receiptStatus: 'RECEIVED' }, false],
      ['canClose', { status: 'DRAFT', receiptStatus: 'NOT_RECEIVED' }, false]
    ]

    for (const [action, row, expected] of actionCases) {
      expect(presentation[action](row), `${action} ${row.status}/${row.approvalStatus}/${row.receiptStatus}`).toBe(expected)
    }

    expect(presentation.canCancelOrder({ status: 'DRAFT' })).toBe(true)
    expect(presentation.canCloseOrder({ status: 'APPROVED', receiptStatus: 'RECEIVED' })).toBe(false)
    expect(presentation.canUnapproveOrder({
      status: 'APPROVED',
      approvalStatus: 'APPROVED',
      receiptStatus: 'NOT_RECEIVED'
    })).toBe(true)
  })

  it('counts pending and approved rows', () => {
    const presentation = usePurchaseOrderPresentation()
    const rows: PurchaseOrderLike[] = [
      { status: 'SUBMITTED' },
      { status: 'PENDING' },
      { status: 'DRAFT', approvalStatus: 'IN_APPROVAL' },
      { status: 'SUBMITTED', approvalStatus: 'IN_APPROVAL' },
      { status: 'APPROVED' },
      { status: 'CLOSED' }
    ]

    expect(presentation.pendingCount(rows)).toBe(1)
    expect(presentation.approvedCount(rows)).toBe(1)
  })

  it('exposes reactive summary counts', () => {
    const tableData = ref<PurchaseOrderLike[]>([
      { status: 'SUBMITTED', approvalStatus: 'IN_APPROVAL' },
      { status: 'APPROVED' },
      { status: 'APPROVED' }
    ])
    const summary = usePurchaseOrderSummary(tableData)

    expect(summary.pendingCount.value).toBe(1)
    expect(summary.approvedCount.value).toBe(2)

    tableData.value = [
      { status: 'PENDING' },
      { status: 'DRAFT', approvalStatus: 'IN_APPROVAL' }
    ]
    expect(summary.pendingCount.value).toBe(0)
    expect(summary.approvedCount.value).toBe(0)
  })
})

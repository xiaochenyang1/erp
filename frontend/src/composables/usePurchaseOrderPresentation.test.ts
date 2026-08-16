import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import {
  usePurchaseOrderPresentation,
  usePurchaseOrderSummary
} from './usePurchaseOrderPresentation'
import type { PurchaseOrderLike } from './usePurchaseOrderPresentation'

describe('purchase order presentation', () => {
  it('formats money and detects order action eligibility', () => {
    const presentation = usePurchaseOrderPresentation()

    expect(presentation.formatMoney(12.3)).toMatch(/12\.30/)
    expect(presentation.canCancelOrder({ status: 'DRAFT' })).toBe(true)
    expect(presentation.canCancelOrder({ status: 'APPROVED' })).toBe(false)
    expect(presentation.canCloseOrder({
      status: 'APPROVED',
      receiptStatus: 'PARTIAL_RECEIVED'
    })).toBe(true)
    expect(presentation.canCloseOrder({
      status: 'APPROVED',
      receiptStatus: 'RECEIVED'
    })).toBe(false)
    expect(presentation.canUnapproveOrder({
      status: 'APPROVED',
      approvalStatus: 'APPROVED',
      receiptStatus: 'NOT_RECEIVED'
    })).toBe(true)
    expect(presentation.canUnapproveOrder({
      status: 'APPROVED',
      approvalStatus: 'APPROVED',
      receiptStatus: 'PARTIAL_RECEIVED'
    })).toBe(false)
  })

  it('counts pending and approved rows', () => {
    const presentation = usePurchaseOrderPresentation()
    const rows: PurchaseOrderLike[] = [
      { status: 'SUBMITTED' },
      { status: 'PENDING' },
      { status: 'DRAFT', approvalStatus: 'IN_APPROVAL' },
      { status: 'APPROVED' },
      { status: 'CLOSED' }
    ]

    expect(presentation.pendingCount(rows)).toBe(3)
    expect(presentation.approvedCount(rows)).toBe(1)
  })

  it('exposes reactive summary counts', () => {
    const tableData = ref<PurchaseOrderLike[]>([
      { status: 'SUBMITTED' },
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
    expect(summary.pendingCount.value).toBe(2)
    expect(summary.approvedCount.value).toBe(0)
  })
})

import { describe, expect, it } from 'vitest'

import type { SalesOrder } from '@/api/sales'
import { useSalesOrderPresentation } from './useSalesOrderPresentation'

const t = (key: string) => key

describe('sales order presentation', () => {
  const presentation = useSalesOrderPresentation(t)

  const order = (state: Record<string, unknown>) => state as unknown as SalesOrder

  it.each([
    [{ status: 'DRAFT' }, true],
    [{ status: 'DRAFT', approvalStatus: 'APPROVED' }, true],
    [{ status: 'REJECTED' }, true],
    [{ status: 'SUBMITTED', approvalStatus: 'DRAFT' }, false],
    [{ status: 'APPROVED', approvalStatus: 'DRAFT' }, false],
    [{ status: 'CANCELLED' }, false],
    [{ status: 'CLOSED' }, false]
  ])('allows editing only for editable statuses (%o)', (state, expected) => {
    expect(presentation.canEdit(order(state))).toBe(expected)
  })

  it.each([
    [{ status: 'DRAFT' }, true],
    [{ status: 'DRAFT', approvalStatus: 'APPROVED' }, true],
    [{ status: 'REJECTED' }, true],
    [{ status: 'SUBMITTED', approvalStatus: 'DRAFT' }, false],
    [{ status: 'APPROVED', approvalStatus: 'DRAFT' }, false],
    [{ status: 'CANCELLED' }, false],
    [{ status: 'CLOSED' }, false]
  ])('allows submission only for draft or rejected orders (%o)', (state, expected) => {
    expect(presentation.canSubmit(order(state))).toBe(expected)
  })

  it.each([
    [{ status: 'SUBMITTED', approvalStatus: 'IN_APPROVAL' }, true],
    [{ status: 'SUBMITTED', approvalStatus: 'PENDING' }, false],
    [{ status: 'SUBMITTED', approvalStatus: 'APPROVED' }, false],
    [{ status: 'DRAFT', approvalStatus: 'IN_APPROVAL' }, false],
    [{ status: 'REJECTED', approvalStatus: 'IN_APPROVAL' }, false],
    [{ status: 'APPROVED', approvalStatus: 'IN_APPROVAL' }, false]
  ])('requires the submitted/in-approval pair for approval (%o)', (state, expected) => {
    expect(presentation.canApprove(order(state))).toBe(expected)
  })

  it.each([
    [{ status: 'SUBMITTED', approvalStatus: 'IN_APPROVAL' }, true],
    [{ status: 'SUBMITTED', approvalStatus: 'PENDING' }, false],
    [{ status: 'SUBMITTED', approvalStatus: 'APPROVED' }, false],
    [{ status: 'DRAFT', approvalStatus: 'IN_APPROVAL' }, false],
    [{ status: 'REJECTED', approvalStatus: 'IN_APPROVAL' }, false],
    [{ status: 'APPROVED', approvalStatus: 'IN_APPROVAL' }, false]
  ])('requires the submitted/in-approval pair for rejection (%o)', (state, expected) => {
    expect(presentation.canReject(order(state))).toBe(expected)
  })

  it.each([
    [{ status: 'APPROVED', approvalStatus: 'APPROVED', deliveryStatus: 'NOT_DELIVERED' }, true],
    [{ status: 'APPROVED', approvalStatus: 'APPROVED', deliveryStatus: 'PARTIAL_DELIVERED' }, false],
    [{ status: 'APPROVED', approvalStatus: 'IN_APPROVAL', deliveryStatus: 'NOT_DELIVERED' }, false],
    [{ status: 'DRAFT', approvalStatus: 'APPROVED', deliveryStatus: 'NOT_DELIVERED' }, false],
    [{ status: 'APPROVED', approvalStatus: 'APPROVED' }, false]
  ])('requires an approved and not-delivered order for reversal (%o)', (state, expected) => {
    expect(presentation.canUnapprove(order(state))).toBe(expected)
  })

  it.each([
    [{ status: 'DRAFT', approvalStatus: 'NOT_SUBMITTED' }, true],
    [{ status: 'REJECTED', approvalStatus: 'REJECTED' }, true],
    [{ status: 'SUBMITTED', approvalStatus: 'IN_APPROVAL' }, true],
    [{ status: 'APPROVED', approvalStatus: 'APPROVED', deliveryStatus: 'NOT_DELIVERED' }, true],
    [{ status: 'APPROVED', approvalStatus: 'APPROVED' }, false],
    [{ status: 'APPROVED', approvalStatus: 'APPROVED', deliveryStatus: 'PARTIAL_DELIVERED' }, false],
    [{ status: 'APPROVED', approvalStatus: 'APPROVED', deliveryStatus: 'FULL_DELIVERED' }, false],
    [{ status: 'CANCELLED', approvalStatus: 'CANCELLED' }, false],
    [{ status: 'CLOSED', approvalStatus: 'CLOSED' }, false]
  ])('matches the service cancellation guard (%o)', (state, expected) => {
    expect(presentation.canCancel(order(state))).toBe(expected)
  })

  it('maps status labels and formatting helpers', () => {
    expect(presentation.statusText('APPROVED')).toBe('salesOrder.status.approved')
    expect(presentation.approvalTagType('REJECTED')).toBe('danger')
    expect(presentation.deliveryText('PARTIAL_DELIVERED')).toBe('salesOrder.status.partial')
    expect(presentation.deliveryText('FULL_DELIVERED')).toBe('salesOrder.status.delivered')
    expect(presentation.lineAmount({ quantity: 2, price: 5 } as any)).toBe(10)
  })
})

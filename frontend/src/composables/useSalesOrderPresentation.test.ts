import { describe, expect, it } from 'vitest'

import type { SalesOrder } from '@/api/sales'
import { useSalesOrderPresentation } from './useSalesOrderPresentation'

const t = (key: string) => key

describe('sales order presentation', () => {
  it('maps action eligibility and status labels', () => {
    const presentation = useSalesOrderPresentation(t)

    expect(presentation.canEdit({ status: 'DRAFT', approvalStatus: 'DRAFT' } as SalesOrder)).toBe(true)
    expect(presentation.canApprove({ approvalStatus: 'IN_APPROVAL' } as SalesOrder)).toBe(true)
    expect(presentation.canUnapprove({
      status: 'APPROVED',
      approvalStatus: 'APPROVED',
      deliveryStatus: 'NOT_DELIVERED'
    } as SalesOrder)).toBe(true)
    expect(presentation.canUnapprove({
      status: 'APPROVED',
      approvalStatus: 'APPROVED',
      deliveryStatus: 'PARTIAL'
    } as SalesOrder)).toBe(false)
    expect(presentation.statusText('APPROVED')).toBe('salesOrder.status.approved')
    expect(presentation.approvalTagType('REJECTED')).toBe('danger')
    expect(presentation.lineAmount({ quantity: 2, price: 5 } as any)).toBe(10)
  })
})

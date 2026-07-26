import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { Supplier } from '@/api/masterdata'
import { usePurchaseRequisitionPresentation } from './usePurchaseRequisitionPresentation'

/** Simulate vue-i18n: known keys return a translated label, unknowns echo the key. */
const t = (key: string) => {
  const labels: Record<string, string> = {
    'purchaseRequisition.statusValue.draft': '草稿',
    'purchaseRequisition.approvalValue.pending': '审批中'
  }
  return labels[key] ?? key
}

describe('purchase requisition presentation', () => {
  it('maps status and approval labels through i18n keys', () => {
    const presentation = usePurchaseRequisitionPresentation(t, ref([]))

    expect(presentation.statuses).toEqual([
      'DRAFT',
      'SUBMITTED',
      'APPROVED',
      'REJECTED',
      'CONVERTED',
      'CANCELLED'
    ])
    expect(presentation.statusLabel('DRAFT')).toBe('草稿')
    expect(presentation.statusLabel(undefined)).toBe('-')
    expect(presentation.approvalLabel('PENDING')).toBe('审批中')
    expect(presentation.approvalLabel(null)).toBe('-')
    expect(presentation.statusLabel('CUSTOM')).toBe('CUSTOM')
  })

  it('resolves supplier labels from options and falls back to the id', () => {
    const presentation = usePurchaseRequisitionPresentation(
      t,
      ref([
        { id: 's1', supplierName: '供应商甲' } as Supplier,
        { id: 's2', name: 'Supplier B' } as Supplier
      ])
    )

    expect(presentation.supplierLabel('s1')).toBe('供应商甲')
    expect(presentation.supplierLabel('s2')).toBe('Supplier B')
    expect(presentation.supplierLabel('missing')).toBe('missing')
    expect(presentation.supplierLabel(undefined)).toBe('-')
  })
})

import { describe, expect, it } from 'vitest'

import { useWorkflowRecordPresentation } from './useWorkflowRecordPresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

describe('workflow record presentation', () => {
  it('maps business types, actions and tags', () => {
    const presentation = useWorkflowRecordPresentation(t)
    expect(presentation.businessTypeLabel('SALES_ORDER')).toBe(
      'workflowRecord.businessTypes.salesOrder'
    )
    expect(presentation.actionLabel('APPROVE')).toBe('workflowRecord.actions.approve')
    expect(presentation.actionType('REJECT')).toBe('danger')
    expect(presentation.actionType('WITHDRAW')).toBe('warning')
    expect(presentation.formatTime(undefined)).toBe('-')
    expect(presentation.recordSummary({
      businessType: 'EXPENSE',
      businessNo: 'FE1'
    })).toContain('workflowRecord.recordSummary')
  })
})

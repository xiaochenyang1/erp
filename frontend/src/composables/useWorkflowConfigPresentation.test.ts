import { describe, expect, it } from 'vitest'

import { useWorkflowConfigPresentation } from './useWorkflowConfigPresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

describe('workflow config presentation', () => {
  it('builds business type options and labels', () => {
    const presentation = useWorkflowConfigPresentation(t)
    expect(presentation.businessTypes.value).toEqual([
      { label: 'workflowConfig.businessTypes.purchaseOrder', value: 'PURCHASE_ORDER' },
      { label: 'workflowConfig.businessTypes.salesOrder', value: 'SALES_ORDER' },
      { label: 'workflowConfig.businessTypes.expense', value: 'EXPENSE' }
    ])
    expect(presentation.businessTypeLabel('SALES_ORDER')).toBe(
      'workflowConfig.businessTypes.salesOrder'
    )
    expect(presentation.businessTypeLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  it('formats user and role option labels', () => {
    const presentation = useWorkflowConfigPresentation(t)
    expect(presentation.userLabel({ id: '1', username: 'alice', realName: 'Alice' })).toContain(
      'workflowConfig.userOption'
    )
    expect(presentation.roleLabel({ id: '2', name: 'Admin', code: 'ADMIN' })).toContain(
      'workflowConfig.roleOption'
    )
  })
})

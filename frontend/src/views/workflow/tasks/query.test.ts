import { describe, expect, it } from 'vitest'

import { createWorkflowTaskQueryFromRoute, readQueryBoolean } from './query'

describe('workflow task route query', () => {
  it('initializes task filters from dashboard-style query strings', () => {
    expect(createWorkflowTaskQueryFromRoute({
      businessType: 'SALES_ORDER',
      businessId: '9007199254740993',
      businessNo: 'SO-20260723-01',
      status: 'PENDING',
      overdueOnly: 'true'
    })).toEqual({
      pageNo: 1,
      pageSize: 10,
      businessType: 'SALES_ORDER',
      businessId: '9007199254740993',
      businessNo: 'SO-20260723-01',
      status: 'PENDING',
      overdueOnly: true
    })
  })

  it('accepts a boolean true and the first value of repeated route params', () => {
    expect(readQueryBoolean({ overdueOnly: true }, 'overdueOnly')).toBe(true)
    expect(createWorkflowTaskQueryFromRoute({
      businessType: ['PURCHASE_ORDER', 'SALES_ORDER'],
      overdueOnly: ['TRUE', 'false']
    })).toMatchObject({
      businessType: 'PURCHASE_ORDER',
      status: 'PENDING',
      overdueOnly: true
    })
  })

  it('defaults absent or non-true overdue filters safely', () => {
    expect(createWorkflowTaskQueryFromRoute({ overdueOnly: 'false' })).toMatchObject({
      businessType: '',
      businessId: '',
      status: 'PENDING',
      overdueOnly: false
    })
  })
})

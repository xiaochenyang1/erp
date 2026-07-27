import { describe, expect, it, vi } from 'vitest'

import type { BusinessTraceDocument } from '@/api/businessTrace'
import {
  createEmptyBusinessTrace,
  normalizeTraceRoute,
  useBusinessTraceList
} from './useBusinessTraceList'

const t = (key: string) => key

const doc = (overrides: Partial<BusinessTraceDocument> = {}) =>
  ({
    documentType: 'SALES_ORDER',
    documentId: '1',
    bizNo: 'SO1',
    ...overrides
  }) as BusinessTraceDocument

const createList = (overrides: Partial<Parameters<typeof useBusinessTraceList>[1]> = {}) =>
  useBusinessTraceList(t, {
    getBusinessTrace: vi.fn(async () => ({
      ...createEmptyBusinessTrace(),
      keyword: 'SO1',
      summary: {
        ...createEmptyBusinessTrace().summary,
        documentCount: 1
      }
    })),
    getBusinessTimeline: vi.fn(async () => ({
      records: [{ id: 'e1', eventType: 'COMMENT', content: 'hi' }],
      total: 1
    } as any)),
    createBusinessTimelineComment: vi.fn(async () => ({})),
    onKeywordChange: vi.fn(),
    onKeywordClear: vi.fn(),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    onInfo: vi.fn(),
    ...overrides
  })

describe('business trace list', () => {
  it('normalizes routes and clears empty search', async () => {
    expect(normalizeTraceRoute('/sales/orders?keyword=1')).toBe('/sales/orders?keyword=1')
    const onKeywordClear = vi.fn()
    const list = createList({ onKeywordClear })
    list.queryForm.keyword = '   '
    expect(await list.handleSearch()).toBe(false)
    expect(onKeywordClear).toHaveBeenCalled()
  })

  it('searches by keyword and updates route callback', async () => {
    const getBusinessTrace = vi.fn(async () => ({
      ...createEmptyBusinessTrace(),
      keyword: 'SO1',
      summary: { ...createEmptyBusinessTrace().summary, documentCount: 2 }
    }))
    const onKeywordChange = vi.fn()
    const list = createList({ getBusinessTrace, onKeywordChange })
    list.queryForm.keyword = ' SO1 '
    expect(await list.handleSearch()).toBe(true)
    expect(getBusinessTrace).toHaveBeenCalledWith({ keyword: 'SO1' })
    expect(list.trace.value.summary.documentCount).toBe(2)
    expect(onKeywordChange).toHaveBeenCalledWith('SO1')
  })

  it('opens timeline, pages comments and submits new comments', async () => {
    const getBusinessTimeline = vi.fn(async () => ({
      records: [{ id: 'e1', eventType: 'COMMENT' }],
      total: 5
    } as any))
    const createBusinessTimelineComment = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onWarning = vi.fn()
    const list = createList({
      getBusinessTimeline,
      createBusinessTimelineComment,
      onSuccess,
      onWarning
    })

    await list.openBusinessTimeline('SALES_ORDER', '1', 'SO1', doc())
    expect(list.businessTimelineVisible.value).toBe(true)
    expect(getBusinessTimeline).toHaveBeenCalledWith(expect.objectContaining({
      businessType: 'SALES_ORDER',
      businessId: '1',
      businessNo: 'SO1',
      pageNo: 1
    }))

    await list.handleTimelinePageChange(2)
    expect(list.businessTimelineQuery.pageNo).toBe(2)
    await list.handleTimelineSizeChange(50)
    expect(list.businessTimelineQuery.pageSize).toBe(50)
    expect(list.businessTimelineQuery.pageNo).toBe(1)

    expect(await list.submitTimelineComment()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('financeReportPages.traces.message.commentRequired')

    list.timelineCommentForm.content = ' 备注 '
    expect(await list.submitTimelineComment()).toBe(true)
    expect(createBusinessTimelineComment).toHaveBeenCalledWith(expect.objectContaining({
      content: '备注'
    }))
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.traces.message.commentSubmitted')
  })

  it('applies route keyword and resolves navigation targets', async () => {
    const onInfo = vi.fn()
    const list = createList({ onInfo })
    expect(await list.applyKeyword('SO9')).toBe(true)
    expect(list.queryForm.keyword).toBe('SO9')
    expect(list.resolveRouteTarget(undefined)).toBeNull()
    expect(onInfo).toHaveBeenCalledWith('financeReportPages.traces.message.noRoute')
    expect(list.resolveRouteTarget('/sales/orders')).toBe('/sales/orders')
  })
})

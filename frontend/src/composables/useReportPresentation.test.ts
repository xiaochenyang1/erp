import { computed, reactive, ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { ReportRecord, ReportState } from './useReportPresentation'
import {
  isReportKey,
  sumReportAmount,
  useReportPresentation
} from './useReportPresentation'

const t = (key: string) => key

const createState = (): ReportState => reactive({
  loading: false,
  pageNo: 1,
  pageSize: 10,
  total: 21,
  records: [
    { totalAmount: 10 },
    { amountOnHand: 20 },
    { amount: 30 },
    { remainingAmount: 40 }
  ] as ReportRecord[]
})

describe('report presentation', () => {
  it('builds tabs, validates keys and derives page summaries', () => {
    const activeKey = ref<'purchase' | 'sales'>('purchase')
    const state = createState()
    const presentation = useReportPresentation(
      t,
      activeKey,
      computed(() => state)
    )

    expect(presentation.reportTabs.value).toHaveLength(7)
    expect(presentation.activeReport.value.key).toBe('purchase')
    expect(presentation.pageCount.value).toBe(3)
    expect(presentation.summaryAmount.value).toBe(100)
    expect(sumReportAmount([])).toBe(0)
    expect(isReportKey('inventoryTransaction')).toBe(true)
    expect(isReportKey('inventoryValuation')).toBe(true)
    expect(isReportKey('productionCost')).toBe(true)
    expect(isReportKey('unknown')).toBe(false)
  })

  it('maps known status, direction and business values with fallbacks', () => {
    const state = createState()
    const presentation = useReportPresentation(
      t,
      ref('purchase'),
      computed(() => state)
    )

    expect(presentation.reportStatusLabel('PARTIAL_RECEIVED'))
      .toBe('financeReportPages.reports.status.partiallyReceived')
    expect(presentation.reportStatusLabel('CUSTOM')).toBe('CUSTOM')
    expect(presentation.reportStatusLabel()).toBe('-')
    expect(presentation.reportDirectionLabel('IN'))
      .toBe('financeReportPages.reports.directionValue.inbound')
    expect(presentation.reportDirectionLabel('OTHER')).toBe('OTHER')
    expect(presentation.reportBusinessTypeLabel('PRODUCTION_COMPLETION'))
      .toBe('financeReportPages.reports.businessTypeValue.productionCompletion')
    expect(presentation.reportBusinessTypeLabel()).toBe('-')
  })

  it('labels and tags every production cost status the backend derives', () => {
    const state = createState()
    const presentation = useReportPresentation(
      t,
      ref('productionCost'),
      computed(() => state)
    )

    expect(presentation.costStatusLabel('BALANCED'))
      .toBe('financeReportPages.reports.costStatusValue.balanced')
    expect(presentation.costStatusLabel('COST_VARIANCE'))
      .toBe('financeReportPages.reports.costStatusValue.costVariance')
    expect(presentation.costStatusLabel('WIP'))
      .toBe('financeReportPages.reports.costStatusValue.wip')
    expect(presentation.costStatusLabel('NOT_POSTED'))
      .toBe('financeReportPages.reports.costStatusValue.notPosted')
    expect(presentation.costStatusLabel('FUTURE')).toBe('FUTURE')
    expect(presentation.costStatusLabel()).toBe('-')

    expect(presentation.costStatusType('BALANCED')).toBe('success')
    expect(presentation.costStatusType('COST_VARIANCE')).toBe('warning')
    expect(presentation.costStatusType('WIP')).toBe('primary')
    expect(presentation.costStatusType('NOT_POSTED')).toBe('info')
    expect(presentation.costStatusType()).toBe('info')
  })

  it('formats report amounts and quantities with locale helpers', () => {
    const state = createState()
    const presentation = useReportPresentation(
      t,
      ref('purchase'),
      computed(() => state)
    )

    expect(presentation.formatMoney(12)).toBeTruthy()
    expect(presentation.formatNumber(12)).toBeTruthy()
  })
})

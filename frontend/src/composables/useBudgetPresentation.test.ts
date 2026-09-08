import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { AccountSubject } from '@/api/finance'
import type { Dept } from '@/api/system'
import { ANNUAL_PERIOD, businessMonth, businessYear, useBudgetPresentation } from './useBudgetPresentation'

const t = (key: string) => key

const createSubjects = () => ref<AccountSubject[]>([
  { id: '1', code: '6601', name: '销售费用' } as unknown as AccountSubject,
  { id: '2', subjectCode: '6602', subjectName: '管理费用' } as unknown as AccountSubject
])

const createDepts = () => ref<Dept[]>([
  { id: '10', code: 'D01', name: '销售部' } as unknown as Dept,
  { id: '20', deptCode: 'D02', deptName: '财务部' } as unknown as Dept
])

const createPresentation = () => useBudgetPresentation(t, {
  subjects: createSubjects(),
  departments: createDepts()
})

describe('budget presentation', () => {
  it('defaults the year and month from the business timezone', () => {
    expect(String(businessYear())).toMatch(/^\d{4}$/)
    expect(businessMonth()).toBeGreaterThanOrEqual(1)
    expect(businessMonth()).toBeLessThanOrEqual(12)
    expect(ANNUAL_PERIOD).toBe(0)
  })

  it('labels accounts and departments from either field spelling', () => {
    const presentation = createPresentation()

    expect(presentation.subjectLabel({ id: '1', code: '6601', name: '销售费用' } as unknown as AccountSubject))
      .toBe('6601 - 销售费用')
    expect(presentation.subjectLabel({ id: '2', subjectCode: '6602', subjectName: '管理费用' } as unknown as AccountSubject))
      .toBe('6602 - 管理费用')
    expect(presentation.deptLabel({ id: '10', code: 'D01', name: '销售部' } as unknown as Dept))
      .toBe('D01 销售部')
    expect(presentation.deptLabel({ id: '20', deptCode: 'D02', deptName: '财务部' } as unknown as Dept))
      .toBe('D02 财务部')
    expect(presentation.deptLabel({ id: '30' } as unknown as Dept)).toBe('')
  })

  it('resolves ids through the flattened trees and degrades readably', () => {
    const presentation = createPresentation()

    expect(presentation.subjectName('1')).toBe('6601 - 销售费用')
    expect(presentation.subjectName(2)).toBe('6602 - 管理费用')
    expect(presentation.subjectName('missing')).toBe('missing')
    expect(presentation.subjectName()).toBe('-')

    expect(presentation.deptName('10')).toBe('D01 销售部')
    expect(presentation.deptName(20)).toBe('D02 财务部')
    expect(presentation.deptName('missing')).toBe('-')
    expect(presentation.deptName()).toBe('-')
  })

  it('formats amounts to two decimals, treating a missing amount as zero', () => {
    const presentation = createPresentation()

    expect(presentation.formatAmount(1234.5)).toMatch(/1,?234\.50/)
    expect(presentation.formatAmount(-12)).toMatch(/12\.00/)
    expect(presentation.formatAmount()).toMatch(/0\.00/)
  })

  it('translates statuses, policies and the charged allocation', () => {
    const presentation = createPresentation()

    expect(presentation.statusText('APPROVED')).toBe('financeReportPages.budgets.status.approved')
    expect(presentation.statusText()).toBe('-')
    expect(presentation.statusType('APPROVED')).toBe('success')
    expect(presentation.statusType('SUBMITTED')).toBe('warning')
    expect(presentation.statusType('CLOSED')).toBe('primary')
    expect(presentation.statusType('CANCELLED')).toBe('danger')
    expect(presentation.statusType('DRAFT')).toBe('info')
    expect(presentation.statusType()).toBe('info')

    expect(presentation.policyText('REJECT')).toBe('financeReportPages.budgets.policyValue.reject')
    expect(presentation.policyText('APPROVAL')).toBe('financeReportPages.budgets.policyValue.approval')
    expect(presentation.policyText()).toBe('-')

    expect(presentation.periodSourceText('MONTHLY')).toBe('financeReportPages.budgets.periodSourceValue.monthly')
    expect(presentation.periodSourceText('ANNUAL')).toBe('financeReportPages.budgets.periodSourceValue.annual')
    expect(presentation.periodSourceText('NONE')).toBe('financeReportPages.budgets.periodSourceValue.none')
    expect(presentation.periodSourceText()).toBe('-')
  })

  it('names period 0 the annual allocation and every other period a month', () => {
    const presentation = createPresentation()

    expect(presentation.monthLabel(ANNUAL_PERIOD)).toBe('financeReportPages.budgets.annual')
    expect(presentation.monthLabel(7)).toBe('7financeReportPages.budgets.monthSuffix')
    expect(presentation.monthLabel()).toBe('-')
  })
})

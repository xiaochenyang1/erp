import { describe, expect, it, vi } from 'vitest'

import type { ExceptionRule, ExceptionRuleScanResult } from '@/api/exceptionRule'
import { normalizeOptionalId, useExceptionRuleList } from './useExceptionRuleList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.count != null ? `${key}:${params.count}` : key

const rule = (overrides: Partial<ExceptionRule> = {}) =>
  ({
    id: 'r1',
    ruleCode: 'R1',
    ruleName: '低库存',
    ruleType: 'LOW_STOCK',
    category: 'STOCK',
    priority: 'HIGH',
    thresholdValue: 10,
    thresholdUnit: 'QTY',
    enabled: true,
    ...overrides
  }) as ExceptionRule

const scanResult = (overrides: Partial<ExceptionRuleScanResult> = {}) =>
  ({
    ruleId: 'r1',
    ruleCode: 'R1',
    ruleType: 'LOW_STOCK',
    status: 'SUCCESS',
    hitCount: 2,
    ticketCreatedCount: 1,
    duplicateTicketCount: 0,
    ...overrides
  }) as ExceptionRuleScanResult

const createList = (overrides: Partial<Parameters<typeof useExceptionRuleList>[1]> = {}) =>
  useExceptionRuleList(t, {
    getRules: vi.fn(async () => ({ records: [rule()], total: 1 } as any)),
    getHits: vi.fn(async () => ({ records: [], total: 0 } as any)),
    scanRule: vi.fn(async () => scanResult()),
    scanAll: vi.fn(async () => [scanResult(), scanResult({ hitCount: 3, ticketCreatedCount: 2, duplicateTicketCount: 1 })]),
    enableRule: vi.fn(async () => ({})),
    disableRule: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('exception rule list', () => {
  it('normalizes optional ids', () => {
    expect(normalizeOptionalId(' 9 ')).toBe('9')
    expect(normalizeOptionalId('')).toBeUndefined()
  })

  it('sends filled rule filters and maps enabled string to boolean', async () => {
    const getRules = vi.fn(async () => ({ records: [], total: 4 } as any))
    const list = createList({ getRules })

    list.rulePagination.page = 3
    list.ruleQueryForm.keyword = ' 库存 '
    list.ruleQueryForm.ruleType = 'LOW_STOCK'
    list.ruleQueryForm.enabled = 'true'
    await list.handleRuleQuery()

    expect(getRules).toHaveBeenCalledWith({
      keyword: '库存',
      ruleType: 'LOW_STOCK',
      enabled: true,
      pageNo: 1,
      pageSize: 20
    })
    expect(list.rulePagination.total).toBe(4)

    list.ruleQueryForm.enabled = 'false'
    await list.handleRuleQuery()
    expect(getRules).toHaveBeenLastCalledWith(expect.objectContaining({ enabled: false }))
  })

  it('pages rules/hits and resets independently', async () => {
    const getRules = vi.fn(async () => ({ records: [], total: 0 } as any))
    const getHits = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getRules, getHits })

    await list.handleRulePageChange(2)
    expect(list.rulePagination.page).toBe(2)

    await list.handleRuleSizeChange(50)
    expect(list.rulePagination.size).toBe(50)
    expect(list.rulePagination.page).toBe(1)

    list.hitQueryForm.ruleType = 'LOW_STOCK'
    list.hitQueryForm.sourceNo = ' SO1 '
    list.hitQueryForm.ticketId = ' 11 '
    await list.handleHitQuery()
    expect(getHits).toHaveBeenCalledWith({
      ruleType: 'LOW_STOCK',
      sourceNo: 'SO1',
      ticketId: '11',
      pageNo: 1,
      pageSize: 20
    })

    await list.handleHitPageChange(3)
    expect(list.hitPagination.page).toBe(3)
    await list.handleHitSizeChange(30)
    expect(list.hitPagination.size).toBe(30)
    expect(list.hitPagination.page).toBe(1)

    list.ruleQueryForm.keyword = 'x'
    await list.handleRuleReset()
    expect(list.ruleQueryForm.keyword).toBe('')
    list.hitQueryForm.sourceNo = 'y'
    await list.handleHitReset()
    expect(list.hitQueryForm.sourceNo).toBe('')
  })

  it('scans one rule, all rules and toggles enablement', async () => {
    const scanRule = vi.fn(async () => scanResult({ hitCount: 5 }))
    const scanAll = vi.fn(async () => [
      scanResult({ hitCount: 1, ticketCreatedCount: 1, duplicateTicketCount: 0 }),
      scanResult({ hitCount: 2, ticketCreatedCount: 1, duplicateTicketCount: 1 })
    ])
    const disableRule = vi.fn(async () => ({}))
    const enableRule = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onWarning = vi.fn()
    const list = createList({ scanRule, scanAll, disableRule, enableRule, onSuccess, onWarning })

    expect(await list.handleScanRule(rule({ enabled: false }))).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('exceptionRule.message.ruleDisabled')

    expect(await list.handleScanRule(rule())).toBe(true)
    expect(scanRule).toHaveBeenCalledWith('r1')
    expect(onSuccess).toHaveBeenCalledWith('exceptionRule.message.scanComplete:5')

    expect(await list.handleScanAll()).toBe(true)
    expect(list.totalScanHits.value).toBe(3)
    expect(list.totalScanTickets.value).toBe(2)
    expect(list.totalScanDuplicates.value).toBe(1)
    expect(onSuccess).toHaveBeenCalledWith('exceptionRule.message.scanComplete:3')

    expect(await list.handleToggleRule(rule({ enabled: true }))).toBe(true)
    expect(disableRule).toHaveBeenCalledWith('r1')
    expect(onSuccess).toHaveBeenCalledWith('exceptionRule.message.disabled')

    expect(await list.handleToggleRule(rule({ enabled: false }))).toBe(true)
    expect(enableRule).toHaveBeenCalledWith('r1')
    expect(onSuccess).toHaveBeenCalledWith('exceptionRule.message.enabled')
  })

  it('reports load/scan/toggle failures', async () => {
    const onError = vi.fn()
    const list = createList({
      getRules: vi.fn(async () => { throw new Error('boom') }),
      getHits: vi.fn(async () => { throw new Error('boom') }),
      scanRule: vi.fn(async () => { throw new Error('boom') }),
      disableRule: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await list.loadRules()
    expect(onError).toHaveBeenCalledWith('exceptionRule.message.rulesLoadFailed')
    await list.loadHits()
    expect(onError).toHaveBeenCalledWith('exceptionRule.message.hitsLoadFailed')
    expect(await list.handleScanRule(rule())).toBe(false)
    expect(onError).toHaveBeenCalledWith('exceptionRule.message.scanFailed')
    expect(await list.handleToggleRule(rule({ enabled: true }))).toBe(false)
    expect(onError).toHaveBeenCalledWith('exceptionRule.message.toggleFailed')
  })
})

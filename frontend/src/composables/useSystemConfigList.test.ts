import { describe, expect, it, vi } from 'vitest'

import type { SequenceRule, SystemConfig } from '@/api/system'
import { useSystemConfigList } from './useSystemConfigList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const config = (overrides: Partial<SystemConfig> = {}) =>
  ({
    id: 'c1',
    configKey: 'app.name',
    configValue: 'ERP',
    configType: 'STRING',
    status: 'ACTIVE',
    updatedAt: '2026-07-26',
    ...overrides
  }) as SystemConfig

const rule = (overrides: Partial<SequenceRule> = {}) =>
  ({
    id: 's1',
    companyId: '1',
    accountBookId: '1',
    bizType: 'SO',
    prefix: 'SO',
    datePattern: 'yyyyMMdd',
    seqLength: 4,
    currentValue: '1',
    status: 'ACTIVE',
    ...overrides
  }) as SequenceRule

const createList = (overrides: Partial<Parameters<typeof useSystemConfigList>[1]> = {}) =>
  useSystemConfigList(t, {
    getConfigs: vi.fn(async () => ({ records: [config()], total: 1 } as any)),
    enableConfig: vi.fn(async () => ({})),
    disableConfig: vi.fn(async () => ({})),
    getSequenceRules: vi.fn(async () => ({ records: [rule()], total: 1 } as any)),
    enableSequenceRule: vi.fn(async () => ({})),
    disableSequenceRule: vi.fn(async () => ({})),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system config list', () => {
  it('queries configs with page reset', async () => {
    const getConfigs = vi.fn(async () => ({ records: [], total: 3 } as any))
    const list = createList({ getConfigs })
    list.pagination.page = 4
    list.queryForm.configKey = 'app'
    await list.handleQuery()
    expect(getConfigs).toHaveBeenCalledWith({
      configKey: 'app',
      pageNo: 1,
      pageSize: 20
    })
  })

  it('pages configs and sequence rules independently', async () => {
    const getConfigs = vi.fn(async () => ({ records: [], total: 0 } as any))
    const getSequenceRules = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getConfigs, getSequenceRules })

    await list.handlePageChange(2)
    expect(list.pagination.page).toBe(2)
    await list.handleSizeChange(50)
    expect(list.pagination.size).toBe(50)
    expect(list.pagination.page).toBe(1)

    await list.handleSequenceRulePageChange(3)
    expect(list.sequenceRulePagination.page).toBe(3)
    await list.handleSequenceRuleSizeChange(30)
    expect(list.sequenceRulePagination.size).toBe(30)
    expect(list.sequenceRulePagination.page).toBe(1)
  })

  it('toggles config and sequence rule status after confirmation', async () => {
    const disableConfig = vi.fn(async () => ({}))
    const enableConfig = vi.fn(async () => ({}))
    const disableSequenceRule = vi.fn(async () => ({}))
    const enableSequenceRule = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({
      disableConfig,
      enableConfig,
      disableSequenceRule,
      enableSequenceRule,
      onSuccess
    })

    expect(await list.handleToggleConfigStatus(config())).toBe(true)
    expect(disableConfig).toHaveBeenCalledWith('c1')
    expect(await list.handleToggleConfigStatus(config({ status: 'DISABLED' }))).toBe(true)
    expect(enableConfig).toHaveBeenCalledWith('c1')

    expect(await list.handleToggleSequenceRuleStatus(rule())).toBe(true)
    expect(disableSequenceRule).toHaveBeenCalledWith('s1')
    expect(await list.handleToggleSequenceRuleStatus(rule({ status: 'DISABLED' }))).toBe(true)
    expect(enableSequenceRule).toHaveBeenCalledWith('s1')
    expect(onSuccess).toHaveBeenCalled()
  })

  it('loads sequence rules lazily when switching tabs', async () => {
    const getSequenceRules = vi.fn(async () => ({ records: [rule()], total: 1 } as any))
    const list = createList({ getSequenceRules })
    await list.handleTabChange('sequenceRules')
    expect(getSequenceRules).toHaveBeenCalled()
    expect(list.sequenceRuleData.value).toHaveLength(1)
  })
})

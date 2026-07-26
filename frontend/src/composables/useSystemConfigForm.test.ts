import { describe, expect, it, vi } from 'vitest'

import type { SequenceRule, SystemConfig } from '@/api/system'
import { useSystemConfigForm } from './useSystemConfigForm'

const t = (key: string) => key

const config = {
  id: 'c1',
  configKey: 'app.name',
  configName: '应用名',
  configValue: 'ERP',
  configType: 'STRING',
  description: '描述',
  status: 'ACTIVE',
  updatedAt: ''
} as SystemConfig

const rule = {
  id: 's1',
  companyId: '1',
  accountBookId: '1',
  bizType: 'SO',
  prefix: 'SO',
  datePattern: 'yyyyMMdd',
  seqLength: 4,
  currentValue: '12',
  status: 'ACTIVE'
} as SequenceRule

const createForm = (overrides: Partial<Parameters<typeof useSystemConfigForm>[1]> = {}) =>
  useSystemConfigForm(t, {
    getConfig: vi.fn(async () => config),
    createConfig: vi.fn(async () => ({})),
    updateConfig: vi.fn(async () => ({})),
    getSequenceRule: vi.fn(async () => rule),
    createSequenceRule: vi.fn(async () => ({})),
    updateSequenceRule: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system config form', () => {
  it('opens create and edit config dialogs', async () => {
    const form = createForm()
    form.handleCreate()
    expect(form.configMode.value).toBe('create')
    expect(form.dialogVisible.value).toBe(true)
    expect(form.configDialogTitle.value).toBe('systemConfigs.dialog.addConfig')

    expect(await form.handleEdit({ id: 'c1' } as SystemConfig)).toBe(true)
    expect(form.configMode.value).toBe('edit')
    expect(form.formData).toMatchObject({
      id: 'c1',
      configKey: 'app.name',
      configName: '应用名',
      configValue: 'ERP',
      description: '描述'
    })
  })

  it('creates and updates configs with trimmed fields', async () => {
    const createConfig = vi.fn(async () => ({}))
    const updateConfig = vi.fn(async () => ({}))
    const onConfigSubmitted = vi.fn()
    const form = createForm({ createConfig, updateConfig, onConfigSubmitted })

    form.handleCreate()
    form.formData.configKey = '  app.x  '
    form.formData.configName = '  名称  '
    form.formData.configValue = '  1  '
    form.formData.description = '  d  '
    expect(await form.handleSubmit()).toBe(true)
    expect(createConfig).toHaveBeenCalledWith({
      configKey: 'app.x',
      configName: '名称',
      configValue: '1',
      description: 'd'
    })

    await form.handleEdit({ id: 'c1' } as SystemConfig)
    form.formData.configValue = '2'
    expect(await form.handleSubmit()).toBe(true)
    expect(updateConfig).toHaveBeenCalledWith('c1', expect.objectContaining({
      configValue: '2',
      configName: '应用名'
    }))
    expect(onConfigSubmitted).toHaveBeenCalled()
  })

  it('creates and updates sequence rules', async () => {
    const createSequenceRule = vi.fn(async () => ({}))
    const updateSequenceRule = vi.fn(async () => ({}))
    const onSequenceRuleSubmitted = vi.fn()
    const form = createForm({ createSequenceRule, updateSequenceRule, onSequenceRuleSubmitted })

    form.handleCreateSequenceRule()
    form.sequenceRuleForm.bizType = ' PO '
    form.sequenceRuleForm.prefix = ' PO '
    form.sequenceRuleForm.datePattern = ' yyyyMMdd '
    form.sequenceRuleForm.seqLength = '6'
    form.sequenceRuleForm.currentValue = '0'
    expect(await form.handleSubmitSequenceRule()).toBe(true)
    expect(createSequenceRule).toHaveBeenCalledWith({
      bizType: 'PO',
      prefix: 'PO',
      datePattern: 'yyyyMMdd',
      seqLength: 6,
      currentValue: '0'
    })

    expect(await form.handleEditSequenceRule({ id: 's1' } as SequenceRule)).toBe(true)
    form.sequenceRuleForm.prefix = 'SOX'
    expect(await form.handleSubmitSequenceRule()).toBe(true)
    expect(updateSequenceRule).toHaveBeenCalledWith('s1', expect.objectContaining({
      prefix: 'SOX',
      seqLength: 4,
      currentValue: '12',
      bizType: undefined
    }))
    expect(onSequenceRuleSubmitted).toHaveBeenCalled()
  })

  it('reports detail load failures', async () => {
    const onError = vi.fn()
    const form = createForm({
      getConfig: vi.fn(async () => { throw new Error('boom') }),
      getSequenceRule: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    expect(await form.handleEdit({ id: 'c1' } as SystemConfig)).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemConfigs.message.configDetailLoadFailed')
    expect(await form.handleEditSequenceRule({ id: 's1' } as SequenceRule)).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemConfigs.message.sequenceRuleDetailLoadFailed')
  })
})

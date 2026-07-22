import { afterEach, describe, expect, it } from 'vitest'

import { i18n, setI18nLocale } from './index'

describe('workflow localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English task and escalation labels', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('workflow.tasks')).toBe('Approval tasks')
    expect(i18n.global.t('workflow.escalationTitle')).toBe('Escalate overdue task')
    expect(i18n.global.t('workflow.escalationSuccess')).toBe('Overdue task escalated')
  })
})

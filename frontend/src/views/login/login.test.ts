import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'

import { i18n, setI18nLocale } from '@/i18n'

const source = readFileSync(resolve(process.cwd(), 'src/views/login/index.vue'), 'utf8')

describe('login page completion contract', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('localizes the prefilled local account and password reset guidance', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('login.prefilledTestAccount')).toBe('admin / LocalAdmin123 (prefilled)')
    expect(i18n.global.t('login.passwordResetHint'))
      .toBe('Contact your system administrator to reset your password')
    expect(source).toContain("$t('login.prefilledTestAccount')")
    expect(source).toContain('@click="showPasswordResetHint"')
  })

  it('does not expose a remember-password control without persistence behavior', () => {
    expect(source).not.toContain('v-model="rememberMe"')
    expect(source).not.toContain('const rememberMe')
  })
})

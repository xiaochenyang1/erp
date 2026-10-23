import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'

import { i18n, setI18nLocale } from '@/i18n'

const source = readFileSync(resolve(process.cwd(), 'src/layout/index.vue'), 'utf8')

describe('application layout localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('localizes profile and password fields in both supported locales', () => {
    setI18nLocale('en-US')
    expect(i18n.global.t('user.profileFields.warehouseScope')).toBe('Warehouse scope')
    expect(i18n.global.t('user.profileFields.listSeparator')).toBe(', ')
    expect(i18n.global.t('user.passwordFields.oldPassword')).toBe('Current password')
    expect(i18n.global.t('user.validation.passwordComplexity')).not.toMatch(/[\u3400-\u9fff]/)

    setI18nLocale('zh-CN')
    expect(i18n.global.t('user.profileFields.warehouseScope')).toBe('仓库范围')
    expect(i18n.global.t('user.profileFields.listSeparator')).toBe('、')
  })

  it('uses translation keys for profile copy and both list separators', () => {
    expect(source).toContain("roles?.join($t('user.profileFields.listSeparator'))")
    expect(source).toContain("warehouseIds?.join($t('user.profileFields.listSeparator'))")
    expect(source).toContain("t('user.validation.passwordComplexity')")
    expect(source).not.toContain('label="用户ID"')
    expect(source).not.toContain('placeholder="请输入原密码"')
  })
})

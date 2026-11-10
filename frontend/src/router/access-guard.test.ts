import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'

import { i18n, setI18nLocale } from '@/i18n'

const source = readFileSync(resolve(process.cwd(), 'src/router/index.ts'), 'utf8')

describe('router access guard localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('uses localized permission and runtime-menu denial messages', () => {
    expect(source).toContain("i18n.global.t('common.noPagePermission')")
    expect(source).toContain("i18n.global.t('common.menuNotAssigned')")
    expect(source).not.toContain("ElMessage.warning('您没有访问该页面的权限')")
    expect(source).not.toContain("ElMessage.warning('当前账号未分配该菜单')")

    setI18nLocale('en-US')
    expect(i18n.global.t('common.noPagePermission')).toContain('permission')
    expect(i18n.global.t('common.menuNotAssigned')).toContain('not assigned')
  })
})

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { platformPageMessages } from './platform-pages'

const pagePaths = [
  'src/views/error/404.vue',
  'src/views/system/observability/index.vue',
  'src/views/system/document-state-rules/index.vue',
  'src/views/system/user-sessions/index.vue',
  'src/views/system/attachments/index.vue',
  'src/views/system/notifications/index.vue',
  'src/views/system/menus/index.vue',
  'src/views/system/depts/index.vue',
  'src/views/system/posts/index.vue'
] as const

const userSessionFeaturePaths = [
  'src/views/system/user-sessions/index.vue',
  'src/composables/useSystemUserSessionPresentation.ts',
  'src/composables/useSystemUserSessionList.ts'
] as const

const localizedSourcePaths = [
  ...pagePaths,
  ...userSessionFeaturePaths.slice(1)
] as const

const leafPaths = (value: unknown, prefix = ''): string[] => {
  if (typeof value === 'string') return [prefix]
  if (!value || typeof value !== 'object') return []
  return Object.entries(value).flatMap(([key, nested]) => (
    leafPaths(nested, prefix ? `${prefix}.${key}` : key)
  ))
}

describe('platform page localization', () => {
  it('keeps Chinese and English message structures aligned and complete', () => {
    const chinesePaths = leafPaths(platformPageMessages['zh-CN']).sort()
    const englishPaths = leafPaths(platformPageMessages['en-US']).sort()

    expect(englishPaths).toEqual(chinesePaths)
    expect(chinesePaths.length).toBeGreaterThan(60)
    for (const locale of ['zh-CN', 'en-US'] as const) {
      for (const path of leafPaths(platformPageMessages[locale])) {
        const value = path.split('.').reduce<unknown>((current, segment) => (
          current && typeof current === 'object'
            ? (current as Record<string, unknown>)[segment]
            : undefined
        ), platformPageMessages[locale])
        expect(value, `${locale}:${path}`).toBeTypeOf('string')
        expect((value as string).trim(), `${locale}:${path}`).not.toBe('')
      }
    }
  })

  it('provides representative English platform copy', () => {
    expect(platformPageMessages['en-US'].notFoundPage.backHome).toBe('Back to home')
    expect(platformPageMessages['en-US'].systemObservability.platformHealth.outOfService).toBe('Out of service')
    expect(platformPageMessages['en-US'].documentStateRules.executionConstraints).toBe('Execution constraints')
    expect(platformPageMessages['en-US'].userSessions.message.loadFailed).toBe('Failed to load sessions')
    expect(platformPageMessages['en-US'].userSessions.message.revokeFailed).toBe('Failed to revoke the session')
    expect(platformPageMessages['en-US'].userSessions.message.revokeUserFailed).toBe('Failed to revoke the user sessions')
    expect(platformPageMessages['en-US'].systemAttachments.message.uploaded).toBe('Attachment uploaded')
    expect(platformPageMessages['en-US'].systemNotifications.markAllRead).toBe('Mark all as read')
    expect(platformPageMessages['en-US'].systemMenu.permission).toBe('Permission code')
    expect(platformPageMessages['en-US'].systemDept.root).toBe('Top-level department')
    expect(platformPageMessages['en-US'].systemPost.validation.dept).toBe('Select a department')
  })

  it('provides every user session key referenced by the feature', () => {
    const catalogPaths = new Set(leafPaths(platformPageMessages['zh-CN']))

    for (const componentPath of userSessionFeaturePaths) {
      const source = readFileSync(resolve(process.cwd(), componentPath), 'utf8')
      const keys = [...source.matchAll(/['"](userSessions\.[A-Za-z0-9_.]+)['"]/g)]
        .map((match) => match[1])

      for (const key of keys) {
        expect(catalogPaths, `${componentPath}:${key}`).toContain(key)
      }
    }
  })

  for (const componentPath of localizedSourcePaths) {
    it(`keeps ${componentPath} free of hard-coded Chinese UI copy`, () => {
      const source = readFileSync(resolve(process.cwd(), componentPath), 'utf8')

      expect(source).not.toMatch(/[\u3400-\u9fff]/)
      expect(source).not.toMatch(/\s(?:label|placeholder|title|description)="[^"]+"/)
      expect(source).not.toMatch(/ElMessage\.(?:error|success|warning|info)\(\s*['"`]/)
    })
  }

  it('formats platform timestamps through the shared locale helpers', () => {
    const observability = readFileSync(resolve(process.cwd(), pagePaths[1]), 'utf8')
    const sessions = readFileSync(resolve(process.cwd(), pagePaths[3]), 'utf8')
    const sessionPresentation = readFileSync(
      resolve(process.cwd(), userSessionFeaturePaths[1]),
      'utf8'
    )
    const attachments = readFileSync(resolve(process.cwd(), pagePaths[4]), 'utf8')
    const attachmentPresentation = readFileSync(
      resolve(process.cwd(), 'src/composables/useAttachmentPresentation.ts'),
      'utf8'
    )
    const notifications = readFileSync(resolve(process.cwd(), pagePaths[5]), 'utf8')

    expect(observability).toContain('formatLocalizedDateTime(health.generatedAt)')
    expect(sessions).toContain('formatDateTime(row.issuedAt)')
    expect(sessions).toContain('formatDateTime(row.lastUsedAt)')
    expect(sessions).toContain('formatDateTime(row.expiresAt)')
    expect(sessionPresentation).toContain('formatLocalizedDateTime(value)')
    expect(attachments).toContain('formatLocalizedDateTime(row.createdTime)')
    expect(attachments).toContain('formatNumber: formatLocalizedNumber')
    expect(attachmentPresentation).toContain('formatNumber(size / 1024')
    expect(notifications).toContain('formatLocalizedDateTime(row.createdTime)')
    expect(notifications).toContain('formatLocalizedNumber(unreadCount)')
  })
})

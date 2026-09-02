import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { contractPageMessages } from './contract-pages'

const componentPath = 'src/views/commercial/contracts/index.vue'
const composablePath = 'src/composables/useContractVersions.ts'

const leafPaths = (source: unknown, prefix = ''): string[] => {
  if (!source || typeof source !== 'object') return [prefix]
  return Object.entries(source as Record<string, unknown>)
    .flatMap(([key, value]) => leafPaths(value, prefix ? `${prefix}.${key}` : key))
}

const readNestedValue = (source: unknown, path: string): unknown => (
  path.split('.').reduce<unknown>((current, segment) => {
    if (!current || typeof current !== 'object') return undefined
    return (current as Record<string, unknown>)[segment]
  }, source)
)

describe('contract page localization', () => {
  it('keeps matching locale leaf paths', () => {
    expect(leafPaths(contractPageMessages['en-US']).sort())
      .toEqual(leafPaths(contractPageMessages['zh-CN']).sort())
  })

  it('defines every component-referenced key in both locales', () => {
    const source = [componentPath, composablePath]
      .map((path) => readFileSync(resolve(process.cwd(), path), 'utf8'))
      .join('\n')
    const keys = new Set([...source.matchAll(/contractPage(?:\.[A-Za-z0-9_]+)+/g)]
      .filter((match) => !source.slice((match.index || 0) + match[0].length).startsWith('.${'))
      .map((match) => match[0]))

    expect(keys.size).toBeGreaterThan(50)
    for (const locale of ['zh-CN', 'en-US'] as const) {
      for (const key of keys) {
        expect(typeof readNestedValue(contractPageMessages[locale], key), `${locale}:${key}`).toBe('string')
      }
    }
  })

  it('provides representative English copy and a localized export file name', () => {
    expect(readNestedValue(contractPageMessages['en-US'], 'contractPage.statusValue.active')).toBe('Active')
    expect(readNestedValue(contractPageMessages['en-US'], 'contractPage.message.confirmAction')).toContain('{name}')
    expect(readNestedValue(contractPageMessages['en-US'], 'contractPage.fileName')).toBe('contract-register.csv')
  })

  it('uses i18n without hard-coded Chinese UI copy', () => {
    const source = readFileSync(resolve(process.cwd(), componentPath), 'utf8')

    expect(source).toContain('useI18n')
    expect(source).not.toMatch(/[\u3400-\u9fff]/)
    expect(source).not.toMatch(/\s(?:label|placeholder|title|description|empty-text|aria-label)="[^"]+"/)
    expect(source).not.toMatch(/ElMessage\.(?:error|success|warning|info)\(\s*['"`]/)
  })
})

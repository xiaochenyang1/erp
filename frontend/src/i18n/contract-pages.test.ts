import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { contractPageMessages } from './contract-pages'

const componentPath = 'src/views/commercial/contracts/index.vue'

const componentPaths = [
  componentPath,
  'src/composables/useContractList.ts',
  'src/composables/useContractForm.ts',
  'src/composables/useContractDetail.ts',
  'src/composables/useContractPresentation.ts'
] as const

const readSources = () => componentPaths.map((path) => ({
  path,
  source: readFileSync(resolve(process.cwd(), path), 'utf8')
}))

const readPageSource = () => readFileSync(resolve(process.cwd(), componentPath), 'utf8')

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

  it('defines every referenced key in the page and its composables in both locales', () => {
    const keys = new Set<string>()
    for (const { source } of readSources()) {
      for (const match of source.matchAll(/contractPage(?:\.[A-Za-z0-9_]+)+/g)) {
        if (source.slice((match.index || 0) + match[0].length).startsWith('.${')) continue
        keys.add(match[0])
      }
    }

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
    expect(readPageSource()).toContain('useI18n')

    for (const { path, source } of readSources()) {
      // CJK punctuation and fullwidth forms sit outside the Han block, so an ideographic comma
      // hard-coded as a list separator used to reach the English UI unnoticed.
      expect(source, path).not.toMatch(/[\u3000-\u303f\u3400-\u9fff\uff00-\uffef]/)
      expect(source, path).not.toMatch(/\s(?:label|placeholder|title|description|empty-text|aria-label)="[^"]+"/)
      expect(source, path).not.toMatch(/ElMessage\.(?:error|success|warning|info)\(\s*['"`]/)
    }
  })
})

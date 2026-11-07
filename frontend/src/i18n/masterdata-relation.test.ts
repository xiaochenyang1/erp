import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { i18n } from './index'

/** Files that own the shared relation panel: fully localized, no Chinese literals. */
const featurePaths = [
  'src/components/masterdata/ProductRelationDialog.vue',
  'src/composables/useProductRelationPanel.ts'
] as const

/** Host pages that only open the panel; they still keep their own local copy tables. */
const entryPaths = [
  'src/views/masterdata/customers/index.vue',
  'src/views/masterdata/suppliers/index.vue'
] as const

const leafEntries = (value: unknown, prefix = ''): Array<[string, string]> => {
  if (typeof value === 'string') return [[prefix, value]]
  if (!value || typeof value !== 'object') return []
  return Object.entries(value).flatMap(([key, nested]) =>
    leafEntries(nested, prefix ? `${prefix}.${key}` : key)
  )
}

const stripComments = (source: string) => source
  .replace(/<!--[\s\S]*?-->/g, '')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '')

const readSource = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')

const referencedKeys = (source: string) =>
  [...source.matchAll(/['"](productRelation\.[A-Za-z0-9_.]+)['"]/g)].map((match) => match[1])

describe('product relation localization', () => {
  it('registers the namespace in both runtime locales', () => {
    expect(i18n.global.getLocaleMessage('zh-CN').productRelation).toBeTruthy()
    expect(i18n.global.getLocaleMessage('en-US').productRelation).toBeTruthy()
  })

  it('keeps locale keys aligned and English values translated', () => {
    const chineseEntries = leafEntries(i18n.global.getLocaleMessage('zh-CN').productRelation)
    const englishEntries = leafEntries(i18n.global.getLocaleMessage('en-US').productRelation)

    expect(chineseEntries.length).toBeGreaterThan(40)
    expect(englishEntries.map(([path]) => path).sort())
      .toEqual(chineseEntries.map(([path]) => path).sort())
    for (const [path, value] of englishEntries) {
      expect(value.trim(), `productRelation.en-US.${path}`).not.toBe('')
      expect(value, `productRelation.en-US.${path}`).not.toMatch(/[\u3400-\u9fff]/)
    }
  })

  it('resolves every referenced key in both locales', () => {
    const chinese = new Set(
      leafEntries(i18n.global.getLocaleMessage('zh-CN').productRelation).map(([path]) => `productRelation.${path}`)
    )
    const english = new Set(
      leafEntries(i18n.global.getLocaleMessage('en-US').productRelation).map(([path]) => `productRelation.${path}`)
    )

    for (const path of [...featurePaths, ...entryPaths]) {
      const keys = referencedKeys(readSource(path))
      expect(keys.length, path).toBeGreaterThan(0)
      for (const key of keys) {
        expect(chinese, `${path}:${key}:zh-CN`).toContain(key)
        expect(english, `${path}:${key}:en-US`).toContain(key)
      }
    }
  })

  it('routes the relation panel through the catalog instead of hard-coded copy', () => {
    for (const path of featurePaths) {
      const source = stripComments(readSource(path))
      expect(source, path).not.toMatch(/[\u3400-\u9fff]/)
      expect(source, path).not.toMatch(/\s(?:label|placeholder|title|empty-text|aria-label)="[^"]+"/)
      expect(source, path).not.toMatch(/ElMessage\.(?:error|success|warning|info)\(\s*['"`]/)
    }
  })

  it('opens the panel from both master data pages', () => {
    for (const path of entryPaths) {
      const source = readSource(path)
      expect(source, path).toContain('ProductRelationDialog.vue')
      expect(source, path).toContain('openRelations')
      expect(source, path).toMatch(/:can-write="canUpdate"/)
    }
  })
})

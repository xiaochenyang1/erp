import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { i18n } from './index'

const componentPaths = [
  'src/views/masterdata/locations/index.vue',
  'src/composables/useWarehouseLocationPresentation.ts',
  'src/composables/useWarehouseLocationList.ts',
  'src/composables/useWarehouseLocationForm.ts'
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

describe('warehouse location localization', () => {
  it('keeps locale keys aligned and English values translated', () => {
    const chinese = i18n.global.getLocaleMessage('zh-CN').warehouseLocation
    const english = i18n.global.getLocaleMessage('en-US').warehouseLocation
    const chineseEntries = leafEntries(chinese)
    const englishEntries = leafEntries(english)

    expect(englishEntries.map(([path]) => path).sort())
      .toEqual(chineseEntries.map(([path]) => path).sort())
    for (const [path, value] of englishEntries) {
      expect(value.trim(), `warehouseLocation.en-US.${path}`).not.toBe('')
      expect(value, `warehouseLocation.en-US.${path}`).not.toMatch(/[\u3400-\u9fff]/)
    }
  })

  it('provides every warehouse location key referenced by the feature', () => {
    const catalogPaths = new Set(
      leafEntries(i18n.global.getLocaleMessage('zh-CN').warehouseLocation)
        .map(([path]) => `warehouseLocation.${path}`)
    )

    for (const componentPath of componentPaths) {
      const source = readFileSync(resolve(process.cwd(), componentPath), 'utf8')
      const keys = [...source.matchAll(/(?:\$t|\bt)\(\s*['"](warehouseLocation\.[A-Za-z0-9_.]+)['"]/g)]
        .map((match) => match[1])

      for (const key of keys) {
        expect(catalogPaths, `${componentPath}:${key}`).toContain(key)
      }
    }
  })

  for (const componentPath of componentPaths) {
    it(`keeps ${componentPath} free of hard-coded Chinese UI copy`, () => {
      const source = readFileSync(resolve(process.cwd(), componentPath), 'utf8')
      expect(stripComments(source)).not.toMatch(/[\u3400-\u9fff]/)
    })
  }
})

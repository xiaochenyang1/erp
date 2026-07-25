import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { operationsPageMessages } from './operations-pages'

const expectedNamespaces = [
  'inventoryAdjustments',
  'inventoryTransfers',
  'inventoryChecks',
  'inventoryAlerts',
  'inventoryStocks',
  'salesReturnOps',
  'purchaseInquiryOps'
] as const

const existingNamespaces = [
  'inventoryMrp',
  'inventoryReplenishment',
  'salesOrder',
  'salesDelivery',
  'purchaseOrder',
  'purchaseReceipt',
  'purchaseReturn'
] as const

const componentPaths = [
  'src/views/inventory/adjustments/index.vue',
  'src/views/inventory/transfers/index.vue',
  'src/views/inventory/checks/index.vue',
  'src/views/inventory/alerts/index.vue',
  'src/views/inventory/stocks/index.vue',
  'src/views/sales/returns/index.vue',
  'src/views/purchase/inquiries/index.vue'
] as const

const flattenLeaves = (source: unknown, prefix = ''): Record<string, unknown> => {
  if (!source || typeof source !== 'object' || Array.isArray(source)) {
    return { [prefix]: source }
  }
  return Object.entries(source as Record<string, unknown>).reduce<Record<string, unknown>>(
    (result, [key, value]) => ({
      ...result,
      ...flattenLeaves(value, prefix ? `${prefix}.${key}` : key)
    }),
    {}
  )
}

describe('operations page messages', () => {
  it('defines only the dedicated operations namespaces in both locales', () => {
    for (const locale of ['zh-CN', 'en-US'] as const) {
      const namespaces = Object.keys(operationsPageMessages[locale]).sort()
      expect(namespaces).toEqual([...expectedNamespaces].sort())
      expect(namespaces.some((namespace) => existingNamespaces.includes(
        namespace as typeof existingNamespaces[number]
      ))).toBe(false)
    }
  })

  it('keeps identical string leaf keys across both locales', () => {
    const zhLeaves = flattenLeaves(operationsPageMessages['zh-CN'])
    const enLeaves = flattenLeaves(operationsPageMessages['en-US'])

    expect(Object.keys(enLeaves).sort()).toEqual(Object.keys(zhLeaves).sort())
    for (const [key, value] of Object.entries(zhLeaves)) {
      expect(typeof value, `zh-CN:${key}`).toBe('string')
      expect(typeof enLeaves[key], `en-US:${key}`).toBe('string')
      expect(value, `zh-CN:${key}`).not.toBe('')
      expect(enLeaves[key], `en-US:${key}`).not.toBe('')
    }
  })

  it('defines every message key referenced by the seven pages', () => {
    const zhLeaves = flattenLeaves(operationsPageMessages['zh-CN'])
    const namespacePattern = expectedNamespaces.join('|')
    const keyPattern = new RegExp(`['"\\x60]((?:${namespacePattern})(?:\\.[A-Za-z0-9_]+)+)['"\\x60]`, 'g')

    for (const componentPath of componentPaths) {
      const source = readFileSync(resolve(process.cwd(), componentPath), 'utf8')
      const keys = [...source.matchAll(keyPattern)].map((match) => match[1])
      expect(keys.length, componentPath).toBeGreaterThan(0)
      for (const key of keys) {
        expect(zhLeaves[key], `${componentPath}:${key}`).toBeTypeOf('string')
      }
    }
  })

  it('keeps user-facing copy out of the seven page components', () => {
    for (const componentPath of componentPaths) {
      const source = readFileSync(resolve(process.cwd(), componentPath), 'utf8')
      const withoutComments = source
        .replace(/<!--[^]*?-->/g, '')
        .replace(/\/\/[^\n]*/g, '')

      expect(withoutComments, componentPath).not.toMatch(/[\u3400-\u9fff]/)
      expect(source, componentPath).not.toMatch(/\s(?:label|placeholder|title)="[^":$][^"]*"/)
      expect(source, componentPath).not.toMatch(/ElMessage\.(?:error|success|warning|info)\(\s*['"`]/)
    }
  })
})

import { describe, expect, it } from 'vitest'

import { adminWorkflowPageMessages } from './admin-workflow-pages'
import { financeReportPageMessages } from './finance-report-pages'
import { i18n } from './index'
import { operationsPageMessages } from './operations-pages'
import { platformPageMessages } from './platform-pages'
import { salesCommercialPageMessages } from './sales-commercial-pages'

type Locale = 'zh-CN' | 'en-US'
type LocaleMessages = Record<Locale, Record<string, unknown>>

const modules = {
  operations: operationsPageMessages,
  financeReport: financeReportPageMessages,
  adminWorkflow: adminWorkflowPageMessages,
  platform: platformPageMessages,
  salesCommercial: salesCommercialPageMessages
} as unknown as Record<string, LocaleMessages>

const coreNamespaces = new Set([
  'common', 'app', 'settings', 'user', 'login', 'dashboard', 'workflow',
  'financeAccount', 'financeAging', 'financeGrossMargin', 'financeStatement',
  'salesOrder', 'salesDelivery', 'purchaseOrder', 'purchaseReceipt', 'purchaseReturn',
  'qcInspection', 'inventoryMrp', 'inventoryReplenishment', 'productionRouting',
  'productionWorkCenter', 'productionBom', 'productionOrder'
])

const leafEntries = (value: unknown, prefix = ''): Array<[string, string]> => {
  if (typeof value === 'string') return [[prefix, value]]
  if (!value || typeof value !== 'object') return []
  return Object.entries(value).flatMap(([key, nested]) => (
    leafEntries(nested, prefix ? `${prefix}.${key}` : key)
  ))
}

describe('modular page message catalog', () => {
  it('uses unique namespaces that do not overwrite the core catalog', () => {
    const owners = new Map<string, string>()

    for (const [moduleName, messages] of Object.entries(modules)) {
      for (const namespace of Object.keys(messages['zh-CN'])) {
        expect(coreNamespaces.has(namespace), `${moduleName}:${namespace} overlaps the core catalog`).toBe(false)
        expect(owners.has(namespace), `${moduleName}:${namespace} also belongs to ${owners.get(namespace)}`).toBe(false)
        owners.set(namespace, moduleName)
      }
    }
    expect(owners.size).toBeGreaterThan(20)
  })

  for (const [moduleName, messages] of Object.entries(modules)) {
    it(`keeps ${moduleName} locale keys aligned and English values translated`, () => {
      const chineseEntries = leafEntries(messages['zh-CN'])
      const englishEntries = leafEntries(messages['en-US'])
      const chinesePaths = chineseEntries.map(([path]) => path).sort()
      const englishPaths = englishEntries.map(([path]) => path).sort()

      expect(englishPaths).toEqual(chinesePaths)
      for (const [path, value] of englishEntries) {
        expect(value.trim(), `${moduleName}:en-US:${path}`).not.toBe('')
        expect(value, `${moduleName}:en-US:${path}`).not.toMatch(/[\u3400-\u9fff]/)
      }
    })
  }

  it('registers every modular namespace in the runtime i18n instance', () => {
    for (const locale of ['zh-CN', 'en-US'] satisfies Locale[]) {
      const runtimeMessages = i18n.global.getLocaleMessage(locale) as Record<string, unknown>
      for (const [moduleName, messages] of Object.entries(modules)) {
        for (const [namespace, value] of Object.entries(messages[locale])) {
          expect(runtimeMessages[namespace], `${locale}:${moduleName}:${namespace}`).toEqual(value)
        }
      }
    }
  })
})

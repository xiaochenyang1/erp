import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { i18n } from './index'

/** Files that own the shared attachment panel: fully localized, no Chinese literals. */
const featurePaths = [
  'src/components/attachment/DocumentAttachmentDialog.vue',
  'src/components/attachment/document-attachment-access.ts',
  'src/composables/useDocumentAttachmentEntry.ts',
  'src/composables/useDocumentAttachments.ts'
] as const

/** The subset that renders copy; the access and entry modules only hold codes and state. */
const keyedFeaturePaths = [
  'src/components/attachment/DocumentAttachmentDialog.vue',
  'src/composables/useDocumentAttachments.ts'
] as const

/** Gated business type -> the document page that must expose its attachment entry. */
const gatedPages = {
  EXPENSE: 'src/views/finance/expenses/index.vue',
  MANUAL_VOUCHER: 'src/views/finance/vouchers/manual/index.vue',
  FIN_INVOICE: 'src/views/finance/invoices/index.vue',
  SALES_ORDER: 'src/views/sales/orders/index.vue',
  SALES_DELIVERY: 'src/views/sales/deliveries/index.vue',
  SALES_RETURN: 'src/views/sales/returns/index.vue',
  PURCHASE_REQUISITION: 'src/views/purchase/requisitions/index.vue',
  PURCHASE_ORDER: 'src/views/purchase/orders/index.vue',
  PURCHASE_RECEIPT: 'src/views/purchase/receipts/index.vue',
  PURCHASE_RETURN: 'src/views/purchase/returns/index.vue',
  INVENTORY_ADJUSTMENT: 'src/views/inventory/adjustments/index.vue',
  INVENTORY_TRANSFER: 'src/views/inventory/transfers/index.vue',
  INVENTORY_CHECK: 'src/views/inventory/checks/index.vue',
  QC_INSPECTION: 'src/views/qc/inspection/index.vue',
  PRODUCTION_ORDER: 'src/views/production/orders/index.vue'
} as const

/**
 * Contracts keep their own contract-scoped attachment panel inside the detail dialog,
 * so COMMERCIAL_CONTRACT is covered without the shared component.
 */
const contractPage = 'src/views/commercial/contracts/index.vue'

const backendGateSource = '../backend/src/main/java/com/tuowei/erp/system/attachment/service/AttachmentBusinessType.java'

const readSource = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')

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

/** Han ideographs — English copy and the shared panel source must stay free of them. */
const hanPattern = new RegExp('[\\u3400-\\u9fff]')

const referencedKeys = (source: string) =>
  [...source.matchAll(/['"](documentAttachment\.[A-Za-z0-9_.]+)['"]/g)].map((match) => match[1])

/** Reads the constant values inside the backend GATED set so the copy cannot drift from the gate. */
const backendGatedTypes = () => {
  const java = readSource(backendGateSource)
  const block = java.match(/GATED = Set\.of\(([\s\S]*?)\);/)
  expect(block, backendGateSource).toBeTruthy()
  return (block as RegExpMatchArray)[1]
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((constant) => {
      const declaration = java.match(new RegExp(`String ${constant} = "([A-Z_]+)"`))
      expect(declaration, `${backendGateSource}:${constant}`).toBeTruthy()
      return (declaration as RegExpMatchArray)[1]
    })
    .sort()
}

describe('document attachment localization', () => {
  it('registers the namespace in both runtime locales', () => {
    expect(i18n.global.getLocaleMessage('zh-CN').documentAttachment).toBeTruthy()
    expect(i18n.global.getLocaleMessage('en-US').documentAttachment).toBeTruthy()
  })

  it('keeps locale keys aligned and English values translated', () => {
    const chineseEntries = leafEntries(i18n.global.getLocaleMessage('zh-CN').documentAttachment)
    const englishEntries = leafEntries(i18n.global.getLocaleMessage('en-US').documentAttachment)

    expect(chineseEntries.length).toBeGreaterThan(30)
    expect(englishEntries.map(([path]) => path).sort())
      .toEqual(chineseEntries.map(([path]) => path).sort())
    for (const [path, value] of englishEntries) {
      expect(value.trim(), `documentAttachment.en-US.${path}`).not.toBe('')
      expect(value, `documentAttachment.en-US.${path}`).not.toMatch(hanPattern)
    }
  })

  it('resolves every referenced key in both locales', () => {
    const locales = ['zh-CN', 'en-US'] as const
    const resolved = locales.map((locale) => new Set(
      leafEntries(i18n.global.getLocaleMessage(locale).documentAttachment)
        .map(([path]) => `documentAttachment.${path}`)
    ))

    for (const path of [...keyedFeaturePaths, ...Object.values(gatedPages)]) {
      const keys = referencedKeys(readSource(path))
      expect(keys.length, path).toBeGreaterThan(0)
      for (const key of keys) {
        locales.forEach((locale, index) => {
          expect(resolved[index], `${path}:${key}:${locale}`).toContain(key)
        })
      }
    }
  })

  it('routes the shared panel through the catalog instead of hard-coded copy', () => {
    for (const path of featurePaths) {
      const source = stripComments(readSource(path))
      expect(source, path).not.toMatch(hanPattern)
      expect(source, path).not.toMatch(/\s(?:label|placeholder|title|empty-text|aria-label)="[^"]+"/)
      expect(source, path).not.toMatch(/ElMessage\.(?:error|success|warning|info)\(\s*['"`]/)
    }
  })

  it('labels exactly the business types the backend gates', () => {
    const labelled = Object.keys(
      i18n.global.getLocaleMessage('zh-CN').documentAttachment.type as Record<string, string>
    ).sort()

    expect(labelled).toEqual(backendGatedTypes())
  })

  it('gives every gated business type an in-page attachment entry', () => {
    expect([...Object.keys(gatedPages), 'COMMERCIAL_CONTRACT'].sort()).toEqual(backendGatedTypes())

    for (const [businessType, path] of Object.entries(gatedPages)) {
      const source = readSource(path)
      expect(source, path).toContain('DocumentAttachmentDialog.vue')
      expect(source, path).toContain(`business-type="${businessType}"`)
      expect(source, path).toMatch(/openAttachments\(row\.id/)
      expect(source, path).toMatch(/v-if="canViewAttachments"/)
      expect(source, path).toMatch(/:can-upload="canUploadAttachments"/)
      expect(source, path).toMatch(/:can-delete="canDeleteAttachments"/)
    }
  })

  it('keeps the contract detail dialog as the COMMERCIAL_CONTRACT entry', () => {
    const source = readSource(contractPage)
    expect(source).toContain('getContractAttachments')
    expect(source).toContain('uploadContractAttachment')
    expect(source).toContain('downloadContractAttachment')
  })
})

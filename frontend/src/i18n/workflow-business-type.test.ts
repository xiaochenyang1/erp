import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { i18n } from './index'

/**
 * The approval centre can only act on the business types the backend routes, and every routed
 * type needs approval nodes configured before it is submitted — otherwise WorkflowApprovalConfig
 * falls back to "anyone holding workflow:view can approve". These files must therefore cover
 * exactly the backend switch, no more and no less.
 */
const backendActionSource =
  '../backend/src/main/java/com/tuowei/erp/workflow/service/WorkflowTaskActionService.java'

const configPresentation = 'src/composables/useWorkflowConfigPresentation.ts'
const taskPresentation = 'src/composables/useWorkflowTaskPresentation.ts'
const taskFilterPage = 'src/views/workflow/tasks/index.vue'
const recordFilterPage = 'src/views/workflow/records/index.vue'

const readSource = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')

/** Business types both switch arms of the backend action service dispatch on. */
const backendRoutedTypes = () => {
  const java = readSource(backendActionSource)
  const arms = ['approve', 'reject'].map((method) => {
    const block = java.match(new RegExp(`public void ${method}\\(Long taskId[\\s\\S]*?\\n    }`))
    expect(block, `${backendActionSource}:${method}`).toBeTruthy()
    return [...(block as RegExpMatchArray)[0].matchAll(/case "([A-Z_]+)"/g)]
      .map((match) => match[1])
      .sort()
  })
  expect(arms[0], backendActionSource).toEqual(arms[1])
  expect(arms[0].length).toBeGreaterThan(0)
  return arms[0]
}

/** Values of the el-option entries bound to a businessType filter. */
const filterOptionValues = (path: string) => {
  const source = readSource(path)
  const select = source.match(/v-model="queryParams\.businessType"[\s\S]*?<\/el-select>/)
  expect(select, `${path}:businessType select`).toBeTruthy()
  return [...(select as RegExpMatchArray)[0].matchAll(/value="([A-Z_]+)"/g)]
    .map((match) => match[1])
    .sort()
}

const localizedKeys = (namespace: string) => {
  const locales = ['zh-CN', 'en-US'] as const
  const perLocale = locales.map((locale) => {
    const messages = i18n.global.getLocaleMessage(locale) as Record<string, Record<string, unknown>>
    const [root, ...rest] = namespace.split('.')
    const node = rest.reduce<Record<string, unknown>>(
      (current, key) => (current?.[key] ?? {}) as Record<string, unknown>,
      messages[root] as Record<string, unknown>
    )
    return Object.entries(node)
      .filter(([, value]) => typeof value === 'string' && value.trim() !== '')
      .map(([key]) => key)
      .sort()
  })
  expect(perLocale[0], namespace).toEqual(perLocale[1])
  return perLocale[0]
}

describe('workflow business type parity', () => {
  it('offers an approval configuration for every routed business type', () => {
    const configured = [...readSource(configPresentation).matchAll(/value: '([A-Z_]+)'/g)]
      .map((match) => match[1])
      .sort()

    expect(configured).toEqual(backendRoutedTypes())
  })

  it('labels every routed business type in the approval task list', () => {
    const block = readSource(taskPresentation).match(
      /const businessTypeLabel[\s\S]*?\n {2}}/
    )
    expect(block, `${taskPresentation}:businessTypeLabel`).toBeTruthy()
    const mapped = [...(block as RegExpMatchArray)[0].matchAll(/([A-Z_]+): t\(/g)]
      .map((match) => match[1])
      .sort()

    expect(mapped).toEqual(backendRoutedTypes())
  })

  it('filters by every routed business type on the task and record pages', () => {
    expect(filterOptionValues(taskFilterPage)).toEqual(backendRoutedTypes())
    expect(filterOptionValues(recordFilterPage)).toEqual(backendRoutedTypes())
  })

  it('resolves every business type label in both locales', () => {
    const routed = backendRoutedTypes()
    const camel = (value: string) =>
      value.toLowerCase().replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase())

    for (const namespace of ['workflowConfig.businessTypes', 'workflowRecord.businessTypes']) {
      expect(localizedKeys(namespace), namespace).toEqual(routed.map(camel).sort())
    }

    const workflowKeys = localizedKeys('workflow')
    for (const type of routed) {
      expect(workflowKeys, `workflow.${camel(type)}`).toContain(camel(type))
    }
  })
})

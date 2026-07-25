import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { adminWorkflowPageMessages } from './admin-workflow-pages'

const expectedNamespaces = [
  'workflowConfig',
  'workflowRecord',
  'systemDicts',
  'systemRoles',
  'systemImports',
  'systemUsers',
  'systemConfigs',
  'systemLogs',
  'systemReadiness',
  'exceptionRule',
  'exceptionSlaPolicy',
  'exceptionTicket'
] as const

const pagePaths = [
  'src/views/workflow/configs/index.vue',
  'src/views/workflow/records/index.vue',
  'src/views/system/dicts/index.vue',
  'src/views/system/roles/index.vue',
  'src/views/system/imports/index.vue',
  'src/views/system/users/index.vue',
  'src/views/system/configs/index.vue',
  'src/views/system/logs/index.vue',
  'src/views/system/readiness/index.vue',
  'src/views/exception-rules/index.vue',
  'src/views/exception-sla-policies/index.vue',
  'src/views/exception-tickets/index.vue'
] as const

const leafPaths = (value: unknown, prefix = ''): string[] => {
  if (typeof value === 'string') return [prefix]
  if (!value || typeof value !== 'object') return []
  return Object.entries(value).flatMap(([key, nested]) =>
    leafPaths(nested, prefix ? `${prefix}.${key}` : key)
  )
}

const stripComments = (source: string) => source
  .replace(/<!--[\s\S]*?-->/g, '')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '')

describe('admin and workflow page localization', () => {
  it('keeps locale structures aligned under the intended namespaces', () => {
    const chinesePaths = leafPaths(adminWorkflowPageMessages['zh-CN']).sort()
    const englishPaths = leafPaths(adminWorkflowPageMessages['en-US']).sort()

    expect(englishPaths).toEqual(chinesePaths)
    expect(Object.keys(adminWorkflowPageMessages['zh-CN']).sort()).toEqual([...expectedNamespaces].sort())
    expect(adminWorkflowPageMessages['zh-CN']).not.toHaveProperty('workflow')
    expect(adminWorkflowPageMessages['en-US']).not.toHaveProperty('workflow')
  })

  it('provides every message key referenced by the migrated pages', () => {
    const catalogPaths = new Set(leafPaths(adminWorkflowPageMessages['zh-CN']))

    for (const pagePath of pagePaths) {
      const source = readFileSync(resolve(process.cwd(), pagePath), 'utf8')
      const referencedKeys = [...source.matchAll(/(?:\$t|\bt)\(\s*['"]([A-Za-z0-9_.]+)['"]/g)]
        .map((match) => match[1])
        .filter((key) => expectedNamespaces.some((namespace) => key.startsWith(`${namespace}.`)))

      for (const key of referencedKeys) {
        expect(catalogPaths, `${pagePath}:${key}`).toContain(key)
      }
    }
  })

  for (const pagePath of pagePaths) {
    it(`keeps ${pagePath} free of hard-coded Chinese UI copy`, () => {
      const source = readFileSync(resolve(process.cwd(), pagePath), 'utf8')
      expect(stripComments(source)).not.toMatch(/[\u3400-\u9fff]/)
    })
  }
})

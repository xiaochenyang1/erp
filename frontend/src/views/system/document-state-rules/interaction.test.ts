import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const source = readFileSync(resolve(process.cwd(), 'src/views/system/document-state-rules/index.vue'), 'utf8')

describe('document state rule interactions', () => {
  it('reloads the table when Enter is pressed in the keyword field', () => {
    expect(source).toContain('@keyup.enter="loadData"')
    expect(source).not.toContain('@keyup.enter="() => {}"')
  })
})

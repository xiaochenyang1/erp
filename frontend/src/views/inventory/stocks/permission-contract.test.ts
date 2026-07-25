import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const componentSource = readFileSync(resolve(process.cwd(), 'src/views/inventory/stocks/index.vue'), 'utf8')

const buttonTagForClick = (clickExpression: string) => {
  const clickAttribute = `@click="${clickExpression}"`
  const clickIndex = componentSource.indexOf(clickAttribute)

  expect(clickIndex, `missing button click handler: ${clickExpression}`).toBeGreaterThanOrEqual(0)

  const tagStart = componentSource.lastIndexOf('<el-button', clickIndex)
  const tagEnd = componentSource.indexOf('>', clickIndex)

  expect(tagStart, `missing button start tag: ${clickExpression}`).toBeGreaterThanOrEqual(0)
  expect(tagEnd, `missing button end tag: ${clickExpression}`).toBeGreaterThan(clickIndex)

  return componentSource.slice(tagStart, tagEnd + 1)
}

const expectButtonPermission = (clickExpression: string, permission: string) => {
  expect(buttonTagForClick(clickExpression)).toContain(`v-permission="'${permission}'"`)
}

describe('inventory reservation action permissions', () => {
  it('guards every reservation view API entry', () => {
    expectButtonPermission('handleOpenReservations(row)', 'inventory:reservation:view')
    expectButtonPermission('handleViewReservation(row)', 'inventory:reservation:view')
    expectButtonPermission('handleViewReservationSource(row)', 'inventory:reservation:view')
  })

  it('guards the reservation check API entry', () => {
    expectButtonPermission('handleReservationCheck', 'inventory:reservation:check')
  })

  it('guards every manual release API entry', () => {
    expectButtonPermission('openReleaseDialog(row)', 'inventory:reservation:release')
    expectButtonPermission('openReleaseDialog(reservationDetail.reservation)', 'inventory:reservation:release')
    expectButtonPermission('submitManualRelease', 'inventory:reservation:release')
  })
})

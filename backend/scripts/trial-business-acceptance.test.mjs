import test from 'node:test'
import assert from 'node:assert/strict'
import { businessDate, checkContainers, checkSettlement, checkStock, localOrigin, selectTrialMenus, trialPermissions } from './trial-business-acceptance.mjs'

test('trial refuses remote, credential-bearing, redirected-path and non-HTTP targets', () => {
  assert.equal(localOrigin('http://127.0.0.1:19888'), 'http://127.0.0.1:19888')
  for (const value of ['https://127.0.0.1:19888', 'http://example.com', 'http://localhost:19888',
    'http://admin:secret@127.0.0.1', 'http://127.0.0.1/api', 'http://127.0.0.1/?x=1', 'http://127.0.0.1/#x']) {
    assert.throws(() => localOrigin(value))
  }
})

test('business dates reject calendar overflow and preserve leap dates', () => {
  assert.equal(businessDate('2028-02-29'), '2028-02-29')
  for (const value of ['2026-02-29', '2026-04-31', '2026-13-01', '2026-1-01', '1999-01-01']) assert.throws(() => businessDate(value))
})

const menuTree = () => [{ id: '1', parentId: '0', status: 'ACTIVE', permission: null,
  children: trialPermissions.map((permission, i) => ({ id: String(i + 2), parentId: '1', permission, status: 'ACTIVE', children: [] })) }]

test('trial role selects only explicit permissions and ancestors, excluding sibling administration', () => {
  const tree = menuTree()
  tree[0].children.push({ id: '99', parentId: '1', status: 'ACTIVE', permission: 'system:user:create' })
  const selected = selectTrialMenus(tree)
  assert.equal(selected.length, trialPermissions.length + 1)
  assert(selected.includes('1')); assert(!selected.includes('99'))
})

test('missing, duplicate, disabled or extra ancestor permissions fail before role assignment', () => {
  const missing = menuTree(); missing[0].children.pop()
  const duplicate = menuTree(); duplicate[0].children.push({ ...duplicate[0].children[0], id: '999' })
  const disabled = menuTree(); disabled[0].status = 'DISABLED'
  const unsafe = menuTree(); unsafe[0].permission = 'system:user:create'
  for (const tree of [missing, duplicate, disabled, unsafe]) assert.throws(() => selectTrialMenus(tree))
})

const partial = () => ({ originalAmount: '100.00', settledAmount: '40.00', remainingAmount: '60.00', baseRemainingAmount: '60.00', currencyCode: 'CNY', exchangeRate: '1.000000', status: 'PARTIAL' })
const partialExpected = { original: 100, settled: 40, remaining: 60 }

test('partial and completed settlements match independent sample calculations', () => {
  assert.deepEqual(checkSettlement(partial(), partialExpected), { ...partialExpected, status: 'PARTIAL' })
  const complete = { ...partial(), settledAmount: 100, remainingAmount: 0, baseRemainingAmount: 0, status: 'SETTLED' }
  assert.equal(checkSettlement(complete, { original: 100, settled: 100, remaining: 0 }).status, 'SETTLED')
})

test('HTTP-success payloads with wrong balances, currencies or statuses cannot pass settlement checks', () => {
  for (const change of [{ originalAmount: 101 }, { settledAmount: 0 }, { remainingAmount: 59 }, { baseRemainingAmount: 0 },
    { currencyCode: 'USD' }, { exchangeRate: 7 }, { status: 'SETTLED' }, { remainingAmount: null }]) {
    assert.throws(() => checkSettlement({ ...partial(), ...change }, partialExpected))
  }
  assert.throws(() => checkSettlement({ ...partial(), settledAmount: 100, remainingAmount: 0, baseRemainingAmount: 0 }, { original: 100, settled: 100, remaining: 0 }))
})

const stock = () => ({ warehouseId: '100', productId: '200', qtyOnHand: '6.0000', qtyAvailable: '6.0000', qtyReserved: '0.0000', amountOnHand: '60.00' })

test('inventory validates quantity, valuation, reservation release and exact product/warehouse', () => {
  assert.deepEqual(checkStock([stock()], '100', '200', 6, 60), { quantity: 6, amount: 60, reserved: 0 })
  for (const change of [{ qtyOnHand: 10 }, { qtyAvailable: 5 }, { qtyReserved: 1 }, { amountOnHand: 90 }, { amountOnHand: null }, { warehouseId: '101' }, { productId: '201' }]) {
    assert.throws(() => checkStock([{ ...stock(), ...change }], '100', '200', 6, 60))
  }
  assert.throws(() => checkStock([stock(), stock()], '100', '200', 6, 60))
})

const target = { project: 'erp-preview-test', bundle: '/isolated/bundle', port: '19888' }
const containers = () => ['mysql', 'redis', 'erp-server', 'web'].map(service => ({
  Config: { Labels: { 'com.docker.compose.project': target.project, 'com.docker.compose.project.working_dir': target.bundle,
    'com.docker.compose.project.config_files': '/isolated/bundle/compose.yaml', 'com.docker.compose.service': service } },
  State: { Health: { Status: 'healthy' } },
  NetworkSettings: { Ports: service === 'web' ? { '8080/tcp': [{ HostIp: '127.0.0.1', HostPort: '19888' }] } : {} }
}))

test('deployment identity includes the actual project, directory, health and loopback exposure', () => {
  checkContainers(containers(), target)
  assert.throws(() => checkContainers(containers(), { ...target, project: 'erp-production' }))
  assert.throws(() => checkContainers(containers(), { ...target, bundle: '/different' }))
  const unhealthy = containers(); unhealthy[0].State.Health.Status = 'unhealthy'
  const exposedDb = containers(); exposedDb[0].NetworkSettings.Ports['3306/tcp'] = [{ HostIp: '127.0.0.1', HostPort: '3306' }]
  const publicWeb = containers(); publicWeb[3].NetworkSettings.Ports['8080/tcp'][0].HostIp = '0.0.0.0'
  const foreignConfig = containers(); foreignConfig[1].Config.Labels['com.docker.compose.project.config_files'] = '/different/compose.yaml'
  for (const state of [unhealthy, exposedDb, publicWeb, foreignConfig, containers().slice(1)]) assert.throws(() => checkContainers(state, target))
})

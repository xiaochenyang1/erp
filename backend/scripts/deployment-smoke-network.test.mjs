import assert from 'node:assert/strict'
import test from 'node:test'
import { prepareReplacementNetwork } from './deployment-smoke-network.mjs'

const project = 'erp-preview-deployment-123456abcdef'
const probeId = 'a'.repeat(64)
const explicitId = 'b'.repeat(64)

function fixture({ alter = network => network, createFailure = false } = {}) {
  const calls = []
  const docker = async args => {
    calls.push(args)
    if (args[1] === 'create') {
      if (createFailure) throw new Error('network name already exists')
      return args.includes('--subnet') ? explicitId : probeId
    }
    if (args[1] === 'inspect') return JSON.stringify([alter({ Id: probeId, Name: `${project}_default`,
      Labels: { 'com.docker.compose.project': project }, Containers: {},
      IPAM: { Config: [{ Subnet: '172.25.0.0/16', Gateway: '172.25.0.1' }] } })])
    return ''
  }
  return { calls, docker }
}

test('uses Docker allocated space and configures it explicitly before Compose starts', async () => {
  const f = fixture()
  assert.deepEqual(await prepareReplacementNetwork(f.docker, project), {
    name: `${project}_default`, subnet: '172.25.0.0/16', gateway: '172.25.0.1'
  })
  const creation = f.calls.at(-1)
  assert.equal(creation[creation.indexOf('--subnet') + 1], '172.25.0.0/16')
  assert.equal(creation[creation.indexOf('--gateway') + 1], '172.25.0.1')
  assert(creation.includes(`com.docker.compose.project=${project}`))
  assert(creation.includes('com.docker.compose.network=default'))
  assert.deepEqual(f.calls.at(-2), ['network', 'rm', probeId])
})

test('an existing network is never removed when probe creation fails', async () => {
  const f = fixture({ createFailure: true })
  await assert.rejects(prepareReplacementNetwork(f.docker, project), /already exists/)
  assert.equal(f.calls.length, 1)
})

test('invalid allocation or a nonempty probe fails before explicit creation and attempts probe cleanup', async () => {
  for (const alter of [network => ({ ...network, IPAM: { Config: [] } }),
    network => ({ ...network, Containers: { active: {} } })]) {
    const f = fixture({ alter })
    await assert.rejects(prepareReplacementNetwork(f.docker, project))
    assert(!f.calls.some(args => args.includes('--subnet')))
    assert.deepEqual(f.calls.at(-1), ['network', 'rm', probeId])
  }
})

test('subnet recreation failure is surfaced without selecting a fixed fallback subnet', async () => {
  const f = fixture()
  const docker = args => args.includes('--subnet') ? Promise.reject(new Error('pool overlaps another network')) : f.docker(args)
  await assert.rejects(prepareReplacementNetwork(docker, project), /pool overlaps/)
  assert.equal(f.calls.filter(args => args[1] === 'create').length, 1)
})

test('rejects an unrelated project before making Docker calls', async () => {
  await assert.rejects(prepareReplacementNetwork(() => assert.fail('must not invoke Docker'), 'erp-production'))
})

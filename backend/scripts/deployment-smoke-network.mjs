import assert from 'node:assert/strict'
import { isIP } from 'node:net'

// Docker only accepts the replacement check's --ip reservation on a network
// with an explicit subnet. Let its allocator choose a free pool first, then
// recreate that empty, owned network with the same pool configured explicitly.
export async function prepareReplacementNetwork(docker, project) {
  assert(/^erp-preview-deployment-[a-f0-9]{12}$/.test(project), 'Expected a disposable deployment project')
  const name = `${project}_default`
  const labels = ['--label', `com.docker.compose.project=${project}`, '--label', 'com.docker.compose.network=default']
  let probeId
  try {
    probeId = (await docker(['network', 'create', ...labels, name])).trim()
    assert(/^[a-f0-9]{64}$/.test(probeId), 'Missing probe network ID')
    const networks = JSON.parse(await docker(['network', 'inspect', probeId]))
    assert.equal(networks.length, 1)
    const network = networks[0]
    assert.equal(network.Id, probeId)
    assert.equal(network.Name, name)
    assert.equal(network.Labels?.['com.docker.compose.project'], project)
    assert.equal(Object.keys(network.Containers || {}).length, 0, 'Probe network must be empty')
    const pool = network.IPAM?.Config?.find(config => isIP(config.Subnet?.split('/')[0]) === 4)
    assert(pool && /^\d{1,3}(\.\d{1,3}){3}\/(\d|[12]\d|3[0-2])$/.test(pool.Subnet), 'Missing allocated IPv4 subnet')
    assert.equal(isIP(pool.Gateway), 4, 'Missing allocated IPv4 gateway')
    await docker(['network', 'rm', probeId])
    probeId = null
    const id = (await docker(['network', 'create', ...labels, '--subnet', pool.Subnet, '--gateway', pool.Gateway, name])).trim()
    assert(/^[a-f0-9]{64}$/.test(id), 'Missing explicit network ID')
    return { name, subnet: pool.Subnet, gateway: pool.Gateway }
  } catch (error) {
    if (probeId && /^[a-f0-9]{64}$/.test(probeId)) {
      try { await docker(['network', 'rm', probeId]) } catch { /* The parent smoke cleanup also checks owned networks. */ }
    }
    throw error
  }
}

import assert from 'node:assert/strict'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'

const execute = promisify(execFile)

// Called only after the caller validates its report kind and source revision.
export async function cleanupDisposableDocker(project, docker = async args =>
  (await execute('docker', args, { timeout: 60000, maxBuffer: 1024 * 1024 })).stdout) {
  assert(/^erp-preview-(deployment|recovery)-[a-f0-9]{12}$/.test(project || ''), 'Refusing cleanup of a non-smoke project')
  const groups = [
    { name: 'containers', list: ['ps', '-aq', '--filter', `label=com.docker.compose.project=${project}`], remove: ['rm', '-f'] },
    { name: 'IP holder', list: ['ps', '-aq', '--filter', `name=^${project}-ip-holder$`], remove: ['rm', '-f'] },
    { name: 'volumes', list: ['volume', 'ls', '-q', '--filter', `label=com.docker.compose.project=${project}`], remove: ['volume', 'rm'] },
    { name: 'networks', list: ['network', 'ls', '-q', '--filter', `label=com.docker.compose.project=${project}`], remove: ['network', 'rm'] },
    { name: 'images', list: ['image', 'ls', '--format', '{{.Repository}}:{{.Tag}}', '--filter', `reference=${project}-*`], remove: ['image', 'rm'] }
  ]
  const failures = []
  for (const group of groups) {
    try {
      const resources = (await docker(group.list)).trim().split(/\s+/).filter(Boolean)
      if (resources.length) await docker([...group.remove, ...resources])
    } catch (error) { failures.push({ resource: group.name, error: error.message }) }
  }
  for (const group of groups) {
    try { assert.equal((await docker(group.list)).trim(), '', `Owned ${group.name} remain`) }
    catch (error) { failures.push({ resource: group.name, error: error.message }) }
  }
  return { passed: failures.length === 0, checkedAt: new Date().toISOString(), project, failures }
}

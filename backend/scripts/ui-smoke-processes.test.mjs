import assert from 'node:assert/strict'
import test from 'node:test'
import { createServer } from 'node:net'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'
import { spawnSmokeProcess, stopSmokeProcess, cleanupSmokeProcesses } from './ui-smoke-processes.mjs'

const fixture = fileURLToPath(new URL('./fixtures/ui-smoke-process-tree.mjs', import.meta.url))
const unix = { skip: process.platform === 'win32', timeout: 15000 }

async function startTree(t, mode = 'normal') {
  const handle = spawnSmokeProcess(process.execPath, [fixture, 'root', mode])
  t.after(async () => {
    await cleanupSmokeProcesses([{ name: 'fixture', ...handle }], { graceMs: 50, forceMs: 3000 })
  })
  handle.child.stderr.resume()
  let data = ''
  handle.child.stdout.on('data', chunk => { data += chunk })
  const deadline = Date.now() + 5000
  while (!data.includes('\n') && Date.now() < deadline) await delay(20)
  assert(data.includes('\n'), 'grandchild must start and report its listening port')
  return { handle, leaf: JSON.parse(data.trim().split('\n')[0]) }
}

async function assertPortReleased(port) {
  const server = createServer()
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(port, '127.0.0.1', resolve)
  })
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()))
}

test('cleanup closes descendant pipes and releases their listening port', unix, async t => {
  const { handle, leaf } = await startTree(t)
  const report = await cleanupSmokeProcesses([{ name: 'frontend', ...handle }])
  assert.equal(report.passed, true)
  assert.equal(report.processes[0].childClosed, true)
  assert.equal(report.processes[0].forced, false)
  await assertPortReleased(leaf.port)
})

test('shutdown gives descendants time to finish gracefully', unix, async t => {
  const { handle, leaf } = await startTree(t, 'graceful')
  const result = await stopSmokeProcess(handle, { graceMs: 3000, forceMs: 3000 })
  assert.equal(result.passed, true)
  assert.equal(result.forced, false)
  assert(result.elapsedMs >= 100)
  await assertPortReleased(leaf.port)
})

test('a descendant ignoring TERM is killed within the cleanup deadline', unix, async t => {
  const { handle, leaf } = await startTree(t, 'stubborn')
  const result = await stopSmokeProcess(handle, { graceMs: 150, forceMs: 3000 })
  assert.equal(result.passed, true)
  assert.equal(result.forced, true)
  assert.equal(result.childClosed, true)
  await assertPortReleased(leaf.port)
})

test('cleanup still reaches descendants after the original parent has exited', unix, async t => {
  const { handle, leaf } = await startTree(t, 'orphan')
  for (let i = 0; i < 100 && handle.child.exitCode === null; i++) await delay(20)
  assert.equal(handle.child.exitCode, 0)
  assert.equal(handle.state.closed, false, 'orphan descendants still hold the pipe')
  const result = await stopSmokeProcess(handle)
  assert.equal(result.passed, true)
  await assertPortReleased(leaf.port)
})

test('one service cleanup does not terminate another owned process group', unix, async t => {
  const first = await startTree(t)
  const second = await startTree(t)
  assert.equal((await stopSmokeProcess(first.handle)).passed, true)
  assert.equal(second.handle.state.closed, false)
  assert.doesNotThrow(() => process.kill(second.leaf.pid, 0))
  assert.equal((await stopSmokeProcess(second.handle)).passed, true)
})

test('failed spawn has no process tree to leak', { timeout: 5000 }, async () => {
  const handle = spawnSmokeProcess('erp-ui-smoke-nonexistent-command', [])
  handle.child.stdout.resume()
  handle.child.stderr.resume()
  const result = await stopSmokeProcess(handle, { graceMs: 1000 })
  assert.equal(result.passed, true)
  assert.equal(result.pid, null)
  assert.equal(handle.state.error.code, 'ENOENT')
})

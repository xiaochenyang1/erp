import assert from 'node:assert/strict'
import { test } from 'node:test'
import { run } from './production-rehearsal.mjs'

test('command output and stdin survive a successful execution', async () => {
  const result = await run(process.execPath, ['-e', 'process.stdin.pipe(process.stdout); process.stderr.write("diagnostic")'], { input: 'payload' })
  assert.equal(result.code, 0)
  assert.equal(result.stdout, 'payload')
  assert.equal(result.stderr, 'diagnostic')
  assert.equal(result.timing.timedOut, false)
})

test('an explicitly allowed nonzero exit supports negative restore checks', async () => {
  const result = await run(process.execPath, ['-e', 'process.stderr.write("checksum rejected"); process.exit(7)'], { allowFailure: true })
  assert.equal(result.code, 7)
  assert.equal(result.stderr, 'checksum rejected')
})

test('ordinary command failures do not disclose command arguments or stdin', async () => {
  await assert.rejects(run(process.execPath, ['-e', 'process.exit(7)', 'private-argument'], { input: 'private-input' }), error => {
    assert.match(error.message, /failed \(7,/)
    assert(!error.message.includes('private-argument'))
    assert(!error.message.includes('private-input'))
    assert.equal(error.commandTiming.code, 7)
    return true
  })
})

test('a timed out process fails even when nonzero exits are allowed', async () => {
  await assert.rejects(run(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { timeout: 100, allowFailure: true }), error => {
    assert.match(error.message, /failed \(timeout,/)
    assert.equal(error.commandTiming.timedOut, true)
    assert.equal(error.commandTiming.timeoutMs, 100)
    assert.equal(error.commandTiming.signal, 'SIGKILL')
    assert(error.commandTiming.elapsedMs >= 90)
    return true
  })
})

test('a signaled process cannot be counted as an expected rejection', async () => {
  await assert.rejects(run(process.execPath, ['-e', 'process.kill(process.pid, "SIGKILL")'], { allowFailure: true }), error => {
    assert.equal(error.commandTiming.timedOut, false)
    assert.equal(error.commandTiming.signal, 'SIGKILL')
    return true
  })
})

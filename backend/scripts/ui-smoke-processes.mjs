import { spawn, execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { setTimeout as delay } from 'node:timers/promises'

const execFileAsync = promisify(execFile)

export function spawnSmokeProcess(command, args, options = {}) {
  // Each Unix service owns a group, including npm's shell and Vite descendants.
  // Do not unref it: successful cleanup must observe closed output pipes.
  const child = spawn(command, args, {
    ...options,
    detached: process.platform !== 'win32',
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe']
  })
  const state = { closed: false, error: null }
  child.once('error', error => { state.error = error })
  child.once('close', () => { state.closed = true })
  return { child, state }
}

function signalGroup(pid, signal) {
  try {
    process.kill(-pid, signal)
    return true
  } catch (error) {
    if (error.code === 'ESRCH') return false
    throw error
  }
}

async function waitUntil(check, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  do {
    if (check()) return true
    await delay(50)
  } while (Date.now() < deadline)
  return check()
}

export async function stopSmokeProcess(handle, { graceMs = 10000, forceMs = 5000 } = {}) {
  const { child, state } = handle
  const startedAt = Date.now()
  let forced = false
  const stopped = () => state.closed && (
    !child.pid || process.platform === 'win32' || !signalGroup(child.pid, 0)
  )
  if (child.pid) {
    if (process.platform === 'win32') {
      try {
        await execFileAsync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], {
          windowsHide: true, timeout: forceMs
        })
      } catch (error) {
        // taskkill can race a process that has already exited and closed its pipes.
        if (!state.closed) throw error
      }
      forced = true
    } else {
      // The group can still exist after its leader exits; do not use exitCode here.
      signalGroup(child.pid, 'SIGTERM')
    }
  }
  let passed = await waitUntil(stopped, graceMs)
  if (!passed && child.pid && process.platform !== 'win32') {
    forced = signalGroup(child.pid, 'SIGKILL')
    passed = await waitUntil(stopped, forceMs)
  }
  return { pid: child.pid ?? null, passed, forced, childClosed: state.closed, elapsedMs: Date.now() - startedAt }
}

export async function cleanupSmokeProcesses(entries, options) {
  const results = []
  for (const { name, ...handle } of [...entries].reverse()) {
    let result
    try {
      result = { name, ...await stopSmokeProcess(handle, options) }
    } catch (error) {
      result = { name, pid: handle.child.pid ?? null, passed: false, error: error.message }
    }
    if (!result.passed) {
      // Release runner handles only after recording failure; never hang and mask it.
      handle.child.stdout?.destroy()
      handle.child.stderr?.destroy()
      handle.child.unref()
    }
    results.push(result)
  }
  return { passed: results.every(result => result.passed), processes: results }
}

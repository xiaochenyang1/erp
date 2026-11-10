import { setTimeout as sleep } from 'node:timers/promises'

// HTTP/database health can be available before ApplicationRunner commits the admin password.
// Wait without attempting login: retrying bad credentials can lock the account and hide a real error.
export async function waitForBackendReadiness(baseUrl, { timeoutMs = 90000, pollMs = 500 } = {}) {
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0 || !Number.isSafeInteger(pollMs) || pollMs <= 0) {
    throw new Error('Readiness timeout and poll interval must be positive integer milliseconds')
  }
  const url = new URL('/actuator/health/readiness', baseUrl)
  const deadline = Date.now() + timeoutMs
  let lastError = 'no response'
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, {
        signal: AbortSignal.timeout(Math.max(1, Math.min(5000, deadline - Date.now()))),
        redirect: 'error'
      })
      if (response.status === 200) {
        const payload = await response.json()
        if (payload?.status !== 'UP') throw new Error('Backend readiness returned HTTP 200 without status UP')
        return
      }
      await response.body?.cancel()
      if (response.status !== 503) {
        throw new Error(`Backend readiness failed: HTTP ${response.status}; check probe configuration and access`)
      }
      lastError = 'HTTP 503 (application initialization is not complete)'
    } catch (error) {
      if (!(error instanceof TypeError) && !['TimeoutError', 'AbortError'].includes(error.name)) throw error
      lastError = error.name
    }
    const remaining = deadline - Date.now()
    if (remaining > 0) await sleep(Math.min(pollMs, remaining))
  }
  throw new Error(`Backend readiness timed out after ${timeoutMs}ms: ${lastError}`)
}

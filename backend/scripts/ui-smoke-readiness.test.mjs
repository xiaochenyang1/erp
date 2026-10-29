import assert from 'node:assert/strict'
import test from 'node:test'
import { createServer } from 'node:http'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'
import { waitForBackendReadiness } from './ui-smoke-readiness.mjs'

const execute = promisify(execFile)
const prepareScript = fileURLToPath(new URL('./prepare-ui-smoke-user.mjs', import.meta.url))

async function serve(t, handler) {
  const server = createServer(handler)
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  t.after(() => new Promise(resolve => {
    server.closeAllConnections()
    server.close(resolve)
  }))
  return `http://127.0.0.1:${server.address().port}`
}

function reply(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json' })
  response.end(JSON.stringify(body))
}

function prepare(baseUrl) {
  return execute(process.execPath, [prepareScript], {
    env: { ...process.env, ERP_UI_SMOKE_BACKEND_URL: baseUrl, ERP_UI_SMOKE_BACKEND_TIMEOUT_MS: '5000' },
    timeout: 10000
  })
}

test('account preparation waits for bootstrap before either login or user writes', async t => {
  let probes = 0
  let ready = false
  const requests = []
  const base = await serve(t, (request, response) => {
    requests.push(`${request.method} ${request.url}`)
    if (request.url === '/actuator/health/readiness') {
      ready = ++probes > 1
      return reply(response, ready ? 200 : 503, { status: ready ? 'UP' : 'OUT_OF_SERVICE' })
    }
    assert.equal(ready, true, 'login and writes must not happen while bootstrap is incomplete')
    if (request.url === '/api/auth/login') return reply(response, 200, { code: '0', data: { accessToken: 'test-token' } })
    if (request.url.startsWith('/api/system/users?')) return reply(response, 200, { code: '0', data: { records: [] } })
    if (request.method === 'POST' && request.url === '/api/system/users') return reply(response, 200, { code: '0', data: { id: 42 } })
    if (request.method === 'PUT' && request.url === '/api/system/users/42/roles') return reply(response, 200, { code: '0', data: null })
    reply(response, 404, {})
  })
  const { stdout } = await prepare(base)
  assert.match(stdout, /Prepared the UI smoke submitter account/)
  assert.deepEqual(requests, [
    'GET /actuator/health/readiness', 'GET /actuator/health/readiness',
    'POST /api/auth/login', 'GET /api/system/users?pageNo=1&pageSize=100&keyword=runtime_smoke',
    'POST /api/system/users', 'PUT /api/system/users/42/roles', 'POST /api/auth/login'
  ])
})

test('a genuine credential failure after readiness fails once without user mutations', async t => {
  const requests = []
  const base = await serve(t, (request, response) => {
    requests.push(request.url)
    if (request.url === '/actuator/health/readiness') return reply(response, 200, { status: 'UP' })
    reply(response, 401, { code: '401', message: 'bad credentials' })
  })
  await assert.rejects(prepare(base), error => {
    assert.match(error.stderr, /POST \/api\/auth\/login failed: HTTP 401/)
    assert.doesNotMatch(error.stderr, /LocalAdmin123|RuntimeSmoke123/)
    return true
  })
  assert.deepEqual(requests, ['/actuator/health/readiness', '/api/auth/login'])
})

test('persistent startup refusal has a deadline and never probes login', async t => {
  const requests = []
  const base = await serve(t, (request, response) => {
    requests.push(request.url)
    reply(response, 503, { status: 'OUT_OF_SERVICE' })
  })
  await assert.rejects(waitForBackendReadiness(base, { timeoutMs: 120, pollMs: 10 }), /readiness timed out/)
  assert.ok(requests.length > 1)
  assert.ok(requests.every(path => path === '/actuator/health/readiness'))
})

for (const status of [401, 403, 404]) {
  test(`readiness HTTP ${status} exposes probe misconfiguration without retry`, async t => {
    let requests = 0
    const base = await serve(t, (_request, response) => { requests++; reply(response, status, {}) })
    await assert.rejects(waitForBackendReadiness(base), new RegExp(`readiness failed: HTTP ${status}`))
    assert.equal(requests, 1)
  })
}

for (const headersSent of [false, true]) {
  test(`a stalled readiness ${headersSent ? 'body' : 'connection'} cannot exceed the deadline`, async t => {
    const base = await serve(t, (_request, response) => {
      if (headersSent) {
        response.writeHead(200, { 'Content-Type': 'application/json' })
        response.flushHeaders()
      }
    })
    const start = Date.now()
    await assert.rejects(waitForBackendReadiness(base, { timeoutMs: 100, pollMs: 10 }), /readiness timed out/)
    assert.ok(Date.now() - start < 2000)
  })
}

test('HTTP 200 must contain the expected readiness status', async t => {
  const base = await serve(t, (_request, response) => reply(response, 200, { status: 'DOWN' }))
  await assert.rejects(waitForBackendReadiness(base), /without status UP/)
})

test('invalid timeout fails before making a request', async () => {
  await assert.rejects(waitForBackendReadiness('http://127.0.0.1:1', { timeoutMs: NaN }), /positive integer/)
})

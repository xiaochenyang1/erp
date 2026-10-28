// Test a copied deployment bundle on disposable local services, never an existing target.
import assert from 'node:assert/strict'
import { execFile } from 'node:child_process'
import { createHash, randomBytes } from 'node:crypto'
import { cp, mkdir, mkdtemp, readFile, writeFile, appendFile, rm } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { createServer } from 'node:net'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseArgs, promisify } from 'node:util'
import { setTimeout as delay } from 'node:timers/promises'
import { verifyDeploymentBundle, initializeDeployment } from './deployment-bundle.mjs'
import { connectCdpSocket } from './ui-smoke-cdp.mjs'
import { spawnSmokeProcess, cleanupSmokeProcesses } from './ui-smoke-processes.mjs'

const execute = promisify(execFile)
const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const secrets = []
let interrupted = false
const redact = value => secrets.reduce((result, secret) => result.replaceAll(secret, '[REDACTED]'), String(value))
const environment = Object.fromEntries(Object.entries(process.env).filter(([key]) => !/^(ERP_|MYSQL_|REDIS_|COMPOSE_|SPRING_|JAVA_)/.test(key)))

async function until(check, timeout = 90000) {
  const deadline = Date.now() + timeout
  let lastError
  do {
    if (interrupted) throw new Error('Deployment smoke interrupted')
    try { return await check() } catch (error) { lastError = error }
    await delay(250)
  } while (Date.now() < deadline)
  throw lastError
}

async function freePort() {
  const server = createServer()
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const port = server.address().port
  await new Promise(resolve => server.close(resolve))
  return port
}

async function browserCheck(base, password, directory, evidence, children) {
  const chrome = [process.env.CHROME_PATH, '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'].find(value => value && existsSync(value))
  assert(chrome, 'Set CHROME_PATH to a Chrome or Edge executable')
  const profile = path.join(directory, 'chrome-profile')
  const handle = spawnSmokeProcess(chrome, ['--headless=new', '--disable-gpu', '--disable-extensions',
    '--disable-background-networking', '--no-first-run', '--no-default-browser-check', '--window-size=1440,1000',
    '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { env: environment })
  children.push({ name: 'chrome', ...handle })
  for (const stream of [handle.child.stdout, handle.child.stderr]) stream.on('data', () => {})
  const port = await until(async () => {
    if (handle.state.error) throw handle.state.error
    return Number((await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0])
  }, 30000)
  const tabs = await (await fetch(`http://127.0.0.1:${port}/json/list`, { signal: AbortSignal.timeout(5000) })).json()
  const errors = []
  const cdp = await connectCdpSocket(new WebSocket(tabs.find(tab => tab.type === 'page').webSocketDebuggerUrl), {
    onEvent: event => { if (event.method === 'Runtime.exceptionThrown') errors.push(event.params.exceptionDetails.text) }
  })
  const evaluate = async expression => {
    const result = await cdp.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
    assert(!result.exceptionDetails, 'Browser evaluation failed')
    return result.result.value
  }
  const wait = expression => until(async () => assert.equal(await evaluate(`Boolean(document.body) && (${expression})`), true), 30000)
  try {
    await cdp.send('Page.enable'); await cdp.send('Runtime.enable')
    await cdp.send('Page.navigate', { url: `${base}/sales/orders` })
    await wait(`location.pathname === '/login' && !!document.querySelector('.login-form input[type="password"]')`)
    await evaluate(`(() => {
      const fields = document.querySelectorAll('.login-form input');
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
      for (const [index, value] of ['admin', ${JSON.stringify(password)}].entries()) {
        setter.call(fields[index], value); fields[index].dispatchEvent(new Event('input', { bubbles: true }));
      }
    })()`)
    await evaluate(`document.querySelector('.login-button').click()`)
    await wait(`location.pathname !== '/login' && !!localStorage.getItem('token')`)
    const routes = [
      ['/dashboard', '今日采购订单'], ['/sales/orders', '销售订单'],
      ['/inventory/stocks', '库存查询'], ['/finance/periods', '会计期间管理']
    ]
    for (const [route, text] of routes) {
      await cdp.send('Page.navigate', { url: base + route })
      await wait(`location.pathname === ${JSON.stringify(route)} && document.body.innerText.includes(${JSON.stringify(text)})`)
      await cdp.send('Page.reload', { ignoreCache: true })
      await wait(`location.pathname === ${JSON.stringify(route)} && document.body.innerText.includes(${JSON.stringify(text)})`)
    }
    assert.deepEqual(errors, [], 'Browser runtime errors')
    const screenshot = await cdp.send('Page.captureScreenshot', { format: 'png' })
    await writeFile(path.join(evidence, 'browser.png'), Buffer.from(screenshot.data, 'base64'))
    return { loginThroughUi: true, protectedDeepLink: true, refreshedRoutes: routes.map(([route]) => route), runtimeErrors: errors }
  } finally { cdp.close() }
}

async function main() {
  const { values } = parseArgs({ options: { bundle: { type: 'string' }, 'expected-manifest-sha256': { type: 'string' }, evidence: { type: 'string' } } })
  const verified = verifyDeploymentBundle({ bundleDirectory: values.bundle, expectedManifestSha256: values['expected-manifest-sha256'] })
  const { stdout: revisionOutput } = await execute('git', ['rev-parse', 'HEAD'], { cwd: repo })
  const { stdout: status } = await execute('git', ['status', '--porcelain'], { cwd: repo })
  assert.equal(status.trim(), '', 'Commit smoke tooling before collecting deployment evidence')
  const evidence = path.resolve(values.evidence)
  assert(evidence !== repo && !evidence.startsWith(repo + path.sep), 'Evidence must be outside the source tree')
  await mkdir(evidence, { mode: 0o700 }) // Refuse to overwrite previous evidence.
  const directory = await mkdtemp(path.join(tmpdir(), 'erp-deployment-smoke-'))
  const work = path.join(directory, 'bundle')
  const project = `erp-deployment-${randomBytes(6).toString('hex')}`
  const holder = `${project}-ip-holder`
  const children = []
  const report = { status: 'RUNNING', startedAt: new Date().toISOString(), ...verified,
    runtimeToolRevision: revisionOutput.trim(), project, checks: [],
    scope: 'Local disposable fullstack prod deployment; synthetic data; not real preproduction or business signoff' }
  let cdpCleanup, token, attachmentId
  const onSignal = () => { interrupted = true }
  process.on('SIGINT', onSignal); process.on('SIGTERM', onSignal)
  const persist = () => writeFile(path.join(evidence, 'report.json'), JSON.stringify(report, null, 2) + '\n')
  const command = async (program, args, timeout = 300000) => {
    try {
      const result = await execute(program, args, { cwd: work, env: environment, timeout, maxBuffer: 16 * 1024 * 1024 })
      if (program === 'docker') await appendFile(path.join(evidence, 'docker.log'), redact(result.stdout + result.stderr))
      return result.stdout
    } catch (error) { throw new Error(redact(`${program} failed: ${error.stderr || error.message}`)) }
  }
  const compose = args => command('docker', ['compose', '-p', project, '--env-file', path.join(work, '.env'), '-f', path.join(work, 'compose.yaml'), ...args])
  const check = async (name, action) => {
    if (interrupted) throw new Error('Deployment smoke interrupted')
    report.activeStep = name; await persist(); console.log(`[deployment] ${name}`)
    try { report.checks.push({ name, passed: true, ...await action() }) }
    catch (error) { report.checks.push({ name, passed: false, failure: redact(error.message) }); throw error }
    report.activeStep = null; await persist()
  }
  try {
    await cp(path.resolve(values.bundle), work, { recursive: true, filter: source => path.basename(source) !== '.env' })
    const port = await freePort()
    const base = `http://127.0.0.1:${port}`
    initializeDeployment({ bundleDirectory: work, expectedManifestSha256: verified.manifestSha256, origin: base, port })
    const config = Object.fromEntries((await readFile(path.join(work, '.env'), 'utf8')).trim().split('\n').map(line => {
      const split = line.indexOf('='); return [line.slice(0, split), line.slice(split + 1)]
    }))
    secrets.push(...Object.entries(config).filter(([key]) => /PASSWORD|SECRET/.test(key)).map(([, value]) => value))
    report.baseUrl = base
    const request = (route, options = {}) => fetch(base + route, { ...options, signal: AbortSignal.timeout(15000) })
    const api = async (route, options = {}) => {
      const response = await request(route, { ...options, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers } })
      assert.equal(response.status, 200, `${route}: HTTP ${response.status}`)
      const payload = await response.json(); assert.equal(payload.code, '0', `${route}: ${payload.message}`); return payload.data
    }
    await check('Compose isolation and production defaults', async () => {
      const rendered = JSON.parse(await compose(['config', '--format', 'json']))
      for (const name of ['mysql', 'redis', 'erp-server']) assert(!rendered.services[name].ports?.length, `${name} must not publish ports`)
      assert.equal(rendered.services.web.ports[0].host_ip, '127.0.0.1')
      assert.equal(rendered.services['erp-server'].environment.SPRING_PROFILES_ACTIVE, 'prod')
      assert.notEqual(rendered.services['erp-server'].environment.ERP_RATE_LIMIT_ENABLED, 'false')
      await writeFile(path.join(evidence, 'compose-redacted.json'), redact(JSON.stringify(rendered, null, 2)))
      return { onlyWebPublished: true, prodProfile: true }
    })
    await check('Build accepted artifacts and start MySQL Redis backend and Nginx', async () => {
      await compose(['build']); await compose(['up', '-d', '--wait', '--wait-timeout', '300'])
      const manifest = JSON.parse(await readFile(path.join(work, 'deployment-manifest.json')))
      assert.equal((await compose(['exec', '-T', 'erp-server', 'sha256sum', '/app/erp-server.jar'])).split(/\s/)[0], manifest.jarSha256)
      const nginxUser = (await compose(['exec', '-T', 'web', 'id', '-u'])).trim(); assert.notEqual(nginxUser, '0')
      report.images = await compose(['images', '--format', 'json'])
      return { jarSha256: manifest.jarSha256, nginxNonRoot: true }
    })
    await check('Static bytes deep links cache rules and missing assets', async () => {
      const index = await readFile(path.join(work, 'frontend/artifact/dist/index.html'))
      for (const route of ['/', '/sales/orders', '/finance/periods']) {
        const response = await request(route); assert.equal(response.status, 200)
        assert.match(response.headers.get('cache-control') || '', /no-store/)
        assert.equal(hash(Buffer.from(await response.arrayBuffer())), hash(index))
      }
      const frontend = JSON.parse(await readFile(path.join(work, 'frontend/artifact/manifest.json')))
      const asset = frontend.files.find(file => /^dist\/assets\/.+\.js$/.test(file.path))
      const response = await request('/' + asset.path.slice(5)); assert.equal(response.status, 200)
      assert.match(response.headers.get('cache-control') || '', /immutable/)
      assert.match(response.headers.get('content-type') || '', /javascript/)
      assert.equal(hash(Buffer.from(await response.arrayBuffer())), asset.sha256)
      assert.equal((await request('/assets/missing.js')).status, 404)
      const deployed = await (await request('/__deployment.json')).json()
      assert.equal(deployed.applicationRevision, verified.applicationRevision)
      assert.equal(deployed.toolRevision, verified.toolRevision)
      return { immutableAssets: true, uncachedHtml: true, missingAssetStatus: 404 }
    })
    await check('Production API authentication and proxy error boundaries', async () => {
      const unauthenticated = await request('/api/system/profile'); assert.equal(unauthenticated.status, 401)
      assert.equal((await unauthenticated.json()).code, '401')
      assert.equal((await request('/actuator/prometheus')).status, 404)
      assert.equal((await request('/readyz')).status, 200)
      const login = await api('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'admin', password: config.ERP_BOOTSTRAP_ADMIN_PASSWORD }) })
      token = login.accessToken; assert(token); secrets.push(token)
      await api('/api/system/profile')
      const missing = await request('/api/nonexistent-deployment-route', { headers: { Authorization: `Bearer ${token}` } })
      assert.equal(missing.status, 404); assert.match(missing.headers.get('content-type') || '', /json/)
      return { authenticated: true, anonymousStatus: 401, missingApiStatus: 404, publicActuatorStatus: 404 }
    })
    const attachment = Buffer.alloc(2 * 1024 * 1024, 'x')
    await check('Multipart attachment larger than 1 MB and download headers', async () => {
      const form = new FormData(); form.set('businessType', 'SALES_ORDER'); form.set('businessId', '910001')
      form.set('businessNo', 'DEPLOYMENT-SMOKE'); form.set('file', new Blob([attachment], { type: 'text/plain' }), 'deployment-attachment.txt')
      const uploaded = await api('/api/system/attachments', { method: 'POST', body: form }); attachmentId = uploaded.id
      const download = await request(`/api/system/attachments/${attachmentId}/download`, { headers: { Authorization: `Bearer ${token}` } })
      assert.equal(download.status, 200); assert.match(download.headers.get('content-disposition') || '', /attachment/)
      assert.equal(hash(Buffer.from(await download.arrayBuffer())), hash(attachment))
      return { bytes: attachment.length, sha256: hash(attachment), attachmentId }
    })
    await check('Browser login and refreshed business deep links', () => browserCheck(base, config.ERP_BOOTSTRAP_ADMIN_PASSWORD, directory, evidence, children))
    await check('Backend replacement changes IP without restarting Nginx or losing attachments', async () => {
      const old = (await compose(['ps', '-q', 'erp-server'])).trim()
      const web = (await compose(['ps', '-q', 'web'])).trim()
      const inspect = JSON.parse(await command('docker', ['inspect', old]))[0]
      const [network, endpoint] = Object.entries(inspect.NetworkSettings.Networks)[0]
      await compose(['stop', 'erp-server']); await compose(['rm', '-f', 'erp-server'])
      await command('docker', ['run', '-d', '--name', holder, '--network', network, '--ip', endpoint.IPAddress,
        '--entrypoint', 'sleep', 'nginx:1.28-alpine', '240'])
      await compose(['up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', 'erp-server'])
      const replacement = (await compose(['ps', '-q', 'erp-server'])).trim()
      const newInspect = JSON.parse(await command('docker', ['inspect', replacement]))[0]
      const newIp = newInspect.NetworkSettings.Networks[network].IPAddress
      assert.notEqual(newIp, endpoint.IPAddress)
      assert.equal((await compose(['ps', '-q', 'web'])).trim(), web)
      await until(async () => { const response = await request('/readyz'); assert.equal(response.status, 200) })
      await api('/api/system/profile')
      const response = await request(`/api/system/attachments/${attachmentId}/download`, { headers: { Authorization: `Bearer ${token}` } })
      assert.equal(response.status, 200); assert.equal(hash(Buffer.from(await response.arrayBuffer())), hash(attachment))
      await api(`/api/system/attachments/${attachmentId}`, { method: 'DELETE' })
      return { oldBackendIp: endpoint.IPAddress, newBackendIp: newIp, sameNginxContainer: true, attachmentPreserved: true }
    })
    verifyDeploymentBundle({ bundleDirectory: values.bundle, expectedManifestSha256: values['expected-manifest-sha256'] })
    report.status = 'PASSED'
  } catch (error) { report.status = 'FAILED'; report.failure = redact(error.message) }
  finally {
    cdpCleanup = await cleanupSmokeProcesses(children)
    report.browserCleanup = cdpCleanup
    try {
      const holderId = (await command('docker', ['ps', '-aq', '--filter', `name=^${holder}$`])).trim()
      if (holderId) await command('docker', ['rm', '-f', holderId])
      if (existsSync(path.join(work, '.env'))) {
        await compose(['logs', '--no-color', '--tail', '200'])
        await compose(['down', '--volumes', '--remove-orphans', '--rmi', 'local', '--timeout', '30'])
      }
      const containers = (await command('docker', ['ps', '-aq', '--filter', `label=com.docker.compose.project=${project}`])).trim()
      const volumes = (await command('docker', ['volume', 'ls', '-q', '--filter', `label=com.docker.compose.project=${project}`])).trim()
      const networks = (await command('docker', ['network', 'ls', '-q', '--filter', `label=com.docker.compose.project=${project}`])).trim()
      assert.equal(containers + volumes + networks, '')
      report.cleanupPassed = cdpCleanup.passed
      await rm(directory, { recursive: true, force: true })
    } catch (error) { report.cleanupPassed = false; report.cleanupFailure = redact(error.message) }
    if (!report.cleanupPassed) report.status = 'FAILED'
    report.finishedAt = new Date().toISOString(); await persist()
    process.removeListener('SIGINT', onSignal); process.removeListener('SIGTERM', onSignal)
  }
  assert.equal(report.status, 'PASSED', `Deployment smoke failed: ${report.failure || report.cleanupFailure}; see ${evidence}`)
  console.log(`[deployment] PASSED: ${evidence}`)
}

main().catch(error => { console.error(redact(error.message)); process.exitCode = 1 })

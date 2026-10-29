// Real Prometheus/Alertmanager/Grafana with a synthetic, isolated ERP target.
import assert from 'node:assert/strict'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { mkdir, mkdtemp, readFile, writeFile, rm } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const exec = promisify(execFile)
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
await mkdir(path.join(root, 'target'), { recursive: true })
const work = await mkdtemp(path.join(root, 'target/monitoring-runtime-'))
const project = `erp-monitoring-smoke-${process.pid}`
const log = path.join(work, 'compose.log')
const report = {
  sourceRevision: (await exec('git', ['rev-parse', 'HEAD'], { cwd: root })).stdout.trim(),
  dirtyWorktree: (await exec('git', ['status', '--porcelain'], { cwd: root })).stdout.trim().length > 0,
  environment: 'isolated synthetic monitoring fixture; not business signoff',
  checks: [],
}
const files = Object.fromEntries(['token', 'warning', 'critical', 'password'].map(k => [k, path.join(work, k)]))
await Promise.all([
  writeFile(files.token, 'monitoring-smoke-token'),
  writeFile(files.warning, 'http://fixture:8080/warning'),
  writeFile(files.critical, 'http://fixture:8080/critical'),
  writeFile(files.password, 'monitoring-smoke-password'),
])
// Test production timing separately with promtool. Accelerate only this fixture.
const prometheus = (await readFile(path.join(root, 'monitoring/prometheus.yml'), 'utf8'))
  .replaceAll('15s', '1s')
const rules = (await readFile(path.join(root, 'monitoring/alert-rules.yml'), 'utf8'))
  .replace(/interval: 30s/g, 'interval: 1s').replace(/for: \d+m/g, 'for: 2s')
const alertmanager = (await readFile(path.join(root, 'monitoring/alertmanager.yml'), 'utf8'))
  .replace('group_wait: 30s', 'group_wait: 1s').replace('group_interval: 5m', 'group_interval: 1s')
await Promise.all([
  writeFile(path.join(work, 'prometheus.yml'), prometheus),
  writeFile(path.join(work, 'rules.yml'), rules),
  writeFile(path.join(work, 'alertmanager.yml'), alertmanager),
])
const override = {
  services: {
    fixture: {
      image: 'python:3.14-alpine', command: ['python', '/fixture.py'],
      volumes: [`${root}/scripts/monitoring-fixture.py:/fixture.py:ro`],
      ports: ['127.0.0.1::8080'], networks: { default: { aliases: ['erp-server'] } },
    },
    prometheus: { volumes: [
      `${work}/prometheus.yml:/etc/prometheus/prometheus.yml:ro`,
      `${work}/rules.yml:/etc/prometheus/rules/alert-rules.yml:ro`,
    ] },
    alertmanager: { volumes: [`${work}/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro`] },
  },
  networks: { default: { internal: true } },
}
const overridePath = path.join(work, 'compose.json')
await writeFile(overridePath, JSON.stringify(override))
const environment = {
  ...process.env,
  ERP_MONITORING_TOKEN_FILE: files.token,
  ERP_MONITORING_WARNING_WEBHOOK_FILE: files.warning,
  ERP_MONITORING_CRITICAL_WEBHOOK_FILE: files.critical,
  ERP_GRAFANA_PASSWORD_FILE: files.password,
  ERP_GRAFANA_ADMIN_USER: 'admin',
  ERP_PROMETHEUS_PORT: '0', ERP_ALERTMANAGER_PORT: '0', ERP_GRAFANA_PORT: '0',
}
const composeArgs = ['compose', '--project-directory', root, '-p', project,
  '-f', path.join(root, 'docker-compose.monitoring.yml'), '-f', overridePath]
async function compose(...args) {
  const { stdout, stderr } = await exec('docker', [...composeArgs, ...args], {
    cwd: root, env: environment, timeout: 300_000, maxBuffer: 8 * 1024 * 1024,
  })
  await writeFile(log, stdout + stderr, { flag: 'a' })
  return stdout
}
const address = (service, port) => `http://${service}:${port}`
async function json(url, options = {}) {
  // Docker internal networks deliberately do not publish host ports. Run the
  // HTTP client inside the fixture so the entire test remains without egress.
  const request = JSON.stringify({ url, ...options })
  const client = 'import json,sys,urllib.request; r=json.loads(sys.argv[1]); data=r.get("body"); req=urllib.request.Request(r["url"],data=None if data is None else data.encode(),headers=r.get("headers",{}),method=r.get("method","GET")); print(urllib.request.urlopen(req,timeout=5).read().decode())'
  const { stdout } = await exec('docker', [...composeArgs, 'exec', '-T', 'fixture', 'python', '-c', client, request], {
    cwd: root, env: environment, timeout: 10_000, maxBuffer: 1024 * 1024,
  })
  return JSON.parse(stdout)
}
async function eventually(name, check, timeout = 90_000) {
  const deadline = Date.now() + timeout
  let error
  while (Date.now() < deadline) {
    try {
      await check()
      report.checks.push({ name, passed: true })
      console.log(`PASS ${name}`)
      return
    } catch (failure) { error = failure }
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  throw new Error(`${name}: ${error?.message}`)
}
try {
  console.log(`Starting isolated monitoring stack ${project}`)
  await compose('up', '-d', '--wait', '--wait-timeout', '90')
  const [prom, fixture, grafana] = await Promise.all([
    address('prometheus', 9090), address('fixture', 8080), address('grafana', 3000),
  ])
  const query = async expr => (await json(`${prom}/api/v1/query?query=${encodeURIComponent(expr)}`)).data.result
  const change = body => json(`${fixture}/state`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body),
  })
  const received = async (receiver, name, status) => {
    const events = await json(`${fixture}/events`)
    assert(events.some(event => event.receiver === receiver &&
      event.payload.alerts?.some(alert => alert.labels.alertname === name && alert.status === status)))
  }
  await eventually('JWT scrape and four business checks', async () => {
    assert.equal((await query('up{job="erp-server"}'))[0]?.value[1], '1')
    assert.equal((await query('erp_business_health_check_count')).length, 4)
  })
  const auth = { authorization: `Basic ${Buffer.from('admin:monitoring-smoke-password').toString('base64')}` }
  await eventually('Grafana dashboard provisioned and data source queries metrics', async () => {
    const dashboard = await json(`${grafana}/api/dashboards/uid/erp-business-health`, { headers: auth })
    assert.equal(dashboard.dashboard.panels.length, 7)
    const result = await json(`${grafana}/api/datasources/proxy/uid/erp-prometheus/api/v1/query?query=erp_business_health_overall_status`, { headers: auth })
    assert.equal(result.data.result[0]?.value[1], '0')
  })
  await change({ warn: true })
  await eventually('Warning fires and reaches local warning receiver', () => received('/warning', 'ErpRecentImportFailures', 'firing'))
  await eventually('Grafana displays Prometheus active alerts', async () => {
    const result = await json(`${grafana}/api/datasources/proxy/uid/erp-prometheus/api/v1/query?query=${encodeURIComponent('ALERTS{alertname="ErpRecentImportFailures",alertstate="firing"}')}`, { headers: auth })
    assert.equal(result.data.result.length, 1)
  })
  await change({ warn: false })
  await eventually('Warning recovery reaches local receiver', () => received('/warning', 'ErpRecentImportFailures', 'resolved'))
  await change({ down: true })
  await eventually('Unavailable target fires critical and reaches local critical receiver', () => received('/critical', 'ServiceDown', 'firing'))
  await change({ down: false })
  await eventually('Critical recovery reaches local receiver', () => received('/critical', 'ServiceDown', 'resolved'))
  await change({ token: 'monitoring-smoke-rotated-token' })
  await writeFile(files.token, 'monitoring-smoke-rotated-token')
  await compose('up', '-d', '--force-recreate', '--no-deps', 'prometheus')
  const rotatedProm = await address('prometheus', 9090)
  await eventually('JWT file rotation and container recreation restore scrape', async () => {
    const result = await json(`${rotatedProm}/api/v1/query?query=${encodeURIComponent('up{job="erp-server"}')}`)
    assert.equal(result.data.result[0]?.value[1], '1')
  })
  report.passed = true
} catch (error) {
  report.passed = false
  report.failure = error.message
  process.exitCode = 1
  console.error(error.message)
} finally {
  try { await compose('logs', '--no-color') } catch { /* preserve original failure */ }
  try { await compose('down', '--volumes', '--remove-orphans', '--timeout', '10') }
  catch (error) { report.cleanupFailure = error.message; report.passed = false; process.exitCode = 1 }
  report.checkedAt = new Date().toISOString()
  await writeFile(path.join(work, 'report.json'), JSON.stringify(report, null, 2) + '\n')
  await Promise.all(Object.values(files).map(file => rm(file, { force: true })))
  console.log(`Monitoring evidence: ${work}`)
}

// Rehearse a previously accepted JAR using real prod services in an isolated network.
// No existing environment URL, credentials, container, or database is accepted.
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createHash, randomBytes } from 'node:crypto'
import { mkdir, mkdtemp, readFile, writeFile, appendFile, copyFile, readdir, realpath, rm } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const backend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repo = path.dirname(backend)
const sha = bytes => createHash('sha256').update(bytes).digest('hex')
const secrets = []
const secret = () => { const value = randomBytes(32).toString('hex'); secrets.push(value); return value }
const redact = value => secrets.reduce((text, value) => text.replaceAll(value, '[REDACTED]'), String(value))
// Ignore ambient deployment overrides, including COMPOSE_FILE and application JSON.
const environment = Object.fromEntries(Object.entries(process.env).filter(([key]) =>
  !/^(ERP_|MYSQL_|REDIS_|COMPOSE_|GF_|SPRING_|JAVA_)/.test(key)))

async function run(command, args, { input, timeout = 300_000, allowFailure = false } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd: backend, env: environment, stdio: ['pipe', 'pipe', 'pipe'] })
    let stdout = '', stderr = '', timedOut = false
    const timer = setTimeout(() => { timedOut = true; child.kill('SIGKILL') }, timeout)
    child.stdout.on('data', data => { stdout += data })
    child.stderr.on('data', data => { stderr += data })
    child.stdin.on('error', () => {})
    child.on('error', error => { clearTimeout(timer); reject(error) })
    child.on('close', code => {
      clearTimeout(timer)
      if ((code !== 0 || timedOut) && !allowFailure) {
        // Do not include command arguments or stdin, which may contain tokens.
        reject(new Error(redact(`${command} failed (${timedOut ? 'timeout' : code}): ${stderr || stdout}`)))
      } else resolve({ code, stdout, stderr })
    })
    child.stdin.end(input)
  })
}

async function main() {
  assert.equal(process.argv.length, 4, 'Usage: node scripts/production-rehearsal.mjs /accepted/candidate-directory /new/evidence-directory')
  const candidate = await realpath(process.argv[2])
  const evidenceRequested = path.resolve(process.argv[3])
  await mkdir(path.dirname(evidenceRequested), { recursive: true })
  const evidence = path.join(await realpath(path.dirname(evidenceRequested)), path.basename(evidenceRequested))
  assert(!evidence.startsWith(`${repo}/`), 'Evidence must be outside the repository')
  assert.notEqual(evidence, repo)
  // Validate provenance before creating resources or the output directory.
  const accepted = JSON.parse(await readFile(path.join(candidate, 'summary.json')))
  const artifacts = JSON.parse(await readFile(path.join(candidate, 'artifact-sha256.json')))
  const source = JSON.parse(await readFile(path.join(candidate, 'source-sha256.json')))
  assert.equal(accepted.status, 'PASSED', 'Candidate must have passed its local release gates')
  assert.equal(source.head, accepted.commit)
  assert.equal(source.dirtyWorktree, false)
  const jar = path.join(candidate, 'erp-server-1.0.0.jar')
  const jarHash = sha(await readFile(jar))
  assert.equal(jarHash, artifacts['erp-server-1.0.0.jar'], 'Candidate JAR hash mismatch')
  // Reuse is valid only while all packaged sources/resources/build inputs match.
  const packaged = (await run('git', ['ls-files', '-z', 'src/main', 'pom.xml'])).stdout.split('\0').filter(Boolean)
  const expectedPackaged = Object.keys(source.files).filter(file => file === 'backend/pom.xml' || file.startsWith('backend/src/main/'))
  assert.deepEqual(packaged.map(file => `backend/${file}`).sort(), expectedPackaged.sort())
  for (const file of packaged) assert.equal(sha(await readFile(path.join(backend, file))), source.files[`backend/${file}`], `Packaged input changed: ${file}`)
  const revision = (await run('git', ['rev-parse', 'HEAD'])).stdout.trim()
  assert.equal((await run('git', ['status', '--porcelain'])).stdout.trim(), '', 'Commit rehearsal changes before collecting evidence')
  await mkdir(evidence, { mode: 0o700 }) // Deliberately refuse an existing directory.
  await mkdir(path.join(backend, 'target'), { recursive: true })
  const work = await mkdtemp(path.join(backend, 'target/production-rehearsal-'))
  const project = `erp-rehearsal-${randomBytes(6).toString('hex')}`
  const image = `${project}:candidate`
  const composeFile = path.join(work, 'compose.json')
  const composeArgs = ['compose', '--project-directory', backend, '-p', project, '-f', composeFile]
  const report = {
    status: 'RUNNING', startedAt: new Date().toISOString(), sourceRevision: revision, dirtyWorktree: false,
    candidateRevision: accepted.commit, candidateDirectory: candidate, jarSha256: jarHash, project,
    scope: 'Local isolated prod profile; synthetic business data; not target-environment or business signoff',
    limitations: ['No historical production data or previous-version image', 'No TLS/reverse-proxy or capacity acceptance',
      'Database restore only: attachments and external object storage are not backed up',
      'Local notification receiver only; no real recipient or human signoff'],
    checks: [],
  }
  let configured = false, configuration, token, stopped = false
  const persist = () => writeFile(path.join(evidence, 'report.json'), JSON.stringify(report, null, 2) + '\n')
  const record = async (name, action) => {
    if (stopped) throw new Error('Interrupted')
    console.log(`[rehearsal] ${name}`)
    const start = Date.now()
    try {
      const details = await action()
      report.checks.push({ name, passed: true, elapsedMs: Date.now() - start, ...details })
      await persist()
    } catch (error) {
      report.checks.push({ name, passed: false, failure: redact(error.message) })
      throw error
    }
  }
  const compose = async (args, options = {}) => {
    const result = await run('docker', [...composeArgs, ...args], options)
    await appendFile(path.join(evidence, 'compose.log'), redact(result.stdout + result.stderr))
    return result.stdout
  }
  const client = `import json,sys,urllib.request,urllib.error
r=json.load(sys.stdin)
b=r.get('body')
req=urllib.request.Request(r['url'],data=None if b is None else json.dumps(b).encode(),headers=r.get('headers',{}),method=r.get('method','GET'))
try:
    response=urllib.request.urlopen(req,timeout=10)
except urllib.error.HTTPError as error:
    response=error
print(json.dumps({'status':response.status,'body':response.read().decode()}))`
  const http = async (url, { method = 'GET', body, headers = {} } = {}) => {
    const result = await run('docker', [...composeArgs, 'exec', '-T', 'probe', 'python', '-c', client], {
      input: JSON.stringify({ url, method, body, headers: { 'content-type': 'application/json', ...headers } }), timeout: 20_000,
    })
    return JSON.parse(result.stdout)
  }
  const json = async (url, options = {}) => {
    const response = await http(url, options)
    assert.equal(response.status, 200, `${url}: HTTP ${response.status}; ${redact(response.body).slice(0, 300)}`)
    return JSON.parse(response.body)
  }
  const app = async (route, options = {}) => {
    const result = await json(`http://erp-server:8080${route}`, {
      ...options, headers: { authorization: `Bearer ${token}`, ...options.headers },
    })
    assert.equal(result.code, '0', `${route}: ${result.message}`)
    return result.data
  }
  const eventually = async (check, timeout = 90_000) => {
    const until = Date.now() + timeout
    let last
    while (Date.now() < until) {
      if (stopped) throw new Error('Interrupted')
      try { return await check() } catch (error) { last = error }
      await new Promise(resolve => setTimeout(resolve, 3000))
    }
    throw last
  }
  const signal = () => { stopped = true }
  process.on('SIGINT', signal)
  process.on('SIGTERM', signal)
  const sql = async (service, statement) => (await run('docker', [...composeArgs, 'exec', '-T', service,
    'mysql', '--defaults-extra-file=/rehearsal/mysql.cnf', '--batch', '--raw', '--skip-column-names', 'erp_server'],
  { input: statement })).stdout.trim()
  const snapshot = async service => {
    const tables = (await sql(service, 'SHOW TABLES;')).split('\n').filter(Boolean)
    assert(tables.length > 100, 'Expected migrated ERP schema')
    const quoted = tables.map(table => { assert(/^[A-Za-z0-9_]+$/.test(table)); return `\`${table}\`` })
    const counts = await sql(service, quoted.map((table, i) => `SELECT '${tables[i]}', COUNT(*) FROM ${table};`).join('\n'))
    const dump = await run('docker', [...composeArgs, 'exec', '-T', service, 'mysqldump',
      '--defaults-extra-file=/rehearsal/mysql.cnf', '--no-create-info', '--skip-comments', '--skip-dump-date',
      '--skip-extended-insert', '--order-by-primary', '--skip-add-locks', '--skip-disable-keys', '--hex-blob',
      '--set-gtid-purged=OFF', 'erp_server'])
    return { tableCount: tables.length, rowCounts: counts, dataSha256: sha(dump.stdout) }
  }
  try {
    await record('Candidate provenance and unchanged packaged sources', async () => ({ packagedFiles: packaged.length }))
    const config = Object.fromEntries((await readFile(path.join(backend, '.env.prod.example'), 'utf8'))
      .split(/\r?\n/).filter(line => /^[A-Z][A-Z0-9_]*=/.test(line)).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]))
    Object.assign(config, { MYSQL_ROOT_PASSWORD: secret(), MYSQL_PASSWORD: secret(), ERP_REDIS_PASSWORD: secret(),
      ERP_JWT_SECRET: secret(), ERP_BOOTSTRAP_ADMIN_PASSWORD: secret(), ERP_CORS_ALLOWED_ORIGINS: 'https://erp-rehearsal.invalid' })
    config.ERP_DATASOURCE_PASSWORD = config.MYSQL_PASSWORD
    const envFile = path.join(work, 'runtime.env')
    await writeFile(envFile, Object.entries(config).map(([key, value]) => `${key}=${value}`).join('\n'), { mode: 0o600 })
    const tokenFile = path.join(work, 'metrics-token')
    const warningFile = path.join(work, 'warning-url')
    const criticalFile = path.join(work, 'critical-url')
    const passwordFile = path.join(work, 'grafana-password')
    const grafanaPassword = secret()
    // Compose secrets are bind mounts; files must be readable by container UIDs.
    // Their parent directory is private and is removed in finally.
    await writeFile(tokenFile, 'pending-login')
    await writeFile(warningFile, 'http://probe:8080/warning')
    await writeFile(criticalFile, 'http://probe:8080/critical')
    await writeFile(passwordFile, grafanaPassword)
    await writeFile(path.join(work, 'mysql.cnf'), `[client]\nuser=root\npassword=${config.MYSQL_ROOT_PASSWORD}\nhost=127.0.0.1\n`, { mode: 0o600 })
    Object.assign(environment, { ERP_COMPOSE_ENV_FILE: envFile, ERP_MONITORING_TOKEN_FILE: tokenFile,
      ERP_MONITORING_WARNING_WEBHOOK_FILE: warningFile, ERP_MONITORING_CRITICAL_WEBHOOK_FILE: criticalFile,
      ERP_GRAFANA_PASSWORD_FILE: passwordFile })
    configuration = JSON.parse((await run('docker', ['compose', '--project-directory', backend, '--env-file', envFile,
      '--profile', 'core', '-p', project, '-f', 'docker-compose.yml', '-f', 'docker-compose.monitoring.yml', 'config', '--format', 'json'])).stdout)
    for (const service of Object.values(configuration.services)) {
      delete service.container_name
      delete service.ports
      delete service.profiles
      service.restart = 'no'
    }
    configuration.networks = { default: { name: `${project}_default`, internal: true } }
    for (const [key, volume] of Object.entries(configuration.volumes)) {
      assert(!volume.external, 'External volumes are forbidden')
      volume.name = `${project}_${key}`
    }
    const services = configuration.services
    services['erp-server'].image = image
    delete services['erp-server'].build
    // Bound memory on development Docker hosts; do not disable production safeguards.
    services['erp-server'].mem_limit = '1536m'
    services.mysql.volumes.push({ type: 'bind', source: work, target: '/rehearsal' })
    const backupDir = path.join(evidence, 'backup')
    await mkdir(backupDir, { mode: 0o700 })
    services.mysql.volumes.push({ type: 'bind', source: backupDir, target: '/backups' },
      { type: 'bind', source: path.join(backend, 'scripts'), target: '/scripts', read_only: true })
    services['mysql-restore'] = structuredClone(services.mysql)
    services['mysql-restore'].volumes = services['mysql-restore'].volumes.map(volume => volume.type === 'volume'
      ? { ...volume, source: 'restore-mysql-data' } : volume)
    services['redis-restore'] = structuredClone(services.redis)
    services['redis-restore'].volumes = services['redis-restore'].volumes.map(volume => ({ ...volume, source: 'restore-redis-data' }))
    configuration.volumes['restore-mysql-data'] = { name: `${project}_restore-mysql-data` }
    configuration.volumes['restore-redis-data'] = { name: `${project}_restore-redis-data` }
    services.probe = { image: 'python:3.14-alpine', command: ['python', '/receiver.py'],
      volumes: [{ type: 'bind', source: path.join(backend, 'scripts/monitoring-fixture.py'), target: '/receiver.py', read_only: true }],
      networks: { default: null } }
    await writeFile(composeFile, JSON.stringify(configuration, null, 2), { mode: 0o600 })
    configured = true
    await record('Isolated production configuration', async () => {
      const resolved = JSON.parse((await run('docker', [...composeArgs, 'config', '--format', 'json'])).stdout)
      await writeFile(path.join(evidence, 'compose.redacted.json'), redact(JSON.stringify(resolved, null, 2)) + '\n')
      assert(resolved.networks.default.internal)
      assert(Object.values(resolved.services).every(service => !service.ports?.length && !service.container_name))
      const appEnv = resolved.services['erp-server'].environment
      assert.equal(appEnv.SPRING_PROFILES_ACTIVE, 'prod')
      assert.notEqual(appEnv.ERP_RATE_LIMIT_ENABLED, 'false')
      assert(!appEnv.SPRING_APPLICATION_JSON)
      assert.equal(appEnv.ERP_PRINCIPAL_CACHE_INVALIDATION_MODE, 'redis')
      return { prodProfile: true, rateLimitDefaultPreserved: true, internalNetwork: true, publishedPorts: 0 }
    })
    await record('Build accepted JAR with the production prebuilt Dockerfile', async () => {
      const context = path.join(work, 'build')
      await mkdir(path.join(context, 'docker-dist'), { recursive: true })
      await copyFile(path.join(backend, 'Dockerfile.prebuilt'), path.join(context, 'Dockerfile'))
      await copyFile(jar, path.join(context, 'docker-dist/erp-server-1.0.0.jar'))
      const build = await run('docker', ['build', '-t', image, context], { timeout: 600_000 })
      await writeFile(path.join(evidence, 'build.log'), redact(build.stdout + build.stderr))
      return { imageId: (await run('docker', ['image', 'inspect', '--format', '{{.Id}}', image])).stdout.trim() }
    })
    await record('MySQL, Redis and ERP prod startup with Flyway', async () => {
      await compose(['up', '-d', '--wait', '--wait-timeout', '240', 'mysql', 'redis', 'probe', 'erp-server'])
      const response = await json('http://erp-server:8080/actuator/health')
      assert.equal(response.status, 'UP')
      assert.equal(await sql('mysql', 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;'), '0')
      const migrations = await sql('mysql', 'SELECT COUNT(*), MAX(installed_rank) FROM flyway_schema_history;')
      assert.equal(await sql('mysql', "SELECT config_value FROM sys_config WHERE config_code='erp.bootstrap.admin-password-initialized';"), 'true')
      assert.notEqual((await run('docker', [...composeArgs, 'exec', '-T', 'erp-server', 'id', '-u'])).stdout.trim(), '0')
      assert.equal((await run('docker', [...composeArgs, 'exec', '-T', 'erp-server', 'sha256sum', '/app/erp-server.jar'])).stdout.split(/\s+/)[0], jarHash)
      return { migrations, nonRoot: true, adminBootstrapInitialized: true }
    })
    const login = async () => {
      const result = await json('http://erp-server:8080/api/auth/login', { method: 'POST', body: { username: 'admin', password: config.ERP_BOOTSTRAP_ADMIN_PASSWORD } })
      assert.equal(result.code, '0')
      assert(result.data.accessToken)
      secrets.push(result.data.accessToken, result.data.refreshToken)
      token = result.data.accessToken
      return result.data
    }
    await record('Real JWT authentication and production endpoint exposure', async () => {
      await login()
      assert.equal((await app('/api/auth/user-info')).username, 'admin')
      for (const headers of [{}, { authorization: 'Bearer invalid' }, { authorization: `Basic ${Buffer.from(`admin:${config.ERP_BOOTSTRAP_ADMIN_PASSWORD}`).toString('base64')}` }]) {
        assert.equal((await http('http://erp-server:8080/actuator/prometheus', { headers })).status, 401)
      }
      const docs = await http('http://erp-server:8080/v3/api-docs', { headers: { authorization: `Bearer ${token}` } })
      assert.equal(docs.status, 404)
      const metrics = await http('http://erp-server:8080/actuator/prometheus', { headers: { authorization: `Bearer ${token}` } })
      assert.equal(metrics.status, 200)
      assert(metrics.body.includes('jvm_memory_used_bytes'))
      assert(metrics.body.includes('erp_business_health_check_count'))
      await writeFile(tokenFile, token)
      return { anonymous: 401, invalidJwt: 401, basicAuth: 401, authenticatedMetrics: 200, openapi: 404 }
    })
    const query = async expression => (await json(`http://prometheus:9090/api/v1/query?query=${encodeURIComponent(expression)}`)).data.result
    await record('Canonical Prometheus and Grafana scrape the real ERP', async () => {
      await compose(['up', '-d', '--wait', '--wait-timeout', '90', 'prometheus', 'alertmanager', 'grafana'])
      const business = await app('/api/system/observability/business-health')
      await eventually(async () => {
        assert.equal((await query('up{job="erp-server"}'))[0]?.value[1], '1')
        const counts = await query('erp_business_health_check_count{job="erp-server"}')
        assert.equal(counts.length, 4)
        for (const check of business.checks) assert.equal(Number(counts.find(metric => metric.metric.check === check.code)?.value[1]), check.count)
      })
      const headers = { authorization: `Basic ${Buffer.from(`admin:${grafanaPassword}`).toString('base64')}` }
      await eventually(async () => {
        const dashboard = await json('http://grafana:3000/api/dashboards/uid/erp-business-health', { headers })
        assert.equal(dashboard.dashboard.panels.length, 7)
        const result = await json('http://grafana:3000/api/datasources/proxy/uid/erp-prometheus/api/v1/query?query=up%7Bjob%3D%22erp-server%22%7D', { headers })
        assert.equal(result.data.result[0]?.value[1], '1')
      })
      return { checks: business.checks, overallStatus: business.overallStatus, syntheticMetrics: false, acceleratedTimers: false }
    })
    const createCustomer = code => app('/api/masterdata/customers', { method: 'POST', body: {
      customerCode: code, customerName: `隔离恢复演练 🧪 ${code}`, customerType: 'ENTERPRISE', settlementMethod: 'CASH', creditLimit: 0, status: 'ACTIVE',
    } })
    let baseline, before, after
    await record('Persist business data and take a verified database backup', async () => {
      before = await createCustomer('REHEARSAL_BEFORE_BACKUP')
      assert(before.id)
      await compose(['stop', '-t', '45', 'erp-server'])
      baseline = await snapshot('mysql')
      await writeFile(path.join(evidence, 'database-before-backup.json'), JSON.stringify(baseline, null, 2) + '\n')
      await compose(['exec', '-T', 'mysql', 'bash', '-c',
        'export MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USER=root MYSQL_PASSWORD="$MYSQL_ROOT_PASSWORD" BACKUP_DIR=/backups; exec bash /scripts/backup-database.sh rehearsal'])
      return { tableCount: baseline.tableCount, dataSha256: baseline.dataSha256 }
    })
    const backups = (await readdir(backupDir)).filter(file => file.endsWith('.sql.gz'))
    assert.equal(backups.length, 1)
    const backupFile = `/backups/${backups[0]}`
    await record('Post-backup business change is present before recovery', async () => {
      await compose(['start', '--wait', '--wait-timeout', '180', 'erp-server'])
      after = await createCustomer('REHEARSAL_AFTER_BACKUP')
      assert(after.id)
      assert.equal((await app(`/api/masterdata/customers/${before.id}`)).customerName, before.customerName)
      await compose(['stop', '-t', '45', 'erp-server'])
      assert.notEqual((await snapshot('mysql')).dataSha256, baseline.dataSha256)
      return { beforeCustomerId: before.id, afterCustomerId: after.id }
    })
    const restore = (file, allowFailure = false) => run('docker', [...composeArgs, 'exec', '-T', 'mysql-restore', 'bash', '-c',
      'export MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USER=root MYSQL_PASSWORD="$MYSQL_ROOT_PASSWORD"; exec bash /scripts/restore-database.sh "$1"', 'restore', file],
    { input: 'YES\n', allowFailure })
    await record('Corrupted backup is rejected without writing the restore database', async () => {
      await compose(['up', '-d', '--wait', '--wait-timeout', '180', 'mysql-restore', 'redis-restore'])
      assert.equal(await sql('mysql-restore', 'SHOW TABLES;'), '')
      const badFile = path.join(backupDir, 'corrupted.sql.gz')
      await writeFile(badFile, 'intentionally corrupted rehearsal archive')
      await writeFile(`${badFile}.sha256`, `${'0'.repeat(64)}  /backups/corrupted.sql.gz\n`)
      const result = await restore('/backups/corrupted.sql.gz', true)
      assert.notEqual(result.code, 0)
      assert.match(result.stdout + result.stderr, /校验和.*失败/)
      assert.equal(await sql('mysql-restore', 'SHOW TABLES;'), '')
      await writeFile(path.join(evidence, 'restore-rejected.log'), redact(result.stdout + result.stderr))
      await rm(badFile)
      await rm(`${badFile}.sha256`)
    })
    await record('Restore into a separate MySQL and compare every table', async () => {
      const result = await restore(backupFile)
      await writeFile(path.join(evidence, 'restore.log'), redact(result.stdout + result.stderr))
      const restored = await snapshot('mysql-restore')
      assert.deepEqual(restored, baseline, 'Restored table counts/data differ from the stopped source snapshot')
      await writeFile(path.join(evidence, 'database-restored.json'), JSON.stringify(restored, null, 2) + '\n')
      return { tableCount: restored.tableCount, dataSha256: restored.dataSha256 }
    })
    const received = async status => {
      const events = await json('http://probe:8080/events')
      assert(events.some(event => event.receiver === '/critical' && event.payload.alerts?.some(alert =>
        alert.labels.alertname === 'ServiceDown' && alert.status === status)))
      return events
    }
    await record('Real service outage reaches the local critical receiver at production timings', async () => {
      await eventually(() => received('firing'), 210_000)
    })
    await record('Restart the accepted application on restored MySQL and fresh Redis', async () => {
      configuration.services['erp-server'].environment.ERP_DATASOURCE_URL = config.ERP_DATASOURCE_URL.replace('mysql:3306', 'mysql-restore:3306')
      configuration.services['erp-server'].environment.ERP_REDIS_HOST = 'redis-restore'
      configuration.services['erp-server'].depends_on = { 'mysql-restore': { condition: 'service_healthy' }, 'redis-restore': { condition: 'service_healthy' } }
      await writeFile(composeFile, JSON.stringify(configuration, null, 2), { mode: 0o600 })
      await compose(['up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '240', 'erp-server'])
      await login()
      await writeFile(tokenFile, token) // Canonical bearer_token_file is reread on subsequent scrapes.
      assert.equal((await app(`/api/masterdata/customers/${before.id}`)).customerName, before.customerName)
      const customers = await app('/api/masterdata/customers?pageNo=1&pageSize=200')
      assert(!customers.records.some(customer => customer.customerCode === after.customerCode))
      assert.equal(await sql('mysql-restore', "SELECT COUNT(*) FROM md_customer WHERE customer_code='REHEARSAL_AFTER_BACKUP';"), '0')
      assert.equal(await sql('mysql', "SELECT COUNT(*) FROM md_customer WHERE customer_code='REHEARSAL_AFTER_BACKUP';"), '1')
      await eventually(async () => { assert.equal((await query('up{job="erp-server"}'))[0]?.value[1], '1') })
      return { baselineCustomerPreserved: true, laterCustomerAbsentInRestore: true, sourceDatabaseUntouched: true, jwtFileRotated: true }
    })
    await record('Production recovery notification reaches the local receiver', async () => {
      const events = await eventually(() => received('resolved'), 390_000)
      await writeFile(path.join(evidence, 'notification-events.json'), JSON.stringify(events, null, 2) + '\n')
    })
    await record('Candidate and working revision remain unchanged', async () => {
      assert.equal(sha(await readFile(jar)), jarHash)
      assert.equal((await run('git', ['rev-parse', 'HEAD'])).stdout.trim(), revision)
      assert.equal((await run('git', ['status', '--porcelain'])).stdout.trim(), '')
      const ids = await compose(['ps', '-q'])
      const inventory = JSON.parse((await run('docker', ['inspect', ...ids.trim().split(/\s+/)])).stdout)
      report.images = inventory.map(item => ({ service: item.Config.Labels['com.docker.compose.service'], image: item.Config.Image, imageId: item.Image }))
    })
    report.status = 'PASSED'
  } catch (error) {
    report.status = 'FAILED'
    report.failure = redact(error.message)
    console.error(report.failure)
    process.exitCode = 1
  } finally {
    if (configured) {
      try { await compose(['logs', '--no-color', '--tail', '500']) } catch (error) { report.logFailure = redact(error.message) }
      try {
        await compose(['down', '--volumes', '--remove-orphans', '--timeout', '30'])
        const filter = `label=com.docker.compose.project=${project}`
        for (const args of [['ps', '-aq', '--filter', filter], ['volume', 'ls', '-q', '--filter', filter], ['network', 'ls', '-q', '--filter', filter]]) {
          assert.equal((await run('docker', args)).stdout.trim(), '', 'Rehearsal resource cleanup incomplete')
        }
        report.cleanupPassed = true
      } catch (error) { report.cleanupFailure = redact(error.message); report.status = 'FAILED'; process.exitCode = 1 }
      await run('docker', ['image', 'rm', image], { allowFailure: true })
    }
    // Remove rendered env/config and credentials, including copied build context.
    await rm(work, { recursive: true, force: true })
    report.finishedAt = new Date().toISOString()
    await persist()
    process.removeListener('SIGINT', signal)
    process.removeListener('SIGTERM', signal)
    console.log(`[rehearsal] ${report.status}: ${evidence}`)
  }
}

main().catch(error => { console.error(redact(error.message)); process.exitCode = 1 })

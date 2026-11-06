import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import { cp, copyFile, mkdir, mkdtemp, readFile, readdir, realpath, rename, rm, writeFile, appendFile } from 'node:fs/promises'
import { existsSync, readFileSync, rmSync } from 'node:fs'
import { createServer } from 'node:net'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseArgs, parseEnv } from 'node:util'
import { setTimeout as delay } from 'node:timers/promises'
import { run } from './production-rehearsal.mjs'
import { verifyDeploymentBundle, initializeDeployment } from './deployment-bundle.mjs'
import { cleanupDisposableDocker } from './deployment-docker-cleanup.mjs'
import { prepareLegacyArtifact, verifyLegacyArtifact } from './deployment-recovery-legacy.mjs'
import { seedHistoricalBusiness, verifyHistoricalReads, verifyUpgradedApproval } from './deployment-recovery-business.mjs'
import { hash, legacyRevision, readJson, revisionPattern, recoveryProjectPattern, sealCheckpoint, verifyCheckpoint,
  validateCheckpoint, writeJson } from './recovery-checkpoint.mjs'

const script = fileURLToPath(import.meta.url)
const repo = path.resolve(path.dirname(script), '../..')
const privateDirectoryPattern = /^erp-recovery-work-[a-zA-Z0-9]{6}$/
const inventoryTables = ['inv_adjustment', 'inv_adjustment_line', 'inv_stock_check', 'inv_stock_check_line',
  'inv_balance', 'inv_lot_balance', 'inv_txn', 'fin_voucher', 'fin_voucher_entry']
export const recoveryChecks = [
  'Start the pinned V168 application on isolated services',
  'Create historical inventory and attachment fixtures through V168 APIs',
  'Stop writers and seal a joint database and attachment checkpoint',
  'Create post-checkpoint changes and remove an original attachment',
  'Reject damaged and mismatched backups before target writes',
  'Restore the exact V168 database and attachment snapshot into fresh volumes',
  'Upgrade the restored database to V169 using the accepted candidate',
  'Preserve historical inventory and configuration during migration',
  'Read restored documents and attachments and exclude later source changes',
  'Apply V169 approval rules to historical drafts and counted stock checks',
  'Keep source data isolated and recovered writes durable across restart'
]
const within = (parent, child) => parent === child || child.startsWith(parent + path.sep)

function requireHistoricalConfiguration(snapshot) {
  assert.match(snapshot.customMenu, /^9000000000001\tRECOVERY_CUSTOM_KEEP\t历史自定义权限\tcustom:recovery:keep\t0$/,
    'The checkpoint must contain the custom historical menu')
  assert.match(snapshot.existingApprovalConfigs, /\tPURCHASE_ORDER\t历史自定义采购审批 🧪\tACTIVE\t/,
    'The checkpoint must contain a persisted custom purchase approval configuration')
  return snapshot
}

export function configureRecoveryNetworks(configuration, project) {
  assert(recoveryProjectPattern.test(project || ''))
  for (const name of ['mysql', 'redis', 'erp-server']) {
    assert(!configuration.services[name].ports?.length, 'Only the web entry may publish a port')
    configuration.services[name].networks = { default: null }
  }
  configuration.networks = {
    default: { name: `${project}_default`, internal: true },
    // Docker internal bridges do not provide the host port ingress used by the
    // API driver. Only Nginx joins this separate, project-owned entry network.
    edge: { name: `${project}_edge`, internal: false }
  }
  configuration.services.web.networks = { default: null, edge: null }
}

export async function cleanupRecoveryEvidence({ evidenceDirectory, expectedRevision, docker, removePrivate = true }) {
  assert(revisionPattern.test(expectedRevision || ''), 'Provide the full expected revision')
  const file = path.join(evidenceDirectory, 'report.json')
  if (!existsSync(file)) return { passed: true, skipped: true, reason: 'Recovery did not publish a project before starting Docker' }
  const report = readJson(file)
  assert.equal(report.schemaVersion, 1)
  assert.equal(report.kind, 'erp-upgrade-recovery')
  assert.equal(report.runtimeToolRevision, expectedRevision)
  assert(recoveryProjectPattern.test(report.project || ''), 'Invalid recovery project')
  assert(privateDirectoryPattern.test(report.privateDirectory || ''), 'Invalid private recovery directory')
  const result = await cleanupDisposableDocker(report.project, docker)
  if (removePrivate) {
    try { rmSync(path.join(tmpdir(), report.privateDirectory), { recursive: true, force: true }) }
    catch (error) { result.passed = false; result.failures.push({ resource: 'private directory', error: error.message }) }
  }
  return result
}

export function verifyRecoveryEvidence({ bundleDirectory, expectedManifestSha256, evidenceDirectory, expectedRevision }) {
  assert(revisionPattern.test(expectedRevision || ''))
  const bundle = verifyDeploymentBundle({ bundleDirectory, expectedManifestSha256 })
  assert.equal(bundle.applicationRevision, expectedRevision)
  assert.equal(bundle.toolRevision, expectedRevision)
  assert(!existsSync(path.join(bundleDirectory, '.env')))
  const expectedJarSha256 = readJson(path.join(bundleDirectory, 'deployment-manifest.json')).jarSha256
  return validateRecoveryReports({ evidenceDirectory, expectedRevision, expectedManifestSha256, expectedJarSha256 })
}

export function validateRecoveryReports({ evidenceDirectory, expectedRevision, expectedManifestSha256, expectedJarSha256 }) {
  const report = readJson(path.join(evidenceDirectory, 'report.json'))
  assert.equal(report.schemaVersion, 1)
  assert.equal(report.kind, 'erp-upgrade-recovery')
  assert.equal(report.status, 'PASSED')
  assert(!report.failure && !report.cleanupFailure && !report.activeStep)
  assert(Number.isFinite(Date.parse(report.startedAt)) && Date.parse(report.finishedAt) >= Date.parse(report.startedAt))
  for (const key of ['applicationRevision', 'toolRevision', 'runtimeToolRevision']) assert.equal(report[key], expectedRevision)
  assert.equal(report.sourceRevision, legacyRevision)
  assert.equal(report.manifestSha256, expectedManifestSha256)
  assert(recoveryProjectPattern.test(report.project || '') && privateDirectoryPattern.test(report.privateDirectory || ''))
  assert.equal(report.cleanupPassed, true)
  assert.equal(report.privateFilesRemoved, true)
  assert.deepEqual(report.checks.map(c => c.name), recoveryChecks, 'Recovery checks are missing, duplicated or out of order')
  assert(report.checks.every(c => c.passed === true))
  const read = name => readJson(path.join(evidenceDirectory, name))
  const checkpointBytes = readFileSync(path.join(evidenceDirectory, 'backup-manifest.json'))
  assert.equal(hash(checkpointBytes), report.checkpointSha256)
  const checkpoint = validateCheckpoint(JSON.parse(checkpointBytes), { applicationRevision: expectedRevision, manifestSha256: expectedManifestSha256 })
  assert.equal(checkpoint.project, report.project)
  const legacyBytes = readFileSync(path.join(evidenceDirectory, 'legacy-artifact.json'))
  assert.equal(hash(legacyBytes), report.legacyManifestSha256)
  const legacy = JSON.parse(legacyBytes)
  assert.equal(legacy.schemaVersion, 1)
  assert.equal(legacy.kind, 'erp-historical-fixture')
  assert.equal(legacy.sourceRevision, legacyRevision)
  assert.equal(legacy.toolRevision, expectedRevision)
  assert.equal(legacy.jarSha256, report.sourceJarSha256)
  assert.equal(legacy.buildPassed, true)
  assert.equal(legacy.schema, '168')
  assert.equal(report.checks[0].schema, '168')
  assert.equal(report.checks[0].sourceRevision, legacyRevision)
  assert.equal(report.checks[0].jarSha256, legacy.jarSha256)
  assert.equal(report.checks[1].generatedThroughOldApplication, true)
  assert.equal(report.checks[2].writersStopped, true)
  assert.deepEqual(read('database-before-backup.json'), checkpoint.databaseSnapshot)
  assert.deepEqual(read('database-restored.json'), checkpoint.databaseSnapshot)
  assert.deepEqual(read('inventory-before-upgrade.json'), read('inventory-after-upgrade.json'))
  assert.deepEqual(requireHistoricalConfiguration(read('configuration-before-upgrade.json')),
    requireHistoricalConfiguration(read('configuration-after-upgrade.json')))
  assert.equal(hash(readFileSync(path.join(evidenceDirectory, 'attachments.json'))), checkpoint.artifacts.attachmentSnapshot.sha256)
  assert.deepEqual(read('attachments-restored.json'), read('attachments.json'))
  const attachments = read('attachments.json')
  assert.equal(attachments.schemaVersion, 1)
  assert.equal(attachments.kind, 'erp-attachment-snapshot')
  assert.equal(attachments.entries.filter(entry => entry.kind === 'file').length, 2)
  const negatives = report.checks[4]
  for (const key of ['databaseRejected', 'attachmentsRejected', 'mixedCheckpointRejected', 'targetsUntouched']) assert.equal(negatives[key], true)
  const upgrade = report.checks[6]
  assert.equal(upgrade.from, '168'); assert.equal(upgrade.to, '169'); assert.equal(upgrade.appliedOnce, true)
  assert.equal(upgrade.jarSha256, expectedJarSha256)
  const reads = report.checks[8]
  for (const key of ['postedPreserved', 'draftPreserved', 'cancelledPreserved', 'adjustedLinkPreserved', 'countedPreserved',
    'unicodeCustomerPreserved', 'originalAttachmentsRestored', 'laterRecordsAbsent']) assert.equal(reads[key], true)
  const approval = report.checks[9]
  for (const key of ['oldDraftRequiresApproval', 'selfApprovalRejected', 'duplicatePostingRejected', 'countWaitedForPosting']) assert.equal(approval[key], true)
  assert.deepEqual(approval.draftStock, { quantity: 12, amount: 120, reserved: 0 })
  assert.deepEqual(approval.countedStock, { quantity: 3, amount: 30, reserved: 0 })
  for (const key of ['sourceDatabaseUnchanged', 'sourceAttachmentsUnchanged', 'newAttachmentWritable', 'restartPassed']) assert.equal(report.checks[10][key], true)
  for (const name of ['.env', 'mysql.cnf', 'private-account.json', 'database.sql.gz', 'attachments.tar.gz']) assert(!existsSync(path.join(evidenceDirectory, name)), 'Private recovery inputs must not be archived')
  return { passed: true, applicationRevision: expectedRevision, sourceRevision: legacyRevision,
    manifestSha256: expectedManifestSha256, sourceSchema: '168', targetSchema: '169', checks: recoveryChecks.length,
    checkpointSha256: report.checkpointSha256, tableCount: checkpoint.databaseSnapshot.tableCount, cleanupPassed: true }
}

export async function runRecovery({ bundleDirectory, expectedManifestSha256, legacyDirectory, expectedLegacyManifestSha256, evidenceDirectory }) {
  const verified = verifyDeploymentBundle({ bundleDirectory, expectedManifestSha256 })
  const git = async args => (await run('git', args)).stdout.trim()
  const revision = await git(['rev-parse', 'HEAD'])
  assert.equal(await git(['status', '--porcelain']), '', 'Commit recovery tooling before collecting evidence')
  assert.equal(verified.applicationRevision, revision)
  assert.equal(verified.toolRevision, revision)
  const legacy = await verifyLegacyArtifact(legacyDirectory, expectedLegacyManifestSha256, revision)
  const requested = path.resolve(evidenceDirectory)
  await mkdir(path.dirname(requested), { recursive: true })
  const evidence = path.join(await realpath(path.dirname(requested)), path.basename(requested))
  assert(!within(repo, evidence) && !within(await realpath(bundleDirectory), evidence), 'Recovery evidence must be outside source and bundle')
  await mkdir(evidence, { mode: 0o700 })
  const directory = await realpath(await mkdtemp(path.join(tmpdir(), 'erp-recovery-work-')))
  const work = path.join(directory, 'bundle'), backups = path.join(directory, 'backups')
  const composeFile = path.join(directory, 'recovery-compose.json')
  const project = `erp-preview-recovery-${randomBytes(6).toString('hex')}`
  const secrets = []
  const rememberSecret = value => { if (value) secrets.push(value); return value }
  const redact = value => secrets.reduce((result, secret) => result.replaceAll(secret, '[REDACTED]'), String(value))
  const report = { schemaVersion: 1, kind: 'erp-upgrade-recovery', status: 'RUNNING', startedAt: new Date().toISOString(),
    ...verified, runtimeToolRevision: revision, sourceRevision: legacyRevision, sourceJarSha256: legacy.jarSha256,
    legacyManifestSha256: expectedLegacyManifestSha256, project, privateDirectory: path.basename(directory), checks: [],
    scope: 'Isolated upgrade from a pinned V168 application using synthetic historical data and joint DB/attachment recovery; not production data acceptance' }
  let interrupted = false, configured = false, config, configuration, base, adminToken, fixture, beforeAttachments,
    sourceAfterDatabase, sourceAfterAttachments, baseline, inventoryBefore, settingsBefore, checkpoint
  const signal = () => { interrupted = true }
  process.on('SIGINT', signal); process.on('SIGTERM', signal)
  const persist = async () => {
    const temp = path.join(evidence, 'report.json.tmp')
    await writeFile(temp, JSON.stringify(report, null, 2) + '\n', { mode: 0o600 })
    await rename(temp, path.join(evidence, 'report.json'))
  }
  const command = async (program, args, options = {}) => {
    try {
      const result = await run(program, args, { ...options, cwd: work })
      if (options.log !== false) await appendFile(path.join(evidence, 'commands.log'), redact(result.stdout + result.stderr))
      return result
    } catch (error) { throw new Error(redact(error.message)) }
  }
  const compose = async (args, options = {}) => command('docker', ['compose', '-p', project, '-f', composeFile, ...args], options)
  const updateCompose = async () => { await writeFile(composeFile, JSON.stringify(configuration, null, 2), { mode: 0o600 }); configured = true }
  const sql = async (service, statement) => (await compose(['exec', '-T', service, 'mysql', '--defaults-extra-file=/recovery/mysql.cnf',
    '--batch', '--raw', '--skip-column-names', 'erp_server'], { input: statement, log: false })).stdout.trim()
  const identifier = value => { assert(/^[a-zA-Z0-9_]+$/.test(value)); return '`' + value + '`' }
  const id = value => { assert(/^[1-9][0-9]{0,19}$/.test(String(value))); return String(value) }
  const snapshot = async (service, selected) => {
    const tables = selected || (await sql(service, 'SHOW TABLES;')).split('\n').filter(Boolean)
    assert(tables.length >= (selected ? 1 : 100), 'Missing ERP schema')
    const rowCounts = await sql(service, tables.map(table => `SELECT '${table}', COUNT(*) FROM ${identifier(table)};`).join('\n'))
    const dump = await compose(['exec', '-T', service, 'mysqldump', '--defaults-extra-file=/recovery/mysql.cnf',
      '--no-create-info', '--skip-comments', '--skip-dump-date', '--skip-extended-insert', '--order-by-primary',
      '--skip-add-locks', '--skip-disable-keys', '--hex-blob', '--set-gtid-purged=OFF', '--no-tablespaces',
      '--default-character-set=utf8mb4', 'erp_server', ...tables], { log: false })
    return { tableCount: tables.length, rowCounts, dataSha256: hash(dump.stdout) }
  }
  const settings = async service => ({
    customMenu: await sql(service, "SELECT id,menu_code,menu_name,permission,version FROM sys_menu WHERE menu_code='RECOVERY_CUSTOM_KEEP';"),
    existingApprovalConfigs: await sql(service, "SELECT id,company_id,account_book_id,business_type,config_name,status,remark,version FROM wf_approval_config WHERE business_type<>'INVENTORY_ADJUSTMENT' ORDER BY id;")
  })
  const schema = service => sql(service, 'SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1;')
  const fileTool = (args, options = {}) => compose(['run', '--rm', '--no-deps', 'attachment-tools', 'python', '/tools/recovery-attachments.py', ...args], options)
  const attachmentSnapshot = async root => JSON.parse((await fileTool(['snapshot', '--root', root])).stdout)
  const request = async (route, options = {}) => {
    try { return await fetch(base + route, { ...options, redirect: 'error', signal: AbortSignal.timeout(15000) }) }
    catch (error) { throw new Error(`${options.method || 'GET'} ${route}: ${error.cause?.code || error.name}: ${error.message}`) }
  }
  const api = async (method, route, body, token = adminToken, expectedStatus = 200, expectedMessage) => {
    const form = body instanceof FormData
    const response = await request(route, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body !== undefined && !form ? { 'Content-Type': 'application/json' } : {}) },
    ...(body !== undefined ? { body: form ? body : JSON.stringify(body) } : {}) })
    const payload = await response.json()
    assert.equal(response.status, expectedStatus, `${method} ${route}: HTTP ${response.status}; ${redact(payload.message || '')}`)
    assert.equal(payload.code, expectedStatus === 200 ? '0' : String(expectedStatus), `${route}: ${redact(payload.message || '')}`)
    if (expectedMessage) assert.match(payload.message, expectedMessage)
    return payload.data
  }
  const login = async account => {
    const result = await api('POST', '/api/auth/login', account, null)
    assert(result.accessToken); rememberSecret(result.accessToken); rememberSecret(result.refreshToken)
    return result
  }
  const loginAdmin = async () => { const result = await login({ username: 'admin', password: config.ERP_BOOTSTRAP_ADMIN_PASSWORD }); adminToken = result.accessToken; return result.user }
  const attachmentBytes = label => Buffer.from((`恢复附件 🧪 ${label}\n`).repeat(100000))
  const upload = async (label, businessId) => {
    const bytes = attachmentBytes(label)
    const form = new FormData()
    form.set('businessType', 'INVENTORY_ADJUSTMENT'); form.set('businessId', String(businessId)); form.set('businessNo', fixture.posted.adjustmentNo)
    form.set('file', new Blob([bytes], { type: 'text/plain' }), `恢复附件-${label}.txt`)
    const row = await api('POST', '/api/system/attachments', form)
    return { id: row.id, bytes: bytes.length, sha256: hash(bytes) }
  }
  const download = async attachment => {
    const result = await request(`/api/system/attachments/${attachment.id}/download`, { headers: { Authorization: `Bearer ${adminToken}` } })
    assert.equal(result.status, 200)
    assert.match(result.headers.get('content-disposition') || '', /attachment/)
    const bytes = Buffer.from(await result.arrayBuffer())
    assert.equal(bytes.length, attachment.bytes); assert.equal(hash(bytes), attachment.sha256)
  }
  const until = async action => {
    const deadline = Date.now() + 90000
    let error
    do {
      if (interrupted) throw new Error('Recovery interrupted')
      try { return await action() } catch (failure) { error = failure }
      await delay(500)
    } while (Date.now() < deadline)
    throw error
  }
  const ready = () => until(async () => assert.equal((await request('/readyz')).status, 200))
  const check = async (index, action) => {
    if (interrupted) throw new Error('Recovery interrupted')
    const name = recoveryChecks[index]
    report.activeStep = name; await persist(); console.log(`[recovery] ${name}`)
    try { report.checks.push({ name, passed: true, ...await action() }) }
    catch (error) { report.checks.push({ name, passed: false, failure: redact(error.message) }); throw error }
    report.activeStep = null; await persist()
  }
  const restoreDatabase = (name, allowFailure = false) => compose(['exec', '-T', 'mysql-restore', 'bash', '-ceu',
    'export MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USER=root MYSQL_PASSWORD="$MYSQL_ROOT_PASSWORD"; exec bash /scripts/restore-database.sh "$1"',
    'restore', '/backups/' + name], { input: 'YES\n', allowFailure })
  await persist()
  try {
    await cp(path.resolve(bundleDirectory), work, { recursive: true, filter: source => path.basename(source) !== '.env' })
    await mkdir(backups, { mode: 0o700 })
    const server = createServer()
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    const port = server.address().port
    await new Promise(resolve => server.close(resolve))
    base = `http://127.0.0.1:${port}`
    report.baseUrl = base
    initializeDeployment({ bundleDirectory: work, expectedManifestSha256, origin: base, port })
    config = parseEnv(await readFile(path.join(work, '.env'), 'utf8'))
    Object.entries(config).filter(([key]) => /PASSWORD|SECRET/.test(key)).forEach(([, value]) => rememberSecret(value))
    await writeFile(path.join(directory, 'mysql.cnf'), `[client]\nhost=127.0.0.1\nuser=root\npassword=${config.MYSQL_ROOT_PASSWORD}\ndefault-character-set=utf8mb4\n`, { mode: 0o600 })
    configuration = JSON.parse((await command('docker', ['compose', '-p', project, '--env-file', path.join(work, '.env'),
      '-f', path.join(work, 'compose.yaml'), 'config', '--format', 'json'], { log: false })).stdout)
    for (const service of Object.values(configuration.services)) { delete service.container_name; service.restart = 'no' }
    configureRecoveryNetworks(configuration, project)
    for (const [key, volume] of Object.entries(configuration.volumes)) { assert(!volume.external); volume.name = `${project}_${key}` }
    const services = configuration.services
    services.mysql.volumes.push({ type: 'bind', source: backups, target: '/backups' },
      { type: 'bind', source: path.join(repo, 'backend/scripts'), target: '/scripts', read_only: true },
      { type: 'bind', source: path.join(directory, 'mysql.cnf'), target: '/recovery/mysql.cnf', read_only: true })
    services['mysql-restore'] = structuredClone(services.mysql)
    for (const volume of services['mysql-restore'].volumes) {
      if (volume.type === 'volume') volume.source = 'restore-mysql-data'
      if (volume.target === '/backups') volume.read_only = true
    }
    services['redis-restore'] = structuredClone(services.redis)
    for (const volume of services['redis-restore'].volumes) volume.source = 'restore-redis-data'
    for (const name of ['restore-mysql-data', 'restore-redis-data', 'restore-attachments']) configuration.volumes[name] = { name: `${project}_${name}` }
    services['attachment-tools'] = { image: 'python:3.14-alpine', profiles: ['tools'], restart: 'no', network_mode: 'none',
      volumes: [{ type: 'volume', source: 'attachments', target: '/source', read_only: true },
        { type: 'volume', source: 'restore-attachments', target: '/restore' },
        { type: 'bind', source: backups, target: '/backups' },
        { type: 'bind', source: path.join(repo, 'backend/scripts/recovery-attachments.py'), target: '/tools/recovery-attachments.py', read_only: true }] }
    const legacyBackend = path.join(directory, 'legacy-backend')
    await mkdir(path.join(legacyBackend, 'docker-dist'), { recursive: true })
    await copyFile(path.join(work, 'backend/Dockerfile'), path.join(legacyBackend, 'Dockerfile'))
    await copyFile(path.join(legacyDirectory, legacy.jar), path.join(legacyBackend, 'docker-dist/erp-server-1.0.0.jar'))
    services['erp-server'].build.context = legacyBackend
    services['erp-server'].image = `${project}-legacy`
    services['erp-server'].mem_limit = '1536m'
    await updateCompose()
    await copyFile(path.join(legacyDirectory, 'artifact.json'), path.join(evidence, 'legacy-artifact.json'))
    await check(0, async () => {
      const rendered = JSON.parse((await compose(['config', '--format', 'json'], { log: false })).stdout)
      for (const name of ['mysql', 'mysql-restore', 'redis', 'redis-restore', 'erp-server']) assert(!rendered.services[name].ports?.length)
      assert.equal(rendered.services.web.ports[0].host_ip, '127.0.0.1')
      assert.equal(services['erp-server'].environment.SPRING_PROFILES_ACTIVE, 'prod')
      assert.equal(rendered.networks.default.internal, true)
      assert.notEqual(rendered.networks.edge.internal, true)
      assert.deepEqual(Object.keys(rendered.services.web.networks).sort(), ['default', 'edge'])
      await writeFile(path.join(evidence, 'compose-redacted.json'), redact(JSON.stringify(rendered, null, 2)))
      await compose(['build', 'erp-server', 'web'])
      await compose(['up', '-d', '--wait', '--wait-timeout', '300', 'mysql', 'redis', 'erp-server', 'web'])
      assert.equal(await schema('mysql'), '168')
      assert.equal(await sql('mysql', 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;'), '0')
      const jarSha256 = (await compose(['exec', '-T', 'erp-server', 'sha256sum', '/app/erp-server.jar'])).stdout.split(/\s/)[0]
      assert.equal(jarSha256, legacy.jarSha256)
      await ready()
      report.sourceImages = (await compose(['images', '--format', 'json'])).stdout
      return { sourceRevision: legacyRevision, schema: '168', jarSha256, isolated: true }
    })
    await check(1, async () => {
      const admin = await loginAdmin()
      const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
      fixture = await seedHistoricalBusiness({ api, admin, date, rememberSecret })
      beforeAttachments = [await upload('before-one', fixture.posted.id), await upload('before-two', fixture.draft.id)]
      for (const attachment of beforeAttachments) await download(attachment)
      await sql('mysql', "INSERT INTO sys_menu (id,parent_id,menu_type,menu_code,menu_name,permission) VALUES (9000000000001,0,'BUTTON','RECOVERY_CUSTOM_KEEP','历史自定义权限','custom:recovery:keep');")
      report.fixtures = { ...fixture, reviewer: { username: fixture.reviewer.username }, attachments: beforeAttachments }
      return { generatedThroughOldApplication: true, postedAdjustmentId: fixture.posted.id, draftId: fixture.draft.id,
        adjustedCountId: fixture.adjusted.id, countedId: fixture.counted.id, attachments: beforeAttachments.length }
    })
    await check(2, async () => {
      await compose(['stop', '-t', '45', 'erp-server'])
      const container = (await compose(['ps', '-aq', 'erp-server'])).stdout.trim()
      assert.equal((await command('docker', ['inspect', '--format', '{{.State.Running}}', container])).stdout.trim(), 'false')
      baseline = await snapshot('mysql')
      inventoryBefore = await snapshot('mysql', inventoryTables)
      settingsBefore = requireHistoricalConfiguration(await settings('mysql'))
      writeJson(path.join(evidence, 'database-before-backup.json'), baseline)
      writeJson(path.join(evidence, 'inventory-before-upgrade.json'), inventoryBefore)
      writeJson(path.join(evidence, 'configuration-before-upgrade.json'), settingsBefore)
      await compose(['exec', '-T', 'mysql', 'bash', '-ceu',
        'export MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USER=root MYSQL_PASSWORD="$MYSQL_ROOT_PASSWORD" BACKUP_DIR=/backups\nbash /scripts/backup-database.sh upgrade\nchown -R "$1:$2" /backups',
        'backup', String(process.getuid()), String(process.getgid())])
      const files = (await readdir(backups)).filter(file => file.endsWith('.sql.gz'))
      assert.equal(files.length, 1)
      const attachments = JSON.parse((await fileTool(['backup', '--root', '/source', '--archive', '/backups/attachments.tar.gz', '--manifest', '/backups/attachments.json'])).stdout)
      assert.equal(attachments.entries.filter(entry => entry.kind === 'file').length, beforeAttachments.length)
      assert.deepEqual(await snapshot('mysql'), baseline, 'Stopped database changed during checkpoint')
      checkpoint = sealCheckpoint({ directory: backups, databaseFile: files[0], applicationRevision: revision,
        manifestSha256: expectedManifestSha256, project, databaseSnapshot: baseline })
      report.checkpointSha256 = checkpoint.checkpointSha256
      await copyFile(path.join(backups, 'backup-manifest.json'), path.join(evidence, 'backup-manifest.json'))
      await copyFile(path.join(backups, 'attachments.json'), path.join(evidence, 'attachments.json'))
      return { writersStopped: true, tableCount: baseline.tableCount, attachmentFiles: beforeAttachments.length, checkpointSha256: checkpoint.checkpointSha256 }
    })
    let laterCustomer, laterAttachment
    await check(3, async () => {
      await compose(['start', '--wait', '--wait-timeout', '180', 'erp-server'])
      await ready(); await loginAdmin()
      laterCustomer = await api('POST', '/api/masterdata/customers', { customerCode: `${fixture.prefix}-AFTER`, customerName: '备份后客户',
        customerType: 'ENTERPRISE', settlementMethod: 'BANK', creditLimit: 0 })
      laterAttachment = await upload('after-checkpoint', fixture.posted.id)
      await api('DELETE', `/api/system/attachments/${beforeAttachments[0].id}`)
      await compose(['stop', '-t', '45', 'erp-server'])
      sourceAfterDatabase = await snapshot('mysql')
      sourceAfterAttachments = await attachmentSnapshot('/source')
      assert.notEqual(sourceAfterDatabase.dataSha256, baseline.dataSha256)
      assert.notDeepEqual(sourceAfterAttachments, readJson(path.join(backups, 'attachments.json')))
      return { laterCustomerId: laterCustomer.id, laterAttachmentId: laterAttachment.id, originalAttachmentDeleted: true }
    })
    await check(4, async () => {
      await compose(['up', '-d', '--wait', '--wait-timeout', '180', 'mysql-restore', 'redis-restore'])
      const empty = async () => { assert.equal(await sql('mysql-restore', 'SHOW TABLES;'), ''); assert.deepEqual((await attachmentSnapshot('/restore')).entries, []) }
      await empty()
      await writeFile(path.join(backups, 'corrupted.sql.gz'), 'corrupted database')
      await writeFile(path.join(backups, 'corrupted.sql.gz.sha256'), '0'.repeat(64) + '  corrupted.sql.gz\n')
      const badDatabase = await restoreDatabase('corrupted.sql.gz', true)
      assert.notEqual(badDatabase.code, 0); assert.match(badDatabase.stdout + badDatabase.stderr, /校验和.*失败/)
      await empty()
      await writeFile(path.join(backups, 'corrupted-attachments.tar.gz'), 'corrupted attachments')
      const badFiles = await fileTool(['restore', '--root', '/restore', '--archive', '/backups/corrupted-attachments.tar.gz', '--manifest', '/backups/attachments.json'], { allowFailure: true })
      assert.notEqual(badFiles.code, 0); assert.match(badFiles.stdout + badFiles.stderr, /gzip|tar|header/i)
      await empty()
      const archive = path.join(backups, 'attachments.tar.gz'), original = await readFile(archive)
      try {
        await copyFile(path.join(backups, checkpoint.manifest.artifacts.database.file), archive)
        assert.throws(() => verifyCheckpoint({ directory: backups, expectedCheckpointSha256: checkpoint.checkpointSha256,
          applicationRevision: revision, manifestSha256: expectedManifestSha256 }), /checksum or size mismatch/)
      } finally { await writeFile(archive, original) }
      await empty()
      return { databaseRejected: true, attachmentsRejected: true, mixedCheckpointRejected: true, targetsUntouched: true }
    })
    await check(5, async () => {
      verifyCheckpoint({ directory: backups, expectedCheckpointSha256: checkpoint.checkpointSha256, applicationRevision: revision, manifestSha256: expectedManifestSha256 })
      await fileTool(['verify', '--archive', '/backups/attachments.tar.gz', '--manifest', '/backups/attachments.json'])
      assert.equal(await sql('mysql-restore', 'SHOW TABLES;'), '')
      await restoreDatabase(checkpoint.manifest.artifacts.database.file)
      const restored = await snapshot('mysql-restore')
      assert.deepEqual(restored, baseline)
      assert.equal(await schema('mysql-restore'), '168')
      writeJson(path.join(evidence, 'database-restored.json'), restored)
      const restoredFiles = JSON.parse((await fileTool(['restore', '--root', '/restore', '--archive', '/backups/attachments.tar.gz', '--manifest', '/backups/attachments.json'])).stdout)
      assert.deepEqual(restoredFiles, readJson(path.join(backups, 'attachments.json')))
      writeJson(path.join(evidence, 'attachments-restored.json'), restoredFiles)
      return { databaseSnapshotMatched: true, attachmentSnapshotMatched: true, tableCount: restored.tableCount, schema: '168' }
    })
    await check(6, async () => {
      const app = services['erp-server']
      app.build.context = path.join(work, 'backend'); app.image = `${project}-candidate`
      app.environment.ERP_DATASOURCE_URL = app.environment.ERP_DATASOURCE_URL.replace('mysql:3306', 'mysql-restore:3306')
      app.environment.ERP_REDIS_HOST = 'redis-restore'
      app.depends_on = { 'mysql-restore': { condition: 'service_healthy' }, 'redis-restore': { condition: 'service_healthy' } }
      for (const volume of app.volumes) { assert.equal(volume.source, 'attachments'); volume.source = 'restore-attachments' }
      assert(!app.environment.SPRING_FLYWAY_TARGET && app.environment.SPRING_FLYWAY_ENABLED !== 'false')
      await updateCompose()
      await compose(['build', 'erp-server'])
      await compose(['up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '240', 'erp-server'])
      await ready()
      const jarSha256 = (await compose(['exec', '-T', 'erp-server', 'sha256sum', '/app/erp-server.jar'])).stdout.split(/\s/)[0]
      assert.equal(jarSha256, readJson(path.join(work, 'deployment-manifest.json')).jarSha256)
      assert.equal(await schema('mysql-restore'), '169')
      assert.equal(await sql('mysql-restore', "SELECT COUNT(*) FROM flyway_schema_history WHERE version='169' AND success=1;"), '1')
      assert.equal(await sql('mysql-restore', 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;'), '0')
      assert.equal(await sql('mysql-restore', 'SELECT COUNT(*) FROM flyway_schema_history WHERE CAST(version AS UNSIGNED)<=168;'),
        await sql('mysql', 'SELECT COUNT(*) FROM flyway_schema_history WHERE CAST(version AS UNSIGNED)<=168;'))
      report.targetImages = (await compose(['images', '--format', 'json'])).stdout
      return { from: '168', to: '169', appliedOnce: true, jarSha256 }
    })
    await check(7, async () => {
      const inventoryAfter = await snapshot('mysql-restore', inventoryTables)
      const settingsAfter = await settings('mysql-restore')
      assert.deepEqual(inventoryAfter, inventoryBefore)
      assert.deepEqual(settingsAfter, settingsBefore)
      writeJson(path.join(evidence, 'inventory-after-upgrade.json'), inventoryAfter)
      writeJson(path.join(evidence, 'configuration-after-upgrade.json'), settingsAfter)
      assert.equal(await sql('mysql-restore', `SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id WHERE rm.role_id=${id(fixture.adminRoleId)} AND m.id>9000000000001 AND m.permission IN ('inventory:adjustment:update','inventory:adjustment:submit','inventory:adjustment:approve');`), '3')
      assert.equal(await sql('mysql-restore', `SELECT COUNT(*) FROM wf_approval_config c JOIN wf_approval_node n ON n.config_id=c.id JOIN wf_approval_node_approver a ON a.node_id=n.id WHERE c.company_id=1 AND c.account_book_id=1 AND c.business_type='INVENTORY_ADJUSTMENT' AND a.approver_type='ROLE' AND a.approver_id=${id(fixture.adminRoleId)};`), '1')
      return { inventoryAndVouchersUnchanged: true, customSettingsPreserved: true, seededPermissions: 3, defaultApprovalRoleBound: true }
    })
    await check(8, async () => {
      await loginAdmin()
      const reads = await verifyHistoricalReads(api, fixture)
      for (const attachment of beforeAttachments) await download(attachment)
      assert.equal(await sql('mysql-restore', `SELECT COUNT(*) FROM md_customer WHERE id=${id(laterCustomer.id)};`), '0')
      assert.equal(await sql('mysql-restore', `SELECT COUNT(*) FROM sys_attachment WHERE id=${id(laterAttachment.id)};`), '0')
      const denied = await request(`/api/system/attachments/${laterAttachment.id}/download`, { headers: { Authorization: `Bearer ${adminToken}` } })
      assert.notEqual(denied.status, 200)
      return { ...reads, originalAttachmentsRestored: true, laterRecordsAbsent: true }
    })
    await check(9, async () => {
      const reviewer = await login(fixture.reviewer)
      assert(reviewer.permissions.includes('inventory:adjustment:approve'))
      return verifyUpgradedApproval({ api, fixture, reviewerToken: reviewer.accessToken })
    })
    await check(10, async () => {
      const newAttachment = await upload('after-recovery', fixture.draft.id)
      await download(newAttachment)
      await compose(['stop', '-t', '45', 'erp-server'])
      await compose(['start', '--wait', '--wait-timeout', '180', 'erp-server'])
      await ready(); await loginAdmin()
      for (const attachment of [...beforeAttachments, newAttachment]) await download(attachment)
      assert.equal(await sql('mysql-restore', "SELECT COUNT(*) FROM flyway_schema_history WHERE version='169' AND success=1;"), '1')
      assert.deepEqual(await snapshot('mysql'), sourceAfterDatabase)
      assert.deepEqual(await attachmentSnapshot('/source'), sourceAfterAttachments)
      return { sourceDatabaseUnchanged: true, sourceAttachmentsUnchanged: true, newAttachmentWritable: true, restartPassed: true }
    })
    verifyDeploymentBundle({ bundleDirectory, expectedManifestSha256 })
    assert.equal(await git(['rev-parse', 'HEAD']), revision)
    assert.equal(await git(['status', '--porcelain']), '')
    report.status = 'PASSED'
  } catch (error) { report.status = 'FAILED'; report.failure = redact(error.message) }
  finally {
    const failures = []
    if (configured) {
      try { await compose(['logs', '--no-color', '--tail', '200']) }
      catch (error) { failures.push(redact(error.message)) }
      finally {
        try { await compose(['down', '--volumes', '--remove-orphans', '--timeout', '30']) }
        catch (error) { failures.push(redact(error.message)) }
      }
    }
    try {
      const cleanup = await cleanupDisposableDocker(project, async args => (await command('docker', args)).stdout)
      if (!cleanup.passed) failures.push(JSON.stringify(cleanup.failures))
    } catch (error) { failures.push(redact(error.message)) }
    try { await rm(directory, { recursive: true, force: true }); report.privateFilesRemoved = true }
    catch (error) { report.privateFilesRemoved = false; failures.push(redact(error.message)) }
    report.cleanupPassed = failures.length === 0
    if (failures.length) { report.cleanupFailure = failures.join('\n'); report.status = 'FAILED' }
    report.finishedAt = new Date().toISOString(); await persist()
    process.removeListener('SIGINT', signal); process.removeListener('SIGTERM', signal)
  }
  assert.equal(report.status, 'PASSED', `Recovery failed: ${report.failure || report.cleanupFailure}; see ${evidence}`)
  console.log(`[recovery] PASSED: ${evidence}`)
  return report
}

async function main() {
  const { values, positionals } = parseArgs({ allowPositionals: true, options: {
    bundle: { type: 'string' }, 'expected-manifest-sha256': { type: 'string' }, legacy: { type: 'string' },
    'expected-legacy-manifest-sha256': { type: 'string' }, evidence: { type: 'string' },
    'expected-revision': { type: 'string' }, output: { type: 'string' }
  } })
  assert.equal(positionals.length, 1)
  const options = { bundleDirectory: values.bundle, expectedManifestSha256: values['expected-manifest-sha256'],
    legacyDirectory: values.legacy, expectedLegacyManifestSha256: values['expected-legacy-manifest-sha256'],
    evidenceDirectory: values.evidence, expectedRevision: values['expected-revision'] }
  const action = positionals[0]
  assert(['prepare-legacy', 'run', 'verify', 'cleanup'].includes(action), 'Use prepare-legacy, run, verify or cleanup')
  const result = action === 'prepare-legacy' ? await prepareLegacyArtifact(values.output)
    : action === 'run' ? await runRecovery(options)
      : action === 'verify' ? verifyRecoveryEvidence(options) : await cleanupRecoveryEvidence(options)
  if (action !== 'run') console.log(JSON.stringify(result, null, 2))
  if (result.passed === false) process.exitCode = 1
}

if (process.argv[1] && path.resolve(process.argv[1]) === script) {
  main().catch(error => {
    console.error(error.message)
    if (process.argv[2] === 'cleanup') console.log(JSON.stringify({ passed: false, failure: error.message }))
    process.exitCode = 1
  })
}

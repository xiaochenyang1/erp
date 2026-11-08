import assert from 'node:assert/strict'
import test from 'node:test'
import { execFileSync } from 'node:child_process'
import { copyFileSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { cleanupRecoveryEvidence, configureRecoveryNetworks, recoveryChecks, validateRecoveryReports } from './deployment-recovery.mjs'
import { hash, legacyRevision, sealCheckpoint } from './recovery-checkpoint.mjs'
import { runLegacyGit, verifyArchivedInputs, verifyLegacyArtifact, verifyLegacyFrontend } from './deployment-recovery-legacy.mjs'
import { addRecoveryStores, recoveryApplication } from './deployment-recovery-compose.mjs'
import { filesIn } from '../../frontend/scripts/release-artifact.mjs'

const revision = 'a'.repeat(40), manifestSha256 = 'b'.repeat(64), jarSha256 = 'c'.repeat(64)
const project = 'erp-preview-recovery-123456abcdef'
const save = (file, value) => writeFileSync(file, JSON.stringify(value, null, 2) + '\n')

function fixture(t) {
  const root = mkdtempSync(path.join(tmpdir(), 'erp-recovery-test-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  const evidenceDirectory = path.join(root, 'evidence'), backups = path.join(root, 'backups')
  mkdirSync(evidenceDirectory); mkdirSync(backups)
  const baseline = { tableCount: 131, rowCounts: 'x'.repeat(200), dataSha256: 'd'.repeat(64) }
  const attachment = { schemaVersion: 1, kind: 'erp-attachment-snapshot', entries: [
    { path: 'first.txt', kind: 'file', size: 2097152, sha256: 'e'.repeat(64) },
    { path: 'second.txt', kind: 'file', size: 2097152, sha256: 'f'.repeat(64) }
  ] }
  writeFileSync(path.join(backups, 'database.sql.gz'), 'DB fixture')
  writeFileSync(path.join(backups, 'database.sql.gz.sha256'), hash('DB fixture') + '  database.sql.gz\n')
  writeFileSync(path.join(backups, 'attachments.tar.gz'), 'archive fixture')
  save(path.join(backups, 'attachments.json'), attachment)
  const checkpoint = sealCheckpoint({ directory: backups, databaseFile: 'database.sql.gz', applicationRevision: revision,
    manifestSha256, project, databaseSnapshot: baseline })
  for (const name of ['backup-manifest.json', 'attachments.json']) copyFileSync(path.join(backups, name), path.join(evidenceDirectory, name))
  for (const name of ['database-before-backup.json', 'database-restored.json', 'database-rollback-restored.json']) save(path.join(evidenceDirectory, name), baseline)
  for (const name of ['inventory-before-upgrade.json', 'inventory-after-upgrade.json']) save(path.join(evidenceDirectory, name), { dataSha256: 'f'.repeat(64) })
  for (const name of ['configuration-before-upgrade.json', 'configuration-after-upgrade.json']) save(path.join(evidenceDirectory, name), {
    customMenu: '9000000000001\tRECOVERY_CUSTOM_KEEP\t历史自定义权限\tcustom:recovery:keep\t0',
    existingApprovalConfigs: '123\t1\t1\tPURCHASE_ORDER\t历史自定义采购审批 🧪\tACTIVE\thistorical configuration\t0'
  })
  save(path.join(evidenceDirectory, 'attachments-restored.json'), attachment)
  save(path.join(evidenceDirectory, 'attachments-rollback-restored.json'), attachment)
  for (const name of ['database-upgraded-before-rollback.json', 'database-upgraded-after-rollback.json']) save(path.join(evidenceDirectory, name), { ...baseline, tableCount: baseline.tableCount + 2, dataSha256: '2'.repeat(64) })
  for (const name of ['attachments-upgraded-before-rollback.json', 'attachments-upgraded-after-rollback.json']) save(path.join(evidenceDirectory, name), {
    ...attachment, entries: [...attachment.entries, { path: 'new.txt', kind: 'file', size: 42, sha256: '3'.repeat(64) }]
  })
  const frontend = { schemaVersion: 1, kind: 'erp-historical-frontend', sourceRevision: legacyRevision, toolRevision: revision,
    sourceInputSha256: '4'.repeat(64), buildPassed: true, files: [
      { path: 'dist/index.html', size: 10, sha256: '5'.repeat(64) },
      { path: 'dist/assets/index.js', size: 20, sha256: '6'.repeat(64) }
    ] }
  save(path.join(evidenceDirectory, 'legacy-frontend.json'), frontend)
  const legacy = { schemaVersion: 1, kind: 'erp-historical-fixture', sourceRevision: legacyRevision, toolRevision: revision,
    jarSha256: '1'.repeat(64), buildPassed: true, schema: '168', frontend: 'frontend',
    frontendManifestSha256: hash(readFileSync(path.join(evidenceDirectory, 'legacy-frontend.json'))) }
  save(path.join(evidenceDirectory, 'legacy-artifact.json'), legacy)
  const report = { schemaVersion: 1, kind: 'erp-upgrade-recovery', status: 'PASSED', startedAt: '2026-11-06T00:00:00Z', finishedAt: '2026-11-06T00:01:00Z',
    applicationRevision: revision, toolRevision: revision, runtimeToolRevision: revision, sourceRevision: legacyRevision,
    sourceJarSha256: legacy.jarSha256, manifestSha256, project, privateDirectory: 'erp-recovery-work-abcdef', cleanupPassed: true, privateFilesRemoved: true,
    browserCleanup: { passed: true, processes: [{ name: 'chrome', passed: true, childClosed: true }] },
    checkpointSha256: checkpoint.checkpointSha256, legacyManifestSha256: hash(readFileSync(path.join(evidenceDirectory, 'legacy-artifact.json'))),
    checks: recoveryChecks.map(name => ({ name, passed: true })) }
  Object.assign(report.checks[0], { sourceRevision: legacyRevision, schema: '168', jarSha256: legacy.jarSha256 })
  report.checks[1].generatedThroughOldApplication = true
  report.checks[2].writersStopped = true
  Object.assign(report.checks[4], { databaseRejected: true, attachmentsRejected: true, mixedCheckpointRejected: true, targetsUntouched: true })
  Object.assign(report.checks[6], { from: '168', to: '173', appliedOnce: true, appliedVersions: ['169', '170', '171', '172', '173'], jarSha256 })
  Object.assign(report.checks[8], { postedPreserved: true, draftPreserved: true, cancelledPreserved: true, adjustedLinkPreserved: true,
    countedPreserved: true, unicodeCustomerPreserved: true, originalAttachmentsRestored: true, laterRecordsAbsent: true })
  Object.assign(report.checks[9], { oldDraftRequiresApproval: true, selfApprovalRejected: true, duplicatePostingRejected: true, countWaitedForPosting: true,
    draftStock: { quantity: 12, amount: 120, reserved: 0 }, countedStock: { quantity: 3, amount: 30, reserved: 0 } })
  Object.assign(report.checks[10], { sourceDatabaseUnchanged: true, sourceAttachmentsUnchanged: true, newAttachmentWritable: true, restartPassed: true })
  Object.assign(report.checks[11], { writersStopped: true, separateVolumes: true, databaseSnapshotMatched: true,
    attachmentSnapshotMatched: true, schema: '168', tableCount: baseline.tableCount, checkpointSha256: checkpoint.checkpointSha256 })
  Object.assign(report.checks[12], { postedPreserved: true, draftPreserved: true, cancelledPreserved: true, adjustedLinkPreserved: true,
    countedPreserved: true, unicodeCustomerPreserved: true, originalAttachmentsRestored: true, laterWritesAbsent: true,
    schema: '168', sourceRevision: legacyRevision, jarSha256: legacy.jarSha256, frontendManifestSha256: legacy.frontendManifestSha256,
    frontendFilesVerified: frontend.files.length, browser: { loginThroughUi: true, protectedDeepLink: true,
      refreshedRoutes: ['/dashboard', '/sales/orders', '/inventory/stocks', '/finance/periods'], runtimeErrors: [] } })
  Object.assign(report.checks[13], { legacyPostingPassed: true, stock: { quantity: 12, amount: 120, reserved: 0 },
    stockAfterRestart: { quantity: 12, amount: 120, reserved: 0 }, rollbackWritesDurable: true,
    upgradedDatabaseUnchanged: true, upgradedAttachmentsUnchanged: true, sourceDatabaseUnchanged: true, sourceAttachmentsUnchanged: true, restartPassed: true })
  const reportFile = path.join(evidenceDirectory, 'report.json')
  save(reportFile, report)
  return { root, evidenceDirectory, report, reportFile, options: { evidenceDirectory, expectedRevision: revision, expectedManifestSha256: manifestSha256, expectedJarSha256: jarSha256 } }
}

test('accepts complete version-bound history, restore and cleanup evidence', t => {
  const f = fixture(t)
  const result = validateRecoveryReports(f.options)
  assert.equal(result.checks, 14)
  assert.equal(result.rollbackChecks, 3)
  assert.equal(result.rollbackSchema, '168')
})

test('upgrade evidence requires both production cost tables introduced after V168', t => {
  const f = fixture(t)
  for (const name of ['database-upgraded-before-rollback.json', 'database-upgraded-after-rollback.json']) {
    const file = path.join(f.evidenceDirectory, name)
    const upgraded = JSON.parse(readFileSync(file, 'utf8'))
    save(file, { ...upgraded, tableCount: upgraded.tableCount - 1 })
  }
  assert.throws(() => validateRecoveryReports(f.options))
})

test('PASSED cannot hide missing checks, stale versions, unfinished actions or failed cleanup', t => {
  const f = fixture(t)
  for (const update of [{ checks: f.report.checks.slice(1) }, { checks: [...f.report.checks, f.report.checks[0]] },
    { runtimeToolRevision: '0'.repeat(40) }, { sourceRevision: '0'.repeat(40) }, { status: 'FAILED' },
    { activeStep: 'interrupted' }, { finishedAt: null }, { cleanupPassed: false }, { privateFilesRemoved: false },
    { browserCleanup: { passed: false } }]) {
    save(f.reportFile, { ...f.report, ...update })
    assert.throws(() => validateRecoveryReports(f.options))
  }
})

test('changed restored rows, old inventory, settings or attachment contents invalidate recovery', t => {
  const f = fixture(t)
  for (const name of ['database-restored.json', 'inventory-after-upgrade.json', 'configuration-after-upgrade.json', 'attachments-restored.json',
    'database-rollback-restored.json', 'attachments-rollback-restored.json', 'database-upgraded-after-rollback.json', 'attachments-upgraded-after-rollback.json', 'legacy-frontend.json']) {
    const file = path.join(f.evidenceDirectory, name), original = readFileSync(file)
    save(file, { tampered: true })
    assert.throws(() => validateRecoveryReports(f.options))
    writeFileSync(file, original)
  }
})

test('mixed checkpoint metadata and leaked private files invalidate recovery', t => {
  const f = fixture(t)
  const file = path.join(f.evidenceDirectory, 'backup-manifest.json'), original = readFileSync(file)
  writeFileSync(file, original + '\n')
  assert.throws(() => validateRecoveryReports(f.options))
  writeFileSync(file, original)
  for (const name of ['.env', 'mysql.cnf', 'private-account.json', 'attachments.tar.gz']) {
    writeFileSync(path.join(f.evidenceDirectory, name), 'private')
    assert.throws(() => validateRecoveryReports(f.options), /Private recovery/)
    rmSync(path.join(f.evidenceDirectory, name))
  }
})

test('matching empty or unrelated configuration snapshots cannot satisfy historical preservation', t => {
  const f = fixture(t)
  const before = path.join(f.evidenceDirectory, 'configuration-before-upgrade.json')
  const after = path.join(f.evidenceDirectory, 'configuration-after-upgrade.json')
  const original = JSON.parse(readFileSync(before, 'utf8'))
  for (const update of [{ customMenu: '' }, { existingApprovalConfigs: '' },
    { existingApprovalConfigs: original.existingApprovalConfigs.replace('PURCHASE_ORDER', 'SALES_ORDER') }]) {
    save(before, { ...original, ...update }); save(after, { ...original, ...update })
    assert.throws(() => validateRecoveryReports(f.options), /checkpoint must contain/)
  }
})

test('missing negative checks and incorrect approval stock totals are rejected', t => {
  const f = fixture(t)
  for (const [index, key, value] of [[4, 'targetsUntouched', false], [6, 'to', '168'], [8, 'laterRecordsAbsent', false],
    [9, 'selfApprovalRejected', false], [9, 'draftStock', { quantity: 14, amount: 140, reserved: 0 }], [10, 'sourceDatabaseUnchanged', false]]) {
    const changed = structuredClone(f.report)
    changed.checks[index][key] = value
    save(f.reportFile, changed)
    assert.throws(() => validateRecoveryReports(f.options))
  }
})

test('rollback rejects a newer schema, mixed releases, lost upgraded data and missing old UI verification', t => {
  const f = fixture(t)
  for (const [index, key, value] of [[11, 'schema', '169'], [11, 'separateVolumes', false], [11, 'checkpointSha256', '0'.repeat(64)],
    [12, 'jarSha256', jarSha256], [12, 'sourceRevision', revision], [12, 'frontendFilesVerified', 0], [12, 'laterWritesAbsent', false],
    [12, 'browser', { loginThroughUi: false }],
    [13, 'upgradedDatabaseUnchanged', false], [13, 'upgradedAttachmentsUnchanged', false], [13, 'rollbackWritesDurable', false],
    [13, 'stockAfterRestart', { quantity: 10, amount: 100, reserved: 0 }], [13, 'stockAfterRestart', undefined]]) {
    const changed = structuredClone(f.report)
    changed.checks[index][key] = value
    save(f.reportFile, changed)
    assert.throws(() => validateRecoveryReports(f.options))
  }
})

test('fallback cleanup refuses foreign projects, revisions and private paths before any Docker call', async t => {
  const f = fixture(t)
  for (const update of [{ project: 'production' }, { project: 'erp-preview-deployment-123456abcdef' },
    { runtimeToolRevision: '0'.repeat(40) }, { privateDirectory: '../outside' }, { kind: 'erp-deployment-smoke' }]) {
    save(f.reportFile, { ...f.report, ...update })
    await assert.rejects(cleanupRecoveryEvidence({ evidenceDirectory: f.evidenceDirectory, expectedRevision: revision,
      docker: async () => { assert.fail('Docker must not be called') }, removePrivate: false }))
  }
})

test('fallback cleanup attempts remaining resources after failure and stays scoped to the recovery project', async t => {
  const f = fixture(t), calls = []
  const result = await cleanupRecoveryEvidence({ evidenceDirectory: f.evidenceDirectory, expectedRevision: revision, removePrivate: false,
    docker: async args => {
      calls.push(args)
      if (args[0] === 'rm') throw new Error('container removal failed')
      if (args[0] === 'ps' && args.join(' ').includes('label=')) return 'owned-container'
      if (args.includes('--filter')) assert(args.at(-1).includes(project))
      return ''
    } })
  assert.equal(result.passed, false)
  assert(calls.some(args => args[0] === 'volume'))
  assert(calls.some(args => args[0] === 'network'))
  assert(calls.some(args => args[0] === 'image'))
})

test('historical JAR verification rejects changed bytes and unrelated source or tool versions', async t => {
  const f = fixture(t)
  const directory = path.join(f.root, 'legacy')
  mkdirSync(directory)
  const jar = path.join(directory, 'erp-server-v168.jar')
  writeFileSync(jar, 'historical fixture bytes')
  const frontend = path.join(directory, 'frontend')
  mkdirSync(path.join(frontend, 'dist/assets'), { recursive: true })
  writeFileSync(path.join(frontend, 'dist/index.html'), 'old index')
  writeFileSync(path.join(frontend, 'dist/assets/index.js'), 'old script')
  save(path.join(frontend, 'manifest.json'), { schemaVersion: 1, kind: 'erp-historical-frontend', sourceRevision: legacyRevision,
    toolRevision: revision, sourceInputSha256: '2'.repeat(64), buildPassed: true, files: filesIn(frontend) })
  const frontendManifestSha256 = hash(readFileSync(path.join(frontend, 'manifest.json')))
  const metadata = { schemaVersion: 1, kind: 'erp-historical-fixture', sourceRevision: legacyRevision, toolRevision: revision,
    schema: '168', buildPassed: true, jar: 'erp-server-v168.jar', jarSha256: hash(readFileSync(jar)), frontend: 'frontend', frontendManifestSha256 }
  const file = path.join(directory, 'artifact.json')
  save(file, metadata)
  const digest = hash(readFileSync(file))
  assert.equal((await verifyLegacyArtifact(directory, digest, revision)).sourceRevision, legacyRevision)
  writeFileSync(path.join(frontend, 'dist/index.html'), 'newer index')
  assert.throws(() => verifyLegacyFrontend(frontend, frontendManifestSha256, revision), /Historical frontend bytes changed/)
  writeFileSync(path.join(frontend, 'dist/index.html'), 'old index')
  await assert.rejects(verifyLegacyArtifact(directory, '0'.repeat(64), revision))
  await assert.rejects(verifyLegacyArtifact(directory, digest, '0'.repeat(40)))
  writeFileSync(jar, 'substituted bytes')
  await assert.rejects(verifyLegacyArtifact(directory, digest, revision), /JAR checksum mismatch/)
  save(file, { ...metadata, sourceRevision: revision })
  await assert.rejects(verifyLegacyArtifact(directory, hash(readFileSync(file)), revision))
})

test('historical frontend inputs match Git blobs byte-for-byte, including binary files', async t => {
  const root = mkdtempSync(path.join(tmpdir(), 'erp-history-frontend-test-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  mkdirSync(path.join(root, 'frontend/public'), { recursive: true })
  writeFileSync(path.join(root, 'frontend/package.json'), '{"name":"fixture"}\n')
  const binary = path.join(root, 'frontend/public/binary.png')
  writeFileSync(binary, Buffer.from([0, 255, 128, 10, 13, 0]))
  const git = args => execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
  git(['init', '-q']); git(['add', '.'])
  git(['-c', 'user.name=Recovery Fixture', '-c', 'user.email=recovery@example.invalid', 'commit', '-qm', 'history'])
  const entries = git(['ls-tree', '-r', '-z', 'HEAD', 'frontend']).split('\0').filter(Boolean)
  assert.match(await verifyArchivedInputs(root, entries), /^[a-f0-9]{64}$/)
  writeFileSync(binary, Buffer.from([0, 255, 129, 10, 13, 0]))
  await assert.rejects(verifyArchivedInputs(root, entries), /Historical frontend input changed/)
})

test('rollback and upgrade use separate data stores and derive application routing from the original source', () => {
  const source = {
    mysql: { networks: { default: null }, volumes: [{ type: 'volume', source: 'mysql-data', target: '/var/lib/mysql' },
      { type: 'bind', source: '/private/backups', target: '/backups' }] },
    redis: { networks: { default: null }, volumes: [{ type: 'volume', source: 'redis-data', target: '/data' }] }
  }
  const configuration = { services: structuredClone(source), volumes: {} }
  addRecoveryStores(configuration, project)
  assert.deepEqual(configuration.services.mysql, source.mysql)
  assert.deepEqual(configuration.services.redis, source.redis)
  const names = Object.values(configuration.volumes).map(volume => volume.name)
  assert.equal(new Set(names).size, 6)
  for (const target of ['restore', 'rollback']) {
    assert.equal(configuration.services[`mysql-${target}`].volumes[0].source, `${target}-mysql-data`)
    assert.equal(configuration.services[`redis-${target}`].volumes[0].source, `${target}-redis-data`)
    assert.equal(configuration.services[`mysql-${target}`].volumes[1].read_only, true)
  }
  const app = { image: `${project}-legacy`, build: { context: '/private/legacy' }, networks: { default: null },
    environment: { SPRING_PROFILES_ACTIVE: 'prod', ERP_DATASOURCE_URL: 'jdbc:mysql://mysql:3306/erp_server?encoding=UTF-8', ERP_REDIS_HOST: 'redis' },
    volumes: [{ type: 'volume', source: 'attachments', target: '/attachments' }] }
  const original = structuredClone(app)
  const upgraded = recoveryApplication(app, { target: 'restore', project, buildContext: '/private/candidate' })
  const rollback = recoveryApplication(app, { target: 'rollback', project })
  assert.deepEqual(app, original)
  assert.match(upgraded.environment.ERP_DATASOURCE_URL, /\/\/mysql-restore:3306\//)
  assert.match(rollback.environment.ERP_DATASOURCE_URL, /\/\/mysql-rollback:3306\//)
  assert.equal(rollback.environment.ERP_REDIS_HOST, 'redis-rollback')
  assert.equal(rollback.volumes[0].source, 'rollback-attachments')
  assert.equal(rollback.image, original.image)
  assert.equal(rollback.build.context, original.build.context)
  assert.throws(() => recoveryApplication(upgraded, { target: 'rollback', project }))
  assert.throws(() => recoveryApplication(app, { target: 'production', project }), /Unknown recovery target/)
  assert.throws(() => addRecoveryStores(configuration, project), /must be new/)
})

test('historical Git inventory and archive use repository-relative paths from the repository root', async t => {
  const root = mkdtempSync(path.join(tmpdir(), 'erp-history-path-test-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  const repository = path.join(root, 'repository')
  const relative = 'backend/src/main/resources/db/migration/V168__fixture.sql'
  mkdirSync(path.dirname(path.join(repository, relative)), { recursive: true })
  writeFileSync(path.join(repository, relative), 'SELECT 168;\n')
  const git = args => execFileSync('git', args, { cwd: repository, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
  git(['init', '-q']); git(['add', '.'])
  git(['-c', 'user.name=Recovery Fixture', '-c', 'user.email=recovery@example.invalid', 'commit', '-qm', 'history'])
  const revision = git(['rev-parse', 'HEAD'])
  const inventory = await runLegacyGit(['ls-tree', '-r', '--name-only', revision, 'backend/src/main/resources/db/migration'], repository)
  assert.equal(inventory.stdout.trim(), relative)
  const archive = path.join(root, 'history.tar')
  await runLegacyGit(['archive', '--format=tar', `--output=${archive}`, revision, 'backend'], repository)
  const extracted = path.join(root, 'extracted')
  mkdirSync(extracted)
  execFileSync('tar', ['-xf', archive, '-C', extracted])
  assert.equal(readFileSync(path.join(extracted, relative), 'utf8'), 'SELECT 168;\n')
})

test('recovery exposes only Nginx through an entry network while keeping data services internal', () => {
  const configuration = { services: Object.fromEntries(['mysql', 'redis', 'erp-server', 'web'].map(name => [name, {}])) }
  configuration.services.web.ports = [{ host_ip: '127.0.0.1', target: 8080, published: '42123' }]
  configureRecoveryNetworks(configuration, project)
  assert.equal(configuration.networks.default.internal, true)
  assert.equal(configuration.networks.edge.internal, false)
  assert.deepEqual(Object.keys(configuration.services.web.networks).sort(), ['default', 'edge'])
  for (const name of ['mysql', 'redis', 'erp-server']) {
    assert.deepEqual(Object.keys(configuration.services[name].networks), ['default'])
    assert.equal(configuration.services[name].ports, undefined)
  }
  assert.equal(configuration.services.web.ports[0].host_ip, '127.0.0.1')
  configuration.services.mysql.ports = [{ published: '3306' }]
  assert.throws(() => configureRecoveryNetworks(configuration, project), /Only the web entry/)
})

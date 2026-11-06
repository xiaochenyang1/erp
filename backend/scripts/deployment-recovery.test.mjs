import assert from 'node:assert/strict'
import test from 'node:test'
import { execFileSync } from 'node:child_process'
import { copyFileSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { cleanupRecoveryEvidence, configureRecoveryNetworks, recoveryChecks, validateRecoveryReports } from './deployment-recovery.mjs'
import { hash, legacyRevision, sealCheckpoint } from './recovery-checkpoint.mjs'
import { runLegacyGit, verifyLegacyArtifact } from './deployment-recovery-legacy.mjs'

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
  for (const name of ['database-before-backup.json', 'database-restored.json']) save(path.join(evidenceDirectory, name), baseline)
  for (const name of ['inventory-before-upgrade.json', 'inventory-after-upgrade.json']) save(path.join(evidenceDirectory, name), { dataSha256: 'f'.repeat(64) })
  for (const name of ['configuration-before-upgrade.json', 'configuration-after-upgrade.json']) save(path.join(evidenceDirectory, name), { custom: 'preserved' })
  save(path.join(evidenceDirectory, 'attachments-restored.json'), attachment)
  const legacy = { schemaVersion: 1, kind: 'erp-historical-fixture', sourceRevision: legacyRevision, toolRevision: revision,
    jarSha256: '1'.repeat(64), buildPassed: true, schema: '168' }
  save(path.join(evidenceDirectory, 'legacy-artifact.json'), legacy)
  const report = { schemaVersion: 1, kind: 'erp-upgrade-recovery', status: 'PASSED', startedAt: '2026-11-06T00:00:00Z', finishedAt: '2026-11-06T00:01:00Z',
    applicationRevision: revision, toolRevision: revision, runtimeToolRevision: revision, sourceRevision: legacyRevision,
    sourceJarSha256: legacy.jarSha256, manifestSha256, project, privateDirectory: 'erp-recovery-work-abcdef', cleanupPassed: true, privateFilesRemoved: true,
    checkpointSha256: checkpoint.checkpointSha256, legacyManifestSha256: hash(readFileSync(path.join(evidenceDirectory, 'legacy-artifact.json'))),
    checks: recoveryChecks.map(name => ({ name, passed: true })) }
  Object.assign(report.checks[0], { sourceRevision: legacyRevision, schema: '168', jarSha256: legacy.jarSha256 })
  report.checks[1].generatedThroughOldApplication = true
  report.checks[2].writersStopped = true
  Object.assign(report.checks[4], { databaseRejected: true, attachmentsRejected: true, mixedCheckpointRejected: true, targetsUntouched: true })
  Object.assign(report.checks[6], { from: '168', to: '169', appliedOnce: true, jarSha256 })
  Object.assign(report.checks[8], { postedPreserved: true, draftPreserved: true, cancelledPreserved: true, adjustedLinkPreserved: true,
    countedPreserved: true, unicodeCustomerPreserved: true, originalAttachmentsRestored: true, laterRecordsAbsent: true })
  Object.assign(report.checks[9], { oldDraftRequiresApproval: true, selfApprovalRejected: true, duplicatePostingRejected: true, countWaitedForPosting: true,
    draftStock: { quantity: 12, amount: 120, reserved: 0 }, countedStock: { quantity: 3, amount: 30, reserved: 0 } })
  Object.assign(report.checks[10], { sourceDatabaseUnchanged: true, sourceAttachmentsUnchanged: true, newAttachmentWritable: true, restartPassed: true })
  const reportFile = path.join(evidenceDirectory, 'report.json')
  save(reportFile, report)
  return { root, evidenceDirectory, report, reportFile, options: { evidenceDirectory, expectedRevision: revision, expectedManifestSha256: manifestSha256, expectedJarSha256: jarSha256 } }
}

test('accepts complete version-bound history, restore and cleanup evidence', t => {
  const f = fixture(t)
  assert.equal(validateRecoveryReports(f.options).checks, 11)
})

test('PASSED cannot hide missing checks, stale versions, unfinished actions or failed cleanup', t => {
  const f = fixture(t)
  for (const update of [{ checks: f.report.checks.slice(1) }, { checks: [...f.report.checks, f.report.checks[0]] },
    { runtimeToolRevision: '0'.repeat(40) }, { sourceRevision: '0'.repeat(40) }, { status: 'FAILED' },
    { activeStep: 'interrupted' }, { finishedAt: null }, { cleanupPassed: false }, { privateFilesRemoved: false }]) {
    save(f.reportFile, { ...f.report, ...update })
    assert.throws(() => validateRecoveryReports(f.options))
  }
})

test('changed restored rows, old inventory, settings or attachment contents invalidate recovery', t => {
  const f = fixture(t)
  for (const name of ['database-restored.json', 'inventory-after-upgrade.json', 'configuration-after-upgrade.json', 'attachments-restored.json']) {
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
  const metadata = { schemaVersion: 1, kind: 'erp-historical-fixture', sourceRevision: legacyRevision, toolRevision: revision,
    schema: '168', buildPassed: true, jar: 'erp-server-v168.jar', jarSha256: hash(readFileSync(jar)) }
  const file = path.join(directory, 'artifact.json')
  save(file, metadata)
  const digest = hash(readFileSync(file))
  assert.equal((await verifyLegacyArtifact(directory, digest, revision)).sourceRevision, legacyRevision)
  await assert.rejects(verifyLegacyArtifact(directory, '0'.repeat(64), revision))
  await assert.rejects(verifyLegacyArtifact(directory, digest, '0'.repeat(40)))
  writeFileSync(jar, 'substituted bytes')
  await assert.rejects(verifyLegacyArtifact(directory, digest, revision), /JAR checksum mismatch/)
  save(file, { ...metadata, sourceRevision: revision })
  await assert.rejects(verifyLegacyArtifact(directory, hash(readFileSync(file)), revision))
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

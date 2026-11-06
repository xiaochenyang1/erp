import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { buildDeploymentBundle } from './deployment-bundle.mjs'
import { cleanupDeploymentEvidence, prepareDeploymentCandidate, verifyDeploymentEvidence } from './deployment-ci.mjs'

const source = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const load = file => JSON.parse(readFileSync(file, 'utf8'))
const save = (file, value) => writeFileSync(file, JSON.stringify(value, null, 2) + '\n')
const project = 'erp-preview-deployment-123456abcdef'

function fixture(t) {
  const root = mkdtempSync(path.join(tmpdir(), 'erp-deployment-ci-test-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  const repository = path.join(root, 'source')
  mkdirSync(path.join(repository, 'backend'), { recursive: true })
  cpSync(path.join(source, 'backend/deployment'), path.join(repository, 'backend/deployment'), { recursive: true })
  cpSync(path.join(source, 'backend/Dockerfile.prebuilt'), path.join(repository, 'backend/Dockerfile.prebuilt'))
  writeFileSync(path.join(repository, '.gitignore'), 'backend/target/\n')
  const git = args => execFileSync('git', args, { cwd: repository, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
  git(['init', '-q']); git(['add', '.'])
  git(['-c', 'user.name=Deployment CI Test', '-c', 'user.email=deployment-ci@example.invalid', 'commit', '-qm', 'fixture'])
  const revision = git(['rev-parse', 'HEAD'])
  const reportFile = path.join(repository, 'backend/target/release-check-report.json')
  const artifacts = []
  for (const [relativePath, bytes] of Object.entries({
    'target/erp-server-1.0.0.jar': 'unit test fixture; not an executable candidate',
    'target/bom.json': '{}', 'target/classes/META-INF/sbom/application.cdx.json': '{}'
  })) {
    const file = path.join(repository, 'backend', relativePath)
    mkdirSync(path.dirname(file), { recursive: true })
    writeFileSync(file, bytes)
    artifacts.push({ relativePath, length: Buffer.byteLength(bytes), sha256: hash(bytes) })
  }
  save(reportFile, { status: 'PASSED', releaseCandidateCommit: revision.slice(0, 7), allowDirtyWorktree: false,
    dirtyWorktreeEntries: [], includeTestcontainers: true, maven: { status: 'PASSED' },
    powerShellScriptSyntaxGate: { status: 'PASSED' }, artifacts })
  const frontendArtifact = path.join(root, 'frontend')
  const frontendFiles = { 'build.log': 'fixture build', 'dist/assets/app.js': '// fixture', 'dist/index.html': '<html>test fixture</html>' }
  for (const [name, content] of Object.entries(frontendFiles)) {
    mkdirSync(path.dirname(path.join(frontendArtifact, name)), { recursive: true })
    writeFileSync(path.join(frontendArtifact, name), content)
  }
  save(path.join(frontendArtifact, 'manifest.json'), { schemaVersion: 1, kind: 'erp-frontend-build', sourceRevision: revision,
    dirtyWorktree: false, sourceInputSha256: 'a'.repeat(64), packageLockSha256: 'b'.repeat(64),
    buildCommand: 'npm run build', builtAt: new Date().toISOString(),
    files: Object.entries(frontendFiles).map(([name, bytes]) => ({ path: name, size: Buffer.byteLength(bytes), sha256: hash(bytes) })) })
  return { root, repository, revision, reportFile, frontendArtifact,
    options: { repository, frontendArtifact, expectedRevision: revision, outputDirectory: path.join(root, 'candidate') } }
}

function evidenceFixture(t) {
  const f = fixture(t)
  const candidate = prepareDeploymentCandidate(f.options)
  const bundleDirectory = path.join(f.root, 'bundle')
  const built = buildDeploymentBundle({ candidateDirectory: candidate.candidateDirectory, outputDirectory: bundleDirectory,
    expectedRevision: f.revision, expectedArtifactsSha256: candidate.artifactIndexSha256, toolRoot: f.repository })
  const evidenceDirectory = path.join(f.root, 'runtime')
  mkdirSync(path.join(evidenceDirectory, 'business-trial'), { recursive: true })
  const timing = { startedAt: '2026-11-06T01:00:00Z', finishedAt: '2026-11-06T01:05:00Z' }
  const report = { schemaVersion: 1, kind: 'erp-deployment-smoke', status: 'PASSED', ...timing, ...built,
    runtimeToolRevision: f.revision, project, cleanupPassed: true,
    browserCleanup: { passed: true, processes: [{ name: 'chrome', passed: true, childClosed: true }] },
    checks: [
      'Compose isolation and production defaults',
      'Build accepted artifacts and start MySQL Redis backend and Nginx',
      'Static bytes deep links cache rules and missing assets',
      'Production API authentication and proxy error boundaries',
      'Multipart attachment larger than 1 MB and download headers',
      'Browser login and refreshed business deep links',
      'Backend replacement changes IP without restarting Nginx or losing attachments'
    ].map(name => ({ name, passed: true })) }
  report.checks.push({ name: 'Purchase payment sales receipt and restricted-user business trial', passed: true,
    report: 'business-trial/report.json', checks: 11, retainedForInteractiveUse: false })
  const trial = { status: 'PASSED', ...timing, ...built, sourceRevision: f.revision, dirtyWorktree: false, project,
    checks: [
      'Verify deployed versions and bootstrap login',
      'Resolve an open business period without reopening locked periods',
      'Create clearly labelled trial master data',
      'Create a restricted order-entry user and verify denied finance access',
      'Purchase 10 units at CNY 10 and receive inventory',
      'Pay supplier installment 1: CNY 40', 'Pay supplier installment 2: CNY 60',
      'Sell 4 units at CNY 15 and verify remaining inventory',
      'Receive customer installment 1: CNY 20', 'Receive customer installment 2: CNY 40',
      'Restricted user can read its own orders and warehouse balance'
    ].map(name => ({ name, passed: true })) }
  const runtimeReport = path.join(evidenceDirectory, 'report.json')
  const trialReport = path.join(evidenceDirectory, 'business-trial/report.json')
  save(runtimeReport, report); save(trialReport, trial)
  return { ...f, report, trial, runtimeReport, trialReport,
    verifyOptions: { bundleDirectory, evidenceDirectory, expectedRevision: f.revision, expectedManifestSha256: built.manifestSha256 } }
}

test('adapts verified backend and frontend bytes into the existing deployment bundle format', t => {
  const f = evidenceFixture(t)
  const candidate = load(path.join(f.options.outputDirectory, 'summary.json'))
  assert.equal(candidate.commit, f.revision)
  assert.equal(candidate.status, 'PASSED')
  assert.deepEqual(verifyDeploymentEvidence(f.verifyOptions), { passed: true, applicationRevision: f.revision,
    manifestSha256: f.verifyOptions.expectedManifestSha256, deploymentChecks: 8, businessChecks: 11, cleanupPassed: true })
})

test('refuses dirty checkouts and the wrong requested revision before publishing a candidate', t => {
  const f = fixture(t)
  assert.throws(() => prepareDeploymentCandidate({ ...f.options, expectedRevision: '0'.repeat(40) }), /Checkout revision mismatch/)
  writeFileSync(path.join(f.repository, 'uncommitted.txt'), 'change')
  assert.throws(() => prepareDeploymentCandidate(f.options), /clean checkout/)
  assert(!existsSync(f.options.outputDirectory))
})

test('failed, stale, dirty and Testcontainers-excluded backend reports cannot become deployment candidates', t => {
  const f = fixture(t)
  const original = load(f.reportFile)
  for (const change of [{ status: 'FAILED' }, { releaseCandidateCommit: '0'.repeat(40) }, { allowDirtyWorktree: true },
    { dirtyWorktreeEntries: [' M backend/pom.xml'] }, { includeTestcontainers: false }, { maven: { status: 'FAILED' } },
    { powerShellScriptSyntaxGate: { status: 'FAILED' } }]) {
    save(f.reportFile, { ...original, ...change })
    assert.throws(() => prepareDeploymentCandidate(f.options))
    assert(!existsSync(f.options.outputDirectory))
  }
})

test('changed backend bytes and missing or duplicate report entries are rejected', t => {
  const f = fixture(t)
  const original = load(f.reportFile)
  for (const artifacts of [original.artifacts.slice(1), [...original.artifacts, original.artifacts[0]],
    original.artifacts.map(a => ({ ...a, sha256: '0'.repeat(64) }))]) {
    save(f.reportFile, { ...original, artifacts })
    assert.throws(() => prepareDeploymentCandidate(f.options))
  }
  save(f.reportFile, original)
  writeFileSync(path.join(f.repository, 'backend/target/erp-server-1.0.0.jar'), 'different jar')
  assert.throws(() => prepareDeploymentCandidate(f.options), /artifact size mismatch/)
})

test('frontend tampering and mixed revisions are rejected before candidate creation', t => {
  const f = fixture(t)
  const manifestFile = path.join(f.frontendArtifact, 'manifest.json')
  const original = load(manifestFile)
  for (const change of [{ sourceRevision: '0'.repeat(40) }, { dirtyWorktree: true }]) {
    save(manifestFile, { ...original, ...change })
    assert.throws(() => prepareDeploymentCandidate(f.options))
  }
  save(manifestFile, original)
  writeFileSync(path.join(f.frontendArtifact, 'dist/assets/app.js'), '// changed')
  assert.throws(() => prepareDeploymentCandidate(f.options))
  assert(!existsSync(f.options.outputDirectory))
})

test('candidate preparation refuses source-tree output and preserves existing output', t => {
  const f = fixture(t)
  assert.throws(() => prepareDeploymentCandidate({ ...f.options, outputDirectory: path.join(f.repository, 'candidate') }), /outside source/)
  mkdirSync(f.options.outputDirectory)
  writeFileSync(path.join(f.options.outputDirectory, 'keep.txt'), 'previous evidence')
  assert.throws(() => prepareDeploymentCandidate(f.options), /EEXIST/)
  assert.equal(readFileSync(path.join(f.options.outputDirectory, 'keep.txt'), 'utf8'), 'previous evidence')
})

test('a PASSED label cannot hide missing deployment steps, cleanup failures or version mismatches', t => {
  const f = evidenceFixture(t)
  for (const change of [{ checks: f.report.checks.slice(1) }, { checks: [...f.report.checks, f.report.checks[0]] },
    { status: 'FAILED' }, { failure: 'runtime failure' }, { activeStep: 'unfinished' }, { finishedAt: null },
    { cleanupPassed: false }, { cleanupFailure: 'volume remains' }, { runtimeToolRevision: '0'.repeat(40) },
    { manifestSha256: '0'.repeat(64) }, { project: 'erp-production' },
    { browserCleanup: { passed: true, processes: [] } },
    { browserCleanup: { passed: true, processes: [{ name: 'chrome', passed: true, childClosed: false }] } }]) {
    save(f.runtimeReport, { ...f.report, ...change })
    assert.throws(() => verifyDeploymentEvidence(f.verifyOptions))
  }
})

test('incomplete or unrelated business evidence cannot satisfy the parent deployment report', t => {
  const f = evidenceFixture(t)
  for (const change of [{ checks: f.trial.checks.slice(1) }, { status: 'RUNNING' }, { sourceRevision: '0'.repeat(40) },
    { project: 'erp-preview-deployment-abcdefabcdef' }, { dirtyWorktree: true },
    { checks: f.trial.checks.map((check, index) => ({ ...check, passed: index !== 5 })) }]) {
    save(f.trialReport, { ...f.trial, ...change })
    assert.throws(() => verifyDeploymentEvidence(f.verifyOptions))
  }
  save(f.trialReport, f.trial)
  f.report.checks.at(-1).checks = 10
  save(f.runtimeReport, f.report)
  assert.throws(() => verifyDeploymentEvidence(f.verifyOptions))
  rmSync(f.trialReport)
  assert.throws(() => verifyDeploymentEvidence(f.verifyOptions), /ENOENT/)
})

test('private trial accounts and initialized bundle credentials are excluded from accepted evidence', t => {
  const f = evidenceFixture(t)
  for (const privateFile of [path.join(path.dirname(f.trialReport), 'private-account.json'),
    path.join(path.dirname(f.trialReport), 'HANDOFF.md'), path.join(f.verifyOptions.bundleDirectory, '.env')]) {
    writeFileSync(privateFile, 'private fixture')
    assert.throws(() => verifyDeploymentEvidence(f.verifyOptions), /credentials|archive/)
    rmSync(privateFile)
  }
})

function fakeDocker({ failContainerRemoval = false } = {}) {
  const resources = {
    containers: new Set(['owned-container', 'foreign-container']), holder: new Set(['owned-holder']),
    volumes: new Set(['owned-volume', 'foreign-volume']), networks: new Set(['owned-network', 'foreign-network']),
    images: new Set([`${project}-web:latest`, 'foreign-web:latest'])
  }
  const calls = []
  const docker = async args => {
    calls.push(args)
    const filter = args[args.indexOf('--filter') + 1]
    if (args.includes('--filter')) {
      assert([`label=com.docker.compose.project=${project}`, `name=^${project}-ip-holder$`, `reference=${project}-*`].includes(filter))
      const type = args[0] === 'ps' ? (filter.startsWith('name=') ? 'holder' : 'containers') : `${args[0]}s`
      return [...resources[type]].filter(value => !value.startsWith('foreign-')).join('\n')
    }
    if (failContainerRemoval && args.includes('owned-container')) throw new Error('container removal failed')
    for (const group of Object.values(resources)) for (const value of args) group.delete(value)
    return ''
  }
  return { resources, calls, docker }
}

test('fallback cleanup removes only the reported project and its IP holder, preserving other resources', async t => {
  const f = evidenceFixture(t)
  const fake = fakeDocker()
  const result = await cleanupDeploymentEvidence({ ...f.verifyOptions, docker: fake.docker })
  assert.equal(result.passed, true)
  assert.deepEqual([...fake.resources.containers], ['foreign-container'])
  assert.deepEqual([...fake.resources.volumes], ['foreign-volume'])
  assert.deepEqual([...fake.resources.networks], ['foreign-network'])
  assert.deepEqual([...fake.resources.images], ['foreign-web:latest'])
  assert.equal(fake.resources.holder.size, 0)
})

test('one cleanup failure still attempts every other resource and makes the result fail', async t => {
  const f = evidenceFixture(t)
  const fake = fakeDocker({ failContainerRemoval: true })
  const result = await cleanupDeploymentEvidence({ ...f.verifyOptions, docker: fake.docker })
  assert.equal(result.passed, false)
  assert(result.failures.some(failure => failure.error.includes('container removal failed')))
  assert(result.failures.some(failure => failure.error.includes('Owned containers remain')))
  assert(fake.calls.some(args => args.includes('owned-volume') && args.includes('rm')))
  assert(fake.calls.some(args => args.includes('owned-network') && args.includes('rm')))
  assert.equal(fake.resources.holder.size, 0)
})

test('cleanup rejects foreign projects or mismatched revisions before invoking Docker', async t => {
  const f = evidenceFixture(t)
  const docker = async () => assert.fail('Docker must not be called')
  for (const change of [{ project: 'erp-production' }, { runtimeToolRevision: '0'.repeat(40) }]) {
    save(f.runtimeReport, { ...f.report, ...change })
    await assert.rejects(cleanupDeploymentEvidence({ ...f.verifyOptions, docker }))
  }
})

test('cleanup needs no Docker call when smoke failed before publishing its project', async t => {
  const f = fixture(t)
  const result = await cleanupDeploymentEvidence({ evidenceDirectory: path.join(f.root, 'not-started'), expectedRevision: f.revision,
    docker: async () => assert.fail('Docker must not be called') })
  assert.equal(result.passed, true)
  assert.equal(result.skipped, true)
})

test('cleanup CLI preserves a failing JSON result and nonzero exit for an invalid project', t => {
  const f = evidenceFixture(t)
  save(f.runtimeReport, { ...f.report, project: 'erp-production' })
  const result = spawnSync(process.execPath, [path.join(source, 'backend/scripts/deployment-ci.mjs'), 'cleanup',
    '--evidence', f.verifyOptions.evidenceDirectory, '--expected-revision', f.revision], { encoding: 'utf8' })
  assert.equal(result.status, 1)
  assert.equal(JSON.parse(result.stdout).passed, false)
  assert.match(JSON.parse(result.stdout).failure, /non-smoke project/)
})

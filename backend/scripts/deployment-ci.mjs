import assert from 'node:assert/strict'
import { execFile, execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { cpSync, existsSync, lstatSync, mkdirSync, readFileSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseArgs, promisify } from 'node:util'
import { verifyArtifact } from '../../frontend/scripts/release-artifact.mjs'
import { verifyDeploymentBundle } from './deployment-bundle.mjs'

const script = fileURLToPath(import.meta.url)
const defaultRepository = path.resolve(path.dirname(script), '../..')
const execute = promisify(execFile)
const revisionPattern = /^[a-f0-9]{40}$/
const projectPattern = /^erp-preview-deployment-[a-f0-9]{12}$/
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const json = file => JSON.parse(readFileSync(file, 'utf8').replace(/^\uFEFF/, ''))
const writeJson = (file, value) => writeFileSync(file, JSON.stringify(value, null, 2) + '\n', { flag: 'wx', mode: 0o600 })
const within = (parent, child) => child === parent || child.startsWith(parent + path.sep)

// CI runs frontend checks before this adapter. The backend report and frontend manifest
// bind their output bytes to the clean checkout used by the existing bundle builder.
export function prepareDeploymentCandidate({ repository = defaultRepository, frontendArtifact, outputDirectory, expectedRevision }) {
  assert(revisionPattern.test(expectedRevision || ''), 'Provide the full expected revision')
  repository = realpathSync(repository)
  const git = args => execFileSync('git', args, { cwd: repository, encoding: 'utf8' }).trim()
  const requireSource = () => {
    assert.equal(git(['rev-parse', 'HEAD']), expectedRevision, 'Checkout revision mismatch')
    assert.equal(git(['status', '--porcelain']), '', 'Candidate requires a clean checkout')
  }
  requireSource()
  const backend = path.join(repository, 'backend')
  const report = json(path.join(backend, 'target/release-check-report.json'))
  assert.equal(report.status, 'PASSED', 'Backend release check must pass')
  assert.equal(report.allowDirtyWorktree, false, 'Dirty backend candidates are not accepted')
  assert.deepEqual(report.dirtyWorktreeEntries, [])
  assert.equal(report.includeTestcontainers, true, 'CI candidate requires Testcontainers')
  assert.equal(report.maven?.status, 'PASSED')
  assert.equal(report.powerShellScriptSyntaxGate?.status, 'PASSED')
  assert(/^[a-f0-9]{7,40}$/.test(report.releaseCandidateCommit || ''), 'Missing backend revision')
  assert.equal(expectedRevision.slice(0, report.releaseCandidateCommit.length), report.releaseCandidateCommit, 'Backend revision mismatch')
  assert.equal(git(['rev-parse', `${report.releaseCandidateCommit}^{commit}`]), expectedRevision, 'Backend revision mismatch')
  const inputs = {
    'erp-server-1.0.0.jar': 'target/erp-server-1.0.0.jar',
    'bom.json': 'target/bom.json',
    'application.cdx.json': 'target/classes/META-INF/sbom/application.cdx.json'
  }
  const artifacts = {}
  for (const [destination, source] of Object.entries(inputs)) {
    const file = path.join(backend, source)
    assert(lstatSync(file).isFile() && !lstatSync(file).isSymbolicLink(), 'Backend artifacts must be regular files')
    const bytes = readFileSync(file)
    const matches = report.artifacts.filter(artifact => artifact.relativePath.replaceAll('\\', '/') === source)
    assert.equal(matches.length, 1, `Missing or duplicate backend artifact: ${source}`)
    assert.equal(matches[0].length, bytes.length, `Backend artifact size mismatch: ${source}`)
    assert.equal(matches[0].sha256, hash(bytes), `Backend artifact checksum mismatch: ${source}`)
    artifacts[destination] = hash(bytes)
  }
  const frontend = realpathSync(frontendArtifact)
  const verifiedFrontend = verifyArtifact({ artifactDirectory: frontend, expectedRevision })
  artifacts['frontend/manifest.json'] = verifiedFrontend.manifestSha256
  const requested = path.resolve(outputDirectory)
  mkdirSync(path.dirname(requested), { recursive: true })
  const output = path.join(realpathSync(path.dirname(requested)), path.basename(requested))
  assert(!within(repository, output) && !within(frontend, output), 'Candidate must be outside source and frontend inputs')
  mkdirSync(output, { mode: 0o700 }) // A partial or previous candidate is never reused.
  for (const [destination, source] of Object.entries(inputs)) cpSync(path.join(backend, source), path.join(output, destination))
  cpSync(frontend, path.join(output, 'frontend'), { recursive: true })
  const tracked = git(['ls-files', '-z']).split('\0').filter(Boolean)
  const files = Object.fromEntries(tracked.map(file => [file, hash(readFileSync(path.join(repository, file)))]))
  requireSource()
  writeJson(path.join(output, 'source-sha256.json'), { head: expectedRevision, dirtyWorktree: false, files })
  writeJson(path.join(output, 'artifact-sha256.json'), artifacts)
  writeJson(path.join(output, 'summary.json'), {
    status: 'PASSED', commit: expectedRevision, checkedAt: new Date().toISOString(),
    scope: 'CI backend release check and frontend artifact integrity; frontend checks are recorded in CI; deployment and business signoff remain separate'
  })
  return { candidateDirectory: output, applicationRevision: expectedRevision,
    artifactIndexSha256: hash(readFileSync(path.join(output, 'artifact-sha256.json'))) }
}

const deploymentChecks = [
  'Compose isolation and production defaults',
  'Build accepted artifacts and start MySQL Redis backend and Nginx',
  'Static bytes deep links cache rules and missing assets',
  'Production API authentication and proxy error boundaries',
  'Multipart attachment larger than 1 MB and download headers',
  'Browser login and refreshed business deep links',
  'Backend replacement changes IP without restarting Nginx or losing attachments',
  'Purchase payment sales receipt and restricted-user business trial'
]
const trialChecks = [
  'Verify deployed versions and bootstrap login',
  'Resolve an open business period without reopening locked periods',
  'Create clearly labelled trial master data',
  'Create a restricted order-entry user and verify denied finance access',
  'Purchase 10 units at CNY 10 and receive inventory',
  'Pay supplier installment 1: CNY 40', 'Pay supplier installment 2: CNY 60',
  'Sell 4 units at CNY 15 and verify remaining inventory',
  'Receive customer installment 1: CNY 20', 'Receive customer installment 2: CNY 40',
  'Restricted user can read its own orders and warehouse balance'
]

function requireChecks(report, names) {
  assert.equal(report.status, 'PASSED', 'Runtime report must pass')
  assert(!report.activeStep && !report.failure && !report.cleanupFailure, 'Runtime report contains an unfinished step or failure')
  assert(Number.isFinite(Date.parse(report.startedAt)) && Date.parse(report.finishedAt) >= Date.parse(report.startedAt), 'Missing runtime timestamps')
  assert.deepEqual(report.checks.map(check => check.name).sort(), [...names].sort(), 'Runtime checks are missing, duplicated or unexpected')
  assert(report.checks.every(check => check.passed === true), 'Every runtime check must pass')
}

export function verifyDeploymentEvidence({ bundleDirectory, expectedManifestSha256, evidenceDirectory, expectedRevision }) {
  assert(revisionPattern.test(expectedRevision || ''), 'Provide the full expected revision')
  const bundle = verifyDeploymentBundle({ bundleDirectory, expectedManifestSha256 })
  assert.equal(bundle.applicationRevision, expectedRevision)
  assert.equal(bundle.toolRevision, expectedRevision, 'CI application and tools must use the same checkout')
  const report = json(path.join(evidenceDirectory, 'report.json'))
  assert.equal(report.schemaVersion, 1)
  assert.equal(report.kind, 'erp-deployment-smoke')
  for (const key of ['applicationRevision', 'toolRevision', 'runtimeToolRevision']) assert.equal(report[key], expectedRevision)
  assert.equal(report.manifestSha256, expectedManifestSha256)
  assert(projectPattern.test(report.project || ''), 'Invalid disposable project')
  requireChecks(report, deploymentChecks)
  assert.equal(report.cleanupPassed, true, 'Deployment cleanup must pass')
  assert.equal(report.browserCleanup?.passed, true, 'Browser cleanup must pass')
  assert.equal(report.browserCleanup.processes.length, 1, 'Expected the owned browser process')
  assert(report.browserCleanup.processes.every(process => process.name === 'chrome' && process.passed === true && process.childClosed === true))
  const trial = json(path.join(evidenceDirectory, 'business-trial/report.json'))
  for (const key of ['sourceRevision', 'applicationRevision', 'toolRevision']) assert.equal(trial[key], expectedRevision)
  assert.equal(trial.manifestSha256, expectedManifestSha256)
  assert.equal(trial.project, report.project)
  assert.equal(trial.dirtyWorktree, false)
  requireChecks(trial, trialChecks)
  const parentTrial = report.checks.find(check => check.name === deploymentChecks.at(-1))
  assert.equal(parentTrial.report, 'business-trial/report.json')
  assert.equal(parentTrial.checks, trial.checks.length)
  assert.equal(parentTrial.retainedForInteractiveUse, false)
  for (const privateFile of ['private-account.json', 'HANDOFF.md']) {
    assert(!existsSync(path.join(evidenceDirectory, 'business-trial', privateFile)), 'Disposable trial credentials must not be archived')
  }
  assert(!existsSync(path.join(bundleDirectory, '.env')), 'CI must archive the uninitialized bundle')
  return { passed: true, applicationRevision: expectedRevision, manifestSha256: expectedManifestSha256,
    deploymentChecks: report.checks.length, businessChecks: trial.checks.length, cleanupPassed: true }
}

// Fallback for a timed-out smoke process. Queries always use the exact random
// project from its report, and all removals are attempted even if one fails.
export async function cleanupDeploymentEvidence({ evidenceDirectory, expectedRevision, docker = async args =>
  (await execute('docker', args, { timeout: 60000, maxBuffer: 1024 * 1024 })).stdout }) {
  assert(revisionPattern.test(expectedRevision || ''), 'Provide the full expected revision')
  const reportFile = path.join(evidenceDirectory, 'report.json')
  if (!existsSync(reportFile)) return { passed: true, skipped: true, reason: 'Smoke did not publish a project before starting Docker' }
  const report = json(reportFile)
  assert.equal(report.schemaVersion, 1, 'Unknown cleanup report schema')
  assert.equal(report.kind, 'erp-deployment-smoke', 'Cleanup requires a deployment smoke report')
  assert.equal(report.runtimeToolRevision, expectedRevision, 'Cleanup report revision mismatch')
  assert(projectPattern.test(report.project || ''), 'Refusing cleanup of a non-smoke project')
  const project = report.project
  const groups = [
    { name: 'containers', list: ['ps', '-aq', '--filter', `label=com.docker.compose.project=${project}`], remove: ['rm', '-f'] },
    { name: 'IP holder', list: ['ps', '-aq', '--filter', `name=^${project}-ip-holder$`], remove: ['rm', '-f'] },
    { name: 'volumes', list: ['volume', 'ls', '-q', '--filter', `label=com.docker.compose.project=${project}`], remove: ['volume', 'rm'] },
    { name: 'networks', list: ['network', 'ls', '-q', '--filter', `label=com.docker.compose.project=${project}`], remove: ['network', 'rm'] },
    { name: 'images', list: ['image', 'ls', '--format', '{{.Repository}}:{{.Tag}}', '--filter', `reference=${project}-*`], remove: ['image', 'rm'] }
  ]
  const failures = []
  for (const group of groups) {
    try {
      const resources = (await docker(group.list)).trim().split(/\s+/).filter(Boolean)
      if (resources.length) await docker([...group.remove, ...resources])
    } catch (error) { failures.push({ resource: group.name, error: error.message }) }
  }
  for (const group of groups) {
    try { assert.equal((await docker(group.list)).trim(), '', `Owned ${group.name} remain`) }
    catch (error) { failures.push({ resource: group.name, error: error.message }) }
  }
  for (const name of ['private-account.json', 'HANDOFF.md']) rmSync(path.join(evidenceDirectory, 'business-trial', name), { force: true })
  return { passed: failures.length === 0, checkedAt: new Date().toISOString(), project, failures }
}

async function main() {
  const { values, positionals } = parseArgs({ allowPositionals: true, options: {
    'frontend-artifact': { type: 'string' }, output: { type: 'string' }, 'expected-revision': { type: 'string' },
    bundle: { type: 'string' }, 'expected-manifest-sha256': { type: 'string' }, evidence: { type: 'string' }
  } })
  assert(positionals.length === 1 && ['prepare', 'verify', 'cleanup'].includes(positionals[0]), 'Use prepare, verify or cleanup')
  const options = { expectedRevision: values['expected-revision'], evidenceDirectory: values.evidence }
  const result = positionals[0] === 'prepare'
    ? prepareDeploymentCandidate({ ...options, frontendArtifact: values['frontend-artifact'], outputDirectory: values.output })
    : positionals[0] === 'verify'
      ? verifyDeploymentEvidence({ ...options, bundleDirectory: values.bundle, expectedManifestSha256: values['expected-manifest-sha256'] })
      : await cleanupDeploymentEvidence(options)
  console.log(JSON.stringify(result, null, 2))
  if (result.passed === false) process.exitCode = 1
}

if (process.argv[1] && path.resolve(process.argv[1]) === script) {
  main().catch(error => {
    console.error(error.message)
    if (process.argv[2] === 'cleanup') console.log(JSON.stringify({ passed: false, checkedAt: new Date().toISOString(), failure: error.message }, null, 2))
    process.exitCode = 1
  })
}

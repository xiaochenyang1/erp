import assert from 'node:assert/strict'
import test from 'node:test'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { cpSync, existsSync, lstatSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildDeploymentBundle, verifyDeploymentBundle, initializeDeployment } from './deployment-bundle.mjs'

const source = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const hash = value => createHash('sha256').update(value).digest('hex')
const revision = 'a'.repeat(40)

function fixture(t) {
  const root = mkdtempSync(path.join(tmpdir(), 'erp-deployment-test-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  const repo = path.join(root, 'tools')
  mkdirSync(path.join(repo, 'backend'), { recursive: true })
  cpSync(path.join(source, 'backend/deployment'), path.join(repo, 'backend/deployment'), { recursive: true })
  cpSync(path.join(source, 'backend/Dockerfile.prebuilt'), path.join(repo, 'backend/Dockerfile.prebuilt'))
  const git = args => execFileSync('git', args, { cwd: repo, stdio: 'ignore' })
  git(['init', '-q']); git(['add', '.'])
  git(['-c', 'user.name=Deployment Test', '-c', 'user.email=deployment-test@example.invalid', 'commit', '-qm', 'fixture'])
  const candidate = path.join(root, 'candidate')
  const put = (name, data) => {
    const file = path.join(candidate, name)
    mkdirSync(path.dirname(file), { recursive: true })
    writeFileSync(file, typeof data === 'object' ? JSON.stringify(data) : data)
  }
  const files = { 'build.log': 'test fixture build', 'dist/assets/app.js': 'window.fixture = true', 'dist/index.html': '<html>fixture</html>' }
  for (const [name, content] of Object.entries(files)) put(`frontend/${name}`, content)
  put('frontend/manifest.json', { schemaVersion: 1, kind: 'erp-frontend-build', sourceRevision: revision,
    dirtyWorktree: false, sourceInputSha256: 'b'.repeat(64), packageLockSha256: 'c'.repeat(64),
    buildCommand: 'npm run build', builtAt: new Date().toISOString(),
    files: Object.entries(files).map(([name, content]) => ({ path: name, size: Buffer.byteLength(content), sha256: hash(content) })) })
  put('erp-server-1.0.0.jar', 'test fixture jar, not a runnable release')
  put('bom.json', {}); put('application.cdx.json', {})
  put('summary.json', { status: 'PASSED', commit: revision })
  put('source-sha256.json', { head: revision, dirtyWorktree: false, files: {} })
  const fingerprints = Object.fromEntries(['erp-server-1.0.0.jar', 'bom.json', 'application.cdx.json', 'frontend/manifest.json']
    .map(name => [name, hash(readFileSync(path.join(candidate, name)))]))
  put('artifact-sha256.json', fingerprints)
  const options = { candidateDirectory: candidate, outputDirectory: path.join(root, 'bundle'), expectedRevision: revision,
    expectedArtifactsSha256: hash(readFileSync(path.join(candidate, 'artifact-sha256.json'))), toolRoot: repo }
  return { root, repo, candidate, put, options }
}

test('packages approved bytes and records distinct application and tool revisions', t => {
  const f = fixture(t)
  const result = buildDeploymentBundle(f.options)
  assert.equal(result.applicationRevision, revision)
  assert.notEqual(result.toolRevision, revision)
  const bundleDirectory = f.options.outputDirectory
  assert.equal(readFileSync(path.join(bundleDirectory, 'backend/docker-dist/erp-server-1.0.0.jar'), 'utf8'), 'test fixture jar, not a runnable release')
  assert.deepEqual(verifyDeploymentBundle({ bundleDirectory, expectedManifestSha256: result.manifestSha256 }), result)
  assert.equal(existsSync(path.join(bundleDirectory, '.env')), false)
  assert.throws(() => buildDeploymentBundle(f.options), /already exists/)
})

test('rejects changed candidate bytes or a mismatched independently recorded index', t => {
  const f = fixture(t)
  assert.throws(() => buildDeploymentBundle({ ...f.options, expectedArtifactsSha256: '0'.repeat(64) }), /artifact-index checksum/)
  f.put('erp-server-1.0.0.jar', 'tampered')
  assert.throws(() => buildDeploymentBundle(f.options), /artifact checksum mismatch/)
  assert.equal(existsSync(f.options.outputDirectory), false)
})

test('rejects failed candidates and mixed source revisions', t => {
  const f = fixture(t)
  f.put('summary.json', { status: 'FAILED', commit: revision })
  assert.throws(() => buildDeploymentBundle(f.options), /must have passed/)
  f.put('summary.json', { status: 'PASSED', commit: 'b'.repeat(40) })
  assert.throws(() => buildDeploymentBundle(f.options), /revision mismatch/)
})

test('rejects changed, added and linked deployment files', t => {
  const f = fixture(t)
  const built = buildDeploymentBundle(f.options)
  const options = { bundleDirectory: f.options.outputDirectory, expectedManifestSha256: built.manifestSha256 }
  const extra = path.join(options.bundleDirectory, 'unexpected.txt')
  writeFileSync(extra, 'extra')
  assert.throws(() => verifyDeploymentBundle(options), /inventory or checksum/)
  rmSync(extra)
  symlinkSync(path.join(options.bundleDirectory, 'README.md'), extra)
  assert.throws(() => verifyDeploymentBundle(options), /Symbolic link/)
  rmSync(extra)
  writeFileSync(path.join(options.bundleDirectory, 'frontend/nginx.conf'), 'tampered config')
  assert.throws(() => verifyDeploymentBundle(options), /inventory or checksum/)
})

test('initializes private credentials once without changing artifact provenance or returning secrets', t => {
  const f = fixture(t)
  const built = buildDeploymentBundle(f.options)
  const options = { bundleDirectory: f.options.outputDirectory, expectedManifestSha256: built.manifestSha256,
    origin: 'https://erp.example.com', port: 18080 }
  const result = initializeDeployment(options)
  assert.deepEqual(Object.keys(result).sort(), ['configFile', 'origin', 'port'])
  const config = Object.fromEntries(readFileSync(result.configFile, 'utf8').trim().split('\n').map(line => {
    const split = line.indexOf('='); return [line.slice(0, split), line.slice(split + 1)]
  }))
  assert.equal(config.ERP_HTTP_BIND_HOST, '127.0.0.1')
  assert.equal(config.ERP_PUBLIC_ORIGIN, options.origin)
  assert.match(config.ERP_BOOTSTRAP_ADMIN_PASSWORD, /^(?=.*[A-Za-z])(?=.*\d)\S{12,72}$/)
  assert.ok(config.ERP_JWT_SECRET.length >= 48)
  assert.notEqual(config.MYSQL_PASSWORD, config.MYSQL_ROOT_PASSWORD)
  if (process.platform !== 'win32') assert.equal(lstatSync(result.configFile).mode & 0o777, 0o600)
  assert.throws(() => initializeDeployment(options), /EEXIST/)
  assert.deepEqual(verifyDeploymentBundle(options), built)
})

test('rejects invalid origins and ports before creating any private configuration', t => {
  const f = fixture(t); const built = buildDeploymentBundle(f.options)
  const options = { bundleDirectory: f.options.outputDirectory, expectedManifestSha256: built.manifestSha256 }
  for (const origin of ['https://user:password@example.com', 'https://erp.example.com/path', 'https://erp.example.com/?x=1']) {
    assert.throws(() => initializeDeployment({ ...options, origin }), /Use an HTTP/)
  }
  assert.throws(() => initializeDeployment({ ...options, origin: 'https://erp.example.com', port: 0 }), /Invalid HTTP/)
  assert.equal(existsSync(path.join(options.bundleDirectory, '.env')), false)
})

test('rejects bundles from uncommitted deployment tool changes', t => {
  const f = fixture(t)
  writeFileSync(path.join(f.repo, 'backend/deployment/fullstack/nginx.conf'), 'uncommitted')
  assert.throws(() => buildDeploymentBundle(f.options), /Commit deployment tool changes/)
})

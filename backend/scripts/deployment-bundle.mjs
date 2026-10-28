import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createHash, randomBytes } from 'node:crypto'
import { existsSync, lstatSync, mkdirSync, readFileSync, readdirSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseArgs } from 'node:util'
import { verifyArtifact } from '../../frontend/scripts/release-artifact.mjs'

const scriptPath = fileURLToPath(import.meta.url)
const defaultToolRoot = path.resolve(path.dirname(scriptPath), '../..')
const digest = value => createHash('sha256').update(value).digest('hex')
const fullRevision = /^[a-f0-9]{40}$/
const sha256 = /^[a-f0-9]{64}$/
const within = (parent, child) => child === parent || child.startsWith(parent + path.sep)
const templates = {
  'backend/Dockerfile.prebuilt': 'backend/Dockerfile',
  'backend/deployment/fullstack/compose.yaml': 'compose.yaml',
  'backend/deployment/fullstack/frontend.Dockerfile': 'frontend/Dockerfile',
  'backend/deployment/fullstack/nginx.conf': 'frontend/nginx.conf',
  'backend/deployment/fullstack/README.md': 'README.md'
}

function bytes(file) {
  const stat = lstatSync(file)
  assert(stat.isFile() && !stat.isSymbolicLink(), `Not a regular file: ${file}`)
  return readFileSync(file)
}

function inventory(directory, prefix = '') {
  const stat = lstatSync(directory)
  assert(stat.isDirectory() && !stat.isSymbolicLink(), `Not a regular directory: ${directory}`)
  return readdirSync(directory).sort().flatMap(name => {
    assert(!name.includes('\\'), 'Bundle paths must use forward slashes')
    const relative = prefix ? `${prefix}/${name}` : name
    const file = path.join(directory, name)
    const entry = lstatSync(file)
    assert(!entry.isSymbolicLink(), `Symbolic link in bundle: ${relative}`)
    if (entry.isDirectory()) return inventory(file, relative)
    assert(entry.isFile(), `Not a regular file: ${relative}`)
    // Runtime credentials are the only mutable file, and are never copied from a candidate.
    if (relative === '.env' || relative === 'deployment-manifest.json') return []
    return [{ path: relative, size: entry.size, sha256: digest(readFileSync(file)) }]
  })
}

function loadJson(file) { return JSON.parse(bytes(file).toString('utf8').replace(/^\uFEFF/, '')) }

export function buildDeploymentBundle({ candidateDirectory, outputDirectory, expectedRevision, expectedArtifactsSha256,
  toolRoot = defaultToolRoot }) {
  assert(fullRevision.test(expectedRevision || ''), 'Provide the full application candidate revision')
  assert(sha256.test(expectedArtifactsSha256 || ''), 'Provide the independently recorded artifact-index SHA-256')
  const candidate = realpathSync(candidateDirectory)
  const indexBytes = bytes(path.join(candidate, 'artifact-sha256.json'))
  assert.equal(digest(indexBytes), expectedArtifactsSha256, 'Candidate artifact-index checksum mismatch')
  const artifacts = JSON.parse(indexBytes)
  const summary = loadJson(path.join(candidate, 'summary.json'))
  const source = loadJson(path.join(candidate, 'source-sha256.json'))
  assert.equal(summary.status, 'PASSED', 'Candidate technical gates must have passed')
  assert.equal(summary.commit, expectedRevision, 'Candidate revision mismatch')
  assert.equal(source.head, expectedRevision, 'Source manifest revision mismatch')
  assert.equal(source.dirtyWorktree, false, 'Candidate must come from a clean worktree')
  for (const name of ['erp-server-1.0.0.jar', 'bom.json', 'application.cdx.json', 'frontend/manifest.json']) {
    assert(sha256.test(artifacts[name] || ''), `Missing artifact digest: ${name}`)
    assert.equal(digest(bytes(path.join(candidate, name))), artifacts[name], `Candidate artifact checksum mismatch: ${name}`)
  }
  verifyArtifact({ artifactDirectory: path.join(candidate, 'frontend'), expectedRevision,
    expectedManifestSha256: artifacts['frontend/manifest.json'] })
  toolRoot = realpathSync(toolRoot)
  const git = args => execFileSync('git', args, { cwd: toolRoot, encoding: 'utf8' }).trim()
  const toolRevision = git(['rev-parse', 'HEAD'])
  assert(fullRevision.test(toolRevision))
  assert.equal(git(['status', '--porcelain']), '', 'Commit deployment tool changes before building a bundle')
  const requested = path.resolve(outputDirectory)
  mkdirSync(path.dirname(requested), { recursive: true })
  const output = path.join(realpathSync(path.dirname(requested)), path.basename(requested))
  assert(!within(toolRoot, output) && !within(candidate, output), 'Bundle must be outside the source and candidate directories')
  assert(!existsSync(output), 'Bundle directory already exists; choose a new directory')
  const templateBytes = Object.fromEntries(Object.entries(templates).map(([input, destination]) =>
    [destination, bytes(path.join(toolRoot, input))]))
  mkdirSync(output, { mode: 0o700 })
  const put = (name, content) => {
    const destination = path.join(output, name)
    mkdirSync(path.dirname(destination), { recursive: true })
    writeFileSync(destination, content, { flag: 'wx', mode: 0o600 })
  }
  try {
    for (const [name, content] of Object.entries(templateBytes)) put(name, content)
    put('backend/docker-dist/erp-server-1.0.0.jar', bytes(path.join(candidate, 'erp-server-1.0.0.jar')))
    for (const name of ['bom.json', 'application.cdx.json', 'summary.json', 'artifact-sha256.json']) {
      put(`evidence/${name}`, bytes(path.join(candidate, name)))
    }
    // The verifier has already rejected extra files, path traversal and symlinks in the frontend artifact.
    const frontend = loadJson(path.join(candidate, 'frontend/manifest.json'))
    for (const file of [...frontend.files, { path: 'manifest.json' }]) {
      put(`frontend/artifact/${file.path}`, bytes(path.join(candidate, 'frontend', file.path)))
    }
    const deployment = { applicationRevision: expectedRevision, toolRevision,
      jarSha256: artifacts['erp-server-1.0.0.jar'], frontendManifestSha256: artifacts['frontend/manifest.json'] }
    put('frontend/deployment.json', JSON.stringify(deployment, null, 2) + '\n')
    assert.equal(git(['rev-parse', 'HEAD']), toolRevision, 'Deployment tool revision changed while bundling')
    assert.equal(git(['status', '--porcelain']), '', 'Deployment tools changed while bundling')
    const manifest = { schemaVersion: 1, kind: 'erp-fullstack-deployment', ...deployment,
      candidateArtifactsSha256: expectedArtifactsSha256, toolDirtyWorktree: false,
      createdAt: new Date().toISOString(), scope: 'Deployment artifact integrity; target environment and business acceptance remain separate',
      files: inventory(output) }
    put('deployment-manifest.json', JSON.stringify(manifest, null, 2) + '\n')
    const manifestSha256 = digest(bytes(path.join(output, 'deployment-manifest.json')))
    return verifyDeploymentBundle({ bundleDirectory: output, expectedManifestSha256: manifestSha256 })
  } catch (error) {
    rmSync(path.join(output, 'deployment-manifest.json'), { force: true })
    throw error
  }
}

export function verifyDeploymentBundle({ bundleDirectory, expectedManifestSha256 }) {
  assert(sha256.test(expectedManifestSha256 || ''), 'Provide the independently recorded bundle manifest SHA-256')
  const directory = path.resolve(bundleDirectory)
  const manifestBytes = bytes(path.join(directory, 'deployment-manifest.json'))
  assert.equal(digest(manifestBytes), expectedManifestSha256, 'Deployment manifest checksum mismatch')
  const manifest = JSON.parse(manifestBytes)
  assert.equal(manifest.schemaVersion, 1)
  assert.equal(manifest.kind, 'erp-fullstack-deployment')
  assert(fullRevision.test(manifest.applicationRevision || '') && fullRevision.test(manifest.toolRevision || ''))
  assert.equal(manifest.toolDirtyWorktree, false)
  assert.deepEqual(inventory(directory), manifest.files, 'Deployment inventory or checksum mismatch')
  assert.equal(digest(bytes(path.join(directory, 'backend/docker-dist/erp-server-1.0.0.jar'))), manifest.jarSha256)
  verifyArtifact({ artifactDirectory: path.join(directory, 'frontend/artifact'), expectedRevision: manifest.applicationRevision,
    expectedManifestSha256: manifest.frontendManifestSha256 })
  assert.deepEqual(loadJson(path.join(directory, 'frontend/deployment.json')), {
    applicationRevision: manifest.applicationRevision, toolRevision: manifest.toolRevision,
    jarSha256: manifest.jarSha256, frontendManifestSha256: manifest.frontendManifestSha256
  })
  return { applicationRevision: manifest.applicationRevision, toolRevision: manifest.toolRevision,
    manifestSha256: expectedManifestSha256, files: manifest.files.length }
}

export function initializeDeployment({ bundleDirectory, expectedManifestSha256, origin, port = 8080 }) {
  verifyDeploymentBundle({ bundleDirectory, expectedManifestSha256 })
  assert(typeof origin === 'string' && origin.trim(), 'Provide the actual public origin')
  const url = new URL(origin)
  assert(['http:', 'https:'].includes(url.protocol) && !url.username && !url.password &&
    !url.search && !url.hash && url.pathname === '/', 'Use an HTTP(S) origin without credentials, path, query or fragment')
  assert(Number.isInteger(port) && port > 0 && port <= 65535, 'Invalid HTTP listen port')
  const secret = () => randomBytes(32).toString('hex')
  const values = { ERP_PUBLIC_ORIGIN: url.origin, ERP_HTTP_BIND_HOST: '127.0.0.1', ERP_HTTP_PORT: port,
    MYSQL_ROOT_PASSWORD: secret(), MYSQL_PASSWORD: secret(), ERP_REDIS_PASSWORD: secret(),
    ERP_JWT_SECRET: randomBytes(48).toString('hex'), ERP_BOOTSTRAP_ADMIN_PASSWORD: `Erp1${secret()}`,
    ERP_TRUSTED_PROXIES: '' }
  const file = path.join(path.resolve(bundleDirectory), '.env')
  writeFileSync(file, Object.entries(values).map(([key, value]) => `${key}=${value}`).join('\n') + '\n',
    { flag: 'wx', mode: 0o600 })
  return { configFile: file, origin: url.origin, port }
}

function main() {
  const { values, positionals } = parseArgs({ allowPositionals: true, options: {
    candidate: { type: 'string' }, output: { type: 'string' }, bundle: { type: 'string' },
    'expected-revision': { type: 'string' }, 'expected-artifacts-sha256': { type: 'string' },
    'expected-manifest-sha256': { type: 'string' }, origin: { type: 'string' }, port: { type: 'string' }
  } })
  assert(positionals.length === 1 && ['build', 'verify', 'init'].includes(positionals[0]),
    'Usage: deployment-bundle.mjs build --candidate DIR --output DIR --expected-revision SHA --expected-artifacts-sha256 SHA | verify/init --bundle DIR --expected-manifest-sha256 SHA [--origin URL --port PORT]')
  const options = { bundleDirectory: values.bundle, expectedManifestSha256: values['expected-manifest-sha256'] }
  const result = positionals[0] === 'build'
    ? buildDeploymentBundle({ candidateDirectory: values.candidate, outputDirectory: values.output,
      expectedRevision: values['expected-revision'], expectedArtifactsSha256: values['expected-artifacts-sha256'] })
    : positionals[0] === 'verify' ? verifyDeploymentBundle(options)
      : initializeDeployment({ ...options, origin: values.origin, port: Number(values.port || 8080) })
  console.log(JSON.stringify(result, null, 2))
}

if (process.argv[1] && path.resolve(process.argv[1]) === scriptPath) {
  try { main() } catch (error) { console.error(error.message); process.exitCode = 1 }
}

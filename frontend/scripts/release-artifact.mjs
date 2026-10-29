import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { closeSync, copyFileSync, existsSync, lstatSync, mkdirSync, openSync, readFileSync, readdirSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseArgs } from 'node:util'

const scriptPath = fileURLToPath(import.meta.url)
const defaultFrontendRoot = path.resolve(path.dirname(scriptPath), '..')
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const revisionPattern = /^[a-f0-9]{40}$/
const hashPattern = /^[a-f0-9]{64}$/
const inside = (parent, child) => child === parent || child.startsWith(`${parent}${path.sep}`)

function filesIn(directory, prefix = '') {
  assert(lstatSync(directory).isDirectory() && !lstatSync(directory).isSymbolicLink(), `Not a regular directory: ${directory}`)
  return readdirSync(directory).sort().flatMap(name => {
    assert(!name.includes('\\'), 'Artifact paths must use forward slashes')
    const fullPath = path.join(directory, name)
    const relativePath = prefix ? `${prefix}/${name}` : name
    const stat = lstatSync(fullPath)
    assert(!stat.isSymbolicLink(), `Symbolic links are not allowed: ${relativePath}`)
    if (stat.isDirectory()) return filesIn(fullPath, relativePath)
    assert(stat.isFile(), `Not a regular file: ${relativePath}`)
    return [{ path: relativePath, size: stat.size, sha256: hash(readFileSync(fullPath)) }]
  })
}

function sourceSnapshot(frontendRoot) {
  const git = args => execFileSync('git', args, { cwd: frontendRoot, encoding: 'utf8' }).trim()
  const revision = git(['rev-parse', 'HEAD'])
  assert(revisionPattern.test(revision), 'A committed source revision is required')
  const status = git(['status', '--porcelain', '--untracked-files=all'])
  const paths = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z', '--', '.'],
    { cwd: frontendRoot, encoding: 'utf8' }).split('\0').filter(Boolean).sort()
  const inputs = paths.map(file => {
    const fullPath = path.join(frontendRoot, file)
    assert(lstatSync(fullPath).isFile() && !lstatSync(fullPath).isSymbolicLink(), `Not a regular build input: ${file}`)
    return [file, hash(readFileSync(fullPath))]
  })
  return { revision, status, inputSha256: hash(JSON.stringify(inputs)) }
}

function npmCommand(args) {
  if (process.env.npm_execpath) return [process.execPath, [process.env.npm_execpath, ...args]]
  if (process.platform === 'win32') return ['cmd.exe', ['/d', '/s', '/c', 'npm', ...args]]
  return ['npm', args]
}

export function verifyArtifact({ artifactDirectory, expectedRevision, expectedManifestSha256, allowDirtyWorktree = false }) {
  assert(revisionPattern.test(expectedRevision || ''), 'Provide the full expected source revision')
  const directory = path.resolve(artifactDirectory)
  const actualFiles = filesIn(directory)
  const manifestBytes = readFileSync(path.join(directory, 'manifest.json'))
  const manifestSha256 = hash(manifestBytes)
  if (expectedManifestSha256 !== undefined) {
    assert(hashPattern.test(expectedManifestSha256), 'Provide a valid expected manifest SHA-256')
    assert.equal(manifestSha256, expectedManifestSha256, 'Frontend manifest checksum mismatch')
  }
  const manifest = JSON.parse(manifestBytes.toString('utf8'))
  assert.equal(manifest.schemaVersion, 1, 'Unsupported frontend artifact schema')
  assert.equal(manifest.kind, 'erp-frontend-build', 'Not a frontend build artifact')
  assert.equal(manifest.sourceRevision, expectedRevision, 'Frontend source revision mismatch')
  assert.equal(typeof manifest.dirtyWorktree, 'boolean', 'Missing worktree provenance')
  assert(allowDirtyWorktree || !manifest.dirtyWorktree, 'Dirty-worktree artifacts cannot be released')
  assert(hashPattern.test(manifest.sourceInputSha256 || ''), 'Missing build input fingerprint')
  assert(hashPattern.test(manifest.packageLockSha256 || ''), 'Missing dependency lock fingerprint')
  assert.equal(manifest.buildCommand, 'npm run build')
  assert(Number.isFinite(Date.parse(manifest.builtAt)), 'Missing build timestamp')
  assert(Array.isArray(manifest.files), 'Missing artifact file inventory')
  assert(manifest.files.some(file => file.path === 'dist/index.html' && file.size > 0), 'Missing built index.html')
  assert(manifest.files.some(file => /^dist\/assets\/.+\.js$/.test(file.path) && file.size > 0), 'Missing built JavaScript assets')
  assert(manifest.files.some(file => file.path === 'build.log'), 'Missing build log')
  for (const file of manifest.files) {
    assert(typeof file.path === 'string' && file.path !== 'manifest.json' && !file.path.includes('\\') &&
      !file.path.startsWith('/') && !file.path.split('/').some(part => ['', '.', '..'].includes(part)), 'Invalid artifact path')
    assert(Number.isSafeInteger(file.size) && file.size >= 0 && hashPattern.test(file.sha256 || ''), 'Invalid file fingerprint')
  }
  assert.deepEqual(actualFiles.filter(file => file.path !== 'manifest.json'), manifest.files,
    'Frontend artifact inventory or checksum mismatch (changed, missing, or extra files)')
  return { sourceRevision: manifest.sourceRevision, dirtyWorktree: manifest.dirtyWorktree,
    files: manifest.files.length, manifestSha256 }
}

export function buildArtifact({ frontendRoot = defaultFrontendRoot, outputDirectory, allowDirtyWorktree = false }) {
  frontendRoot = realpathSync(frontendRoot)
  const before = sourceSnapshot(frontendRoot)
  assert(allowDirtyWorktree || !before.status, 'Commit candidate changes before building release artifacts')
  const requested = path.resolve(outputDirectory)
  mkdirSync(path.dirname(requested), { recursive: true })
  const output = path.join(realpathSync(path.dirname(requested)), path.basename(requested))
  const dist = path.join(frontendRoot, 'dist')
  assert(!inside(output, frontendRoot) && !inside(dist, output), 'Artifact output must not replace sources or dist')
  assert(!existsSync(output), 'Artifact directory already exists; choose a new directory')
  mkdirSync(output, { mode: 0o700 })
  try {
    // Always rebuild: a previous dist directory cannot become a new candidate by relabeling it.
    rmSync(dist, { recursive: true, force: true })
    const log = openSync(path.join(output, 'build.log'), 'wx', 0o600)
    let result
    try {
      const [command, args] = npmCommand(['run', 'build'])
      result = spawnSync(command, args, { cwd: frontendRoot, stdio: ['ignore', log, log], timeout: 600_000 })
    } finally {
      closeSync(log)
    }
    assert(!result.error && result.status === 0 && !result.signal, `Frontend build failed; see ${path.join(output, 'build.log')}`)
    const distFiles = filesIn(dist)
    assert(distFiles.some(file => file.path === 'index.html' && file.size > 0), 'Build did not produce index.html')
    for (const file of distFiles) {
      const destination = path.join(output, 'dist', file.path)
      mkdirSync(path.dirname(destination), { recursive: true })
      copyFileSync(path.join(dist, file.path), destination)
    }
    const after = sourceSnapshot(frontendRoot)
    assert.deepEqual(after, before, 'Source changed during the frontend build; discard this candidate')
    const [npm, args] = npmCommand(['--version'])
    const manifest = {
      schemaVersion: 1, kind: 'erp-frontend-build', sourceRevision: before.revision,
      dirtyWorktree: Boolean(before.status), sourceInputSha256: before.inputSha256,
      packageLockSha256: hash(readFileSync(path.join(frontendRoot, 'package-lock.json'))),
      builtAt: new Date().toISOString(), buildCommand: 'npm run build',
      runtime: { node: process.version, npm: execFileSync(npm, args, { cwd: frontendRoot, encoding: 'utf8' }).trim(), platform: process.platform },
      ci: process.env.GITHUB_ACTIONS === 'true' ? { repository: process.env.GITHUB_REPOSITORY,
        runId: process.env.GITHUB_RUN_ID, runAttempt: process.env.GITHUB_RUN_ATTEMPT } : null,
      scope: 'Build provenance and file integrity; business acceptance remains separate.',
      files: filesIn(output),
    }
    writeFileSync(path.join(output, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, { flag: 'wx', mode: 0o600 })
    return verifyArtifact({ artifactDirectory: output, expectedRevision: before.revision, allowDirtyWorktree })
  } catch (error) {
    rmSync(path.join(output, 'manifest.json'), { force: true })
    writeFileSync(path.join(output, 'failure.json'), `${JSON.stringify({ status: 'FAILED', reason: error.message }, null, 2)}\n`, { mode: 0o600 })
    throw error
  }
}

function main() {
  const { values, positionals } = parseArgs({ allowPositionals: true, options: {
    output: { type: 'string' }, artifact: { type: 'string' }, 'expected-revision': { type: 'string' },
    'expected-manifest-sha256': { type: 'string' },
    'allow-dirty-worktree': { type: 'boolean', default: false },
  } })
  assert(positionals.length === 1 && ['build', 'verify'].includes(positionals[0]),
    'Usage: release-artifact.mjs build [--output DIR] | verify --artifact DIR --expected-revision SHA')
  const allowDirtyWorktree = values['allow-dirty-worktree']
  const result = positionals[0] === 'build'
    ? buildArtifact({ outputDirectory: values.output || path.join(defaultFrontendRoot, 'release-artifacts'), allowDirtyWorktree })
    : verifyArtifact({ artifactDirectory: values.artifact, expectedRevision: values['expected-revision'],
      expectedManifestSha256: values['expected-manifest-sha256'], allowDirtyWorktree })
  console.log(JSON.stringify(result, null, 2))
}

if (process.argv[1] && path.resolve(process.argv[1]) === scriptPath) {
  try { main() } catch (error) { console.error(error.message); process.exitCode = 1 }
}

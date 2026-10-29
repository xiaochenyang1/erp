import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, rmSync, symlinkSync, writeFileSync, existsSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { buildArtifact, verifyArtifact } from './release-artifact.mjs'

function fixture(t, buildSource = '') {
  const root = mkdtempSync(path.join(os.tmpdir(), 'erp-frontend-artifact-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  const frontendRoot = path.join(root, 'repo', 'frontend')
  mkdirSync(frontendRoot, { recursive: true })
  writeFileSync(path.join(frontendRoot, '.gitignore'), 'dist/\nrelease-artifacts/\n')
  writeFileSync(path.join(frontendRoot, 'package.json'), JSON.stringify({ name: 'fixture', version: '1.0.0', scripts: { build: 'node build.cjs' } }))
  writeFileSync(path.join(frontendRoot, 'package-lock.json'), '{"lockfileVersion":3}\n')
  writeFileSync(path.join(frontendRoot, 'build.cjs'), `const fs = require('node:fs');
    fs.mkdirSync('dist/assets', {recursive:true});
    fs.writeFileSync('dist/index.html', '<script src="/assets/app.js"></script>');
    fs.writeFileSync('dist/assets/app.js', 'console.log("ERP")');
    ${buildSource}`)
  const git = args => execFileSync('git', args, { cwd: frontendRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
  git(['init', '-q', '..'])
  git(['add', '.'])
  git(['-c', 'user.name=Artifact Test', '-c', 'user.email=artifact-test@example.invalid', '-c', 'commit.gpgsign=false', 'commit', '-qm', 'fixture'])
  return { root, frontendRoot, outputDirectory: path.join(root, 'evidence'), expectedRevision: git(['rev-parse', 'HEAD']) }
}

test('builds fresh assets and verifies an offline artifact against the expected candidate', t => {
  const f = fixture(t)
  mkdirSync(path.join(f.frontendRoot, 'dist'), { recursive: true })
  writeFileSync(path.join(f.frontendRoot, 'dist', 'stale.js'), 'old candidate')
  const result = buildArtifact(f)
  assert.equal(result.dirtyWorktree, false)
  assert.equal(result.sourceRevision, f.expectedRevision)
  assert.equal(existsSync(path.join(f.outputDirectory, 'dist', 'stale.js')), false)
  rmSync(path.join(f.root, 'repo'), { recursive: true, force: true })
  assert.deepEqual(verifyArtifact({ artifactDirectory: f.outputDirectory, expectedRevision: f.expectedRevision }), result)
})

for (const [name, mutate] of [
  ['changed asset', dir => writeFileSync(path.join(dir, 'dist/assets/app.js'), 'tampered')],
  ['missing asset', dir => rmSync(path.join(dir, 'dist/assets/app.js'))],
  ['extra asset', dir => writeFileSync(path.join(dir, 'dist/assets/extra.js'), 'unexpected')],
  ['symbolic link', dir => { rmSync(path.join(dir, 'dist/assets/app.js')); symlinkSync('../index.html', path.join(dir, 'dist/assets/app.js')) }],
]) {
  test(`rejects ${name} in a downloaded artifact`, t => {
    const f = fixture(t)
    buildArtifact(f)
    mutate(f.outputDirectory)
    assert.throws(() => verifyArtifact({ artifactDirectory: f.outputDirectory, expectedRevision: f.expectedRevision }), /checksum mismatch|Symbolic links/)
  })
}

test('rejects another source revision and unsafe manifest paths', t => {
  const f = fixture(t)
  buildArtifact(f)
  assert.throws(() => verifyArtifact({ artifactDirectory: f.outputDirectory, expectedRevision: 'a'.repeat(40) }), /revision mismatch/)
  const manifestPath = path.join(f.outputDirectory, 'manifest.json')
  const manifest = JSON.parse(readFileSync(manifestPath))
  manifest.files[0].path = '../outside'
  writeFileSync(manifestPath, JSON.stringify(manifest))
  assert.throws(() => verifyArtifact({ artifactDirectory: f.outputDirectory, expectedRevision: f.expectedRevision }), /Invalid artifact path|Missing build log/)
})

test('pins downloaded evidence to the original manifest when provenance is rewritten', t => {
  const f = fixture(t)
  const built = buildArtifact(f)
  const options = { artifactDirectory: f.outputDirectory, expectedRevision: f.expectedRevision,
    expectedManifestSha256: built.manifestSha256 }
  assert.deepEqual(verifyArtifact(options), built)
  const manifestPath = path.join(f.outputDirectory, 'manifest.json')
  const manifest = JSON.parse(readFileSync(manifestPath))
  manifest.sourceInputSha256 = 'a'.repeat(64)
  writeFileSync(manifestPath, JSON.stringify(manifest))
  assert.throws(() => verifyArtifact(options), /manifest checksum mismatch/)
  assert.throws(() => verifyArtifact({ ...options, expectedManifestSha256: '' }), /valid expected manifest/)
})

test('the verification CLI checks the downloaded manifest digest', t => {
  const f = fixture(t)
  const built = buildArtifact(f)
  const script = fileURLToPath(new URL('./release-artifact.mjs', import.meta.url))
  const output = execFileSync(process.execPath, [script, 'verify', '--artifact', f.outputDirectory,
    '--expected-revision', f.expectedRevision, '--expected-manifest-sha256', built.manifestSha256], { encoding: 'utf8' })
  assert.deepEqual(JSON.parse(output), built)
  assert.throws(() => execFileSync(process.execPath, [script, 'verify', '--artifact', f.outputDirectory,
    '--expected-revision', f.expectedRevision, '--expected-manifest-sha256', '0'.repeat(64)],
  { stdio: ['ignore', 'pipe', 'pipe'] }), /manifest checksum mismatch/)
})

test('dirty builds require an explicit local override and cannot pass default release verification', t => {
  const f = fixture(t)
  writeFileSync(path.join(f.frontendRoot, 'local-change.txt'), 'not committed')
  assert.throws(() => buildArtifact(f), /Commit candidate changes/)
  assert.equal(existsSync(f.outputDirectory), false)
  buildArtifact({ ...f, allowDirtyWorktree: true })
  assert.throws(() => verifyArtifact({ artifactDirectory: f.outputDirectory, expectedRevision: f.expectedRevision }), /cannot be released/)
  assert.equal(verifyArtifact({ artifactDirectory: f.outputDirectory, expectedRevision: f.expectedRevision, allowDirtyWorktree: true }).dirtyWorktree, true)
})

test('refuses to overwrite existing evidence', t => {
  const f = fixture(t)
  mkdirSync(f.outputDirectory)
  writeFileSync(path.join(f.outputDirectory, 'original.txt'), 'keep')
  assert.throws(() => buildArtifact(f), /already exists/)
  assert.equal(readFileSync(path.join(f.outputDirectory, 'original.txt'), 'utf8'), 'keep')
})

test('source changes during the build leave failure diagnostics without a release manifest', t => {
  const f = fixture(t, "fs.appendFileSync('package-lock.json', ' ');")
  assert.throws(() => buildArtifact(f), /Source changed/)
  assert.equal(existsSync(path.join(f.outputDirectory, 'manifest.json')), false)
  assert.equal(JSON.parse(readFileSync(path.join(f.outputDirectory, 'failure.json'))).status, 'FAILED')
})

test('a failing build cannot reuse previous dist files', t => {
  const f = fixture(t, 'process.exit(1);')
  assert.throws(() => buildArtifact(f), /Frontend build failed/)
  assert.equal(existsSync(path.join(f.outputDirectory, 'manifest.json')), false)
  assert.equal(existsSync(path.join(f.outputDirectory, 'build.log')), true)
})

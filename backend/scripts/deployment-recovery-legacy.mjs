import assert from 'node:assert/strict'
import { cp, copyFile, lstat, mkdir, readFile, realpath, rm, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { run } from './production-rehearsal.mjs'
import { hash, legacyRevision, readJson, revisionPattern, sha256Pattern, writeJson } from './recovery-checkpoint.mjs'
import { filesIn } from '../../frontend/scripts/release-artifact.mjs'

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

// Git pathspecs below are repository-relative. The shared process runner otherwise
// defaults to backend/, where `backend/...` would select the wrong subtree.
export const runLegacyGit = (args, repository = repo) => run('git', args, { cwd: repository })

export async function verifyArchivedInputs(source, entries) {
  const fingerprints = []
  for (const entry of entries) {
    const match = /^(100644|100755) blob ([a-f0-9]{40})\t(frontend\/.+)$/.exec(entry)
    assert(match && !match[3].split('/').some(part => ['', '.', '..'].includes(part)), 'Invalid historical frontend input')
    const file = path.join(source, match[3]), stat = await lstat(file)
    assert(stat.isFile() && !stat.isSymbolicLink())
    const bytes = await readFile(file)
    const blob = createHash('sha1').update(`blob ${bytes.length}\0`).update(bytes).digest('hex')
    assert.equal(blob, match[2], `Historical frontend input changed: ${match[3]}`)
    fingerprints.push([match[3], hash(bytes)])
  }
  assert(fingerprints.length > 0)
  return hash(JSON.stringify(fingerprints))
}

export function verifyLegacyFrontend(directory, expectedManifestSha256, expectedToolRevision) {
  const manifest = readJson(path.join(directory, 'manifest.json'))
  assert(sha256Pattern.test(expectedManifestSha256 || ''))
  const files = filesIn(directory)
  assert.equal(files.find(file => file.path === 'manifest.json')?.sha256, expectedManifestSha256)
  assert.equal(manifest.schemaVersion, 1)
  assert.equal(manifest.kind, 'erp-historical-frontend')
  assert.equal(manifest.sourceRevision, legacyRevision)
  assert.equal(manifest.toolRevision, expectedToolRevision)
  assert.equal(manifest.buildPassed, true)
  assert(sha256Pattern.test(manifest.sourceInputSha256 || ''))
  assert.deepEqual(files.filter(file => file.path !== 'manifest.json'), manifest.files, 'Historical frontend bytes changed')
  assert(manifest.files.some(file => file.path === 'dist/index.html' && file.size > 0))
  assert(manifest.files.some(file => /^dist\/assets\/.+\.js$/.test(file.path) && file.size > 0))
  return manifest
}

export async function prepareLegacyArtifact(outputDirectory) {
  const git = async args => (await runLegacyGit(args)).stdout.trim()
  const toolRevision = await git(['rev-parse', 'HEAD'])
  assert(revisionPattern.test(toolRevision))
  assert.equal(await git(['status', '--porcelain']), '', 'Commit recovery tooling before preparing historical evidence')
  await git(['merge-base', '--is-ancestor', legacyRevision, toolRevision])
  const migrations = (await git(['ls-tree', '-r', '--name-only', legacyRevision, 'backend/src/main/resources/db/migration']))
    .split('\n').filter(Boolean)
  const versions = migrations.map(file => Number(path.basename(file).match(/^V(\d+)__/)[1]))
  assert.equal(Math.max(...versions), 168, 'The pinned historical source must end at V168')
  const requested = path.resolve(outputDirectory)
  await mkdir(path.dirname(requested), { recursive: true })
  const output = path.join(await realpath(path.dirname(requested)), path.basename(requested))
  assert(output !== repo && !output.startsWith(repo + path.sep), 'Historical build must be outside the source tree')
  await mkdir(output, { mode: 0o700 })
  const source = path.join(output, 'source')
  await mkdir(source)
  const archive = path.join(output, 'source.tar')
  await runLegacyGit(['archive', '--format=tar', `--output=${archive}`, legacyRevision, 'backend', 'frontend'])
  await run('tar', ['-xf', archive, '-C', source])
  await rm(archive)
  const build = await run('bash', ['./mvnw', '-B', '-DskipTests', 'package'], {
    cwd: path.join(source, 'backend'), timeout: 420000, allowFailure: true
  })
  await writeFile(path.join(output, 'build.log'), build.stdout + build.stderr)
  assert.equal(build.code, 0, 'Historical fixture build failed; see legacy/build.log')
  const jar = path.join(source, 'backend/target/erp-server-1.0.0.jar')
  // Verify the packaged migrations against the extracted historical source.
  await run('python3', ['-c',
    'import sys,zipfile,pathlib; root=pathlib.Path(sys.argv[1]); z=zipfile.ZipFile(sys.argv[2]); files=list((root/"backend/src/main/resources/db/migration").glob("V*.sql")); packaged=[n for n in z.namelist() if n.startswith("BOOT-INF/classes/db/migration/V") and n.endswith(".sql")]; assert len(packaged)==len(files); assert all(z.read("BOOT-INF/classes/db/migration/"+f.name)==f.read_bytes() for f in files)',
    source, jar])
  for (const file of migrations) {
    assert.equal(hash(await readFile(path.join(source, file))), hash((await runLegacyGit(['show', `${legacyRevision}:${file}`])).stdout), 'Historical migration changed during build')
  }
  await copyFile(jar, path.join(output, 'erp-server-v168.jar'))
  const inputs = (await runLegacyGit(['ls-tree', '-r', '-z', legacyRevision, 'frontend'])).stdout.split('\0').filter(Boolean)
  const sourceInputSha256 = await verifyArchivedInputs(source, inputs)
  const frontendSource = path.join(source, 'frontend'), frontendOutput = path.join(output, 'frontend')
  const install = await run('npm', ['ci', '--audit=false'], { cwd: frontendSource, timeout: 300000, allowFailure: true })
  await writeFile(path.join(output, 'frontend-install.log'), install.stdout + install.stderr)
  assert.equal(install.code, 0, 'Historical frontend dependency install failed; see legacy/frontend-install.log')
  const frontendBuild = await run('npm', ['run', 'build'], { cwd: frontendSource, timeout: 300000, allowFailure: true })
  await writeFile(path.join(output, 'frontend-build.log'), frontendBuild.stdout + frontendBuild.stderr)
  assert.equal(frontendBuild.code, 0, 'Historical frontend build failed; see legacy/frontend-build.log')
  assert.equal(await verifyArchivedInputs(source, inputs), sourceInputSha256)
  await mkdir(frontendOutput)
  await cp(path.join(frontendSource, 'dist'), path.join(frontendOutput, 'dist'), { recursive: true })
  writeJson(path.join(frontendOutput, 'manifest.json'), { schemaVersion: 1, kind: 'erp-historical-frontend',
    sourceRevision: legacyRevision, toolRevision, sourceInputSha256, buildPassed: true,
    files: filesIn(frontendOutput), scope: 'Pinned historical frontend for isolated rollback; not a release candidate' })
  const frontendManifestSha256 = hash(await readFile(path.join(frontendOutput, 'manifest.json')))
  verifyLegacyFrontend(frontendOutput, frontendManifestSha256, toolRevision)
  const metadata = { schemaVersion: 1, kind: 'erp-historical-fixture', sourceRevision: legacyRevision,
    toolRevision, schema: '168', migrationCount: migrations.length, jar: 'erp-server-v168.jar',
    jarSha256: hash(await readFile(jar)), frontend: 'frontend', frontendManifestSha256, buildPassed: true, testsSkipped: true,
    scope: 'Pinned historical backend and frontend for upgrade fixtures and isolated rollback; not a release candidate' }
  writeJson(path.join(output, 'artifact.json'), metadata)
  return { ...metadata, legacyDirectory: output, manifestSha256: hash(await readFile(path.join(output, 'artifact.json'))) }
}

export async function verifyLegacyArtifact(directory, expectedManifestSha256, expectedToolRevision) {
  assert(sha256Pattern.test(expectedManifestSha256 || ''), 'Provide the independently recorded historical artifact digest')
  directory = await realpath(directory)
  const metadataFile = path.join(directory, 'artifact.json')
  assert.equal(hash(await readFile(metadataFile)), expectedManifestSha256)
  const metadata = readJson(metadataFile)
  assert.equal(metadata.schemaVersion, 1)
  assert.equal(metadata.kind, 'erp-historical-fixture')
  assert.equal(metadata.sourceRevision, legacyRevision)
  assert.equal(metadata.toolRevision, expectedToolRevision)
  assert.equal(metadata.schema, '168')
  assert.equal(metadata.buildPassed, true)
  assert.equal(metadata.jar, 'erp-server-v168.jar')
  assert(existsSync(path.join(directory, metadata.jar)))
  const stat = await lstat(path.join(directory, metadata.jar))
  assert(stat.isFile() && !stat.isSymbolicLink(), 'Historical JAR must be a regular file')
  assert.equal(hash(await readFile(path.join(directory, metadata.jar))), metadata.jarSha256, 'Historical JAR checksum mismatch')
  assert.equal(metadata.frontend, 'frontend')
  verifyLegacyFrontend(path.join(directory, metadata.frontend), metadata.frontendManifestSha256, expectedToolRevision)
  return metadata
}

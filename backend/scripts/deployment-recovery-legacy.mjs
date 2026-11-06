import assert from 'node:assert/strict'
import { copyFile, lstat, mkdir, readFile, realpath, rm, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { run } from './production-rehearsal.mjs'
import { hash, legacyRevision, readJson, revisionPattern, sha256Pattern, writeJson } from './recovery-checkpoint.mjs'

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

export async function prepareLegacyArtifact(outputDirectory) {
  const git = async args => (await run('git', args)).stdout.trim()
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
  await run('git', ['archive', '--format=tar', `--output=${archive}`, legacyRevision, 'backend'])
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
    assert.equal(hash(await readFile(path.join(source, file))), hash((await run('git', ['show', `${legacyRevision}:${file}`])).stdout), 'Historical migration changed during build')
  }
  await copyFile(jar, path.join(output, 'erp-server-v168.jar'))
  const metadata = { schemaVersion: 1, kind: 'erp-historical-fixture', sourceRevision: legacyRevision,
    toolRevision, schema: '168', migrationCount: migrations.length, jar: 'erp-server-v168.jar',
    jarSha256: hash(await readFile(jar)), buildPassed: true, testsSkipped: true,
    scope: 'Pinned historical application built solely to produce V168 upgrade fixtures; not a release candidate' }
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
  return metadata
}

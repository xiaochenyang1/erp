import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { lstatSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'

export const targetSchema = '173'
export const legacyRevision = '8766ef0ac966da8628bbdfc2b9bbb12c6e93ba17'
export const recoveryProjectPattern = /^erp-preview-recovery-[a-f0-9]{12}$/
export const hash = bytes => createHash('sha256').update(bytes).digest('hex')
export const readJson = file => JSON.parse(readFileSync(file, 'utf8').replace(/^\uFEFF/, ''))
export const sha256Pattern = /^[a-f0-9]{64}$/
export const revisionPattern = /^[a-f0-9]{40}$/
export const writeJson = (file, value) => writeFileSync(file, JSON.stringify(value, null, 2) + '\n', { flag: 'wx', mode: 0o600 })

function fileRecord(directory, name) {
  assert(typeof name === 'string' && /^[a-zA-Z0-9_.-]+$/.test(name) && !['.', '..'].includes(name), 'Invalid checkpoint filename')
  const file = path.join(directory, name)
  const stat = lstatSync(file)
  assert(stat.isFile() && !stat.isSymbolicLink(), 'Checkpoint inputs must be regular files')
  return { file: name, bytes: stat.size, sha256: hash(readFileSync(file)) }
}

export function validateCheckpoint(manifest, { applicationRevision, manifestSha256 }) {
  assert.equal(manifest.schemaVersion, 1)
  assert.equal(manifest.kind, 'erp-recovery-checkpoint')
  assert.equal(manifest.sourceRevision, legacyRevision, 'Unexpected V168 source revision')
  assert.equal(manifest.sourceSchema, '168')
  assert.equal(manifest.targetSchema, targetSchema)
  assert(revisionPattern.test(applicationRevision || '') && sha256Pattern.test(manifestSha256 || ''))
  assert.equal(manifest.applicationRevision, applicationRevision)
  assert.equal(manifest.deploymentManifestSha256, manifestSha256)
  assert.equal(manifest.writersStopped, true, 'A joint checkpoint requires stopped writers')
  assert(recoveryProjectPattern.test(manifest.project || ''))
  assert(Number.isFinite(Date.parse(manifest.createdAt)))
  assert.deepEqual(Object.keys(manifest.artifacts).sort(), ['attachmentArchive', 'attachmentSnapshot', 'database', 'databaseChecksum'])
  for (const file of Object.values(manifest.artifacts)) {
    assert(/^[a-zA-Z0-9_.-]+$/.test(file.file) && !['.', '..'].includes(file.file))
    assert(Number.isSafeInteger(file.bytes) && file.bytes > 0 && sha256Pattern.test(file.sha256))
  }
  assert(manifest.artifacts.database.file.endsWith('.sql.gz'))
  assert.equal(manifest.artifacts.databaseChecksum.file, manifest.artifacts.database.file + '.sha256')
  assert.equal(manifest.artifacts.attachmentArchive.file, 'attachments.tar.gz')
  assert.equal(manifest.artifacts.attachmentSnapshot.file, 'attachments.json')
  assert(manifest.databaseSnapshot.tableCount > 100 && sha256Pattern.test(manifest.databaseSnapshot.dataSha256))
  assert(manifest.databaseSnapshot.rowCounts?.length > 100)
  return manifest
}

export function sealCheckpoint({ directory, databaseFile, applicationRevision, manifestSha256, project, databaseSnapshot }) {
  const manifest = {
    schemaVersion: 1, kind: 'erp-recovery-checkpoint', createdAt: new Date().toISOString(), project,
    sourceRevision: legacyRevision, sourceSchema: '168', targetSchema, applicationRevision,
    deploymentManifestSha256: manifestSha256, writersStopped: true, databaseSnapshot,
    artifacts: Object.fromEntries(Object.entries({ database: databaseFile, databaseChecksum: databaseFile + '.sha256',
      attachmentArchive: 'attachments.tar.gz', attachmentSnapshot: 'attachments.json' })
      .map(([key, name]) => [key, fileRecord(directory, name)]))
  }
  validateCheckpoint(manifest, { applicationRevision, manifestSha256 })
  const checksum = readFileSync(path.join(directory, databaseFile + '.sha256'), 'utf8').trim().split(/\r?\n/)
  assert.equal(checksum.length, 1, 'Expected exactly one database checksum')
  assert.equal(checksum[0].trim().split(/\s+/)[0].toLowerCase(), manifest.artifacts.database.sha256, 'Database checksum mismatch')
  const file = path.join(directory, 'backup-manifest.json')
  writeJson(file, manifest)
  return { manifest, checkpointSha256: hash(readFileSync(file)) }
}

// This preflight runs before either restore command. A valid DB backup and a
// valid attachment backup from different checkpoints must not be combined.
export function verifyCheckpoint({ directory, expectedCheckpointSha256, applicationRevision, manifestSha256 }) {
  assert(sha256Pattern.test(expectedCheckpointSha256 || ''), 'Provide the independently recorded checkpoint digest')
  const record = fileRecord(directory, 'backup-manifest.json')
  assert.equal(record.sha256, expectedCheckpointSha256, 'Checkpoint manifest checksum mismatch')
  const manifest = validateCheckpoint(readJson(path.join(directory, record.file)), { applicationRevision, manifestSha256 })
  for (const expected of Object.values(manifest.artifacts)) {
    assert.deepEqual(fileRecord(directory, expected.file), expected, 'Checkpoint artifact checksum or size mismatch')
  }
  return manifest
}

import assert from 'node:assert/strict'
import test from 'node:test'
import { mkdtempSync, mkdirSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { hash, sealCheckpoint, verifyCheckpoint } from './recovery-checkpoint.mjs'

function fixture(t) {
  const directory = mkdtempSync(path.join(tmpdir(), 'erp-checkpoint-test-'))
  t.after(() => rmSync(directory, { recursive: true, force: true }))
  const applicationRevision = 'a'.repeat(40), manifestSha256 = 'b'.repeat(64)
  writeFileSync(path.join(directory, 'database.sql.gz'), 'database bytes')
  writeFileSync(path.join(directory, 'database.sql.gz.sha256'), `${hash('database bytes')}  database.sql.gz\n`)
  writeFileSync(path.join(directory, 'attachments.tar.gz'), 'attachment bytes')
  writeFileSync(path.join(directory, 'attachments.json'), '{"entries":[]}')
  const { checkpointSha256 } = sealCheckpoint({ directory, databaseFile: 'database.sql.gz', applicationRevision, manifestSha256,
    project: 'erp-preview-recovery-abcdef123456', databaseSnapshot: { tableCount: 131, dataSha256: 'c'.repeat(64), rowCounts: 'x'.repeat(200) } })
  return { directory, expectedCheckpointSha256: checkpointSha256, applicationRevision, manifestSha256 }
}

test('joint checkpoint binds database and attachments to source and target versions', t => {
  const options = fixture(t)
  const result = verifyCheckpoint(options)
  assert.equal(result.sourceSchema, '168')
  assert.equal(result.targetSchema, '169')
  assert.equal(result.writersStopped, true)
  assert.equal(Object.keys(result.artifacts).length, 4)
})

for (const file of ['database.sql.gz', 'database.sql.gz.sha256', 'attachments.tar.gz', 'attachments.json']) {
  test(`rejects changed or missing ${file} before restore`, t => {
    const options = fixture(t)
    writeFileSync(path.join(options.directory, file), 'different checkpoint')
    assert.throws(() => verifyCheckpoint(options), /checksum or size mismatch/)
    rmSync(path.join(options.directory, file))
    assert.throws(() => verifyCheckpoint(options), /ENOENT/)
  })
}

test('regenerating a manifest cannot change the independently recorded checkpoint', t => {
  const options = fixture(t)
  const file = path.join(options.directory, 'backup-manifest.json')
  const manifest = JSON.parse(readFileSync(file))
  manifest.writersStopped = false
  writeFileSync(file, JSON.stringify(manifest))
  assert.throws(() => verifyCheckpoint(options), /manifest checksum mismatch/)
  assert.throws(() => verifyCheckpoint({ ...options, expectedCheckpointSha256: hash(readFileSync(file)) }), /stopped writers/)
})

test('different candidate and symbolic links are rejected', t => {
  const options = fixture(t)
  assert.throws(() => verifyCheckpoint({ ...options, applicationRevision: 'd'.repeat(40) }))
  const outside = path.join(options.directory, 'other')
  mkdirSync(outside)
  const file = path.join(options.directory, 'attachments.tar.gz')
  writeFileSync(path.join(outside, 'archive'), readFileSync(file))
  rmSync(file)
  symlinkSync(path.join(outside, 'archive'), file)
  assert.throws(() => verifyCheckpoint(options), /regular files/)
})

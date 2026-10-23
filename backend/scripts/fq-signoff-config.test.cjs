const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadConfig, evidenceContext } = require('./fq-signoff-config.cjs')

const remote = {
  BASE_URL: 'https://preprod.example.test', ERP_FQ_ENVIRONMENT: 'preprod',
  ERP_FQ_ADMIN_USERNAME: 'acceptance-admin', ERP_FQ_ADMIN_PASSWORD: 'private-admin-password',
  ERP_FQ_SUBMITTER_USERNAME: 'acceptance-submitter', ERP_FQ_SUBMITTER_PASSWORD: 'private-submitter-password',
  ERP_FQ_DATABASE_INSTANCE: 'preprod-db', ERP_FQ_REDIS_INSTANCE: 'preprod-redis', ERP_FQ_DOCKER_PROFILE: 'core',
  ERP_FQ_RELEASE_COMMIT: 'd2d6975faccb2d71fb8218ab4c59accbc1c421df',
  ERP_FQ_EVIDENCE_DIRECTORY: '/tmp/fq-test-preprod-evidence',
}

test('local defaults preserve the existing local-runtime entry point', () => {
  const config = loadConfig({})
  assert.equal(config.baseUrl, 'http://127.0.0.1:8080')
  assert.equal(config.admin.username, 'admin')
  assert.equal(config.submitter.username, 'runtime_smoke')
  assert.equal(config.environment, 'local-runtime')
  assert.equal(config.releaseCommit, null)
})

test('remote target uses supplied credentials and deployment metadata', () => {
  const config = loadConfig(remote)
  assert.deepEqual(config.admin, { username: 'acceptance-admin', password: 'private-admin-password' })
  assert.deepEqual(config.submitter, { username: 'acceptance-submitter', password: 'private-submitter-password' })
  assert.equal(config.databaseInstance, 'preprod-db')
  assert.equal(config.releaseCommit, remote.ERP_FQ_RELEASE_COMMIT)
})

test('remote targets never fall back to local credentials or environment labels', () => {
  assert.throws(() => loadConfig({ BASE_URL: remote.BASE_URL }), /ERP_FQ_ENVIRONMENT/)
  for (const field of Object.keys(remote).filter(key => key.startsWith('ERP_FQ_'))) {
    const missing = { ...remote }; delete missing[field]
    assert.throws(() => loadConfig(missing), new RegExp(field))
  }
})

test('preprod accessed through a local tunnel still requires explicit deployment configuration', () => {
  assert.throws(() => loadConfig({ BASE_URL: 'http://127.0.0.1:19080', ERP_FQ_ENVIRONMENT: 'preprod' }), /ERP_FQ_/)
  assert.equal(loadConfig({ ...remote, BASE_URL: 'http://127.0.0.1:19080' }).environment, 'preprod')
})

test('preprod cannot default to the local server when its address is missing', () => {
  assert.throws(() => loadConfig({ ...remote, BASE_URL: '' }), /BASE_URL is required/)
})

test('mislabeling a remote URL as local-runtime is rejected', () => {
  assert.throws(() => loadConfig({ ...remote, ERP_FQ_ENVIRONMENT: 'local-runtime' }), /loopback/)
})

test('URLs containing embedded credentials cannot enter reports', () => {
  assert.throws(() => loadConfig({ ...remote, BASE_URL: 'https://user:private@example.test' }), /without credentials/)
  assert.throws(() => loadConfig({ ...remote, BASE_URL: 'https://example.test?token=private' }), /without credentials/)
})

test('public context separates the deployed candidate from the tool revision and excludes credentials', () => {
  const context = evidenceContext(loadConfig(remote), process.env.ERP_FQ_TEST_REPO || process.cwd())
  assert.equal(context.candidateRevision, remote.ERP_FQ_RELEASE_COMMIT)
  assert.match(context.sourceRevision, /^[a-f0-9]{40}$/)
  assert.equal(context.environment, 'preprod')
  assert(!JSON.stringify(context).includes('private-'))
  assert(!JSON.stringify(context).includes('acceptance-admin'))
})

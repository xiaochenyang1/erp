const path = require('node:path')
const { execFileSync } = require('node:child_process')

function loadConfig(env = process.env) {
  if (env.ERP_FQ_ENVIRONMENT && env.ERP_FQ_ENVIRONMENT !== 'local-runtime' && !env.BASE_URL?.trim()) {
    throw new Error('BASE_URL is required for this target')
  }
  let url
  try { url = new URL(env.BASE_URL || 'http://127.0.0.1:8080') } catch { throw new Error('BASE_URL is invalid') }
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error('BASE_URL must be an HTTP(S) URL without credentials, query or fragment')
  }
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
  const environment = env.ERP_FQ_ENVIRONMENT || (loopback ? 'local-runtime' : '')
  if (!environment) throw new Error('ERP_FQ_ENVIRONMENT is required for a non-local target')
  if (environment === 'local-runtime' && !loopback) throw new Error('local-runtime requires a loopback BASE_URL')
  const local = environment === 'local-runtime' && loopback
  const evidenceDirectory = env.ERP_FQ_EVIDENCE_DIRECTORY || env.ERP_EVIDENCE_DIRECTORY
  if (!local && !evidenceDirectory?.trim()) throw new Error('ERP_FQ_EVIDENCE_DIRECTORY is required for this target')
  const value = (name, fallback) => {
    const result = env[name] || (local ? fallback : undefined)
    if (typeof result !== 'string' || !result.trim()) throw new Error(`${name} is required for this target`)
    return result
  }
  const releaseCommit = local && !env.ERP_FQ_RELEASE_COMMIT ? null : value('ERP_FQ_RELEASE_COMMIT')
  if (releaseCommit && !/^[a-f0-9]{7,40}$/i.test(releaseCommit)) throw new Error('ERP_FQ_RELEASE_COMMIT must identify the deployed Git commit')
  return {
    baseUrl: url.toString().replace(/\/$/, ''),
    outDir: path.resolve(evidenceDirectory || path.join('target', 'fq-signoff-api-check')),
    admin: { username: value('ERP_FQ_ADMIN_USERNAME', 'admin'), password: value('ERP_FQ_ADMIN_PASSWORD', 'LocalAdmin123') },
    submitter: { username: value('ERP_FQ_SUBMITTER_USERNAME', 'runtime_smoke'), password: value('ERP_FQ_SUBMITTER_PASSWORD', 'RuntimeSmoke123') },
    environment,
    databaseInstance: value('ERP_FQ_DATABASE_INSTANCE', 'erp_codex_runtime'),
    redisInstance: value('ERP_FQ_REDIS_INSTANCE', 'none-local'),
    dockerProfile: value('ERP_FQ_DOCKER_PROFILE', 'skipped'),
    releaseCommit,
  }
}

function evidenceContext(config, repo) {
  const git = args => execFileSync('git', args, { cwd: repo, encoding: 'utf8' }).trim()
  const sourceRevision = git(['rev-parse', 'HEAD'])
  return {
    baseUrl: config.baseUrl,
    sourceRevision,
    dirtyWorktree: git(['status', '--porcelain']).length > 0,
    candidateRevision: config.releaseCommit || sourceRevision,
    environment: config.environment,
    databaseInstance: config.databaseInstance,
    redisInstance: config.redisInstance,
    dockerProfile: config.dockerProfile,
  }
}

module.exports = { loadConfig, evidenceContext }

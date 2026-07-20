/**
 * One-shot F/Q technical precheck + register readiness evidence (not business GO).
 * Usage: node scripts/fq-signoff-all.cjs
 */
const { spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080'
const ROOT = path.resolve(__dirname, '..')

function runNode(script) {
  const r = spawnSync(process.execPath, [path.join(ROOT, 'scripts', script)], {
    cwd: ROOT,
    encoding: 'utf8',
    env: process.env,
  })
  if (r.stdout) process.stdout.write(r.stdout)
  if (r.stderr) process.stderr.write(r.stderr)
  if (r.status !== 0) throw new Error(`${script} exited ${r.status}`)
}

async function login() {
  const r = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'LocalAdmin123' }),
  })
  const j = await r.json()
  if (String(j.code) !== '0') throw new Error(j.message || 'login failed')
  return j.data.accessToken
}

async function api(token, method, p, body) {
  const opts = {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  }
  if (body !== undefined) opts.body = JSON.stringify(body)
  const r = await fetch(`${BASE}${p}`, opts)
  const j = await r.json()
  return { http: r.status, body: j }
}

function ok(res) {
  return String(res.body?.code) === '0'
}

async function main() {
  console.log('[fq-all] 1/3 run forward API check')
  runNode('fq-signoff-api-check.cjs')
  console.log('[fq-all] 2/3 run human-item negative checks')
  runNode('fq-signoff-human-items.cjs')

  const reportPath = path.join(ROOT, 'target/fq-signoff-api-check/report.json')
  const humanPath = path.join(ROOT, 'target/fq-signoff-api-check/human-items-report.json')
  const forward = JSON.parse(fs.readFileSync(reportPath, 'utf8'))
  const human = JSON.parse(fs.readFileSync(humanPath, 'utf8'))

  // treat human-items as resolving F10/F11/Q4/Q5
  const humanPass = human.summary.failed === 0
  const forwardHardFail = (forward.results || []).filter((r) => !r.pass && !r.needsHuman).length === 0
  const technicalPass = humanPass && forwardHardFail

  const summary = {
    generatedAt: new Date().toISOString(),
    technicalPass,
    forward: forward.summary,
    human: human.summary,
    finalForm: 'docs/FQ-SIGNOFF-FINAL-2026-07-16.md',
  }
  fs.writeFileSync(
    path.join(ROOT, 'target/fq-signoff-api-check/all-summary.json'),
    JSON.stringify(summary, null, 2),
  )

  console.log('[fq-all] 3/3 register readiness technical item')
  const token = await login()
  const commit = spawnSync('git', ['rev-parse', '--short', 'HEAD'], { cwd: ROOT, encoding: 'utf8' })
    .stdout.trim()

  const runRes = await api(token, 'POST', '/api/system/readiness/runs', {
    releaseCommit: commit || 'local',
    releaseVersion: 'fq-technical-precheck',
    environment: 'local-runtime',
    databaseInstance: 'erp_codex_runtime',
    redisInstance: 'none-local',
    dockerProfile: 'skipped',
    generateDefaultItems: false,
    recordPreflightEvidence: false,
    remark: 'F/Q technical precheck auto registration (not business GO)',
  })
  if (!ok(runRes)) throw new Error(`create run: ${runRes.body?.message}`)
  const runId = runRes.body.data.id
  const runNo = runRes.body.data.runNo

  const itemRes = await api(token, 'POST', `/api/system/readiness/runs/${runId}/items`, {
    itemCode: 'FQ_TECHNICAL_PRECHECK',
    itemName: '财务质检技术预检（含负例）',
    category: 'ACCEPTANCE',
    priority: 'P0',
    expectedResult: 'F1-F12/Q1-Q5 库内状态与闸门负例 API 全通过；业务签字另计',
  })
  if (!ok(itemRes)) throw new Error(`create item: ${itemRes.body?.message}`)
  const itemId = itemRes.body.data.id

  const detail = [
    `technicalPass=${technicalPass}`,
    `forward autoPass=${forward.summary.autoPass} autoFail=${forward.summary.autoFail} needsHuman=${forward.summary.needsHuman}`,
    `human passed=${human.summary.passed}/${human.summary.total}`,
    `reports: target/fq-signoff-api-check/report.json + human-items-report.json`,
    `sign form: docs/FQ-SIGNOFF-FINAL-2026-07-16.md`,
  ].join('\n')

  const evRes = await api(token, 'POST', `/api/system/readiness/items/${itemId}/evidence`, {
    evidenceType: 'API',
    requestMethod: 'NODE',
    requestUri: 'scripts/fq-signoff-all.cjs',
    httpStatus: 200,
    businessType: 'FQ_TECHNICAL_PRECHECK',
    businessNo: runNo,
    summary: technicalPass ? 'F/Q technical precheck PASSED' : 'F/Q technical precheck FAILED',
    detail,
  })
  if (!ok(evRes)) throw new Error(`evidence: ${evRes.body?.message}`)

  const resultRes = await api(token, 'POST', `/api/system/readiness/items/${itemId}/result`, {
    status: technicalPass ? 'PASSED' : 'FAILED',
    actualResult: detail,
    failureReason: technicalPass ? null : 'see detail',
  })
  if (!ok(resultRes)) throw new Error(`result: ${resultRes.body?.message}`)

  // Do NOT submit GO decision — business signoff required
  const out = {
    ...summary,
    readinessRunId: String(runId),
    readinessRunNo: runNo,
    readinessItemId: String(itemId),
    note: 'Item marked PASSED technically; overall release decision left to humans after FQ-SIGNOFF-FINAL signatures.',
  }
  fs.writeFileSync(path.join(ROOT, 'target/fq-signoff-api-check/all-summary.json'), JSON.stringify(out, null, 2))
  console.log(JSON.stringify(out, null, 2))
  if (!technicalPass) process.exit(1)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})

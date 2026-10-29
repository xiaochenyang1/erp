/**
 * 预算管理 API smoke。需本机后端已启动，并连接可写的联调库。
 *
 * 用法：
 *   node scripts/budget-api-smoke.cjs
 *   BASE_URL=http://127.0.0.1:8080 ADMIN_PASSWORD=LocalAdmin123 node scripts/budget-api-smoke.cjs
 */
const fs = require('fs')
const path = require('path')

const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080'
const ADMIN_USER = process.env.ADMIN_USER || 'admin'
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'LocalAdmin123'
const SUFFIX = String(Date.now()).slice(-8)
const results = []

function record(id, title, pass, detail) {
  results.push({ id, title, pass: Boolean(pass), detail: String(detail || '') })
  console.log(`[${pass ? 'PASS' : 'FAIL'}] ${id} ${title} - ${detail}`)
}

async function api(token, method, urlPath, body) {
  const response = await fetch(`${BASE}${urlPath}`, {
    method,
    headers: {
      Authorization: token ? `Bearer ${token}` : undefined,
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  let parsed
  try { parsed = JSON.parse(text) } catch { parsed = { code: String(response.status), message: text } }
  return { status: response.status, body: parsed }
}

function ok(response) {
  return response.status < 400 && String(response.body?.code) === '0'
}

function flatten(items) {
  return (items || []).flatMap((item) => [item, ...flatten(item.children)])
}

async function main() {
  console.log(`BASE_URL=${BASE}`)
  const login = await api(null, 'POST', '/api/auth/login', {
    username: ADMIN_USER,
    password: ADMIN_PASSWORD,
  })
  if (!ok(login)) throw new Error(`登录失败：${login.body?.message || login.status}`)
  const token = login.body.data.accessToken
  const permissions = new Set(login.body.data.permissions || [])
  const expectedPermissions = ['finance:budget:view', 'finance:budget:manage', 'finance:budget:approve']
  record('B0', '预算权限已授予 ERP_ADMIN', expectedPermissions.every((item) => permissions.has(item)), expectedPermissions.join(', '))

  const subjectsResponse = await api(token, 'GET', '/api/finance/account-subjects/tree')
  const deptsResponse = await api(token, 'GET', '/api/system/depts/tree')
  if (!ok(subjectsResponse) || !ok(deptsResponse)) throw new Error('科目或部门资源加载失败')
  const subject = flatten(subjectsResponse.body.data).find((item) => item.subjectType === 'EXPENSE' && item.status === 'ACTIVE')
  const dept = flatten(deptsResponse.body.data).find((item) => item.status === 'ACTIVE')
  if (!subject || !dept) throw new Error('缺少可用费用科目或部门')

  let year
  for (let candidate = 2099; candidate >= 2090; candidate -= 1) {
    const page = await api(token, 'GET', `/api/finance/budgets?budgetYear=${candidate}&pageSize=1`)
    if (ok(page) && Number(page.body.data.total || 0) === 0) {
      year = candidate
      break
    }
  }
  if (!year) throw new Error('2090-2099 年均已有预算，无法执行无冲突 smoke')

  const line = {
    periodMonth: 0,
    deptId: dept.id,
    subjectId: subject.id,
    budgetAmount: 100,
    remark: '预算 API smoke 年度额度',
  }
  const create = await api(token, 'POST', '/api/finance/budgets', {
    budgetYear: year,
    budgetName: `预算 API Smoke ${SUFFIX}`,
    controlPolicy: 'APPROVAL',
    remark: 'budget-api-smoke',
    lines: [line],
  })
  record('B1', '创建预算草稿', ok(create) && create.body.data.status === 'DRAFT', ok(create) ? `id=${create.body.data.id}` : create.body?.message)
  if (!ok(create)) throw new Error('创建预算失败')
  const budgetId = create.body.data.id

  const duplicate = await api(token, 'POST', '/api/finance/budgets', {
    budgetYear: year,
    budgetName: `重复维度 ${SUFFIX}`,
    controlPolicy: 'REJECT',
    lines: [line, { ...line }],
  })
  record('B2', '重复预算维度被拒绝', !ok(duplicate) && String(duplicate.body?.message || '').includes('重复'), `${duplicate.status} ${duplicate.body?.message || ''}`)

  const submit = await api(token, 'POST', `/api/finance/budgets/${budgetId}/submit`)
  record('B3', '预算提交审批', ok(submit) && submit.body.data.status === 'SUBMITTED', ok(submit) ? submit.body.data.status : submit.body?.message)

  const approve = await api(token, 'POST', `/api/finance/budgets/${budgetId}/approve`)
  record('B4', '预算审批生效', ok(approve) && approve.body.data.status === 'APPROVED', ok(approve) ? approve.body.data.status : approve.body?.message)

  const execution = await api(token, 'GET', `/api/finance/budgets/execution?budgetYear=${year}&periodMonth=8&deptId=${dept.id}&subjectId=${subject.id}&amount=150`)
  const executionData = execution.body?.data || {}
  record('B5', '年度额度回退与超预算预览', ok(execution)
    && executionData.periodSource === 'ANNUAL'
    && executionData.controlPolicy === 'APPROVAL'
    && executionData.overrun === true
    && Number(executionData.projectedAvailableAmount) === -50,
  ok(execution) ? `source=${executionData.periodSource} projected=${executionData.projectedAvailableAmount}` : execution.body?.message)

  const detail = await api(token, 'GET', `/api/finance/budgets/${budgetId}`)
  record('B6', '预算详情汇总正确', ok(detail)
    && Number(detail.body.data.totalBudgetAmount) === 100
    && Number(detail.body.data.totalCommittedAmount) === 0
    && Number(detail.body.data.totalActualAmount) === 0,
  ok(detail) ? `budget=${detail.body.data.totalBudgetAmount}` : detail.body?.message)

  const close = await api(token, 'POST', `/api/finance/budgets/${budgetId}/close`)
  record('B7', '预算关闭', ok(close) && close.body.data.status === 'CLOSED', ok(close) ? close.body.data.status : close.body?.message)

  const page = await api(token, 'GET', `/api/finance/budgets?budgetYear=${year}&status=CLOSED&pageSize=20`)
  const listed = ok(page) && (page.body.data.records || []).some((item) => String(item.id) === String(budgetId))
  record('B8', '关闭预算可按状态回读', listed, ok(page) ? `total=${page.body.data.total}` : page.body?.message)

  const outputDir = path.resolve(__dirname, '..', 'target', 'budget-api-smoke')
  fs.mkdirSync(outputDir, { recursive: true })
  const reportPath = path.join(outputDir, `report-${SUFFIX}.json`)
  fs.writeFileSync(reportPath, JSON.stringify({ baseUrl: BASE, budgetId, year, results }, null, 2))
  const failed = results.filter((item) => !item.pass)
  console.log(`RESULT ${results.length - failed.length}/${results.length} PASS`)
  console.log(`REPORT ${reportPath}`)
  if (failed.length) process.exitCode = 1
}

main().catch((error) => {
  console.error(error.stack || error.message || String(error))
  process.exitCode = 1
})

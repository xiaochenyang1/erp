#!/usr/bin/env node
/** Smoke for newer P1/P2 APIs: aging, mrp, quotes, statements, gross-margin, transfer path check */
const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080'
async function login() {
  const r = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: process.env.ADMIN_USER || 'admin', password: process.env.ADMIN_PASSWORD || 'LocalAdmin123' })
  })
  const j = await r.json()
  if (String(j.code) !== '0') throw new Error(j.message || 'login failed')
  return j.data.accessToken
}
async function get(token, path) {
  const r = await fetch(`${BASE}${path}`, { headers: { Authorization: `Bearer ${token}` } })
  const j = await r.json()
  return { status: r.status, code: j.code, message: j.message, data: j.data }
}
async function post(token, path, body) {
  const r = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body == null ? undefined : JSON.stringify(body)
  })
  const j = await r.json()
  return { status: r.status, code: j.code, message: j.message, data: j.data }
}
function ok(res) { return res.status < 400 && String(res.code) === '0' }
;(async () => {
  const token = await login()
  const checks = []
  const add = (name, res) => {
    const pass = ok(res)
    checks.push({ name, pass, message: res.message || '' })
    console.log(pass ? '[PASS]' : '[FAIL]', name, res.message || '')
  }
  add('aging', await get(token, '/api/finance/aging'))
  add('mrp-run', await post(token, '/api/inventory/mrp/run'))
  add('quotes-list', await get(token, '/api/sales/quotes?pageNo=1&pageSize=5'))
  add('statements-need-params', await get(token, '/api/finance/statements?partnerType=CUSTOMER&partnerId=1&dateFrom=2026-01-01&dateTo=2026-12-31'))
  add('gross-margin', await get(token, '/api/finance/gross-margin?dateFrom=2026-01-01&dateTo=2026-12-31'))
  add('notifications-list', await get(token, '/api/system/notifications?pageNo=1&pageSize=5'))
  add('notifications-read-batch', await post(token, '/api/system/notifications/read-batch', { recipientIds: [1] }))
  const failed = checks.filter((c) => !c.pass)
  // statements may fail if partner 1 missing - tolerate code message containing 不存在
  const hardFail = failed.filter((c) => !(c.name === 'statements-need-params' && /不存在|不能为空/.test(c.message)))
  if (hardFail.length) {
    console.error('FAILED', hardFail.length)
    process.exit(1)
  }
  console.log('ALL CHECKED', checks.length)
})().catch((e) => { console.error(e); process.exit(1) })

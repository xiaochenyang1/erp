#!/usr/bin/env node
/**
 * C7: 打印 sys_menu / sys_role_menu / sys_sequence_rule 已占用 id 上沿，
 * 避免新 Flyway 种子复用主键被 ON DUPLICATE KEY 静默吞掉（V99/V100 教训）。
 *
 * 用法（后端需可登录；默认 local admin）:
 *   node scripts/check-seed-id-occupancy.cjs
 *
 * 若无专用查询 API，则输出「手工 SQL」与建议下一 id 段。
 */
const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080'
const USER = process.env.ADMIN_USER || 'admin'
const PASS = process.env.ADMIN_PASSWORD || 'LocalAdmin123'

async function login() {
  const r = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASS }),
  })
  const j = await r.json()
  if (String(j.code) !== '0') throw new Error(j.message || 'login failed')
  return j.data.accessToken
}

async function main() {
  console.log('=== Seed ID occupancy helper (C7) ===')
  console.log('Known collision fixes: V99 seq 2014, V100 menu 5310-5313')
  console.log('')
  console.log('Recommended next free ranges (verify against DB before use):')
  console.log('  sys_sequence_rule.id  >= 2033  (2031=FIN_INVOICE, 2032=PURCHASE_INQUIRY)')
  console.log('  sys_menu.id           >= 5380  (5340 invoice, 5350 inquiry, 5360 price, 5370 aging)')
  console.log('  sys_role_menu.id      use snowflake OR max(id)+1 after SELECT')
  console.log('  sys_config.id         avoid 5401 (webhook); check MAX(id) first')
  console.log('')
  console.log('Run against MySQL erp_codex_runtime:')
  console.log(`  SELECT MAX(id) FROM sys_menu;`)
  console.log(`  SELECT MAX(id) FROM sys_sequence_rule;`)
  console.log(`  SELECT id, biz_type FROM sys_sequence_rule ORDER BY id;`)
  console.log(`  SELECT id, menu_code, permission FROM sys_menu WHERE id >= 5200 ORDER BY id;`)
  console.log('')

  try {
    const token = await login()
    console.log('[ok] backend login succeeded; use DB client for authoritative MAX(id).')
    console.log(`     token acquired for ${USER} @ ${BASE}`)
    // Health ping only — seed tables are not exposed as public max-id APIs by design.
    const h = await fetch(`${BASE}/actuator/health`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    console.log(`[ok] health HTTP ${h.status}`)
  } catch (e) {
    console.warn('[warn] backend not reachable; print static guidance only:', e.message)
  }

  console.log('')
  console.log('Before writing a new seed migration:')
  console.log('  1) SELECT occupied ids for the same table')
  console.log('  2) Never reuse ids already in ANY historical V* seed')
  console.log('  3) Prefer high water + gap; add a *SeedCollision* regression test if critical')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})

#!/usr/bin/env bash
# Freeze a clean local candidate and collect isolated technical evidence.
set -Eeuo pipefail
backend_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
repo_root="$(git -C "${backend_root}" rev-parse --show-toplevel)"
[[ $# == 1 ]] || { echo 'Usage: release-candidate-local.sh /absolute/new/evidence-directory' >&2; exit 1; }
evidence_root="$(node -p 'require("path").resolve(process.argv[1])' "$1")"
case "${evidence_root}/" in "${repo_root}/"*) echo 'Use an evidence directory outside the repository.' >&2; exit 1;; esac
[[ ! -e "${evidence_root}" ]] || { echo 'Evidence directory already exists; choose a new directory.' >&2; exit 1; }
[[ -z "$(git -C "${repo_root}" status --porcelain)" ]] || { echo 'Commit candidate changes before collecting release evidence.' >&2; exit 1; }
mkdir -p "${evidence_root}"
candidate="$(git -C "${repo_root}" rev-parse HEAD)"
printf '%s\n' "${candidate}" > "${evidence_root}/candidate-sha.txt"
last_gate=initialization
completed=false

collect() {
  # Preserve available diagnostics even when a gate fails. Never clean evidence.
  for directory in surefire-reports extension-features-api-smoke data-scope-api-smoke; do
    if [[ -d "${backend_root}/target/${directory}" ]]; then
      cp -R "${backend_root}/target/${directory}" "${evidence_root}/"
    fi
  done
  for file in "${backend_root}"/target/ui-smoke* "${backend_root}"/target/monitoring-runtime-*; do
    [[ ! -e "${file}" ]] || cp -R "${file}" "${evidence_root}/"
  done
  node --input-type=module - "${evidence_root}" "${candidate}" "${last_gate}" "${completed}" <<'JS'
import { writeFileSync } from 'node:fs'
const [dir, commit, lastGate, completed] = process.argv.slice(2)
writeFileSync(`${dir}/summary.json`, JSON.stringify({commit, lastGate, status: completed === 'true' ? 'PASSED' : 'FAILED', checkedAt: new Date().toISOString(), scope: 'local technical gates; preproduction and business signoff remain separate'}, null, 2) + '\n')
JS
}
trap collect EXIT

run_gate() {
  last_gate="$1"
  local directory="$2"
  shift 2
  echo "[candidate] ${last_gate}"
  if ! (cd "${directory}" && "$@") > "${evidence_root}/${last_gate}.log" 2>&1; then
    tail -35 "${evidence_root}/${last_gate}.log" >&2
    exit 1
  fi
}

run_gate frontend-audit "${repo_root}/frontend" npm audit --omit=dev --audit-level=high --registry=https://registry.npmjs.org
run_gate rehearsal-command-tests "${backend_root}" node --test scripts/production-rehearsal.test.mjs
run_gate fq-config-tests "${backend_root}" node --test scripts/fq-signoff-config.test.cjs
run_gate backend-release "${backend_root}" ./scripts/test-local.sh --release-check \
  -MavenRepoLocal "${ERP_MAVEN_REPO_LOCAL:-${HOME}/.m2/repository}" \
  -ReportDirectory "${evidence_root}/release-check"
cp "${backend_root}/target/erp-server-1.0.0.jar" "${evidence_root}/"
cp "${backend_root}/target/bom.json" "${evidence_root}/"
cp "${backend_root}/target/classes/META-INF/sbom/application.cdx.json" "${evidence_root}/"
for task in type-check lint test check:contracts build; do
  run_gate "frontend-${task//:/-}" "${repo_root}/frontend" npm run "${task}"
done
cp -R "${repo_root}/frontend/dist" "${evidence_root}/frontend-dist"
run_gate monitoring-config "${backend_root}" ./scripts/verify-monitoring.sh
run_gate monitoring-runtime "${backend_root}" node scripts/monitoring-runtime-smoke.mjs
run_gate ui-and-api "${backend_root}" env UI_SMOKE_ROUTES=1 UI_SMOKE_WORKFLOW= \
  UI_SMOKE_FRONTEND_MODE=preview ERP_UI_SMOKE_API_REGRESSION=1 ERP_UI_SMOKE_FQ_REGRESSION=1 \
  ERP_FQ_EVIDENCE_DIRECTORY="${evidence_root}/fq-signoff" ERP_FQ_RELEASE_COMMIT="${candidate}" ./scripts/ui-smoke-local.sh

last_gate=final-evidence-verification
[[ "$(git -C "${repo_root}" rev-parse HEAD)" == "${candidate}" ]]
[[ -z "$(git -C "${repo_root}" status --porcelain)" ]]
node --input-type=module - "${repo_root}" "${evidence_root}" "${candidate}" <<'JS'
import assert from 'node:assert/strict'
import { readFileSync, writeFileSync, readdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
const [root, evidence, head] = process.argv.slice(2)
const ui = JSON.parse(readFileSync(`${root}/backend/target/ui-smoke-report.json`))
const fq = JSON.parse(readFileSync(`${evidence}/fq-signoff/all-summary.json`))
assert.equal(fq.technicalPass, true)
assert.equal(fq.context.candidateRevision, head)
assert.equal(fq.context.sourceRevision, head)
assert.equal(fq.context.dirtyWorktree, false)
assert.equal(ui.sourceRevision, head)
assert.equal(ui.dirtyWorktree, false)
assert(ui.results.length > 0 && ui.results.every(x => x.passed))
assert(ui.workflows.length > 0 && ui.workflows.every(x => x.passed))
const reports = readdirSync(`${root}/backend/target/surefire-reports`).filter(p => /^TEST-.*\.xml$/.test(p))
assert(reports.length > 0)
const counts = { tests: 0, failures: 0, errors: 0, skipped: 0 }
for (const file of reports) {
  const suite = readFileSync(`${root}/backend/target/surefire-reports/${file}`, 'utf8').match(/<testsuite\s[^>]*>/)?.[0]
  assert(suite, `Missing Surefire suite: ${file}`)
  for (const key of Object.keys(counts)) {
    const count = suite.match(new RegExp(`\\b${key}="(\\d+)"`))
    assert(count, `Missing ${key}: ${file}`)
    counts[key] += Number(count[1])
  }
}
assert(counts.tests > 0)
assert.equal(counts.failures + counts.errors + counts.skipped, 0, 'Release tests must not fail or skip')
writeFileSync(`${evidence}/backend-test-summary.json`, JSON.stringify(counts, null, 2) + '\n')
const runtimeReports = readdirSync(`${root}/backend/target`).filter(p => p.startsWith('monitoring-runtime-'))
assert(runtimeReports.length > 0)
for (const dir of runtimeReports) {
  const report = JSON.parse(readFileSync(`${root}/backend/target/${dir}/report.json`))
  assert.equal(report.sourceRevision, head)
  assert.equal(report.dirtyWorktree, false)
  assert.equal(report.passed, true)
}
const paths = execFileSync('git', ['ls-files', '-z'], { cwd: root, encoding: 'utf8' }).split('\0').filter(Boolean)
const files = Object.fromEntries(paths.map(p => [p, createHash('sha256').update(readFileSync(`${root}/${p}`)).digest('hex')]))
writeFileSync(`${evidence}/source-sha256.json`, JSON.stringify({head, dirtyWorktree: false, files}, null, 2) + '\n')
const artifacts = Object.fromEntries(['erp-server-1.0.0.jar', 'bom.json', 'application.cdx.json'].map(p => [p, createHash('sha256').update(readFileSync(`${evidence}/${p}`)).digest('hex')]))
writeFileSync(`${evidence}/artifact-sha256.json`, JSON.stringify(artifacts, null, 2) + '\n')
JS
completed=true
echo "[candidate] All local gates passed. Evidence: ${evidence_root}"

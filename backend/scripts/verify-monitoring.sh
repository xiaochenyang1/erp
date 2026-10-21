#!/usr/bin/env bash
# Validate deployment templates and rule timing without contacting ERP or receivers.
set -Eeuo pipefail
backend_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
# Keep bind mounts inside the repository: Colima may not share macOS TMPDIR.
mkdir -p "${backend_root}/target"
temp_dir="$(mktemp -d "${backend_root}/target/monitoring-check.XXXXXX")"
chmod 755 "${temp_dir}"
trap 'rm -rf -- "${temp_dir}"' EXIT

printf '%s\n' validation-token > "${temp_dir}/erp-metrics-token"
printf '%s\n' http://127.0.0.1:1/validation-only > "${temp_dir}/erp-default-webhook-url"
cp "${temp_dir}/erp-default-webhook-url" "${temp_dir}/erp-critical-webhook-url"
printf '%s\n' validation-only-not-a-deployment-password > "${temp_dir}/grafana-password"
export ERP_MONITORING_TOKEN_FILE="${temp_dir}/erp-metrics-token"
export ERP_MONITORING_WARNING_WEBHOOK_FILE="${temp_dir}/erp-default-webhook-url"
export ERP_MONITORING_CRITICAL_WEBHOOK_FILE="${temp_dir}/erp-critical-webhook-url"
export ERP_GRAFANA_PASSWORD_FILE="${temp_dir}/grafana-password"

cd "${backend_root}"
cmp monitoring/prometheus.yml docs/monitoring/prometheus-scrape.yml
cmp monitoring/alertmanager.yml docs/monitoring/alertmanager.yml
docker compose -f docker-compose.monitoring.yml config --format json > "${temp_dir}/compose.json"
prometheus_image="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1])).services.prometheus.image' "${temp_dir}/compose.json")"
alertmanager_image="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1])).services.alertmanager.image' "${temp_dir}/compose.json")"

docker run --rm --network none --entrypoint promtool \
  -v "${backend_root}/monitoring:/etc/prometheus/rules:ro" \
  -v "${temp_dir}:/etc/prometheus/secrets:ro" \
  "${prometheus_image}" check config /etc/prometheus/rules/prometheus.yml
docker run --rm --network none --entrypoint promtool \
  -v "${backend_root}/monitoring:/etc/prometheus/rules:ro" \
  "${prometheus_image}" test rules /etc/prometheus/rules/alert-rules.test.yml
docker run --rm --network none --entrypoint amtool \
  -v "${backend_root}/monitoring/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro" \
  -v "${temp_dir}:/etc/alertmanager/secrets:ro" \
  "${alertmanager_image}" check-config /etc/alertmanager/alertmanager.yml
for severity in critical warning; do
  receiver=erp-default
  [[ "${severity}" != critical ]] || receiver=erp-critical
  docker run --rm --network none --entrypoint amtool \
    -v "${backend_root}/monitoring/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro" \
    -v "${temp_dir}:/etc/alertmanager/secrets:ro" \
    "${alertmanager_image}" config routes test --config.file=/etc/alertmanager/alertmanager.yml \
    --verify.receivers="${receiver}" "severity=${severity}" alertname=ErpValidationOnly
done
node --input-type=module -e '
  import { readFileSync } from "node:fs";
  const dashboard = JSON.parse(readFileSync("docs/monitoring/grafana-dashboard.json"));
  const alerts = dashboard.panels.find(p => p.targets?.some(t => t.expr?.startsWith("ALERTS{")));
  if (!alerts || alerts.type !== "table" || !alerts.datasource) throw new Error("Missing Prometheus ALERTS table");
  if (dashboard.panels.some(p => p.type === "alertlist")) throw new Error("Grafana alertlist does not query Prometheus rule state");
  console.log(`Monitoring configuration, rule timing/recovery, receiver routes and ${dashboard.panels.length} dashboard panels passed.`);
'

#!/usr/bin/env bash
# Build the current backend first, then run UI smoke against a disposable MySQL database.
set -Eeuo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
backend_root="$(cd -- "${script_dir}/.." && pwd)"
container_name="erp-ui-smoke-mysql-${$}"
bootstrap_pid=""
cleanup() {
  if [[ -n "${bootstrap_pid}" ]] && kill -0 "${bootstrap_pid}" 2>/dev/null; then
    kill "${bootstrap_pid}" 2>/dev/null || true
    wait "${bootstrap_pid}" 2>/dev/null || true
  fi
  docker rm -f "${container_name}" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM
export UI_SMOKE_FRONTEND_MODE="${UI_SMOKE_FRONTEND_MODE:-preview}"
export ERP_UI_SMOKE_BACKEND_PORT="${ERP_UI_SMOKE_BACKEND_PORT:-19381}"
export UI_SMOKE_FRONTEND_PORT="${UI_SMOKE_FRONTEND_PORT:-19373}"
export ERP_UI_SMOKE_BACKEND_URL="http://127.0.0.1:${ERP_UI_SMOKE_BACKEND_PORT}"
export ERP_LOCAL_ADMIN_PASSWORD="LocalAdmin123"
export ERP_UI_SMOKE_RUNTIME_PASSWORD="RuntimeSmoke123"
export ERP_UI_SMOKE_DATASOURCE_USERNAME="root"
export ERP_UI_SMOKE_DATASOURCE_PASSWORD="ui_smoke_disposable_password"

[[ -f "${backend_root}/target/erp-server-1.0.0.jar" ]] || { echo 'Build the backend package before running UI smoke.' >&2; exit 1; }
if [[ "${UI_SMOKE_FRONTEND_MODE}" == preview ]]; then
  npm --prefix "${backend_root}/../frontend" run build
fi
node --input-type=module -e '

  import { createServer } from "node:net";
  for (const port of [process.env.ERP_UI_SMOKE_BACKEND_PORT, process.env.UI_SMOKE_FRONTEND_PORT, 9223]) {
    await new Promise((resolve, reject) => {
      const server = createServer(); server.once("error", reject);
      server.listen(Number(port), "127.0.0.1", () => server.close(resolve));
    });
  }
'
docker run --detach --rm --name "${container_name}" \
  --env "MYSQL_ROOT_PASSWORD=${ERP_UI_SMOKE_DATASOURCE_PASSWORD}" \
  --env MYSQL_DATABASE=erp_ui_smoke --publish 127.0.0.1::3306 \
  mysql:8.4 --default-time-zone=+08:00 >/dev/null
mysql_port="$(docker port "${container_name}" 3306/tcp | awk -F: 'NR == 1 { print $NF }')"
export ERP_UI_SMOKE_DATASOURCE_URL="jdbc:mysql://127.0.0.1:${mysql_port}/erp_ui_smoke?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
for attempt in $(seq 1 60); do
  if docker exec "${container_name}" mysqladmin ping -h 127.0.0.1 -uroot "-p${ERP_UI_SMOKE_DATASOURCE_PASSWORD}" --silent >/dev/null 2>&1; then break; fi
  [[ "${attempt}" -lt 60 ]] || { echo 'UI smoke MySQL did not start.' >&2; exit 1; }
  sleep 1
done
cd "${backend_root}"
java -jar target/erp-server-1.0.0.jar --spring.profiles.active=local \
  "--spring.datasource.url=${ERP_UI_SMOKE_DATASOURCE_URL}" \
  "--spring.datasource.username=${ERP_UI_SMOKE_DATASOURCE_USERNAME}" \
  "--spring.datasource.password=${ERP_UI_SMOKE_DATASOURCE_PASSWORD}" \
  "--server.port=${ERP_UI_SMOKE_BACKEND_PORT}" > target/ui-smoke-bootstrap.log 2>&1 &
bootstrap_pid=$!
for attempt in $(seq 1 120); do
  kill -0 "${bootstrap_pid}" 2>/dev/null || { tail -30 target/ui-smoke-bootstrap.log; exit 1; }
  if curl --fail --silent "${ERP_UI_SMOKE_BACKEND_URL}/actuator/health" >/dev/null; then break; fi
  [[ "${attempt}" -lt 120 ]] || { echo 'UI smoke bootstrap did not become healthy.' >&2; exit 1; }
  sleep 1
done
node scripts/prepare-ui-smoke-user.mjs
kill "${bootstrap_pid}"
wait "${bootstrap_pid}" || true
bootstrap_pid=""
node scripts/ui-smoke.mjs

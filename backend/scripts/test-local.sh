#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
backend_root="$(cd -- "${script_dir}/.." && pwd)"

mysql_image="${ERP_LOCAL_TEST_MYSQL_IMAGE:-mysql:8.4}"
mysql_container="erp-local-test-mysql-${$}"
mysql_database="${ERP_LOCAL_TEST_MYSQL_DATABASE:-erp_codex_test}"
mysql_user="${ERP_LOCAL_TEST_MYSQL_USERNAME:-erp_test}"
mysql_password="${ERP_LOCAL_TEST_MYSQL_PASSWORD:-erp_test_password}"
mysql_root_password="${ERP_LOCAL_TEST_MYSQL_ROOT_PASSWORD:-erp_local_test_root_password}"
mysql_host="127.0.0.1"
mysql_port=""

cleanup() {
  if [[ -n "${mysql_container}" ]] && docker container inspect "${mysql_container}" >/dev/null 2>&1; then
    docker rm -f "${mysql_container}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

fail() {
  echo "[test-local] $*" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || fail "Docker is required. Start Docker Desktop and retry."
[[ -x "${backend_root}/mvnw" ]] || fail "Maven wrapper not found: ${backend_root}/mvnw"

# Docker CLI contexts (for example Colima on macOS) are not automatically
# visible to Testcontainers, which talks to the daemon through DOCKER_HOST.
# Preserve an explicit value; otherwise export the active CLI context endpoint.
if [[ -z "${DOCKER_HOST:-}" ]]; then
  docker_context="$(docker context show 2>/dev/null || true)"
  if [[ -n "${docker_context}" ]]; then
    docker_host="$(docker context inspect --format '{{.Endpoints.docker.Host}}' "${docker_context}" 2>/dev/null || true)"
    if [[ -n "${docker_host}" && "${docker_host}" != "<no value>" ]]; then
      export DOCKER_HOST="${docker_host}"
      echo "[test-local] Exported DOCKER_HOST from Docker context ${docker_context}: ${DOCKER_HOST}"
    fi
  fi
fi

# Colima exposes the daemon through a host-side Unix socket. Testcontainers'
# Ryuk container must mount the socket path as it appears inside the Docker VM,
# not the host-side Colima path.
if [[ "${DOCKER_HOST:-}" == unix://* && -z "${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-}" ]]; then
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
  echo "[test-local] Exported TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE}"
fi

echo "[test-local] Starting disposable MySQL container ${mysql_container} (${mysql_image})"
docker run --detach --rm \
  --name "${mysql_container}" \
  --env "MYSQL_ROOT_PASSWORD=${mysql_root_password}" \
  --env "MYSQL_DATABASE=${mysql_database}" \
  --env "MYSQL_USER=${mysql_user}" \
  --env "MYSQL_PASSWORD=${mysql_password}" \
  --publish "${mysql_host}::3306" \
  "${mysql_image}" \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_0900_ai_ci \
  --default-time-zone=+08:00 >/dev/null

mysql_port="$(docker port "${mysql_container}" 3306/tcp | awk -F: 'NR == 1 { print $NF }')"
[[ "${mysql_port}" =~ ^[0-9]+$ ]] || fail "Could not determine the disposable MySQL host port."

echo "[test-local] Waiting for MySQL on ${mysql_host}:${mysql_port}"
for attempt in $(seq 1 60); do
  if docker exec "${mysql_container}" mysqladmin ping -h 127.0.0.1 -uroot "-p${mysql_root_password}" --silent >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt}" -eq 60 ]]; then
    docker logs "${mysql_container}" >&2 || true
    fail "MySQL did not become ready within 60 seconds."
  fi
  sleep 1
done

export ERP_TEST_DATASOURCE_URL="jdbc:mysql://${mysql_host}:${mysql_port}/${mysql_database}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
export ERP_TEST_DATASOURCE_USERNAME="${mysql_user}"
export ERP_TEST_DATASOURCE_PASSWORD="${mysql_password}"

goals_present=false
for argument in "$@"; do
  if [[ "${argument}" != -* ]]; then
    goals_present=true
    break
  fi
done

maven_arguments=("$@")
if [[ "${goals_present}" == false ]]; then
  maven_arguments+=(test)
fi

echo "[test-local] Running Maven against disposable MySQL: ${maven_arguments[*]}"
cd "${backend_root}"
"${backend_root}/mvnw" -B "${maven_arguments[@]}"

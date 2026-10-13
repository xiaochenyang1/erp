const backendUrl = (process.env.ERP_UI_SMOKE_BACKEND_URL || 'http://127.0.0.1:8081').replace(/\/$/, '')
const adminPassword = process.env.ERP_LOCAL_ADMIN_PASSWORD || 'LocalAdmin123'
const smokePassword = process.env.ERP_UI_SMOKE_RUNTIME_PASSWORD || 'RuntimeSmoke123'

async function apiRequest(method, path, { token, body } = {}) {
  const response = await fetch(new URL(path, backendUrl), {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body == null ? {} : { 'Content-Type': 'application/json' })
    },
    body: body == null ? undefined : JSON.stringify(body)
  })
  const raw = await response.text()
  let payload = null
  try {
    payload = raw ? JSON.parse(raw) : null
  } catch {
    throw new Error(`${method} ${path} returned non-JSON: HTTP ${response.status}`)
  }
  if (!response.ok || payload?.code !== '0') {
    throw new Error(`${method} ${path} failed: HTTP ${response.status} ${payload?.message || ''}`.trim())
  }
  return payload.data
}

async function main() {
  const login = await apiRequest('POST', '/api/auth/login', {
    body: { username: 'admin', password: adminPassword }
  })
  if (!login?.accessToken) {
    throw new Error('Admin login did not return an access token')
  }

  const adminToken = login.accessToken
  const page = await apiRequest(
    'GET',
    '/api/system/users?pageNo=1&pageSize=100&keyword=runtime_smoke',
    { token: adminToken }
  )
  let user = page.records?.find((record) => record.username === 'runtime_smoke')

  if (!user) {
    user = await apiRequest('POST', '/api/system/users', {
      token: adminToken,
      body: {
        username: 'runtime_smoke',
        password: smokePassword,
        employeeNo: 'UI-SMOKE-CI',
        realName: 'UI Smoke Submitter',
        deptId: 3501,
        postId: 3601,
        remark: 'CI-only UI smoke workflow submitter'
      }
    })
  } else {
    if (user.status !== 'ACTIVE') {
      await apiRequest('POST', `/api/system/users/${user.id}/enable`, { token: adminToken })
    }
    await apiRequest('POST', `/api/system/users/${user.id}/reset-password`, {
      token: adminToken,
      body: { newPassword: smokePassword }
    })
  }

  await apiRequest('PUT', `/api/system/users/${user.id}/roles`, {
    token: adminToken,
    body: { roleIds: [3002] }
  })
  const smokeLogin = await apiRequest('POST', '/api/auth/login', {
    body: { username: 'runtime_smoke', password: smokePassword }
  })
  if (!smokeLogin?.accessToken) {
    throw new Error('UI smoke submitter login did not return an access token')
  }

  process.stdout.write('Prepared the UI smoke submitter account.\n')
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`)
  process.exitCode = 1
})

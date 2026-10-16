// Bound CDP waits so a disconnected browser cannot leave the smoke runner hanging.
export async function connectCdpSocket(ws, { timeoutMs = 30000, onEvent = () => {} } = {}) {
  const pending = new Map()
  let sequence = 0
  let failure
  let resolveOpen
  let rejectOpen
  const opened = new Promise((resolve, reject) => {
    resolveOpen = resolve
    rejectOpen = reject
  })
  const openTimer = setTimeout(() => fail(new Error('CDP connection timed out')), timeoutMs)

  function fail(error) {
    if (failure) return
    failure = error
    clearTimeout(openTimer)
    rejectOpen(error)
    for (const request of pending.values()) {
      clearTimeout(request.timer)
      request.reject(error)
    }
    pending.clear()
    if (ws.readyState < 2) ws.close()
  }

  function markOpen() {
    clearTimeout(openTimer)
    resolveOpen()
  }

  ws.addEventListener('open', markOpen, { once: true })
  ws.addEventListener('close', (event) => fail(new Error(`CDP connection closed (${event.code})`)))
  ws.addEventListener('error', () => fail(new Error('CDP connection error')))
  ws.addEventListener('message', (message) => {
    let payload
    try {
      payload = JSON.parse(message.data)
    } catch {
      fail(new Error('Invalid CDP response'))
      return
    }
    if (payload.id) {
      const request = pending.get(payload.id)
      if (!request) return
      pending.delete(payload.id)
      clearTimeout(request.timer)
      if (payload.error) {
        request.reject(new Error(`${payload.error.message}: ${payload.error.data || ''}`))
      } else {
        request.resolve(payload.result)
      }
    } else {
      onEvent(payload)
    }
  })

  if (ws.readyState === 1) markOpen()
  else if (ws.readyState !== 0) fail(new Error('CDP connection is not open'))
  await opened

  return {
    send(method, params = {}) {
      if (failure) return Promise.reject(failure)
      if (ws.readyState !== 1) {
        fail(new Error('CDP connection is not open'))
        return Promise.reject(failure)
      }
      const id = ++sequence
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => fail(new Error(`CDP command timed out: ${method}`)), timeoutMs)
        pending.set(id, { resolve, reject, timer })
        try {
          ws.send(JSON.stringify({ id, method, params }))
        } catch (error) {
          fail(error)
        }
      })
    },
    close() {
      fail(new Error('CDP connection closed by client'))
    }
  }
}

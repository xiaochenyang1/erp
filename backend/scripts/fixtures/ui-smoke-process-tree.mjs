import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { fileURLToPath } from 'node:url'

const [role = 'root', mode = 'normal'] = process.argv.slice(2)
if (role === 'leaf') {
  const server = createServer()
  if (mode === 'stubborn') process.on('SIGTERM', () => {})
  if (mode === 'graceful') process.on('SIGTERM', () => {
    setTimeout(() => server.close(), 150)
  })
  server.listen(0, '127.0.0.1', () => {
    console.log(JSON.stringify({ pid: process.pid, port: server.address().port }))
  })
} else {
  const child = spawn(process.execPath, [fileURLToPath(import.meta.url), role === 'root' ? 'middle' : 'leaf', mode], {
    stdio: ['ignore', 'inherit', 'inherit']
  })
  if (role === 'root' && mode === 'orphan') {
    // Deliberately leave the group alive after its original leader exits.
    child.unref()
  }
}

import assert from 'node:assert/strict'
import test from 'node:test'
import { connectCdpSocket } from './ui-smoke-cdp.mjs'

class FakeSocket extends EventTarget {
  readyState = 0
  sent = []
  open() {
    this.readyState = 1
    this.dispatchEvent(new Event('open'))
  }
  send(value) { this.sent.push(JSON.parse(value)) }
  receive(value) { this.dispatchEvent(new MessageEvent('message', { data: JSON.stringify(value) })) }
  close() {
    this.readyState = 3
    this.dispatchEvent(Object.assign(new Event('close'), { code: 1006 }))
  }
}

async function connected(options) {
  const ws = new FakeSocket()
  const connection = connectCdpSocket(ws, options)
  ws.open()
  return { ws, cdp: await connection }
}

test('matches out-of-order replies and forwards browser error events', async () => {
  const events = []
  const { ws, cdp } = await connected({ onEvent: event => events.push(event) })
  const first = cdp.send('Page.navigate', { url: 'about:blank' })
  const second = cdp.send('Runtime.evaluate')
  const event = { method: 'Runtime.exceptionThrown', params: { exceptionDetails: { text: 'error' } } }
  ws.receive(event)
  ws.receive({ id: ws.sent[1].id, result: { value: 2 } })
  ws.receive({ id: ws.sent[0].id, result: { frameId: 'frame' } })
  assert.deepEqual(await first, { frameId: 'frame' })
  assert.deepEqual(await second, { value: 2 })
  assert.deepEqual(events, [event])
  cdp.close()
})

test('disconnect rejects all pending and future commands', async () => {
  const { ws, cdp } = await connected()
  const first = assert.rejects(cdp.send('Page.navigate'), /connection closed/)
  const second = assert.rejects(cdp.send('Runtime.evaluate'), /connection closed/)
  ws.close()
  await Promise.all([first, second])
  await assert.rejects(cdp.send('Page.navigate'), /connection closed/)
})

test('unanswered command times out and invalidates the connection', async () => {
  const { ws, cdp } = await connected({ timeoutMs: 20 })
  await assert.rejects(cdp.send('Runtime.evaluate'), /command timed out: Runtime.evaluate/)
  assert.equal(ws.readyState, 3)
  await assert.rejects(cdp.send('Page.navigate'), /command timed out/)
})

test('connection handshake has a deadline', async () => {
  const ws = new FakeSocket()
  await assert.rejects(connectCdpSocket(ws, { timeoutMs: 20 }), /connection timed out/)
  assert.equal(ws.readyState, 3)
})

test('close during handshake rejects immediately', async () => {
  const ws = new FakeSocket()
  const rejected = assert.rejects(connectCdpSocket(ws), /connection closed/)
  ws.close()
  await rejected
})

test('socket error rejects pending commands', async () => {
  const { ws, cdp } = await connected()
  const rejected = assert.rejects(cdp.send('Page.navigate'), /connection error/)
  ws.dispatchEvent(new Event('error'))
  await rejected
  assert.equal(ws.readyState, 3)
})

test('protocol error rejects only that command', async () => {
  const { ws, cdp } = await connected()
  const rejected = assert.rejects(cdp.send('Missing.command'), /unknown method/)
  ws.receive({ id: ws.sent[0].id, error: { message: 'unknown method' } })
  await rejected
  const next = cdp.send('Page.enable')
  ws.receive({ id: ws.sent[1].id, result: {} })
  assert.deepEqual(await next, {})
  cdp.close()
})

test('synchronous send failure rejects and closes the connection', async () => {
  const { ws, cdp } = await connected()
  ws.send = () => { throw new Error('write failed') }
  await assert.rejects(cdp.send('Page.navigate'), /write failed/)
  assert.equal(ws.readyState, 3)
})

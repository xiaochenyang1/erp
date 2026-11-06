import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'
import { connectCdpSocket } from './ui-smoke-cdp.mjs'
import { spawnSmokeProcess } from './ui-smoke-processes.mjs'

const environment = Object.fromEntries(Object.entries(process.env).filter(([key]) => !/^(ERP_|MYSQL_|REDIS_|COMPOSE_|SPRING_|JAVA_)/.test(key)))

export async function browserCheck(base, password, directory, evidence, children, isInterrupted = () => false) {
  async function until(check, timeout = 90000) {
    const deadline = Date.now() + timeout
    let lastError
    do {
      if (isInterrupted()) throw new Error('Deployment smoke interrupted')
      try { return await check() } catch (error) { lastError = error }
      await delay(250)
    } while (Date.now() < deadline)
    throw lastError
  }

  const chrome = [process.env.CHROME_PATH, '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'].find(value => value && existsSync(value))
  assert(chrome, 'Set CHROME_PATH to a Chrome or Edge executable')
  const profile = path.join(directory, 'chrome-profile')
  const handle = spawnSmokeProcess(chrome, ['--headless=new', '--disable-gpu', '--disable-extensions',
    '--disable-background-networking', '--no-first-run', '--no-default-browser-check', '--window-size=1440,1000',
    '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { env: environment })
  children.push({ name: 'chrome', ...handle })
  for (const stream of [handle.child.stdout, handle.child.stderr]) stream.on('data', () => {})
  const port = await until(async () => {
    if (handle.state.error) throw handle.state.error
    return Number((await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0])
  }, 30000)
  const tabs = await (await fetch(`http://127.0.0.1:${port}/json/list`, { signal: AbortSignal.timeout(5000) })).json()
  const errors = []
  const cdp = await connectCdpSocket(new WebSocket(tabs.find(tab => tab.type === 'page').webSocketDebuggerUrl), {
    onEvent: event => { if (event.method === 'Runtime.exceptionThrown') errors.push(event.params.exceptionDetails.text) }
  })
  const evaluate = async expression => {
    const result = await cdp.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
    assert(!result.exceptionDetails, 'Browser evaluation failed')
    return result.result.value
  }
  const wait = expression => until(async () => assert.equal(await evaluate(`Boolean(document.body) && (${expression})`), true, expression), 30000)
  try {
    await cdp.send('Page.enable'); await cdp.send('Runtime.enable')
    await cdp.send('Page.navigate', { url: `${base}/sales/orders` })
    await wait(`location.pathname === '/login' && !!document.querySelector('.login-form input[type="password"]')`)
    await evaluate(`(() => {
      const fields = document.querySelectorAll('.login-form input');
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
      for (const [index, value] of ['admin', ${JSON.stringify(password)}].entries()) {
        setter.call(fields[index], value); fields[index].dispatchEvent(new Event('input', { bubbles: true }));
      }
    })()`)
    await evaluate(`document.querySelector('.login-button').click()`)
    await wait(`location.pathname !== '/login' && !!localStorage.getItem('token')`)
    const routes = [
      ['/dashboard', '今日采购订单'], ['/sales/orders', '销售订单'],
      ['/inventory/stocks', '库存查询'], ['/finance/periods', '会计期间管理']
    ]
    for (const [route, text] of routes) {
      await cdp.send('Page.navigate', { url: base + route })
      await wait(`location.pathname === ${JSON.stringify(route)} && document.body.innerText.includes(${JSON.stringify(text)})`)
      await evaluate('window.__deploymentReloadMarker = true')
      await cdp.send('Page.reload', { ignoreCache: true })
      await wait(`window.__deploymentReloadMarker !== true && document.readyState === 'complete' && location.pathname === ${JSON.stringify(route)} && document.body.innerText.includes(${JSON.stringify(text)})`)
    }
    assert.deepEqual(errors, [], 'Browser runtime errors')
    const screenshot = await cdp.send('Page.captureScreenshot', { format: 'png' })
    await writeFile(path.join(evidence, 'browser.png'), Buffer.from(screenshot.data, 'base64'))
    return { loginThroughUi: true, protectedDeepLink: true, refreshedRoutes: routes.map(([route]) => route), runtimeErrors: errors }
  } catch (error) {
    try {
      const screenshot = await cdp.send('Page.captureScreenshot', { format: 'png' })
      await writeFile(path.join(evidence, 'browser-failure.png'), Buffer.from(screenshot.data, 'base64'))
    } catch { /* Preserve the original browser failure if the process is already gone. */ }
    throw error
  } finally { cdp.close() }
}

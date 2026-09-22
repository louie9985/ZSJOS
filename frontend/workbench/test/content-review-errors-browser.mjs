// UTF-8. Dependency-free Chromium CDP verification; runs only isolated test fixtures.
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { join } from 'node:path'
const executable = process.env.CONTENT_ERROR_TEST_CHROME
if (!executable) throw new Error('Set CONTENT_ERROR_TEST_CHROME to a Chromium/headless-shell executable')
const browser = spawn(executable, ['--no-sandbox', '--disable-dev-shm-usage', '--remote-debugging-pipe', '--headless', '--no-first-run'], { stdio: ['ignore', 'ignore', 'pipe', 'pipe', 'pipe'] })
let serial = 0, buffer = '', session
const pending = new Map(), errors = []
browser.stderr.on('data', () => {})
browser.stdio[4].on('data', data => {
  buffer += data.toString()
  let end
  while ((end = buffer.indexOf('\0')) >= 0) {
    const message = JSON.parse(buffer.slice(0, end)); buffer = buffer.slice(end + 1)
    if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.text + ': ' + (message.params.exceptionDetails.exception?.description || ''))
    const task = pending.get(message.id)
    if (task) { pending.delete(message.id); message.error ? task.reject(new Error(JSON.stringify(message.error))) : task.resolve(message.result) }
  }
})
function send(method, params = {}, target = session) {
  return new Promise((resolve, reject) => {
    const id = ++serial
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)) }, 15000)
    pending.set(id, { resolve: result => { clearTimeout(timer); resolve(result) }, reject: error => { clearTimeout(timer); reject(error) } })
    browser.stdio[3].write(JSON.stringify({ id, method, params, ...(target ? { sessionId: target } : {}) }) + '\0')
  })
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
  return result.result.value
}
async function until(expression) {
  for (let i = 0; i < 500; i++) {
    if (await evaluate(`Boolean(${expression})`)) return
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  console.error('Browser errors:', errors, 'Body:', await evaluate('document.body?.innerText.slice(0, 2000)'))
  throw new Error(`Browser condition failed: ${expression}`)
}
async function button(text) {
  await evaluate(`Array.from(document.querySelectorAll('button')).find(e => e.textContent.replace(/\\s/g,'') === ${JSON.stringify(text)}).click()`)
}
async function shot(name) {
  await new Promise(resolve => setTimeout(resolve, 350))
  const result = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`/tmp/zsjos-content-errors-${name}.png`, Buffer.from(result.data, 'base64'))
}
async function font() {
  if (!process.env.CONTENT_ERROR_TEST_FONT) return
  const data = readFileSync(process.env.CONTENT_ERROR_TEST_FONT).toString('base64')
  await evaluate(`(async () => { const f = new FontFace('FixtureCJK', 'url(data:font/otf;base64,${data})'); document.fonts.add(await f.load()); const s=document.createElement('style'); s.textContent='* { font-family: FixtureCJK, sans-serif !important }'; document.head.append(s); await document.fonts.ready; })()`)
}

// Optional temporary esbuild output avoids a shared development service and real business APIs.
let server
let root = process.env.CONTENT_ERROR_TEST_URL
if (!root && process.env.CONTENT_ERROR_TEST_SITE) {
  server = createServer((req, res) => {
    const file = req.url === '/' ? 'index.html' : req.url.slice(1)
    if (!/^[\w.-]+$/.test(file)) { res.writeHead(404).end(); return }
    try {
      res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html; charset=utf-8')
      res.end(readFileSync(join(process.env.CONTENT_ERROR_TEST_SITE, file)))
    } catch { res.writeHead(404).end() }
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  root = `http://127.0.0.1:${server.address().port}/`
}
if (!root) throw new Error('Set CONTENT_ERROR_TEST_SITE to isolated esbuild output')

try {
  const target = await send('Target.createTarget', { url: 'about:blank' }, undefined)
  session = (await send('Target.attachToTarget', { targetId: target.targetId, flatten: true }, undefined)).sessionId
  await send('Runtime.enable'); await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: root })
  await until("document.querySelector('#works_1_leadResourceUrl') && [...document.querySelectorAll('button')].some(e => e.textContent.includes('保存修改') && !e.disabled)")
  await font()
  const ready = async mode => {
    await evaluate(`contentErrorFixture.reset(${JSON.stringify(mode)})`)
    await until("document.querySelector('#works_1_leadResourceUrl') && [...document.querySelectorAll('button')].some(e => e.textContent.includes('保存修改') && !e.disabled)")
  }
  await button('保存修改')
  await until("document.querySelector('#works_1_leadResourceUrl').closest('.ant-form-item').innerText.includes('第 2 件作品')")
  assert.equal(await evaluate("document.querySelector('#works_0_title').value"), '保留输入作品 1')
  assert.equal(await evaluate('contentErrorFixture.closes'), 0)
  await shot('desktop-field')
  await ready('invalid-link'); await button('提交审批')
  await until("document.querySelector('#works_1_leadResourceUrl').closest('.ant-form-item').innerText.includes('完整 HTTPS')")
  assert.equal(await evaluate('contentErrorFixture.saves'), 0)
  await ready('past-time'); await button('提交审批')
  await until("document.body.innerText.includes('预计发布时间不能早于当前时间')")
  assert.equal(await evaluate('contentErrorFixture.saves'), 0)
  await ready('submit-rejected')
  // Two synchronous clicks must issue one save, including while validation awaits.
  await evaluate("(() => { const e=[...document.querySelectorAll('button')].find(e=>e.textContent.replace(/\\s/g,'')==='提交审批'); e.click(); e.click() })()")
  await until("document.body.innerText.includes('草稿已保存，但审批未提交')")
  assert.equal(await evaluate('contentErrorFixture.saves'), 1)
  assert.equal(await evaluate('contentErrorFixture.submits'), 1)
  assert.equal(await evaluate('contentErrorFixture.requests[0].expectedVersion'), 2)
  await button('提交审批')
  await until('contentErrorFixture.submits === 2')
  assert.equal(await evaluate('contentErrorFixture.requests[1].expectedVersion'), 3)
  await ready('refresh-error'); await button('保存修改')
  await until("document.body.innerText.includes('草稿已保存，但未能读取最新版本，尚未发起审批')")
  assert.equal(await evaluate('contentErrorFixture.submits'), 0)
  assert.ok(await evaluate("[...document.querySelectorAll('button')].find(e=>e.textContent.includes('保存修改')).disabled"))
  await ready('changed'); await button('保存修改')
  await until("document.body.innerText.includes('本轮状态已变化')")
  assert.equal(await evaluate('contentErrorFixture.saves'), 0)
  assert.equal(await evaluate('contentErrorFixture.closes'), 0)
  await ready('empty-dict')
  await until("document.body.innerText.includes('已有历史选项可保留')")
  assert.ok(await evaluate("document.body.innerText.includes('原作品目的')"))
  await button('保存修改'); await until('contentErrorFixture.closes === 1')
  await ready('submit-lost'); await button('提交审批')
  await until('contentErrorFixture.closes === 1')
  assert.equal(await evaluate('contentErrorFixture.submits'), 1)
  assert.equal(await evaluate('contentErrorFixture.saves'), 1)
  assert.ok(await evaluate('contentErrorFixture.reads >= 3'))
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  await ready('submit-unknown'); await button('提交审批')
  await until("document.body.innerText.includes('暂未确认审批提交结果')")
  assert.ok(await evaluate("[...document.querySelectorAll('button')].find(e=>e.textContent.includes('保存修改')).disabled"))
  assert.equal(await evaluate('contentErrorFixture.closes'), 0)
  assert.ok(await evaluate("[...document.querySelectorAll('.ant-modal')].filter(e=>e.clientWidth).every(e=>e.scrollWidth<=e.clientWidth+1)"))
  await evaluate("document.querySelector('.ant-modal-body').scrollTop = 0")
  await shot('mobile-uncertain')
  await evaluate("contentErrorFixture.reset('dict-error')")
  await until("document.body.innerText.includes('字典读取无权限')")
  assert.ok(await evaluate("[...document.querySelectorAll('button')].find(e=>e.textContent.includes('保存修改')).disabled"))
  assert.deepEqual(errors, [])
  console.log('PASS: desktop/mobile, field context, HTTPS/time validation, retained input, duplicate click, saved/rejected retry version, refresh failure, changed state, historical dictionaries, lost submit response, uncertain submit, dictionary error')
} finally { browser.kill('SIGTERM'); if (server) await new Promise(resolve => server.close(resolve)) }

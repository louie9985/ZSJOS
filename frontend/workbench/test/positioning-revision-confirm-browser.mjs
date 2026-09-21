// UTF-8. Dependency-free Chromium CDP verification; runs only isolated test fixtures.
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import assert from 'node:assert/strict'
const executable = process.env.POSITIONING_TEST_CHROME
if (!executable) throw new Error('Set POSITIONING_TEST_CHROME to a Chromium/headless-shell executable')
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
  writeFileSync(`/tmp/zsjos-positioning-revision-${name}.png`, Buffer.from(result.data, 'base64'))
}
async function font() {
  if (!process.env.POSITIONING_TEST_FONT) return
  const data = readFileSync(process.env.POSITIONING_TEST_FONT).toString('base64')
  await evaluate(`(async () => { const f = new FontFace('FixtureCJK', 'url(data:font/otf;base64,${data})'); document.fonts.add(await f.load()); const s=document.createElement('style'); s.textContent='* { font-family: FixtureCJK, sans-serif !important }'; document.head.append(s); await document.fonts.ready; })()`)
}
try {
  const target = await send('Target.createTarget', { url: 'about:blank' }, null)
  session = (await send('Target.attachToTarget', { targetId: target.targetId, flatten: true }, null)).sessionId
  await send('Page.enable'); await send('Runtime.enable')
  const root = process.env.POSITIONING_TEST_URL || 'http://127.0.0.1:5188/test/positioning-revision-confirm.html'
  for (const list of [false, true]) {
    for (const mobile of [false, true]) {
      await send('Emulation.setDeviceMetricsOverride', { width: mobile ? 390 : 1440, height: mobile ? 844 : 1000, deviceScaleFactor: 1, mobile })
      await send('Page.navigate', { url: root + (list ? '?list' : '') })
      await until('window.revisionFixture?.reads > 0')
      const entry = list ? '修改定位卡' : '修订定位卡'
      await until(`document.body?.innerText.includes('${entry}')`)
      await button(entry)
      await until("document.querySelector('.ant-modal-confirm')")
      assert.equal(await evaluate('window.revisionFixture.writes'), 0)
      assert.equal(await evaluate('window.revisionFixture.edits'), 0)
      await font()
      await until("document.activeElement?.textContent.replace(/\\s/g,'') === '取消'")
      assert.ok(await evaluate("document.body?.innerText.includes('重新提交运营审核')"))
      await shot(`${list ? 'list' : 'overview'}-${mobile ? 'mobile' : 'desktop'}`)
      assert.ok(await evaluate("(() => {const r=document.querySelector('.ant-modal-confirm').getBoundingClientRect();return r.left>=0 && r.right<=innerWidth})()"))
      await button('取消')
      await until("!document.querySelector('.ant-modal-confirm')")
      assert.deepEqual(await evaluate('({writes:revisionFixture.writes,edits:revisionFixture.edits,reads:revisionFixture.reads})'), { writes: 0, edits: 0, reads: 1 })
      await button(entry); await until("document.querySelector('.ant-modal-confirm')")
      await button('确认修改')
      await until('window.revisionFixture.reads === 2')
      assert.equal(await evaluate('window.revisionFixture.writes'), 1)
      assert.equal(await evaluate('window.revisionFixture.edits'), list ? 0 : 1)
      console.log(`PASS ${list ? 'list' : 'overview'} ${mobile ? 'mobile' : 'desktop'} cancel and confirm`)
    }
    await send('Page.navigate', { url: root + '?fail' + (list ? '&list' : '') })
    await until('window.revisionFixture?.reads > 0')
    await button(list ? '修改定位卡' : '修订定位卡'); await until("document.querySelector('.ant-modal-confirm')")
    await button('确认修改')
    await until("document.body?.innerText.includes('定位卡版本已变化')")
    assert.equal(await evaluate('window.revisionFixture.edits'), 0)
    assert.equal(await evaluate('window.revisionFixture.reads'), 1)
    console.log(`PASS failure ${list ? 'list' : 'overview'}: no editor or refresh`)
    await send('Page.navigate', { url: root + '?denied' + (list ? '&list' : '') })
    await until('window.revisionFixture?.reads > 0')
    assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).some(e=>['修改定位卡','修订定位卡'].includes(e.textContent.replace(/\\s/g,'')))"), false)
  }
  await send('Page.navigate', { url: root + '?draft' })
  await until("document.body?.innerText.includes('继续填写／提交审核')")
  await button('继续填写／提交审核')
  assert.equal(await evaluate('window.revisionFixture.edits'), 1)
  assert.equal(await evaluate('window.revisionFixture.writes'), 0)
  assert.equal(await evaluate("!!document.querySelector('.ant-modal-confirm')"), false)
  assert.deepEqual(errors, [])
  console.log('PASS draft continuation, unavailable revision actions and no runtime exceptions')
} finally { browser.kill() }

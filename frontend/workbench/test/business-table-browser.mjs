// UTF-8. Dependency-free Chromium CDP verification; runs only isolated test fixtures.
import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import assert from 'node:assert/strict'
const executable = process.env.TABLE_TEST_CHROME
if (!executable) throw new Error('Set TABLE_TEST_CHROME to a Chromium/headless-shell executable')
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
  console.error('Browser errors:', errors, 'Body:', await evaluate('document.body.innerText.slice(0, 2000)'))
  throw new Error(`Browser condition failed: ${expression}`)
}
async function click(selector) {
  const point = await evaluate(`(() => { const e = Array.from(document.querySelectorAll(${JSON.stringify(selector)})).find(e => e.getBoundingClientRect().height > 0); e?.scrollIntoView({block:'center'}); if (!e) throw Error('Missing click target'); const r=e.getBoundingClientRect(); return { x:r.x+r.width/2, y:r.y+r.height/2 } })()`)
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point })
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point })
}
async function button(text) {
  await evaluate(`Array.from(document.querySelectorAll('button')).find(e => e.textContent.replace(/\\s/g,'') === ${JSON.stringify(text)}).click()`)
}
async function shot(name) {
  const result = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`/tmp/zsjos-table-${name}.png`, Buffer.from(result.data, 'base64'))
}
try {
  const target = await send('Target.createTarget', { url: 'about:blank' }, undefined)
  session = (await send('Target.attachToTarget', { targetId: target.targetId, flatten: true }, undefined)).sessionId
  await send('Runtime.enable'); await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false })
  const base = process.env.TABLE_TEST_BASE || 'http://127.0.0.1:5186'
  await send('Page.navigate', { url: `${base}/test/business-table.html` })
  await until("document.querySelector('[data-table-key=fixture-main] tbody tr')")
  assert.equal(await evaluate("document.querySelector('[data-table-key=fixture-compact]').textContent.includes('正确：停用')"), true)
  assert.equal(await evaluate("document.querySelectorAll('[data-table-key=fixture-compact] .ant-pro-table-list-toolbar-setting-item').length"), 0)
  const header = "Array.from(document.querySelectorAll('[data-table-key=fixture-main] th')).find(e=>e.textContent.includes('名称'))"
  const rect = await evaluate(`(() => { const r=(${header}).getBoundingClientRect(); return {x:r.right-2,y:r.y+r.height/2,width:r.width} })()`)
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: rect.x, y: rect.y, button: 'left', clickCount: 1 })
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: rect.x + 90, y: rect.y, button: 'left', buttons: 1 })
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: rect.x + 90, y: rect.y, button: 'left', clickCount: 1 })
  await until("JSON.parse(localStorage.getItem('crm-table:fixture-main:widths') || '{}').name >= 300")
  assert.match(await evaluate("document.querySelector('#fixture-state').textContent"), /排序 0/)
  await new Promise(resolve => setTimeout(resolve, 350))
  await evaluate(`(${header}).click()`)
  await until("document.querySelector('#fixture-state').textContent.includes('排序 1')")
  await evaluate("Array.from(document.querySelectorAll('[data-table-key=fixture-main] tbody tr.ant-table-row input[type=checkbox]')).find(e=>e.getBoundingClientRect().height>0).click()")
  await until("document.querySelector('#fixture-state').textContent.includes('已选 1')")
  await click('[data-table-key=fixture-main] .ant-pagination-next button')
  await until("document.querySelector('#fixture-state').textContent.includes('第 2 页')")
  assert.match(await evaluate("document.querySelector('#fixture-state').textContent"), /已选 1/)
  await shot('desktop')
  await button('模拟错误'); await until("document.querySelector('.business-table-error')")
  await button('重试'); await until("document.querySelector('#fixture-state').textContent.includes('刷新 1')")
  await button('模拟无权限'); await until("document.querySelector('[data-table-key=fixture-main] .ant-result-403')")
  assert.equal(await evaluate("document.querySelectorAll('[data-table-key=fixture-main] tbody').length"), 0)
  await button('恢复'); await until("Array.from(document.querySelectorAll('[data-table-key=fixture-main] th')).some(e=>e.textContent.includes('名称'))")
  await click('[data-table-key=fixture-main] .anticon-setting')
  await until("document.querySelector('.ant-popover:not(.ant-popover-hidden)')")
  await shot('column-settings')
  await evaluate("Array.from(document.querySelectorAll('.ant-popover:not(.ant-popover-hidden) .ant-tree-treenode')).find(e=>e.textContent.includes('金额'))?.querySelector('.ant-tree-checkbox')?.click()")
  await until("!Array.from(document.querySelectorAll('[data-table-key=fixture-main] th')).some(e=>e.textContent.includes('金额'))")
  await click('[data-table-key=fixture-main] .anticon-setting')
  await click('[data-table-key=fixture-main] .anticon-fullscreen')
  await until('Boolean(document.fullscreenElement)')
  await evaluate('document.exitFullscreen()')
  await button('暗色'); await button('大字号')
  await shot('dark-large')
  await send('Page.reload'); await until("Array.from(document.querySelectorAll('[data-table-key=fixture-main] th')).some(e=>e.textContent.includes('名称'))")
  assert.ok(await evaluate(`(${header}).getBoundingClientRect().width >= 300`))
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  await until("window.innerWidth === 390")
  assert.equal(await evaluate('document.body.scrollWidth <= window.innerWidth + 1'), true, 'mobile body must not overflow')
  assert.equal(await evaluate("Array.from(document.querySelectorAll('.business-table')).every(e=>e.getBoundingClientRect().right<=window.innerWidth)"), true)
  await shot('mobile')
  await evaluate("localStorage.setItem('crm-theme',JSON.stringify({preset:'default-light',inboxLayoutMode:'table'}))")
  await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: `${base}/test/inbox-avatar-rail.html?page=lead` })
  await until("document.querySelector('[data-table-key=lead-management-page-1] tbody tr')")
  await shot('lead-desktop')
  await send('Page.navigate', { url: `${base}/test/inbox-avatar-rail.html?page=student` })
  await until("document.querySelector('[data-table-key=registration-pages-1] tbody tr')")
  await shot('student-desktop')
  assert.deepEqual(errors, [])
  console.log('PASS: native values, compact editor, resize/persistence/sort isolation, pagination/selection, retry/denial, settings, theme, desktop/mobile and real Lead/Student pages')
} finally { browser.kill(); }

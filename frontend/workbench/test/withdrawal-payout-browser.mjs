// UTF-8. Dependency-free Chromium CDP verification; runs only isolated test fixtures.
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import assert from 'node:assert/strict'
const executable = process.env.PAYOUT_TEST_CHROME
if (!executable) throw new Error('Set PAYOUT_TEST_CHROME to a Chromium/headless-shell executable')
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
async function click(selector) {
  const point = await evaluate(`(() => { const e = Array.from(document.querySelectorAll(${JSON.stringify(selector)})).find(e => e.getBoundingClientRect().height > 0); e?.scrollIntoView({block:'center'}); if (!e) throw Error('Missing click target'); const r=e.getBoundingClientRect(); return { x:r.x+r.width/2, y:r.y+r.height/2 } })()`)
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point })
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point })
}
async function button(text) {
  await evaluate(`Array.from(document.querySelectorAll('button')).find(e => e.textContent.replace(/\\s/g,'') === ${JSON.stringify(text)}).click()`)
}
async function shot(name) {
  await new Promise(resolve => setTimeout(resolve, 350))
  const result = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`/tmp/zsjos-withdrawal-${name}.png`, Buffer.from(result.data, 'base64'))
}
async function fill(selector, text) {
  await click(selector)
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'a', code: 'KeyA', modifiers: 2, windowsVirtualKeyCode: 65 })
  await send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'a', code: 'KeyA', modifiers: 2, windowsVirtualKeyCode: 65 })
  await send('Input.insertText', { text })
}
async function pressEnter() {
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 })
  await send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 })
}
async function font() {
  if (!process.env.PAYOUT_TEST_FONT) return
  const data = readFileSync(process.env.PAYOUT_TEST_FONT).toString('base64')
  await evaluate(`(async () => { const f = new FontFace('FixtureCJK', 'url(data:font/otf;base64,${data})'); document.fonts.add(await f.load()); const s=document.createElement('style'); s.textContent='* { font-family: FixtureCJK, sans-serif !important }'; document.head.append(s); await document.fonts.ready; })()`)
}
try {
  const target = await send('Target.createTarget', { url: 'about:blank' }, undefined)
  session = (await send('Target.attachToTarget', { targetId: target.targetId, flatten: true }, undefined)).sessionId
  await send('Runtime.enable'); await send('Page.enable')
  for (const client of (process.env.PAYOUT_TEST_CLIENTS || 'workbench,admin').split(',')) {
    const admin = client === 'admin', port = admin ? 5189 : 5188
    const url = `http://127.0.0.1:${port}/test/withdrawal-payout.html`
    const modal = admin ? '.el-dialog' : '.ant-modal'
    const visibleModal = `Array.from(document.querySelectorAll('${modal}')).find(e=>e.getBoundingClientRect().height>0)`
    await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
    await send('Page.navigate', { url })
    await until("document.body?.innerText.includes('TEST-1')")
    await font()
    assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).find(e=>e.textContent.includes('批量登记打款')).disabled"), true)
    assert.equal(await evaluate("document.querySelectorAll('tbody input[type=checkbox]:disabled').length"), 1)
    console.log(client, 'rows ready')
    await click(admin ? '.el-table__body tr:nth-child(1) .el-checkbox' : 'tbody tr[data-row-key="1"] input[type=checkbox]')
    await until("document.body?.innerText.includes('批量登记打款（1）')")
    await click(admin ? '.el-table__body tr:nth-child(2) .el-checkbox' : 'tbody tr[data-row-key="2"] input[type=checkbox]')
    await until("document.body?.innerText.includes('批量登记打款（2）')")
    console.log(client, 'two selected')
    await button('批量登记打款（2）')
    await until(`${visibleModal}?.innerText.includes('已选择 2 条')`)
    assert.equal(await evaluate(`${visibleModal}.innerText.includes('银行流水') || ${visibleModal}.innerText.includes('凭证')`), false)
    await fill(`${modal} textarea`, '批量登记验收')
    await fill(`${modal} input`, '2026-09-20 15:30:00')
    await pressEnter()
    if (admin) {
      await evaluate("Array.from(document.querySelectorAll('.el-picker-panel button')).find(e=>e.textContent.includes('确定'))?.click()")
    } else {
      await evaluate("Array.from(document.querySelectorAll('.ant-picker-ok button')).find(e=>e.getBoundingClientRect().height>0)?.click()")
    }
    await evaluate('payoutFixture.fail=true')
    await button(admin ? '确认已线下打款' : '确定')
    await until('payoutFixture.calls.length===1')
    await until("document.body?.innerText.includes('提现状态已变化')")
    const first = await evaluate('payoutFixture.calls[0]')
    assert.equal(first.url, '/zsjos/withdrawal/batch-payout')
    assert.deepEqual(first.body, { ids: [1,2], paidAt: '2026-09-20T15:30:00', remark: '批量登记验收' })
    await shot(`${client}-desktop`)
    await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: false })
    await until(`(() => { const r=${visibleModal}?.getBoundingClientRect(); return r && r.left>=0 && r.right<=390 })()`)
    await shot(`${client}-mobile`)
    await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
    await evaluate('payoutFixture.fail=false; payoutFixture.delay=500')
    await button(admin ? '确认已线下打款' : '确定')
    await until('payoutFixture.calls.length===2')
    assert.equal(await evaluate(`Array.from(${visibleModal}.querySelectorAll('button')).find(e=>e.textContent.split(' ').join('')==='取消').disabled`), true)
    await until(`!${visibleModal}`)
    await until("document.body?.innerText.includes('批量登记打款（0）')")
    // The same optional form can submit no fields for a single record.
    if (admin) await button('登记打款')
    else { await button('详情'); await until("document.body?.innerText.includes('登记打款')"); await button('登记打款') }
    await until(`${visibleModal}?.innerText.includes('打款时间（选填）')`)
    await button(admin ? '确认已线下打款' : '确定')
    await until('payoutFixture.calls.length===3')
    assert.deepEqual(await evaluate('payoutFixture.calls[2]'), { url: '/zsjos/withdrawal/1/payout', body: {} })
    await until(`!${visibleModal}`)
    // Refresh clears selected records, errors expose retry, and empty state is distinct.
    await click(admin ? '.el-table__body tr:nth-child(1) .el-checkbox' : 'tbody tr[data-row-key="1"] input[type=checkbox]')
    await until("document.body?.innerText.includes('批量登记打款（1）')")
    await evaluate('payoutFixture.loadError=true')
    await button('查询')
    await until("document.body?.innerText.includes('批量登记打款（0）') && document.body?.innerText.includes('重试')")
    await evaluate('payoutFixture.loadError=false; payoutFixture.empty=true')
    await button('重试')
    await until("!document.body?.innerText.includes('TEST-1') && document.body?.innerText.includes('暂无')")
    await send('Page.navigate', { url: url+'?no-payout' })
    await until("document.body?.innerText.includes('TEST-1')")
    assert.equal(await evaluate("document.body?.innerText.includes('批量登记打款')"), false)
    assert.equal(await evaluate("document.querySelectorAll('tbody input[type=checkbox]').length"), 0)
    console.log(`${client}: selection, optional fields, shared payload, failure/retry, busy state, single empty form, loading error/empty and permission controls passed`)
  }
  assert.deepEqual(errors, [])
} finally { await send('Browser.close', {}, undefined).catch(() => {}); browser.kill() }

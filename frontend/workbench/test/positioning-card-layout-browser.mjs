// UTF-8. Dependency-free Chromium CDP verification; runs only isolated test fixtures.
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { join } from 'node:path'
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
  writeFileSync(`/tmp/zsjos-positioning-layout-${name}.png`, Buffer.from(result.data, 'base64'))
}
async function font() {
  if (!process.env.POSITIONING_TEST_FONT) return
  const data = readFileSync(process.env.POSITIONING_TEST_FONT).toString('base64')
  await evaluate(`(async () => { const f = new FontFace('FixtureCJK', 'url(data:font/otf;base64,${data})'); document.fonts.add(await f.load()); const s=document.createElement('style'); s.textContent='* { font-family: FixtureCJK, sans-serif !important }'; document.head.append(s); await document.fonts.ready; })()`)
}

// Optional temporary esbuild output avoids a shared development service and real business APIs.
let server
let root = process.env.POSITIONING_TEST_URL
if (!root && process.env.POSITIONING_TEST_SITE) {
  server = createServer((req, res) => {
    const file = req.url === '/' ? 'index.html' : req.url.slice(1)
    if (!/^[\w.-]+$/.test(file)) { res.writeHead(404).end(); return }
    try {
      res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html; charset=utf-8')
      res.end(readFileSync(join(process.env.POSITIONING_TEST_SITE, file)))
    } catch { res.writeHead(404).end() }
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  root = `http://127.0.0.1:${server.address().port}/`
}
root ||= 'http://127.0.0.1:5188/test/positioning-card-editor.html'
const titles = ['账号名称建议', '目标用户', '抖音平台主页搭建', ...Array.from({length:34}, (_,i)=>`验收字段 ${i+4}`)]
async function geometry(width) {
  const layout = await evaluate(`(() => {
    const modal = [...document.querySelectorAll('.ant-modal')].find(e => e.getBoundingClientRect().width > 0)
    const table = modal.querySelector('.positioning-card-fields')
    const rect = e => { const r=e.getBoundingClientRect(); return {x:r.x,width:r.width,right:r.right} }
    return { headings:[...table.querySelector('.positioning-card-field-head').children].map(rect),
      rows:[...table.querySelectorAll('.positioning-card-field-row')].map(row=>({
        title:row.querySelector('.positioning-card-field-title').textContent,
        cells:[...row.children].map(rect), overflow:row.scrollWidth > row.clientWidth+1,
      })), overflow:modal.scrollWidth > modal.clientWidth+1,
      referenceRows:[...table.querySelectorAll('.positioning-card-field-row')].filter(row=>row.querySelector('button')).map(row=>row.querySelector('.positioning-card-field-title').textContent) }
  })()`)
  assert.deepEqual(layout.rows.map(row => row.title), titles, 'server sort must survive interleaved groups')
  assert.equal(layout.overflow, false, 'modal must not overflow horizontally')
  assert.equal(layout.headings.length, 4)
  for (const row of layout.rows) {
    assert.equal(row.cells.length, 4, `four cells: ${row.title}`)
    assert.equal(row.overflow, false, `no overflow: ${row.title}`)
    row.cells.forEach((cell, index) => {
      assert.ok(cell.x >= 0 && cell.right <= width+1, `in viewport: ${row.title}`)
      if (width > 960) {
        assert.ok(Math.abs(cell.x-layout.headings[index].x)<1, `aligned start: ${row.title}, column ${index}`)
        assert.ok(Math.abs(cell.width-layout.headings[index].width)<1, `aligned width: ${row.title}, column ${index}`)
      }
    })
  }
}
async function input(key, value) {
  await evaluate(`(() => {
    const el = document.querySelector('textarea[id="data_${key}"]')
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(el, ${JSON.stringify(value)})
    el.dispatchEvent(new Event('input',{bubbles:true}))
  })()`)
}
try {
  const target = await send('Target.createTarget', {url:'about:blank'}, null)
  session = (await send('Target.attachToTarget',{targetId:target.targetId,flatten:true},null)).sessionId
  await send('Page.enable'); await send('Runtime.enable')
  for (const width of [1440, 1024, 768, 390]) {
    await send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false})
    await send('Page.navigate',{url:root})
    await until("document.body?.innerText.includes('填写定位卡草稿')")
    await button('填写定位卡草稿')
    await until("document.querySelectorAll('.positioning-card-field-row').length===37")
    await new Promise(resolve=>setTimeout(resolve,400))
    await font(); await geometry(width)
    assert.equal(await evaluate("[...document.querySelectorAll('.positioning-card-field-row')].filter(e=>e.querySelector('button')).map(e=>e.querySelector('.positioning-card-field-title').textContent).join(',')"), '抖音平台主页搭建')
    await input('pc_account_name','首次填写内容')
    await input('pc_target_user','第二行填写内容')
    await input('fixture_33','末行填写内容')
    await shot(`initial-${width}`)
    await button('保存并关闭')
    await until("!document.querySelector('.ant-modal-wrap:not([style*=\"display: none\"])')")
    await button('填写定位卡草稿')
    await until("document.querySelector('textarea[id=\"data_pc_target_user\"]')?.value==='第二行填写内容'")
    await geometry(width)
    assert.equal(await evaluate("document.querySelector('textarea[id=\"data_fixture_33\"]').value"),'末行填写内容')
    await input('pc_target_user','后续修改内容')
    await button('保存并关闭')
    await until("!document.querySelector('.ant-modal-wrap:not([style*=\"display: none\"])')")
    await button('查看快照')
    await until("document.body.innerText.includes('后续修改内容')")
    await geometry(width); await shot(`snapshot-${width}`)
    console.log(`PASS ${width}: 37 rows aligned, configured order/reference preserved, save/reopen/edit/snapshot`)
  }
  assert.deepEqual(errors,[])
} finally { browser.kill(); server?.close() }

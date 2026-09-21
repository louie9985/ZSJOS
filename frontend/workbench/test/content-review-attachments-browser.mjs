// UTF-8. Dependency-free Chromium CDP verification; runs only isolated test fixtures.
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import assert from 'node:assert/strict'
const executable = process.env.CONTENT_TEST_CHROME
if (!executable) throw new Error('Set CONTENT_TEST_CHROME to a Chromium/headless-shell executable')
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
  writeFileSync(`/tmp/zsjos-content-attachments-${name}.png`, Buffer.from(result.data, 'base64'))
}
try {
  const target = await send('Target.createTarget', { url: 'about:blank' }, undefined)
  session = (await send('Target.attachToTarget', { targetId: target.targetId, flatten: true }, undefined)).sessionId
  await send('Runtime.enable'); await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 1000, deviceScaleFactor: 1, mobile: false })
  await send('Page.navigate', { url: 'http://127.0.0.1:5188/test/content-review-attachments.html' })
  await until("document.querySelectorAll('.content-review-attachments').length === 4")
  if (process.env.CONTENT_TEST_FONT) {
    const font = readFileSync(process.env.CONTENT_TEST_FONT).toString('base64')
    await evaluate(`(async () => { const font = new FontFace('FixtureCJK', 'url(data:font/otf;base64,${font})'); document.fonts.add(await font.load()); const style=document.createElement('style'); style.textContent='* { font-family: FixtureCJK, sans-serif !important }'; document.head.append(style); await document.fonts.ready; })()`)
  }
  // Paste actual image File data into each cover and the first work's attachment area.
  const paste = async (index, type = 'image/png', name = '截图.png') => evaluate(`(() => {
    const data = new DataTransfer(); data.items.add(new File([Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aJwAAAABJRU5ErkJggg=='), c=>c.charCodeAt(0))], ${JSON.stringify(name)}, {type:${JSON.stringify(type)}}));
    document.querySelectorAll('.content-review-attachments')[${index}].dispatchEvent(new ClipboardEvent('paste', {bubbles:true,cancelable:true,clipboardData:data}));
  })()`)
  await paste(0); await paste(2); await paste(1)
  await until("document.querySelectorAll('.content-review-attachment').length === 3")
  assert.equal(await evaluate('attachmentFixture.uploads'), 0)
  assert.equal(await evaluate("document.querySelectorAll('.content-review-attachments')[3].querySelectorAll('article').length"), 0)
  // Multiple file selection shares the same per-work queue.
  await evaluate(`(() => { const input=document.querySelectorAll('.content-review-attachments')[1].querySelector('input[type=file]'); const data=new DataTransfer(); data.items.add(new File(['pdf'], '审核.pdf', {type:'application/pdf'})); data.items.add(new File(['doc'], '说明.docx', {type:'application/vnd.openxmlformats-officedocument.wordprocessingml.document'})); input.files=data.files; input.dispatchEvent(new Event('change', {bubbles:true})); })()`)
  await until("document.querySelectorAll('.content-review-attachment').length === 5")
  await evaluate("document.querySelectorAll('.content-review-attachments')[1].scrollIntoView({block:'center'})")
  await shot('desktop')
  // One upload failure prevents the business command; the retry reuses successes.
  await evaluate('attachmentFixture.failNext = true')
  await button('保存草稿')
  await until("document.body.innerText.includes('模拟文件传输失败')")
  assert.equal(await evaluate('attachmentFixture.requests.length'), 0)
  await button('保存草稿')
  await until('attachmentFixture.requests.length === 1')
  assert.equal(await evaluate('attachmentFixture.uploads'), 6)
  assert.equal(await evaluate('JSON.parse(attachmentFixture.requests[0][0].deliverableSnapshotJson).length'), 3)
  assert.equal(await evaluate('attachmentFixture.requests[0][1].deliverableSnapshotJson'), '[]')
  await button('保存草稿')
  await until('attachmentFixture.requests.length === 2')
  assert.equal(await evaluate('attachmentFixture.uploads'), 6)
  // Narrow layouts remain usable and do not overflow their scrolling form.
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  await evaluate("document.querySelectorAll('.content-review-attachments')[1].scrollIntoView({block:'center'})")
  await new Promise(resolve => setTimeout(resolve, 350))
  assert.ok(await evaluate("Array.from(document.querySelectorAll('.content-review-attachments')).every(e => e.scrollWidth <= e.clientWidth + 1)"))
  await new Promise(resolve => setTimeout(resolve, 3200))
  await shot('mobile')
  await evaluate("attachmentFixture.edit('DRAFT')")
  await until("document.body.innerText.includes('历史审核.pdf')")
  await evaluate("document.querySelector('button[aria-label=\"移除 历史审核.pdf\"]').click()")
  await button('确定')
  await until('attachmentFixture.saves.length === 1')
  assert.equal(await evaluate('attachmentFixture.saves[0].body.works[0].deliverableSnapshotJson'), '[]')
  assert.equal(await evaluate('attachmentFixture.saves[0].body.works[0].sourceVersionId'), undefined)
  assert.ok(await evaluate("attachmentFixture.saves[0].url.endsWith('/save-student-draft')"))
  await evaluate("attachmentFixture.edit('REJECTED')")
  await until("document.body.innerText.includes('历史审核.pdf')")
  await button('确定')
  await until('attachmentFixture.saves.length === 2')
  assert.equal(await evaluate('attachmentFixture.saves[1].body.works[0].deliverableSnapshotJson'), '[12]')
  assert.equal(await evaluate('attachmentFixture.saves[1].body.works[0].sourceVersionId'), 20)
  assert.ok(await evaluate("attachmentFixture.saves[1].url.endsWith('/resubmit-from-student')"))
  assert.equal(await evaluate('attachmentFixture.uploads'), 6)
  assert.deepEqual(errors, [])
  console.log('PASS: scoped paste, multi-select, deferred uploads, failure/retry, success reuse, empty attachments, desktop/mobile UI, draft clear, rejected revision retention')
} finally { browser.kill('SIGTERM') }

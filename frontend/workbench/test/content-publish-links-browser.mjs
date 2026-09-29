// UTF-8. Dependency-free Chromium CDP verification; runs only isolated fixtures.
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import assert from 'node:assert/strict'
import { join } from 'node:path'

const executable = process.env.CONTENT_ERROR_TEST_CHROME
if (!executable) throw new Error('Set CONTENT_ERROR_TEST_CHROME to a Chromium/headless-shell executable')
const OUT = process.env.PUBLISH_LINKS_TEST_OUT || '/tmp'
mkdirSync(OUT, { recursive: true })

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
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)) }, 20000)
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
  for (let i = 0; i < 400; i++) {
    if (await evaluate(`Boolean(${expression})`)) return
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  console.error('Browser errors:', errors, 'Body:', await evaluate('document.body?.innerText.slice(0, 3000)'))
  throw new Error(`Browser condition failed: ${expression}`)
}
async function button(text) {
  await evaluate(`Array.from(document.querySelectorAll('button')).find(e => e.textContent.replace(/\\s/g,'') === ${JSON.stringify(text)}).click()`)
}
async function shot(name) {
  await new Promise(resolve => setTimeout(resolve, 400))
  const result = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(join(OUT, `publish-links-${name}.png`), Buffer.from(result.data, 'base64'))
}
async function font() {
  if (!process.env.CONTENT_ERROR_TEST_FONT) return
  const data = readFileSync(process.env.CONTENT_ERROR_TEST_FONT).toString('base64')
  await evaluate(`(async () => { const f = new FontFace('FixtureCJK', 'url(data:font/otf;base64,${data})'); document.fonts.add(await f.load()); const s=document.createElement('style'); s.textContent='* { font-family: FixtureCJK, sans-serif !important }'; document.head.append(s); await document.fonts.ready; })()`)
}

// Reuses the running Vite dev server, which already resolves the app's
// import.meta.env and CSS pipeline; fixtures stub every business request.
const root = process.env.PUBLISH_LINKS_TEST_URL || 'http://127.0.0.1:5174/test/content-publish-links.html'

const openView = async view => {
  await send('Page.navigate', { url: `${root}?view=${view}` })
  await until('document.querySelector("#root")?.childElementCount > 0')
  await font()
}

try {
  const target = await send('Target.createTarget', { url: 'about:blank' }, undefined)
  session = (await send('Target.attachToTarget', { targetId: target.targetId, flatten: true }, undefined)).sessionId
  await send('Runtime.enable'); await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })

  // 1. 登记发布弹窗：发布时间可早于当前时间（真实发布时间）。
  await openView('review')
  await until('document.querySelector(".content-review-list-item")')
  await evaluate('document.querySelector(".content-review-list-item").click()')
  await until('[...document.querySelectorAll("button")].some(e => e.textContent.replace(/\\s/g,"") === "登记发布")')
  await button('登记发布')
  await until('document.querySelector(".ant-modal-body .ant-picker-input input")')
  const publishField = await evaluate('document.querySelector(".ant-modal-body .ant-form-item:last-of-type")?.innerText || ""')
  assert.ok(publishField.includes('真实发布时间'), publishField)
  assert.ok(!/晚于当前时间|之前的时间|不能早于/.test(publishField), publishField)
  // 直接写入一个过去的日期时间，等同于运营补登历史作品。
  const past = await evaluate(`(() => {
    const input = document.querySelector('.ant-modal-body .ant-picker-input input')
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
    setter.call(input, '2026-09-01 08:30:00')
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    return input.value
  })()`)
  assert.equal(past, '2026-09-01 08:30:00')
  await evaluate(`(() => {
    const input = document.querySelector('.ant-modal-body .ant-form-item:first-of-type input')
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
    setter.call(input, 'https://example.com/published-work')
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
  })()`)
  await shot('review-modal-desktop')
  // 确定按钮按结构定位：antd 会在两个中文字之间插入空格，文案匹配不稳。
  await evaluate(`document.querySelector('.ant-modal-footer .ant-btn-primary').click()`)
  await until('publishLinksFixture.posts === 1')
  const payload = await evaluate('publishLinksFixture.requests[0]')
  assert.equal(payload.publishedAt, '2026-09-01T08:30:00', JSON.stringify(payload))
  assert.equal(payload.platformUrl, 'https://example.com/published-work')

  // 2. 账号页作品历史：卡片与详情弹窗复用 ResourceLink。
  await openView('account')
  await until('document.querySelector(".account-published-grid")')
  assert.equal(await evaluate('document.querySelectorAll(".account-work-card").length'), 2)
  assert.equal(await evaluate('document.querySelectorAll(".account-work-link .resource-link-anchor").length'), 1)
  assert.equal(await evaluate('document.querySelector(".account-work-link .resource-link-anchor").getAttribute("href")'), 'https://example.com/published-work')
  assert.equal(await evaluate('document.querySelector(".account-work-link .resource-link-anchor").getAttribute("target")'), '_blank')
  assert.equal(await evaluate('document.querySelector(".account-work-link .resource-link-anchor").getAttribute("rel")'), 'noopener noreferrer')
  // 链接必须在卡片按钮之外：<button> 内嵌 <a> 是无效结构。
  assert.equal(await evaluate('document.querySelectorAll("button a, button .resource-link-anchor").length'), 0)
  await shot('account-desktop')
  await evaluate('document.querySelector(".account-work-open").click()')
  await until('document.querySelector(".ant-modal-body .resource-link-card")')
  assert.equal(await evaluate('document.querySelector(".ant-modal-body .resource-link-card .resource-link-anchor").getAttribute("href")'), 'https://example.com/published-work')
  await shot('account-detail-desktop')

  // 3. 内容生产页：发布结果只读展示 + 版本链接复用 ResourceLink。
  await openView('production')
  await until('document.querySelector(".content-production-published")')
  const publishedSection = await evaluate('document.querySelector(".content-production-published").innerText')
  assert.ok(publishedSection.includes('发布时间'), publishedSection)
  assert.ok(publishedSection.includes('平台链接'), publishedSection)
  assert.equal(await evaluate('document.querySelectorAll(".content-production-published .resource-link-card").length'), 1)
  assert.equal(await evaluate('document.querySelector(".content-production-published .resource-link-anchor").getAttribute("href")'), 'https://example.com/published-work')
  // 版本字段的链接都来自通用组件：每一个 target=_blank 锚点都必须带 resource-link-anchor。
  assert.equal(await evaluate('document.querySelectorAll(".content-production-fields a[target=_blank]:not(.resource-link-anchor)").length'), 0)
  assert.ok(await evaluate('document.querySelectorAll(".content-production-fields .resource-link-anchor").length >= 3'))
  await shot('production-desktop')

  // 4. 窄屏：账号页卡片与内容生产页不产生横向溢出。
  for (const width of [390, 1440]) {
    await send('Emulation.setDeviceMetricsOverride', { width, height: 900, deviceScaleFactor: 1, mobile: width < 768 })
    await openView('account')
    await until('document.querySelector(".account-published-grid")')
    assert.ok(await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'))
    await shot(`account-${width}`)
    await openView('production')
    await until('document.querySelector(".content-production-published")')
    assert.ok(await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'))
    await shot(`production-${width}`)
  }

  assert.deepEqual(errors, [])
  console.log('PASS: 登记发布时间接受过去时间并原样提交；账号页卡片/详情与内容生产页发布链接复用 ResourceLink；390/1440 无横向溢出。')
} finally {
  browser.kill()
}

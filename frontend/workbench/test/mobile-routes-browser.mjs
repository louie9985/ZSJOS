// UTF-8. Isolated production-bundle browser checks; all API responses are synthetic.
import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { createServer } from 'node:http'
import { join, extname } from 'node:path'
import assert from 'node:assert/strict'
const executable = process.env.MOBILE_TEST_CHROME
if (!executable) throw Error('Set MOBILE_TEST_CHROME to an existing Chromium executable')
const adminBundle = process.env.MOBILE_TEST_ADMIN_DIST || '/tmp/zsjos-mobile-admin-dist'
const bundle = process.env.MOBILE_TEST_DIST || '/tmp/zsjos-mobile-dist'
let denied = false, permissionError = false, expireOnce = false
const requests = []
const routes = ['/zsjos/tasks/today', '/zsjos/material-library/content-production', '/zsjos/material-library/content-review', '/bpm/task/todo', '/system/notice', '/system/role']
const names = ['今日待办', '内容制作', '内容审核', '审批中心', '通知公告', '角色管理']
const server = createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost')
  if (url.pathname.startsWith('/admin-api/')) {
    let body = ''; for await (const chunk of req) body += chunk
    requests.push({ path: url.pathname, authorization: req.headers.authorization, tenant: req.headers['tenant-id'], body })
    let code = 0, data = []
    if (url.pathname.endsWith('/get-permission-info')) {
      code = permissionError ? 500 : expireOnce ? 401 : 0; expireOnce = false
      data = { user: { id: 123, username: 'fixture', nickname: '测试员工' }, roles: [], permissions: [], menus: denied ? [] : routes.map((path, i) => ({ id: i + 1, parentId: 0, name: names[i], path, visible: true, keepAlive: true, workbenchRenderMode: i >= 4 ? 'admin_embed' : 'native', component: i >= 4 ? path.slice(1) + '/index' : undefined, type: 2 })) }
    } else if (url.pathname.endsWith('/login')) {
      const platform = JSON.parse(body).platform
      data = { accessToken: `fixture-${platform}`, refreshToken: `fixture-refresh-${platform}`, expiresTime: Date.now() + 7200000, clientId: platform === 'MOBILE' ? 'zsjos-mobile' : 'zsjos-pc' }
    } else if (url.pathname.endsWith('/refresh-token')) {
      assert.equal(url.searchParams.get('clientId'), 'zsjos-mobile')
      data = { accessToken: 'fixture-refreshed', refreshToken: 'fixture-refresh-MOBILE' }
    } else if (url.pathname.endsWith('/profile/get')) data = { id: 123, nickname: '测试员工', sex: 1, roles: [], posts: [] }
    else if (/count/.test(url.pathname)) data = 0
    else if (/page|cursor/.test(url.pathname)) data = { list: [], total: 0 }
    else if (/forced-form\/status/.test(url.pathname)) data = { required: false, pendingCount: 0 }
    res.setHeader('Content-Type', 'application/json; charset=utf-8')
    res.end(JSON.stringify({ code, data, msg: code ? 'fixture error' : '' })); return
  }
  if (url.pathname === '/fixture-font.otf' && process.env.MOBILE_TEST_FONT) { res.setHeader('Content-Type', 'font/otf'); res.end(readFileSync(process.env.MOBILE_TEST_FONT)); return }
  const embedPath = url.pathname.replace(/^\/admin-embed\//, '')
  const path = url.pathname.startsWith('/admin-embed/')
    ? join(adminBundle, /\.[a-z0-9]+$/i.test(embedPath) ? embedPath : 'index.html')
    : url.pathname.startsWith('/assets/') ? join(bundle, url.pathname) : join(bundle, 'index.html')
  try { res.setHeader('Content-Type', ({ '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml' })[extname(path)] || 'application/octet-stream'); res.end(readFileSync(path)) }
  catch { res.statusCode = 404; res.end() }
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const base = `http://127.0.0.1:${server.address().port}`
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
  await send('Input.dispatchMouseEvent', {type:'mouseMoved', x:1, y:1})
  if (process.env.MOBILE_TEST_FONT) {
    await evaluate(`(async () => { for (const win of [window, ...Array.from(document.querySelectorAll('iframe')).map(frame=>frame.contentWindow)]) { const font = new win.FontFace('FixtureCJK', 'url(/fixture-font.otf)'); win.document.fonts.add(await font.load()); const style = win.document.createElement('style'); style.textContent = '* { font-family: FixtureCJK, sans-serif !important; }'; win.document.head.append(style); await win.document.fonts.ready } })()` )
  }
  await new Promise(resolve => setTimeout(resolve, 400))
  const result = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`/tmp/zsjos-mobile-${name}.png`, Buffer.from(result.data, 'base64'))
}
try {
  const target = await send('Target.createTarget', { url: 'about:blank' }, undefined)
  session = (await send('Target.attachToTarget', { targetId: target.targetId, flatten: true }, undefined)).sessionId
  await send('Runtime.enable'); await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  await send('Page.navigate', { url: `${base}/zsjos/mobile/` })
  await until("document.querySelector('input')")
  await evaluate(`localStorage.setItem('ACCESS_TOKEN', 'fixture-PC'); localStorage.setItem('REFRESH_TOKEN', 'fixture-refresh-PC'); localStorage.setItem('CLIENT_ID', 'zsjos-pc')`)
  await evaluate(`(() => { const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set; document.querySelectorAll('input').forEach((e,i) => { setter.call(e, i ? 'synthetic-password' : 'fixture'); e.dispatchEvent(new Event('input',{bubbles:true})) }) })()`)
  await button('登录')
  await until("location.pathname === '/zsjos/mobile/zsjos/tasks/today' && document.querySelector('.mobile-nav-trigger')")
  assert.equal(JSON.parse(requests.find(r => r.path.endsWith('/login')).body).platform, 'MOBILE')
  assert.equal(await evaluate("localStorage.getItem('ACCESS_TOKEN')"), 'fixture-PC')
  await click('.mobile-nav-trigger')
  await until("document.querySelector('.ant-drawer-body')")
  await evaluate("Array.from(document.querySelectorAll('.ant-drawer-body .ant-menu-title-content')).find(e=>e.textContent.includes('内容制作')).click()")
  await until("location.pathname.endsWith('/content-production') && document.querySelector('.content-production-page')")
  await until("!document.querySelector('.ant-drawer-open')")
  await shot('production-390')
  await evaluate('history.back()'); await until("location.pathname === '/zsjos/mobile/zsjos/tasks/today'")
  await evaluate('history.forward()'); await until("document.querySelector('.content-production-page')")
  assert.equal(await evaluate("document.body?.innerText.includes('请使用电脑端访问')"), false)
  await send('Page.reload')
  await until("document.querySelector('.content-production-page')")
  // A copied URL in a new tab must restore Mobile even without sessionStorage.
  const copyTarget = await send('Target.createTarget', { url: 'about:blank' }, undefined)
  session = (await send('Target.attachToTarget', { targetId: copyTarget.targetId, flatten: true }, undefined)).sessionId
  await send('Runtime.enable'); await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  await send('Page.navigate', { url: `${base}/zsjos/mobile/zsjos/material-library/content-review?view=mine#detail` })
  await until("document.querySelector('.content-review-page')")
  assert.equal(await evaluate("sessionStorage.getItem('zsjos.auth.platform')"), 'MOBILE')
  assert.equal(await evaluate('location.search + location.hash'), '?view=mine#detail')
  await shot('review-390')
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
  await send('Page.reload'); await until("document.querySelector('.content-review-page')")
  await shot('review-1440')
  expireOnce = true
  await send('Page.reload'); await until("document.querySelector('.content-review-page')")
  assert.ok(requests.some(r=>r.path.endsWith('/refresh-token')))
  assert.equal(await evaluate("localStorage.getItem('ACCESS_TOKEN')"), 'fixture-PC')
  assert.equal(await evaluate("localStorage.getItem('MOBILE_ACCESS_TOKEN')"), 'fixture-refreshed')
  permissionError = true
  await send('Page.reload'); await until("document.body?.innerText.includes('fixture error')")
  permissionError = false; await button('重试'); await until("document.querySelector('.content-review-page')")
  const avatar = await evaluate("(() => { const r=document.querySelector('.ant-avatar').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2} })()")
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', ...avatar })
  await until("Array.from(document.querySelectorAll('.ant-dropdown-menu-item')).some(e=>e.textContent.includes('退出登录'))")
  await evaluate("Array.from(document.querySelectorAll('.ant-dropdown-menu-item')).find(e=>e.textContent.includes('退出登录')).click()")
  await until("document.querySelector('.login-page')")
  assert.equal(await evaluate("localStorage.getItem('MOBILE_ACCESS_TOKEN')"), null)
  assert.equal(await evaluate("localStorage.getItem('ACCESS_TOKEN')"), 'fixture-PC')
  await evaluate("localStorage.setItem('MOBILE_ACCESS_TOKEN','fixture-MOBILE');localStorage.setItem('MOBILE_REFRESH_TOKEN','fixture-refresh-MOBILE');localStorage.setItem('MOBILE_CLIENT_ID','zsjos-mobile')")
  denied = true
  await send('Page.reload'); await until("document.querySelector('.ant-result')")
  assert.equal(await evaluate("Boolean(document.querySelector('.content-review-page'))"), false)
  denied = false
  // Fresh authentication on a deep link must not discard query/hash or jump home.
  await evaluate("localStorage.removeItem('MOBILE_ACCESS_TOKEN'); localStorage.removeItem('MOBILE_REFRESH_TOKEN')")
  await send('Page.reload'); await until("document.querySelector('.login-page')")
  await evaluate(`(() => { const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set; document.querySelectorAll('input').forEach((e,i) => { setter.call(e, i ? 'synthetic-password' : 'fixture'); e.dispatchEvent(new Event('input',{bubbles:true})) }) })()`)
  await button('登录'); await until("document.querySelector('.content-review-page')")
  assert.equal(await evaluate('location.pathname + location.search + location.hash'), '/zsjos/mobile/zsjos/material-library/content-review?view=mine#detail')
  console.log('Native Mobile flows passed; checking real Vue embed')
  await send('Page.navigate', { url: `${base}/zsjos/mobile/system/notice` })
  await until("document.querySelector('iframe')?.contentWindow.document.querySelector('.el-table')")
  assert.ok(requests.some(r=>r.path.endsWith('/system/notice/page') && r.authorization === 'Bearer fixture-MOBILE'))
  assert.equal(await evaluate("localStorage.getItem('CLIENT_ID')"), 'zsjos-pc')
  assert.ok(await evaluate("localStorage.getItem('mobileUser')"))
  assert.equal(await evaluate("localStorage.getItem('user')"), null)
  await shot('embed-1440')
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  await shot('embed-390')
  // The real Vue bridge navigates within one iframe; outer links stay namespaced.
  await evaluate("document.querySelector('iframe').contentWindow.postMessage({type:'zsjos:admin-embed:navigate',path:'/system/role?fixture=1#detail'},location.origin)")
  await until("location.pathname === '/zsjos/mobile/system/role'")
  assert.equal(await evaluate('location.search + location.hash'), '?fixture=1#detail')
  expireOnce = true
  await evaluate("document.querySelector('iframe').contentWindow.location.reload()")
  await until("localStorage.getItem('MOBILE_ACCESS_TOKEN') === 'fixture-refreshed' && document.querySelector('iframe')?.contentWindow.document.querySelector('.el-table')")
  console.log('Vue Mobile refresh passed')
  assert.equal(await evaluate("localStorage.getItem('CLIENT_ID')"), 'zsjos-pc')
  await evaluate("localStorage.removeItem('MOBILE_ACCESS_TOKEN');localStorage.removeItem('MOBILE_REFRESH_TOKEN');document.querySelector('iframe').contentWindow.location.reload()")
  await until("document.querySelector('.login-page')")
  assert.equal(await evaluate('location.pathname'), '/zsjos/mobile/system/role')
  // Ordinary PC tabs retain canonical URLs and the separate PC token.
  await evaluate("sessionStorage.clear()")
  await send('Page.navigate', { url: `${base}/zsjos/tasks/today` })
  await until("document.querySelector('.mobile-nav-trigger')")
  assert.equal(await evaluate('location.pathname'), '/zsjos/tasks/today')
  assert.equal(requests.filter(r=>r.path.endsWith('/get-permission-info')).at(-1).authorization, 'Bearer fixture-PC')
  await send('Page.navigate', { url: `${base}/system/notice` })
  await until("document.querySelector('iframe')?.contentWindow.document.querySelector('.el-table')")
  assert.equal(requests.filter(r=>r.path.endsWith('/system/notice/page')).at(-1).authorization, 'Bearer fixture-PC')
  assert.equal(await evaluate('location.pathname'), '/system/notice')
  assert.equal(await evaluate("document.querySelector('iframe').contentWindow.document.body.innerText.includes('新增')"), false)
  assert.deepEqual(errors, [])
  console.log('PASS Mobile menu navigation, reload, fresh-tab deep links, query/hash, login return, refresh, PC isolation, error/retry, denied menus/actions, real Vue Mobile/PC embeds and Mobile expiry return; screenshots 390/1440')
} finally {
  browser.kill()
  server.close()
}

// UTF-8. Dependency-free Chromium CDP verification; runs only isolated test fixtures.
import { spawn } from 'node:child_process'
import { writeFileSync, readFileSync, existsSync } from 'node:fs'
import { createServer } from 'node:http'
import { resolve, extname } from 'node:path'
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
  const result = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`/tmp/zsjos-table-${name}.png`, Buffer.from(result.data, 'base64'))
}
// Build with VITE_APP_BASE_API=/app-api and pass the isolated output directory.
const root = resolve(process.argv[2] || '/tmp/zsjos-h5-notify-dist')
let mode = 'success', attempts = 0
const server = createServer((req, res) => {
 const path = new URL(req.url, 'http://localhost').pathname
 if (path === '/test-cjk.otf' && process.env.H5_TEST_FONT) { res.end(readFileSync(process.env.H5_TEST_FONT)); return }
 if (path.startsWith('/app-api/')) {
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  let data = null, code = 0, msg = ''
  if (path.endsWith('/permission-info')) data = {user:{id:7,nickname:'合成测试'},permissions:mode === 'unauthorized' ? [] : ['zsjos:lead:query-submitted'],roles:[]}
  else if (path.endsWith('/messages/1')) {
   if (mode === 'error' && attempts++ === 0) { code = 500; msg = '合成接口失败' }
   else data = {id:1,templateTitle:'订单通知测试',templateContent:'合成消息正文',createTime:'2026-09-21T08:00:00',readStatus:true,bizType:'sales_order',bizId:20,
    actionType:mode === 'message' ? 'message_detail' : 'business_detail',
    businessTarget:mode === 'missing' ? null : '/lead/30',targetUnavailableReason:mode === 'missing' ? '关联订单不存在或当前账号无权查看' : null}
  } else if (path.endsWith('/messages/unread-count')) data = 0
  else if (path.includes('/lead')) { code = 403; msg = '合成客资详情权限验证' }
  res.end(JSON.stringify({code,data,msg})); return
 }
 const candidate = resolve(root, '.' + path)
 const file = candidate.startsWith(root + '/') && existsSync(candidate) && extname(candidate) ? candidate : resolve(root,'index.html')
 res.setHeader('Content-Type', ({'.js':'text/javascript','.css':'text/css','.html':'text/html','.svg':'image/svg+xml'})[extname(file)] || 'application/octet-stream')
 res.end(readFileSync(file))
})
await new Promise(resolve => server.listen(0,'127.0.0.1',resolve))
const base = `http://127.0.0.1:${server.address().port}`
try {
 const {targetId}=await send('Target.createTarget',{url:'about:blank'});
 session=(await send('Target.attachToTarget',{targetId,flatten:true})).sessionId;
 await send('Runtime.enable'); await send('Page.enable');
 await send('Page.addScriptToEvaluateOnNewDocument',{source:"localStorage.setItem('h5_access_token','synthetic-only')"});
 for (const width of [1440,390]) {
  await send('Emulation.setDeviceMetricsOverride',{width,height:844,deviceScaleFactor:1,mobile:width===390});
  for (const scenario of ['success','missing','message','error','unauthorized']) {
   mode=scenario; attempts=0; console.log('Checking',width,scenario);
   await send('Page.navigate',{url:base+'/messages/1'});
   if (scenario==='error') {
    await until("document.body?.innerText.includes('重新加载')");
    await button('重新加载');
   }
   await until("document.body?.innerText.includes('合成消息正文')");
   if (process.env.H5_TEST_FONT) {
    await evaluate(`(async()=>{const f=new FontFace('FixtureCJK','url(/test-cjk.otf)');await f.load();document.fonts.add(f);const s=document.createElement('style');s.textContent='*:not(.van-icon) { font-family: FixtureCJK, sans-serif !important; }';document.head.append(s)})()`);
   }
   if (scenario==='missing') {
    assert.ok(await evaluate("document.querySelector('[role=status]').textContent.includes('无权查看')"));
    assert.equal(await evaluate("!!document.querySelector('.message-detail__action')"),false);
    await shot(`h5-message-missing-${width}`);
   } else if (scenario==='message') {
    assert.equal(await evaluate("!!document.querySelector('.message-detail__action')"),false);
   } else {
    await shot(`h5-message-${scenario}-${width}`);
    await evaluate("document.querySelector('.message-detail__action').click()");
    await until(scenario==='unauthorized' ? "location.pathname==='/unauthorized'" : "location.pathname==='/lead/30'");
   }
  }
 }
 assert.deepEqual(errors,[]);
 console.log('PASS: H5 production bundle, desktop/mobile exact order target, unavailable target, message-only action, API retry, permission denial; no runtime exceptions');
} finally { browser.kill(); server.close(); }

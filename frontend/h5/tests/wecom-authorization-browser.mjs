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
const root = resolve(process.argv[2] || 'dist')
const requests = []
const server = createServer((req,res) => {
 const u = new URL(req.url,'http://localhost');
 if(u.pathname.startsWith('/part-api/')) {
  res.setHeader('Content-Type','application/json; charset=utf-8');
  let data = 0, code = 0, msg = '';
  if(u.pathname.endsWith('/wecom-authorize-url')) {
   requests.push(Object.fromEntries(u.searchParams)); code=500; msg='合成授权失败，可重试';
  } else if(u.pathname.endsWith('/permission-info')) data={user:{id:7,nickname:'测试'},permissions:[],roles:[]};
  else if(u.pathname.endsWith('/profile/get')) data={nickname:'测试',wecomBound:false,wecomEnabled:false};
  res.end(JSON.stringify({code,data,msg})); return;
 }
 const candidate=resolve(root,'.'+u.pathname);
 const f=candidate.startsWith(root+'/') && existsSync(candidate) && extname(candidate) ? candidate : resolve(root,'index.html');
 res.setHeader('Content-Type',({'.js':'text/javascript','.css':'text/css','.html':'text/html'})[extname(f)] || 'application/octet-stream');res.end(readFileSync(f));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const base=`http://127.0.0.1:${server.address().port}`;
try {
 const {targetId}=await send('Target.createTarget',{url:'about:blank'});
 session=(await send('Target.attachToTarget',{targetId,flatten:true})).sessionId;
 await send('Runtime.enable');await send('Page.enable');
 for(const inClient of [true,false]) {
  console.log('Checking mode',inClient);
  await send('Emulation.setUserAgentOverride',{userAgent:inClient?'Mozilla/5.0 MicroMessenger wxwork/4.1':'Mozilla/5.0 Chrome/130'});
  await send('Emulation.setDeviceMetricsOverride',{width:inClient?390:1440,height:844,deviceScaleFactor:1,mobile:inClient});
  await send('Page.navigate',{url:base+'/login'});
  await until("!!document.querySelector('.login-wecom-btn')");
  await evaluate('localStorage.clear()');
  const before=requests.length;
  await new Promise(r=>setTimeout(r,500));assert.equal(requests.length,before);
  await click('.login-wecom-btn');await until("document.body?.innerText.includes('同意并继续')");
  assert.equal(requests.length,before);await button('再看看');await new Promise(r=>setTimeout(r,400));assert.equal(requests.length,before);
  await click('.login-wecom-btn');await until("document.body?.innerText.includes('同意并继续')");await button('同意并继续');
  await until("document.body?.innerText.includes('合成授权失败')");
  assert.equal(requests.at(-1).inWecom,String(inClient));
  assert.ok(requests.at(-1).redirectUri.includes('/login?'));
  await new Promise(r=>setTimeout(r,2300));await click('.login-wecom-btn');await new Promise(r=>setTimeout(r,400));assert.equal(requests.length,before+2);
  await evaluate("localStorage.setItem('h5_access_token','synthetic-only')");
  console.log('Checking binding',inClient);await send('Page.navigate',{url:base+'/profile'});await until("document.body?.innerText.includes('去绑定')");
  await button('去绑定');await until("document.body?.innerText.includes('合成授权失败')");
  assert.equal(requests.at(-1).inWecom,String(inClient));assert.ok(requests.at(-1).redirectUri.includes('/profile?wecomBind=1'));
  await evaluate('localStorage.clear()');
 }
 assert.deepEqual(errors,[]);console.log('PASS: consent/cancel, no auto redirect, retry, in-client vs browser login and bind mode');
} finally {browser.kill();server.close();}

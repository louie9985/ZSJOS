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
const root=resolve(process.argv[2] || '/tmp/zsjos-login-guidance-dist');
let scenario='unbound', refreshes=0, callbackCalls=0;
const server=createServer((req,res)=>{
 const u=new URL(req.url,'http://localhost');
 if(u.pathname==='/fixture-font.otf' && process.env.H5_TEST_FONT){res.end(readFileSync(process.env.H5_TEST_FONT));return;}
 if(u.pathname.startsWith('/part-api/')) {
  res.setHeader('Content-Type','application/json; charset=utf-8');
  let data=0,code=0,msg='';
  if(u.pathname.endsWith('/wecom-login')) {
   callbackCalls++;code=scenario==='unbound'?1900000018:1900000999;
   msg=scenario==='unbound'?'当前企业微信尚未绑定兼职账号':'企业微信身份核验失败（60011）';
  } else if(u.pathname.endsWith('/refresh-token')) {
   refreshes++;if(scenario==='expired'){code=401;msg='刷新令牌已过期'}
   else data={accessToken:'renewed-fixture',refreshToken:'fixture-seven-day',clientId:'zsjos-mobile'};
  } else if(u.pathname.endsWith('/permission-info')) {
   if(req.headers.authorization==='Bearer expired-fixture'){code=401;msg='访问令牌已过期'}
   else data={user:{id:7,nickname:'合成测试'},permissions:[],roles:[]};
  } else if(u.pathname.endsWith('/profile/get')) data={name:'合成测试',wecomBound:false};
  else if(u.pathname.endsWith('/partner/me')) data={name:'合成测试'};
  res.end(JSON.stringify({code,data,msg}));return;
 }
 const candidate=resolve(root,'.'+u.pathname);
 const f=candidate.startsWith(root+'/')&&existsSync(candidate)&&extname(candidate)?candidate:resolve(root,'index.html');
 res.setHeader('Content-Type',({'.js':'text/javascript','.css':'text/css','.html':'text/html'})[extname(f)]||'application/octet-stream');res.end(readFileSync(f));
});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=`http://127.0.0.1:${server.address().port}`;
try {
 const {targetId}=await send('Target.createTarget',{url:'about:blank'});session=(await send('Target.attachToTarget',{targetId,flatten:true})).sessionId;
 await send('Runtime.enable');await send('Page.enable');
 for(const width of [390,1440]) {
  await send('Emulation.setDeviceMetricsOverride',{width,height:844,deviceScaleFactor:1,mobile:width===390});
  scenario='unbound';await send('Page.navigate',{url:base+'/login?code=fixture&state=fixture&redirect=%2Fprofile'});
  await until("document.body?.innerText.includes('使用账号密码登录')");
  assert.ok(await evaluate("document.body.innerText.includes('激活账号')"));
  assert.equal(await evaluate("new URL(location.href).searchParams.has('code')"),false);
  assert.equal(await evaluate("localStorage.getItem('h5_access_token')"),null);
  if(process.env.H5_TEST_FONT) await evaluate(`(async()=>{const f=new FontFace('FixtureCJK','url(/fixture-font.otf)');await f.load();document.fonts.add(f);const s=document.createElement('style');s.textContent='*:not(.van-icon){font-family:FixtureCJK,sans-serif!important}';document.head.append(s)})()`);
  await new Promise(r=>setTimeout(r,400));
  await shot(`unbound-${width}`);
  await button('使用账号密码登录');await new Promise(r=>setTimeout(r,400));
  assert.equal(await evaluate("location.pathname"),'/login');
  const calls=callbackCalls;await send('Page.reload');await until("!!document.querySelector('.login-wecom-btn')");
  assert.equal(callbackCalls,calls);
  scenario='denied';await send('Page.navigate',{url:base+'/login?code=fixture&state=fixture'});
  await until("document.body?.innerText.includes('60011')");
  assert.equal(await evaluate("document.body.innerText.includes('尚未绑定企业微信')"),false);
 }
 for(const mode of ['refresh','expired']) {
  scenario=mode;refreshes=0;
  await evaluate("localStorage.setItem('h5_access_token','expired-fixture');localStorage.setItem('h5_refresh_token','fixture-seven-day')");
  await send('Page.navigate',{url:base+'/login?redirect=%2Fprofile'});
  if(mode==='refresh') {
   await until("location.pathname==='/profile'");assert.equal(await evaluate("localStorage.getItem('h5_access_token')"),'renewed-fixture');
  } else {
   await until("!!document.querySelector('.login-wecom-btn') && !localStorage.getItem('h5_access_token')");
   assert.equal(await evaluate("localStorage.getItem('h5_refresh_token')"),null);
  }
  assert.equal(refreshes,1);await evaluate('localStorage.clear()');
 }
 assert.deepEqual(errors,[]);console.log('PASS: unbound popup/account guidance, no callback replay, other error distinction, remembered-session refresh and expiry');
} finally {browser.kill();server.close();}

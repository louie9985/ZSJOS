// UTF-8. Exercise the Vue client's actual response interceptor with isolated imports.
import { build } from 'esbuild'
import assert from 'node:assert/strict'
import vm from 'node:vm'
const fixtures = {
  axios: `export default { create: () => ({ interceptors: { request: { use() {} }, response: { use(fn) { globalThis.responseInterceptor = fn } } } }) }`,
  'element-plus': `export const ElMessage = { error(msg) { globalThis.messages.push(msg) } }; export const ElMessageBox = {}`,
  qs: `export default { stringify() { return '' } }`,
  '@/config/axios/config': `export const config = { result_code: 200, base_url: '', request_timeout: 1 }`,
  '@/utils/auth': `export const getAccessToken=()=>undefined, getRefreshToken=()=>undefined, getClientId=()=>undefined, getTenantId=()=>undefined, getVisitTenantId=()=>undefined, removeToken=()=>{}, setToken=()=>{}`,
  '@/utils/workbenchAuth': `export const isMobileWorkbench=()=>false, returnToMobileWorkbench=()=>{}`,
  '@/router': `export const resetRouter=()=>{}`,
  '@/hooks/web/useCache': `export const deleteUserCache=()=>{}`,
  '@/utils/encrypt': `export const ApiEncrypt={getEncryptHeader:()=> 'x-encrypted'}`,
  '@/utils/impersonation': `export const getStoredImpersonation=()=>undefined, handleImpersonationInvalid=()=>{}`,
}
const result = await build({ entryPoints: ['../admin/src/config/axios/service.ts'], bundle: true, write: false, format: 'iife', define: { 'import.meta.env': '{}' }, plugins: [{ name: 'isolated-transport', setup(build) {
  build.onResolve({ filter: /.*/ }, args => args.path in fixtures ? { path: args.path, namespace: 'fixture' } : undefined)
  build.onLoad({ filter: /.*/, namespace: 'fixture' }, args => ({ contents: fixtures[args.path], loader: 'js' }))
} }] })
const context = vm.createContext({ messages: [], useI18n: () => ({ t: value => value }), console })
vm.runInContext(result.outputFiles[0].text, context)
for (const code of [1900012100, 1900012124, 1900012137]) {
  const msg = '第 2 件作品：引流资料链接格式不正确，请填写包含有效域名的完整 HTTPS 地址'
  const response = { data: { code, msg, data: { fieldPath: 'works[1].leadResourceUrl', workIndex: 1 } }, config: {}, headers: {}, request: {} }
  await assert.rejects(context.responseInterceptor(response), error => error === 'error')
  assert.equal(context.messages.at(-1), msg)
  await assert.rejects(context.responseInterceptor({ ...response, config: { preserveBusinessError: true } }), error => error.message === msg && error.code === code)
}
const success = { code: 0, data: { id: 7 } }
assert.equal(await context.responseInterceptor({ data: success, headers: {}, request: {}, config: {} }), success)
console.log('PASS: Vue interceptor code/msg compatibility for 3 content errors, optional field details, opted-in business errors and success envelopes')

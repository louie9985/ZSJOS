// Test-only API fixtures for the real production bundle; never forwards payment requests.
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { resolve, extname, sep } from 'node:path'

const root = resolve(import.meta.dirname, '../dist')
const attempts = new Map()
const items = [
  { productName: '健康管理师培训课程', skuName: '高级班', actualAmount: 3980,
    specs: [{ attrKey: 'mode', attrName: '授课方式', value: 'online', label: '线上授课' },
      { attrKey: 'package', attrName: '套餐', value: 'standard', label: '标准套餐' }] },
  { productName: '营养指导专题课程', skuName: '进阶班', actualAmount: 680,
    specs: [{ attrKey: 'hours', attrName: '课时', value: '12', label: '12 课时' }] },
]
createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost')
  const send = payload => { res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(payload)) }
  if (url.pathname.startsWith('/public-api/')) {
    if (req.method !== 'GET') { req.resume(); return send({ code: 1, msg: '测试预览不发起真实支付' }) }
    const scenario = url.pathname.split('/').at(-1)
    if (url.searchParams.get('token') !== 'fixture') return send({ code: 1, msg: '支付链接无效' })
    const count = attempts.get(scenario) || 0
    attempts.set(scenario, count + 1)
    if (scenario === 'error' || (scenario === 'retry' && count === 0)) return send({ code: 1, msg: '支付渠道暂不可用' })
    if (scenario === 'loading') await new Promise(resolve => setTimeout(resolve, 3000))
    const productItems = scenario === 'empty' ? [] : scenario === 'legacy' ? [{ actualAmount: 3980 }]
      : scenario === 'multi' ? items : [items[0]]
    return send({ code: 0, data: { paymentIntentNo: scenario, amount: scenario === 'multi' ? 4660 : 3980,
      currency: 'CNY', status: scenario === 'expired' ? 'expired' : 'created', items: productItems } })
  }
  try {
    const path = url.pathname.startsWith('/assets/') ? resolve(root, '.' + url.pathname) : resolve(root, 'index.html')
    if (!path.startsWith(root + sep)) { res.writeHead(404); return res.end() }
    const body = await readFile(path)
    const type = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' }[extname(path)] || 'application/octet-stream'
    res.writeHead(200, { 'Content-Type': type + '; charset=utf-8' }); res.end(body)
  } catch { res.writeHead(404); res.end() }
}).listen(5187, '127.0.0.1', () => console.log('Payment test preview: http://localhost:5187/pay/multi?token=fixture'))

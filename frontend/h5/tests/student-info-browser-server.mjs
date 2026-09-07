// Test-only fixtures serving the production bundle; no production data or accounts.
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { resolve, extname } from 'node:path'
const root = resolve(import.meta.dirname, '../dist')
const definitions = [
  ['registration_category', '报名分类', 'dict'], ['skill_level_name', '技能等级名称', 'dict'],
  ['name', '姓名', 'text'], ['gender', '性别', 'dict'], ['age', '年龄', 'text'],
  ['id_card', '身份证号码', 'text'], ['household_area', '户籍所在地', 'area'], ['mobile', '手机号', 'text'],
  ['education_level', '现学历层次', 'dict'], ['school', '毕业院校', 'text'], ['graduation_time', '毕业时间', 'text'],
  ['employer', '工作单位', 'text'], ['job', '岗位', 'text'], ['study_purpose', '您报名的学习目的', 'textarea'],
  ['mailing_address', '邮寄地址', 'textarea'], ['registration_teacher', '报名老师', 'text']
]
const fields = definitions.map(([key, label, type]) => ({ key, label, type, enabled: true,
  required: key === 'name', sensitive: ['mobile', 'id_card'].includes(key), note: key === 'name' ? '测试备注：请确认姓名' : '' }))
const options = Object.fromEntries(fields.filter(f => f.type === 'dict').map(f => [f.key, [{ value: 'fixture', label: '测试选项' }]]))
let submitted = false
createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost')
  const json = data => { res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify({ code: 0, data })) }
  if (url.pathname === '/public-api/zsjos/student-info-form/detail') {
    const status = req.headers['x-student-info-token'] === 'e'.repeat(43) ? 'EXPIRED' : submitted ? 'SUBMITTED' : 'DRAFT'
    return json({ status, tenantId: 1, configVersion: 1, fields: status === 'DRAFT' ? fields : [], options })
  }
  if (url.pathname === '/public-api/zsjos/student-info-form/submit') { req.resume(); submitted = true; return json(true) }
  if (url.pathname === '/app-api/system/area/tree') return json([{ id: 10, name: '测试省', children: [{ id: 20, name: '测试市' }] }])
  try {
    const path = url.pathname.startsWith('/assets/') ? resolve(root, '.' + url.pathname) : resolve(root, 'index.html')
    if (!path.startsWith(root + '/assets/') && !path.startsWith(root + '\\assets\\') && path !== resolve(root, 'index.html')) { res.writeHead(404); return res.end() }
    const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' }
    const body = await readFile(path)
    res.writeHead(200, { 'Content-Type': (types[extname(path)] || 'application/octet-stream') + '; charset=utf-8' }); res.end(body)
  } catch { res.writeHead(404); res.end() }
}).listen(5186, '127.0.0.1', () => console.log('Browser fixture listening on 127.0.0.1:5186'))

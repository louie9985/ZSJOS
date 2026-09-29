import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'

function loadApi(get) {
  const source = readFileSync(new URL('../src/api/zsjos/calendar/index.ts', import.meta.url), 'utf8')
  const js = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true }
  }).outputText
  const exports = {}
  runInNewContext(js, { exports, require: id => {
    assert.equal(id, '@/config/axios')
    return { get }
  } })
  return exports
}

for (const calendarType of ['EXAM', 'COURSE']) {
  test(`${calendarType} employee lookup preserves page metadata and explicit calendar permission context`, async () => {
    const page = { list: [{ id: 7, nickname: '测试员工', deptId: 31 }], total: 23 }
    const calls = []
    const api = loadApi(async options => { calls.push(options); return page })
    assert.equal(await api.getCalendarNotifyUsers(calendarType, '测试', 2, 20), page)
    assert.deepEqual(JSON.parse(JSON.stringify(calls)), [{
      url: '/zsjos/calendar-notification/users',
      params: { calendarType, keyword: '测试', pageNo: 2, pageSize: 20 }
    }])
  })
}

test('employee lookup propagates authorization errors', async () => {
  const denied = new Error('没有该日历的发送通知权限')
  const api = loadApi(async () => { throw denied })
  await assert.rejects(api.getCalendarNotifyUsers('COURSE'), error => error === denied)
})

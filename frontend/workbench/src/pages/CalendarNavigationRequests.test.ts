import { readFileSync } from 'node:fs'
import ts from 'typescript'
import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'

// Execute the actual page loader, with controlled promises, to cover response ordering.
function loader(page: string, bindings: Record<string, unknown>): () => Promise<void> {
  const source = readFileSync(new URL('./' + page + '.tsx', import.meta.url), 'utf8')
  const tree = ts.createSourceFile(page + '.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  let callback = ''
  function visit(node: ts.Node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(tree) === 'load' && node.initializer && ts.isCallExpression(node.initializer)) callback = node.initializer.arguments[0].getText(tree)
    ts.forEachChild(node, visit)
  }
  visit(tree)
  expect(callback).not.toBe('')
  const code = ts.transpile('const callback = ' + callback, { target: ts.ScriptTarget.ES2022 })
  return new Function(...Object.keys(bindings), code + '; return callback')(...Object.values(bindings))
}
function deferred() {
  let resolve!: (value: unknown) => void
  let reject!: (error: Error) => void
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
describe.each(['CourseCalendarPage', 'MediaCalendarPage'])('%s navigation requests', page => {
  it.each([false, true])('ignores obsolete success/error even if it completes first: %s', async oldFirst => {
    for (const fails of [false, true]) {
      const old = deferred(), latest = deferred()
      let calls = 0
      const fetch = () => (++calls === 1 ? old.promise : latest.promise)
      const state: Record<string, unknown> = {}
      const setter = (key: string) => (value: unknown) => { state[key] = value }
      const run = loader(page, {
        api: { courseCalendar: { page: fetch }, mediaAccount: { calendar: fetch } },
        requestSequence: { current: 0 }, range: { start: dayjs('2026-10-01'), end: dayjs('2026-10-31') },
        director: undefined, keyword: '', operator: undefined, stage: undefined, status: undefined, locationTarget: { current: undefined },
        setRows: setter('rows'), setUnscheduled: setter('unscheduled'), setLoading: setter('loading'), setError: setter('error'), ApiError: class extends Error {},
      })
      const response = (id: number) => page === 'CourseCalendarPage' ? [{ id }] : { list: [{ id }], unscheduledCount: id }
      const first = run(), second = run()
      if (!oldFirst) { latest.resolve(response(2)); await second }
      if (fails) old.reject(new Error('obsolete failure')); else old.resolve(response(1))
      await first
      if (oldFirst) {
        expect(state.loading).toBe(true); expect(state.rows).toEqual([]); expect(state.error).toBe('')
        latest.resolve(response(2)); await second
      }
      expect(state.rows).toEqual([{ id: 2 }]); expect(state.error).toBe(''); expect(state.loading).toBe(false)
      if (page === 'MediaCalendarPage') expect(state.unscheduled).toBe(2)
    }
  })
})

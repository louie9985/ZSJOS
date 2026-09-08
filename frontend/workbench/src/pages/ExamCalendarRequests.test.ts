import { readFileSync } from 'node:fs'
import ts from 'typescript'
import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'

const source = readFileSync(new URL('./ExamCalendarPage.tsx', import.meta.url), 'utf8')
const tree = ts.createSourceFile('ExamCalendarPage.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)

// Execute the page's actual loaders with controlled promises, without copying their implementation.
function loader(name: string, bindings: Record<string, unknown>): () => Promise<void> {
  let callback = ''
  function visit(node: ts.Node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(tree) === name && node.initializer && ts.isCallExpression(node.initializer)) {
      callback = node.initializer.arguments[0].getText(tree)
    }
    ts.forEachChild(node, visit)
  }
  visit(tree)
  expect(callback).not.toBe('')
  const code = ts.transpile(`const callback = ${callback}`, { target: ts.ScriptTarget.ES2022 })
  return new Function(...Object.keys(bindings), `${code}; return callback`)(...Object.values(bindings))
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

describe('exam calendar latest request wins', () => {
  it.each(['load', 'loadRough', 'loadProducts'])('%s ignores stale successes and failures', async name => {
    for (const fails of [false, true]) {
      const old = deferred<unknown>(), latest = deferred<unknown>()
      let calls = 0
      const fetch = () => (++calls === 1 ? old.promise : latest.promise)
      const state: Record<string, unknown> = {}
      const setter = (key: string) => (value: unknown) => { state[key] = value }
      const bindings = {
        api: { examCalendar: { exactPage: fetch, roughPage: fetch, productOptions: fetch } },
        requests: { current: { exact: 0, rough: 0, products: 0 } },
        range: { start: dayjs('2026-10-01'), end: dayjs('2026-10-31') }, categoryId: undefined, displayStatus: undefined,
        setSchedules: setter('rows'), setRoughRows: setter('rows'), setProducts: setter('rows'),
        setLoading: setter('loading'), setRoughLoading: setter('loading'), setProductLoading: setter('loading'),
        setError: setter('error'), setRoughError: setter('error'), setProductError: setter('error'),
        setRoughTotal: setter('total'), setDetail: setter('detail'), setDayDetail: setter('day'), ApiError: class extends Error {}
      }
      const run = loader(name, bindings)
      const first = run(), second = run()
      const response = (id: number) => name === 'loadProducts' ? [{ id }] : { list: [{ id }], total: 1 }
      latest.resolve(response(2)); await second
      if (fails) old.reject(new Error('stale failure')); else old.resolve(response(1))
      await first
      expect(state.rows).toEqual([{ id: 2 }])
      expect(state.error).toBe('')
      expect(state.loading).toBe(false)
    }
  })

  it('submits independently retained conditions instead of only registered form fields', () => {
    expect(source).toContain('scheduleInput({ ...values, selectedAttrs })')
    expect(source).toContain('setSelectedAttrs({ ...schedule.selectedAttrs })')
    expect(source).toContain('clearedInvalidAttrs')
    expect(source).not.toContain("name={['selectedAttrs'")
    expect(source.match(/const current = latestReload.current/g)).toHaveLength(2)
    expect(source).not.toContain('Promise.all([load(), roughOpen')
  })
})

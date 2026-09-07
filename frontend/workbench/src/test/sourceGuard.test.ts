import { describe, expect, it } from 'vitest'
import {
  expectSourceNotToContainTokens,
  expectSourceToContainTokens,
  sourceContainsTokens,
  sourceHasCall,
  sourceTokens,
} from './sourceGuard'

describe('source guard token matching', () => {
  it('ignores formatting and quote style without losing string values', () => {
    const source = `await http.post(
      "/zsjos/positioning-card/42/student-link",
      null,
      { params: { version } },
    );`

    expect(sourceContainsTokens(
      source,
      "await http.post('/zsjos/positioning-card/42/student-link', null, { params: { version } })",
    )).toBe(true)
    expect(sourceContainsTokens(
      source,
      "await http.post('/zsjos/positioning-card/42/student-link', null, { params: { id } })",
    )).toBe(false)
  })

  it('keeps template expressions and operators significant', () => {
    const source = 'const visible = baseTabs ? merge(baseTabs, projected) : projected'

    expect(sourceContainsTokens(source, 'baseTabs ? merge(baseTabs, projected) : projected')).toBe(true)
    expect(sourceContainsTokens(source, 'baseTabs || merge(baseTabs, projected)')).toBe(false)
  })

  it('matches JSX attributes and embedded expressions as tokens', () => {
    const source = '<div\n  className="lead-form-step"\n  hidden={current !== 0}\n/>'

    expect(sourceContainsTokens(source, "className='lead-form-step' hidden={current !== 0}")).toBe(true)
  })

  it('ignores parser recovery tokens for intentionally partial snippets', () => {
    const source = 'if (enabled) { run() }\nconst options = { sorter: active ? true : undefined }'

    expect(sourceContainsTokens(source, 'if (enabled) {')).toBe(true)
    expect(sourceContainsTokens(source, 'sorter: active ? true : undefined')).toBe(true)
  })

  it('does not produce trivia tokens', () => {
    expect(sourceTokens('value /* note */  ===\r\n  true;')).toEqual([
      'Identifier:value',
      'EqualsEqualsEqualsToken:===',
      'TrueKeyword:true',
    ])
  })

  it('finds calls by their AST property path', () => {
    const source = 'void api\n  .myNotifyMessage(messageId)'

    expect(sourceHasCall(source, 'api.myNotifyMessage')).toBe(true)
    expect(sourceHasCall(source, 'api.myNotifyMessagePage')).toBe(false)
  })

  it('reports missing and prohibited token sequences', () => {
    expect(() => expectSourceToContainTokens('const allowed = true', 'const missing = true')).toThrow(/missing/)
    expect(() => expectSourceNotToContainTokens('const prohibited = true', 'prohibited = true')).toThrow(/prohibited/)
  })
})

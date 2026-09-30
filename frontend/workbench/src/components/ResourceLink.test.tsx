import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import ResourceLink, { LinkedText, remarkLinkParts, resourceTarget } from './ResourceLink'

describe('shared resource links', () => {
  it.each(['javascript:alert(1)', 'data:text/html,a', '//example.com', '/\\example.com', 'https://name:password@example.com', 'not a url'])('rejects unsafe/non-link input %s', value => expect(resourceTarget(value)).toBeUndefined())
  it('renders external links with safe new-tab semantics and preserves surrounding text', () => {
    const html = renderToStaticMarkup(<LinkedText text={'说明 https://example.com/a。\n后文'} />)
    expect(html).toContain('说明 '); expect(html).toContain('。\n后文'); expect(html).toContain('noopener noreferrer'); expect(html).toContain('target="_blank"')
  })
  it('uses router navigation for explicit internal routes', () => {
    const html = renderToStaticMarkup(<MemoryRouter><ResourceLink href="/zsjos/my-students" title="学员" /></MemoryRouter>)
    expect(html).toContain('href="/zsjos/my-students"'); expect(html).not.toContain('target="_blank"')
  })
  it('renders resource title and domain without fetching metadata', () => {
    const html = renderToStaticMarkup(<ResourceLink href="https://example.com/ref" title="参考账号" variant="resource" />)
    expect(html).toContain('参考账号'); expect(html).toContain('example.com'); expect(html).toContain('复制链接')
  })
})

describe('calendar remark links', () => {
  const cases: Array<[string, string[]]> = [
    ['', []],
    ['普通备注  数字123，标点。\n第二行', []],
    ['https://example.com/a?q=1&next=%2Flesson#part', ['https://example.com/a?q=1&next=%2Flesson#part']],
    ['  https://example.com/a  \n', ['https://example.com/a']],
    ['说明 https://example.com/a。\n后文 http://example.org/b，结束', ['https://example.com/a', 'http://example.org/b']],
    ['HTTPS://EXAMPLE.COM/path', ['https://example.com/path']],
    ['（https://example.com/a）【http://example.org/b】', ['https://example.com/a', 'http://example.org/b']],
    ['“https://example.com/a”《https://example.com/b》', ['https://example.com/a', 'https://example.com/b']],
    ['(https://example.com/wiki/a_(b)).', ['https://example.com/wiki/a_(b)']],
    ['https://example.com/a(b(c)))', ['https://example.com/a(b(c))']],
    ['https://example.com/a[x]].', ['https://example.com/a[x]']],
    ['https://example.com/a{b}};', ['https://example.com/a%7Bb%7D']],
    ['https://example.com/a.,;!', ['https://example.com/a']],
    ['https://example.com/?q=a!&next=%2F%3F#part!', ['https://example.com/?q=a!&next=%2F%3F#part!']],
    ['https://example.com/a?', ['https://example.com/a?']],
    ['https://example.com/a#', ['https://example.com/a#']],
    ['https://example.com/中文路径', ['https://example.com/%E4%B8%AD%E6%96%87%E8%B7%AF%E5%BE%84']],
    ['https://example.com/中文说明没有分隔', ['https://example.com/%E4%B8%AD%E6%96%87%E8%AF%B4%E6%98%8E%E6%B2%A1%E6%9C%89%E5%88%86%E9%9A%94']],
    ['https://[::1]/a', ['https://[::1]/a']],
    ['www.example.com example.com /zsjos/my-students', []],
    ['javascript:alert(1) data:text/html,hello mailto:test@example.com', []],
    ['https:// https://?x=1 https:///path https://#part', []],
    ['https://user:password@example.com http://example.com:99999', []],
    ['https://example.com\\bad', []],
    ['<script>alert(1)</script><img src=x onerror=alert(2)>', []],
    ['无效 https://?x=1 然后 https://example.com/ok。', ['https://example.com/ok']]
  ]

  it.each(cases)('preserves original text and extracts safe targets: %s', (text, expected) => {
    const parts = remarkLinkParts(text)
    expect(parts.map(part => part.text).join('')).toBe(text)
    expect(parts.flatMap(part => part.href ? [part.href] : [])).toEqual(expected)
  })

  it('keeps exact URL text, parameters, and surrounding whitespace visible', () => {
    const text = '  HTTPS://EXAMPLE.COM/a?q=1&next=%2F#p  \n'
    const html = renderToStaticMarkup(<LinkedText text={text} mode="remark" />)
    expect(html).toContain('>  <span')
    expect(html).toContain('HTTPS://EXAMPLE.COM/a?q=1&amp;next=%2F#p</span>')
    expect(html).toContain('  \n</span>')
    expect(html).toContain('target="_blank"')
    expect(html).toContain('rel="noopener noreferrer"')
    expect(html).toContain('（新标签页打开）')
  })

  it('escapes markup without executing it or interpreting Markdown labels', () => {
    const html = renderToStaticMarkup(<LinkedText text={'<script>alert(1)</script> [资料](https://example.com/a)'} mode="remark" />)
    expect(html).not.toContain('<script>')
    expect(html).toContain('&lt;script&gt;')
    expect(html).toContain('[资料](')
    expect(html).toContain('>https://example.com/a</span>')
  })

  it('leaves default resource cards and concise labels unchanged', () => {
    const html = renderToStaticMarkup(<LinkedText text="https://example.com/ref?q=1" resource />)
    expect(html).toContain('resource-link-card')
    expect(html).toContain('>example.com/ref</span>')
    expect(html).toContain('href="https://example.com/ref?q=1"')
    expect(html).not.toContain('resource-linked-text-remark')
  })
})

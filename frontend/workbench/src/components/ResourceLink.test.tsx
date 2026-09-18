import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import ResourceLink, { LinkedText, resourceTarget } from './ResourceLink'

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

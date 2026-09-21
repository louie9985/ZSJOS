import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { Form } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import ResourceLinkInput from './ResourceLinkInput'

describe('ResourceLinkInput', () => {
  it('preserves the submitted text while providing a safe preview', () => {
    const html = renderToStaticMarkup(<ResourceLinkInput value=" https://example.com/a?q=1 " id="reference" maxLength={2000} disabled />)
    expect(html).toContain('value=" https://example.com/a?q=1 "')
    expect(html).toContain('href="https://example.com/a?q=1"')
    expect(html).toContain('noopener noreferrer')
    expect(html).toContain('id="reference"')
    expect(html).toContain('maxLength="2000"')
    expect(html).toContain('disabled=""')
  })
  it.each(['', 'javascript:alert(1)', 'https://user:password@example.com', 'plain text'])('does not turn %j into a clickable preview', value => {
    const html = renderToStaticMarkup(<ResourceLinkInput value={value} />)
    expect(html).not.toContain('href=')
  })
  it('uses the existing router link for internal URLs', () => {
    const html = renderToStaticMarkup(<MemoryRouter><ResourceLinkInput value="/zsjos/my-students" /></MemoryRouter>)
    expect(html).toContain('href="/zsjos/my-students"')
    expect(html).not.toContain('target="_blank"')
  })
  it('receives initial value and label association from Form.Item', () => {
    const html = renderToStaticMarkup(<Form initialValues={{ url: 'https://example.com' }}><Form.Item name="url" label="参考链接"><ResourceLinkInput /></Form.Item></Form>)
    expect(html).toContain('value="https://example.com"')
    expect(html).toContain('for="url"')
    expect(html).toContain('id="url"')
  })
  it('treats a supplied undefined value as empty rather than reusing the default', () => {
    const html = renderToStaticMarkup(<ResourceLinkInput value={undefined} defaultValue="https://example.com" />)
    expect(html).not.toContain('https://example.com')
  })
})

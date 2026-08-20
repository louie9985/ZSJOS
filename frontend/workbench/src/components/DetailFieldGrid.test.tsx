import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import DetailFieldGrid from './DetailFieldGrid'

describe('DetailFieldGrid', () => {
  it('renders semantic fields, empty values and wide fields', () => {
    const html = renderToStaticMarkup(<DetailFieldGrid columns={2} items={[
      { key: 'name', label: '姓名', value: <strong>张三</strong> },
      { key: 'mobile', label: '手机号', value: '' },
      { key: 'remark', label: '备注', value: '长文本', span: 2 }
    ]}/>)

    expect(html).toContain('<dl class="detail-field-grid columns-2">')
    expect(html).toContain('<dt>姓名</dt><dd><strong>张三</strong></dd>')
    expect(html).toContain('<dt>手机号</dt><dd>-</dd>')
    expect(html).toContain('class="detail-field span-2"')
  })

  it('supports one and three column modifiers without dropping zero values', () => {
    expect(renderToStaticMarkup(<DetailFieldGrid columns={1} items={[{ key: 'count', label: '数量', value: 0 }]}/>))
      .toContain('class="detail-field-grid columns-1"')
    expect(renderToStaticMarkup(<DetailFieldGrid columns={3} items={[{ key: 'status', label: '状态', value: false }]}/>))
      .toContain('<dd>false</dd>')
  })
})

import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import PositioningMaterialPreview from './PositioningMaterialPreview'
import PositioningCardFields from './PositioningCardFields'
import type { MaterialVersion } from '../services/materialApi'
import type { StudentContactFormField } from '../services/api'

const version = { id: 1, fields: [], values: {}, dictSnapshot: {}, files: [] } as unknown as MaterialVersion
describe('positioning material cover and visible hints', () => {
  it('renders a separate version cover even when no image field exists', () => {
    const html = renderToStaticMarkup(<PositioningMaterialPreview version={{ ...version, coverPreviewUrl: 'https://example.com/cover.png' }} />)
    expect(html).toContain('src="https://example.com/cover.png"')
  })
  it('uses the reserved cover file when the cover projection is absent', () => {
    const files = [{ id: 1, fieldKey: '__cover__', groupIndex: -1, fileId: 2, name: 'cover', contentType: 'image/png', size: 10, previewUrl: 'https://example.com/file.png' }]
    expect(renderToStaticMarkup(<PositioningMaterialPreview version={{ ...version, files }} />)).toContain('src="https://example.com/file.png"')
  })
  it('shows the complete hint text directly and groups material fields under their project', () => {
    const fields: StudentContactFormField[] = [
      { key: 'plan', title: '平台主页搭建', type: 'textarea', enabled: true, systemField: false, required: false, sort: 1, description: '主页搭建、头像选择、背景图设置' },
      { key: 'refs', title: '参考素材', type: 'material_picker', enabled: true, systemField: false, required: false, sort: 2, referenceFor: 'plan' },
    ]
    const html = renderToStaticMarkup(<PositioningCardFields fields={fields} render={field => <input aria-label={field.title} />} />)
    expect(html).toContain('主页搭建、头像选择、背景图设置')
    expect(html).not.toContain('查看填写提示')
    expect(html.match(/<section/g)).toHaveLength(1)
    expect(html).toContain('aria-label="参考素材"')
  })
})

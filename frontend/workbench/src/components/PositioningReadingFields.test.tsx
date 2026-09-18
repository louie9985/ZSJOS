import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import PositioningReadingFields, { positioningReadingRows, positioningReadingBlocks } from './PositioningReadingFields'
import { positioningMaterialCover } from './PositioningMaterialPreview'
import type { MaterialVersion } from '../services/materialApi'
import PositioningSnapshot from './PositioningSnapshot'
import type { PositioningCard, StudentContactFormField } from '../services/api'

const field = (key: string, sort: number, extra: Partial<StudentContactFormField> = {}): StudentContactFormField => ({ key, title: key, type: 'text', enabled: true, required: false, systemField: false, sort, ...extra })
describe('positioning grouped reading preserves template content', () => {
  it('keeps every enabled field exactly once, including orphan references', () => {
    const fields = [field('reference', 2, { referenceFor: 'root' }), field('root', 1), field('orphan', 4, { referenceFor: 'retired' }), field('disabled', 3, { enabled: false })]
    const rows = positioningReadingRows(fields)
    expect(rows.map(row => row.field.key)).toEqual(['root', 'orphan'])
    expect(rows.flatMap(row => [row.field.key, ...row.references.map(ref => ref.key)])).toEqual(['root', 'reference', 'orphan'])
    expect(fields.map(item => item.key)).toEqual(['reference', 'root', 'orphan', 'disabled'])
  })
  it('renders all current fields and original group labels without invented sections', () => {
    const html = renderToStaticMarkup(<PositioningReadingFields fields={[field('first', 1, { group: '服务端组名' }), field('long', 2, { type: 'textarea' })]} render={item => <span>{item.key === 'long' ? '完整长内容'.repeat(60) : '首项'}</span>} />)
    expect(html).toContain('服务端组名'); expect(html).toContain('完整长内容'.repeat(60)); expect(html).toContain('positioning-reading-grid'); expect(html).toContain('positioning-reading-field-name')
  })
  it('retains dictionary snapshot labels, boolean false and zero in both reading and legacy rendering', () => {
    const card = { fieldsSnapshot: [field('multi', 1), field('flag', 2), field('zero', 3)], valuesSnapshot: { multi: ['a', 'b'], flag: false, zero: 0 }, dictSnapshot: { multi: [{ value: 'a', labelSnapshot: '历史甲' }, { value: 'b', labelSnapshot: '历史乙' }] } } as unknown as PositioningCard
    for (const reading of [true, false]) {
      const html = renderToStaticMarkup(<PositioningSnapshot card={card} reading={reading} />)
      expect(html).toContain('历史甲'); expect(html).toContain('历史乙'); expect(html).toContain('否'); expect(html).toContain('>0<')
    }
  })
  it('groups platform and stage fields without inventing S0 references, while preserving values and reference ownership', () => {
    const fields = [
      field('pc_homepage_douyin', 1, { description: '不展示的填写提示' }),
      field('douyin_ref', 2, { type: 'material_picker', materialTypeCode: 'viral_account', referenceFor: 'pc_homepage_douyin' }),
      field('pc_homepage_other', 3), field('pc_delivery_s0', 4), field('pc_delivery_s1', 5),
      field('s1_ref', 6, { type: 'material_picker', materialTypeCode: 'viral_content', referenceFor: 'pc_delivery_s1' }),
      field('ordinary', 7), field('pc_delivery_s2', 8),
    ]
    const blocks = positioningReadingBlocks(positioningReadingRows(fields))
    expect(blocks.map(block => block.kind)).toEqual(['platform', 'stage', undefined, 'stage'])
    expect(blocks[1].rows[0].references).toEqual([])
    expect(blocks[1].rows[1].references[0].key).toBe('s1_ref')
    const rendered: string[] = []
    const html = renderToStaticMarkup(<PositioningReadingFields fields={fields} render={item => { rendered.push(item.key); return `完整值-${item.key}` }} />)
    expect(rendered.sort()).toEqual(fields.map(item => item.key).sort())
    const positions = fields.map(item => html.indexOf(`完整值-${item.key}`))
    expect(positions).toEqual([...positions].sort((a, b) => a - b))
    expect(html).not.toContain('不展示的填写提示')
    expect(html).toContain('positioning-plan-platform'); expect(html).toContain('positioning-plan-stage')
  })
  it('uses the material version cover URL and falls back only to its designated cover file', () => {
    const version = { coverPreviewUrl: 'https://example.com/cover', files: [{ fieldKey: 'other', previewUrl: 'https://example.com/unrelated' }, { fieldKey: '__cover__', previewUrl: 'https://example.com/fallback' }] } as MaterialVersion
    expect(positioningMaterialCover(version)).toBe(version.coverPreviewUrl)
    expect(positioningMaterialCover({ ...version, coverPreviewUrl: undefined })).toBe('https://example.com/fallback')
    expect(positioningMaterialCover({ ...version, coverPreviewUrl: undefined, files: version.files.slice(0, 1) })).toBeUndefined()
  })
})

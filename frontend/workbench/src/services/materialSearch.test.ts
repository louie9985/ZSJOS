import { describe, expect, it } from 'vitest'
import type { Material } from './materialApi'
import { materialSearchEntries, materialSearchHits, materialSearchSnippet } from './materialSearch'

const material = {
  materialNo: 'MAT-1', title: '旧标题', summary: '摘要',
  currentVersion: {
    title: '当前标题', values: { hidden: '不能展示', empty: ' ', topic: '短词', category: ['a', 'b'], missing: 99,
      works: [{ text: '第一项' }, { text: '第二项命中' }] },
    dictSnapshot: { category: [{ value: 'a', label: '历史名称' }, { value: 'b', label: '另一个名称' }] },
    files: [], fields: [
      { key: 'hidden', label: '隐藏字段', type: 'text' },
      { key: 'empty', label: '空值', type: 'text', searchable: true },
      { key: 'topic', label: '主题', type: 'text', searchable: true },
      { key: 'category', label: '分类', type: 'dict-multi', searchable: true },
      { key: 'missing', label: '无快照', type: 'employee', searchable: true },
      { key: 'works', label: '作品', type: 'repeat-group', children: [
        { key: 'text', label: '文案', type: 'textarea', searchable: true }
      ] }
    ]
  }
} as unknown as Material

describe('material searchable projections', () => {
  it('uses saved labels, omits unsearchable/empty fields and never exposes entity ids without snapshots', () => {
    const entries = materialSearchEntries(material)
    expect(entries.map(entry => entry.key)).toEqual(['materialNo', 'title', 'summary', 'topic', 'category', 'works.0.text', 'works.1.text'])
    expect(entries.find(entry => entry.key === 'category')?.terms).toEqual(['历史名称', '另一个名称'])
    expect(entries.find(entry => entry.key === 'title')?.text).toBe('当前标题')
    expect(entries.find(entry => entry.key === 'works.1.text')?.terms).toEqual([])
  })
  it('locates repeated rows independently and matches only visible content', () => {
    expect(materialSearchHits(materialSearchEntries(material), '命中').map(entry => entry.key)).toEqual(['works.1.text'])
    expect(materialSearchHits(materialSearchEntries(material), '不能展示')).toEqual([])
    expect(materialSearchHits(materialSearchEntries(material), '')).toEqual([])
    expect(materialSearchHits(materialSearchEntries(material), 'mat-1')[0].key).toBe('materialNo')
  })
  it('keeps a late match in a long-text snippet', () => {
    const text = '前'.repeat(200) + '命中' + '后'.repeat(200)
    expect(materialSearchSnippet(text, '命中')).toContain('命中')
    expect(materialSearchSnippet(text, '命中')).toHaveLength(102)
    expect(materialSearchSnippet(text, '')).not.toContain('命中')
  })
})

import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import ContentReviewWorkDetail from './ContentReviewWorkDetail'
import { AccountProfiles } from '../pages/ContentReviewBatchPage'
import type { ContentReviewBatch, ContentReviewItem } from '../services/materialApi'

const render = (contentSnapshot: Record<string, unknown>) => renderToStaticMarkup(<ContentReviewWorkDetail batchId={1}
  item={{ id: 1, files: [], contentSnapshot } as unknown as ContentReviewItem} />)

describe('content approval snapshot presentation', () => {
  it('formats planned time and highlights frozen purpose/format without empty legacy fields', () => {
    const html = render({ plannedPublishAt: '2026-09-27T00:00:00', titleSnapshot: '提交标题',
      scriptText: '提交正文', commentHook: '提交钩子', purposeLabelSnapshot: '历史目的', formatLabelSnapshot: '历史形式' })
    for (const value of ['2026-09-27 00:00', '提交标题', '提交正文', '提交钩子', '历史目的', '历史形式', 'ant-tag-blue', 'ant-tag-purple', 'ant-tag-cyan']) expect(html).toContain(value)
    for (const value of ['选题', '成品外链', '补充信息', '2026-09-27T']) expect(html).not.toContain(value)
  })
  it('maps each optional link independently and retains historical supplement values', () => {
    const html = render({ detailUrl: 'https://example.com/detail', leadResourceUrl: 'https://example.com/lead',
      referenceWorkUrl: 'https://example.com/reference', deliverableUrl: 'https://example.com/video', topicSnapshot: '版本选题', topic: '旧选题' })
    for (const value of ['作品详情', '引流资料链接', '参考作品链接', '成品外链', '补充信息', '版本选题', '/detail', '/lead', '/reference', '/video']) expect(html).toContain(value)
    expect(html).not.toContain('旧选题')
    expect(render({ detailUrl: 'javascript:alert(1)' })).not.toContain('href="javascript:')
    expect(render({ detailUrl: '   ' })).toContain('作品详情链接')
    expect((render({}).match(/未填写/g) || []).length).toBe(4)
    expect((html.match(/resource-link-card/g) || []).length).toBe(4)
    expect((html.match(/aria-label="复制链接"/g) || []).length).toBe(4)
  })
  it('renders every account value as a wrapping tag with expanded background and frozen values', () => {
    const batch = { contextSnapshot: { accountSnapshots: [{ id: 1, nickname: '快照账号', platformLabel: '快照平台',
      operatorName: '运营快照', directorName: '编导快照', stageLabelSnapshot: '快照期段', currentStatusLabelSnapshot: '快照状态',
      platformAccountId: 'account-demo', productGoal: '目标', productFormLabel: '产品形式', publishFrequency: '每周三次',
      primaryProblems: [{ labelSnapshot: '历史瓶颈' }] }] } } as unknown as ContentReviewBatch
    const html = renderToStaticMarkup(<AccountProfiles batch={batch} accountLink={() => '快照账号'} />)
    expect(html).toContain('<details open="">')
    expect((html.match(/<dd><span class="ant-tag/g) || []).length).toBe(9)
    for (const value of ['快照期段', '快照状态', '历史瓶颈', '每周三次']) expect(html).toContain(value)
  })
})

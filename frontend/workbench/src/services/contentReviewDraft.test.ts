import { describe, expect, it, vi } from 'vitest'
import { restoreDraftAccounts, restoreDraftReferences, restoreDraftWorks } from './contentReviewDraft'
import { prepareContentReviewWorks, restoreContentReviewFiles } from './contentReviewAttachments'

describe('saved content draft round trip', () => {
  it('retains untouched material versions and attachments when only the title changes', async () => {
    const refs = [{ materialId: 12, materialVersionId: 34, title: '原参考素材', materialNo: 'M12', materialTypeName: '拆解', coverPreviewUrl: 'https://example.com/cover' }]
    const upload = vi.fn()
    const files = restoreContentReviewFiles([{ fieldKey: 'deliverable', infraFileId: 56, originalName: '稿件.pdf', contentType: 'application/pdf', fileSize: 10 }], 'deliverable')
    const batch = { status: 'DRAFT', items: [{ contentId: 1, contentVersionId: 2, files: [], contentSnapshot: { title: '原标题', materialRefs: refs } }] } as unknown as import('./materialApi').ContentReviewBatch
    const restored = restoreDraftWorks(batch)[0]
    expect(restored.sourceContentId).toBe(1)
    expect(restored.sourceVersionId).toBe(2)
    expect(restored.referenceMaterials).toEqual(refs)
    const [request] = await prepareContentReviewWorks([{ title: '修改标题', referenceMaterials: restored.referenceMaterials, attachmentItems: files }], vi.fn(), upload)
    expect(request).toEqual({ title: '修改标题', referenceMaterials: refs, deliverableSnapshotJson: '[56]' })
    expect(upload).not.toHaveBeenCalled()
  })
  it('keeps references with their work when works are reordered', async () => {
    const first = { referenceMaterials: restoreDraftReferences([{ materialId: 1, materialVersionId: 10 }]) }
    const second = { referenceMaterials: restoreDraftReferences([{ materialId: 2, materialVersionId: 20 }]) }
    const request = await prepareContentReviewWorks([second, first], vi.fn())
    expect(request.map(work => work.referenceMaterials)).toEqual([second.referenceMaterials, first.referenceMaterials])
    expect(restoreDraftReferences(undefined)).toEqual([])
  })
})

it('restores server account snapshots into the original form without current-account lookups', () => {
  const source = { id: 9, nickname: '原账号', accountNo: 'ACC9', platformValue: 'dy', platformLabel: '抖音',
    sStage: 'stage1', sStageLabel: '历史期段', currentStatusValue: 'active', currentStatusLabel: '历史状态',
    productGoal: '历史隐藏字段', primaryProblems: [{ value: 'p1', labelSnapshot: '历史瓶颈' }] }
  const batch = { accountId: 9, accountIds: [9], contextSnapshot: { accountSnapshots: [source] } } as unknown as import('./materialApi').ContentReviewBatch
  const restored = restoreDraftAccounts(batch)
  expect(restored.accountIds).toEqual([9])
  expect(restored.accountSnapshots['9']).toMatchObject({ ...source, stageLabelSnapshot: '历史期段', currentStatusLabelSnapshot: '历史状态' })
  expect(restored.accounts[0].nickname).toBe('原账号')
  expect(source).not.toHaveProperty('stageLabelSnapshot')
})

it('keeps comments, source identities and persisted links through revision serialization', async () => {
  const batch = { items: [{ contentId: 7, contentVersionId: 8, files: [], contentSnapshot: {
    title: '原标题', commentHook: '原评论区钩子', topic: '原选题', deliverableUrl: 'https://example.com/video',
    referenceWorkUrl: 'https://example.com/reference', purposeLabelSnapshot: '原目的' } }] } as unknown as import('./materialApi').ContentReviewBatch
  const work = restoreDraftWorks(batch)[0]
  const [request] = await prepareContentReviewWorks([{ ...work, title: '改后标题' }], vi.fn())
  expect(request).toMatchObject({ sourceContentId: 7, sourceVersionId: 8, title: '改后标题',
    commentHook: '原评论区钩子', topic: '原选题', deliverableUrl: 'https://example.com/video',
    referenceWorkUrl: 'https://example.com/reference', purposeLabelSnapshot: '原目的' })
})

it('round trips the version topic, all optional links and label snapshots without reuploading', async () => {
  const contentSnapshot = { titleSnapshot: '版本标题', topicSnapshot: '版本选题', topic: '旧选题',
    detailUrl: 'https://example.com/detail', leadResourceUrl: 'https://example.com/lead',
    referenceWorkUrl: 'https://example.com/reference', deliverableUrl: 'https://example.com/video',
    purposeValue: 'goal', purposeLabelSnapshot: '旧目的标签', formatValue: 'format', formatLabelSnapshot: '旧形式标签',
    scriptText: '正文', commentHook: '钩子', plannedPublishAt: '2026-09-27T10:30:00' }
  const batch = { items: [{ contentId: 7, contentVersionId: 8, contentSnapshot, files: [
    { id: 1, fieldKey: 'cover', infraFileId: 11, originalName: 'cover.png', contentType: 'image/png', fileSize: 10 },
    { id: 2, fieldKey: 'deliverable', infraFileId: 12, originalName: 'review.pdf', contentType: 'application/pdf', fileSize: 20 },
  ] }] } as unknown as import('./materialApi').ContentReviewBatch
  const upload = vi.fn()
  const [request] = await prepareContentReviewWorks(restoreDraftWorks(batch), vi.fn(), upload)
  expect(request).toMatchObject({ title: '版本标题', topic: '版本选题', detailUrl: contentSnapshot.detailUrl,
    leadResourceUrl: contentSnapshot.leadResourceUrl, referenceWorkUrl: contentSnapshot.referenceWorkUrl,
    deliverableUrl: contentSnapshot.deliverableUrl, purposeLabelSnapshot: '旧目的标签', formatLabelSnapshot: '旧形式标签',
    coverFileId: 11, deliverableSnapshotJson: '[12]' })
  expect(upload).not.toHaveBeenCalled()
})

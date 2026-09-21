import { describe, expect, it, vi } from 'vitest'
import { contentReviewFileError, prepareContentReviewWorks, restoreContentReviewFiles, type ContentReviewAttachment } from './contentReviewAttachments'
vi.mock('./api', () => ({ api: { mediaContent: { uploadVersionFile: vi.fn() } } }))
const file = new File(['test'], 'review.pdf', { type: 'application/pdf' })
const pending = (uid: string): ContentReviewAttachment => ({ uid, name: file.name, file, status: 'pending' })
const result = { fileId: 7, name: file.name, contentType: file.type, size: 4 }
describe('content review attachments', () => {
  it('allows review documents but keeps covers image-only', () => {
    expect(contentReviewFileError(file)).toBeUndefined()
    expect(contentReviewFileError(file, true)).toBe('封面仅支持图片')
    expect(contentReviewFileError(new File(['html'], 'x.html', { type: 'text/html' }))).toBeTruthy()
  })
  it('retains successful uploads and retries failures without sending partial work', async () => {
    const work: Record<string, unknown> = { title: '作品', attachmentItems: [pending('one'), pending('two')] }
    const update = (_index: number, field: string, items: ContentReviewAttachment[]) => { work[field] = items }
    const upload = vi.fn().mockResolvedValueOnce(result).mockRejectedValueOnce(new Error('传输失败'))
    await expect(prepareContentReviewWorks([work], update, upload)).rejects.toThrow('作品 1')
    expect((work.attachmentItems as ContentReviewAttachment[]).map(item => item.status)).toEqual(['done', 'error'])
    const retry = vi.fn().mockResolvedValue({ ...result, fileId: 8 })
    const requests = await prepareContentReviewWorks([work], update, retry)
    expect(retry).toHaveBeenCalledTimes(1)
    expect(requests).toEqual([{ title: '作品', deliverableSnapshotJson: '[7,8]' }])
  })
  it('distinguishes omitted attachments from explicit removal', async () => {
    expect(await prepareContentReviewWorks([{}, { attachmentItems: [] }], vi.fn())).toEqual([{}, { deliverableSnapshotJson: '[]' }])
  })
  it('restores frozen file references without uploading them again', async () => {
    const items = restoreContentReviewFiles([{ fieldKey: 'deliverable', infraFileId: 7, originalName: file.name, contentType: file.type, fileSize: 4 }], 'deliverable')
    const upload = vi.fn()
    expect(await prepareContentReviewWorks([{ attachmentItems: items }], vi.fn(), upload)).toEqual([{ deliverableSnapshotJson: '[7]' }])
    expect(upload).not.toHaveBeenCalled()
  })
})

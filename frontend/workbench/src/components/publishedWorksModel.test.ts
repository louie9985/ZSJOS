import { describe, expect, it } from 'vitest'
import { publishedWorkCover, publishedWorksForAccount, safeWorkUrl } from './publishedWorksModel'
import type { MediaContentVersion, MediaStudentDetail } from '../services/api'

describe('published account works', () => {
  it('isolates accounts, excludes drafts, orders by publication without mutating source', () => {
    const contents = [
      { id: 1, accountId: 10, status: 'published', publishedAt: 100 },
      { id: 2, accountId: 11, status: 'published', publishedAt: 300 },
      { id: 3, accountId: 10, status: 'topic', publishedAt: 400 },
      { id: 4, accountId: 10, status: 'published', publishedAt: 200 },
    ] as MediaStudentDetail['contents']
    expect(publishedWorksForAccount(contents, 10).map(item => item.id)).toEqual([4, 1])
    expect(contents.map(item => item.id)).toEqual([1, 2, 3, 4])
    expect(publishedWorksForAccount(contents, 99)).toEqual([])
  })
  it('uses the published version preview instead of a newer draft', () => {
    const versions = [
      { versionNo: 2, files: [{ fieldKey: 'cover', contentType: 'image/png', previewUrl: 'https://example.com/draft.png' }] },
      { versionNo: 1, files: [{ fieldKey: 'cover', contentType: 'image/png', previewUrl: 'https://example.com/published.png' }] },
    ] as MediaContentVersion[]
    expect(publishedWorkCover(versions, 1)).toBe('https://example.com/published.png')
    expect(publishedWorkCover(versions, 3)).toBeUndefined()
  })
  it('rejects executable or malformed links', () => {
    expect(safeWorkUrl('javascript:alert(1)')).toBeUndefined()
    expect(safeWorkUrl('/missing-host')).toBeUndefined()
    expect(safeWorkUrl('https://example.com/work')).toBe('https://example.com/work')
  })
})

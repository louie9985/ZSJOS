import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, http, type PositioningCardDraftRequest } from './api'

const response = <T,>(data: T) => ({ data: { code: 0, data } })

afterEach(() => vi.restoreAllMocks())

describe('positioning card API contract', () => {
  it('reads frozen attachments through the established route with snapshot scope', async () => {
    const file = { id: 2415, name: '访谈.png', type: 'image/png', size: 128, url: '/signed-file' }
    const get = vi.spyOn(http, 'get').mockResolvedValue(response(file))
    await expect(api.positioningCard.snapshotAttachment(20, 2415, 31)).resolves.toEqual(file)
    expect(get).toHaveBeenLastCalledWith('/zsjos/positioning-card/20/snapshot/attachments/2415', {
      params: { submissionId: 31 },
    })
    await api.positioningCard.snapshotAttachment(20, 2415)
    expect(get).toHaveBeenLastCalledWith('/zsjos/positioning-card/20/snapshot/attachments/2415', {
      params: { submissionId: undefined },
    })
  })

  it('propagates attachment rejection without retrying a less restrictive endpoint', async () => {
    const denied = new Error('无权访问此定位卡附件')
    const get = vi.spyOn(http, 'get').mockRejectedValue(denied)
    await expect(api.positioningCard.snapshotAttachment(20, 2415, 31)).rejects.toBe(denied)
    expect(get).toHaveBeenCalledTimes(1)
  })

  it('creates and updates server-backed drafts', async () => {
    const createRequest: PositioningCardDraftRequest = { studentPersonId: 29, serviceRelationId: 11, values: { platform: 'douyin' } }
    const updateRequest: PositioningCardDraftRequest & { version: number } = { ...createRequest, version: 3 }
    const post = vi.spyOn(http, 'post').mockResolvedValue(response({ id: 41, version: 1 }))
    const put = vi.spyOn(http, 'put').mockResolvedValue(response({ id: 41, version: 4 }))

    await expect(api.positioningCard.createDraft(createRequest)).resolves.toEqual({ id: 41, version: 1 })
    await expect(api.positioningCard.updateDraft(41, updateRequest)).resolves.toEqual({ id: 41, version: 4 })
    expect(post).toHaveBeenCalledWith('/zsjos/positioning-card/draft', createRequest)
    expect(put).toHaveBeenCalledWith('/zsjos/positioning-card/draft/41', updateRequest)
  })

  it('loads import sources and imports the selected submission', async () => {
    const params = { studentPersonId: 29, serviceRelationId: 11 }
    const importRequest = { sourceSubmissionId: 31, ...params, targetDraftId: 41, version: 3 }
    const sources = [{ submissionId: 31 }]
    const imported = { id: 41, version: 4, skippedFieldKeys: ['legacy-field'] }
    const get = vi.spyOn(http, 'get').mockResolvedValue(response(sources))
    const post = vi.spyOn(http, 'post').mockResolvedValue(response(imported))

    await expect(api.positioningCard.importSources(params)).resolves.toEqual(sources)
    await expect(api.positioningCard.importSubmission(importRequest)).resolves.toEqual(imported)
    expect(get).toHaveBeenCalledWith('/zsjos/positioning-card/import-sources', { params })
    expect(post).toHaveBeenCalledWith('/zsjos/positioning-card/import', importRequest)
  })

  it('generates the student link and starts a versioned revision', async () => {
    const link = { sharePath: '/student/positioning/confirm?token=opaque', expiresAt: 1_800_000_000_000 }
    const revision = { id: 41, version: 5 }
    const post = vi.spyOn(http, 'post')
      .mockResolvedValueOnce(response(link))
      .mockResolvedValueOnce(response(revision))

    await expect(api.positioningCard.generateStudentLink(41, 4)).resolves.toEqual(link)
    await expect(api.positioningCard.startRevision(41, 4)).resolves.toEqual(revision)
    expect(post).toHaveBeenNthCalledWith(1, '/zsjos/positioning-card/41/student-link', null, { params: { version: 4 } })
    expect(post).toHaveBeenNthCalledWith(2, '/zsjos/positioning-card/41/start-revision', null, { params: { version: 4 } })
  })
})

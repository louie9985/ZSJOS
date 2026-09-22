import { describe, expect, it } from 'vitest'
import { contentReviewCategories, reviewAccounts } from './contentReviewQuery'
import type { ContentReviewBatch } from './materialApi'

const batchFixture = (fields: Partial<ContentReviewBatch>): ContentReviewBatch => ({ id: 1, batchNo: 'test', accountId: 1, operatorUserId: 1, status: 'DRAFT', currentStage: '', availableActions: [], version: 1, relationSnapshot: {}, contextSnapshot: {}, items: [], ...fields })

describe('content review query presentation', () => {
  it('groups approval and returned states without changing the server lifecycle', () => {
    expect(contentReviewCategories.find(category => category.key === 'PENDING')?.statuses).toEqual(['DIRECTOR_REVIEW', 'FINAL_REVIEW'])
    expect(contentReviewCategories.find(category => category.key === 'NEED_MODIFY')?.statuses).toEqual(['NEED_MODIFY', 'REJECTED'])
    expect(contentReviewCategories.find(category => category.key === 'COMPLETED')?.label).toBe('待发布')
  })
  it('uses server account summaries and never substitutes IDs for names', () => {
    const accounts = [{ accountId: 17, operatorName: '历史姓名', operatorNameResolved: false }]
    expect(reviewAccounts(batchFixture({ accounts }))).toBe(accounts)
    expect(reviewAccounts(batchFixture({ contextSnapshot: { account: { id: 17 } } }))[0].accountName).toBeUndefined()
  })
  it('retains multi-account frozen names on compatibility responses', () => {
    const batch = batchFixture({ contextSnapshot: { accountSnapshots: [{ id: 1, nickname: '账号甲', operatorName: '运营甲' }, { id: 2, nickname: '账号乙', directorName: '编导乙', directorNameResolved: true }] } })
    expect(reviewAccounts(batch).map(account => account.accountName)).toEqual(['账号甲', '账号乙'])
    expect(reviewAccounts(batch)[1].directorNameResolved).toBe(true)
  })
})

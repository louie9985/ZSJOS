import type { ContentReviewAccount, ContentReviewBatch } from './materialApi'

// Workflow categories group server lifecycle states, not administrator-maintained business choices.
export const contentReviewCategories = [
  { key: 'ALL', label: '全部', statuses: [] },
  { key: 'DRAFT', label: '草稿', statuses: ['DRAFT'] },
  { key: 'PENDING', label: '待审批', statuses: ['DIRECTOR_REVIEW', 'FINAL_REVIEW'] },
  { key: 'NEED_MODIFY', label: '待修改', statuses: ['NEED_MODIFY', 'REJECTED'] },
  { key: 'COMPLETED', label: '待发布', statuses: ['COMPLETED'] },
  { key: 'PUBLISHED', label: '已发布', statuses: ['PUBLISHED'] },
  { key: 'CANCELLED', label: '已取消', statuses: ['CANCELLED'] }
]

export function reviewAccounts(batch: ContentReviewBatch): ContentReviewAccount[] {
  if (batch.accounts?.length) return batch.accounts
  const snapshots = Array.isArray(batch.contextSnapshot.accountSnapshots)
    ? batch.contextSnapshot.accountSnapshots : batch.contextSnapshot.account ? [batch.contextSnapshot.account] : []
  return snapshots.filter((value): value is Record<string, unknown> => Boolean(value) && typeof value === 'object')
    .map(snapshot => ({ accountId: typeof snapshot.id === 'number' ? snapshot.id : undefined,
      accountName: typeof snapshot.nickname === 'string' ? snapshot.nickname : undefined,
      platformLabel: typeof snapshot.platformLabel === 'string' ? snapshot.platformLabel : undefined,
      operatorName: typeof snapshot.operatorName === 'string' ? snapshot.operatorName : undefined,
      directorName: typeof snapshot.directorName === 'string' ? snapshot.directorName : undefined,
      operatorNameResolved: snapshot.operatorNameResolved === true,
      directorNameResolved: snapshot.directorNameResolved === true }))
}

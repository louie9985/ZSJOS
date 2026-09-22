import dayjs from 'dayjs'
import type { ContentReviewBatch } from './materialApi'
import { restoreContentReviewFiles } from './contentReviewAttachments'
import type { ContentApprovalReferenceMaterial } from '../components/ContentApprovalDraft'

/** Restore the persisted selection into the save payload as well as the preview. */
export function restoreDraftReferences(value: unknown): ContentApprovalReferenceMaterial[] {
  if (!Array.isArray(value)) return []
  return value.map(item => {
    const ref = item as ContentApprovalReferenceMaterial
    return { materialId: Number(ref.materialId), materialVersionId: ref.materialVersionId,
      materialNo: ref.materialNo, title: ref.title, materialTypeName: ref.materialTypeName,
      coverPreviewUrl: ref.coverPreviewUrl }
  })
}

export function restoreDraftWorks(batch: ContentReviewBatch) {
  return batch.items.map(item => ({
    sourceContentId: item.contentId,
    sourceVersionId: item.contentVersionId,
    title: item.contentSnapshot.titleSnapshot || item.contentSnapshot.title,
    scriptText: item.contentSnapshot.scriptText,
    topic: item.contentSnapshot.topicSnapshot || item.contentSnapshot.topic,
    deliverableUrl: item.contentSnapshot.deliverableUrl,
    plannedPublishAt: item.contentSnapshot.plannedPublishAt ? dayjs(String(item.contentSnapshot.plannedPublishAt)) : undefined,
    purposeValue: item.contentSnapshot.purposeValue,
    purposeLabelSnapshot: item.contentSnapshot.purposeLabelSnapshot,
    formatValue: item.contentSnapshot.formatValue,
    formatLabelSnapshot: item.contentSnapshot.formatLabelSnapshot,
    detailUrl: item.contentSnapshot.detailUrl,
    leadResourceUrl: item.contentSnapshot.leadResourceUrl,
    commentHook: item.contentSnapshot.commentHook,
    referenceContentVersionId: item.contentSnapshot.referenceContentVersionId,
    referenceWorkUrl: item.contentSnapshot.referenceWorkUrl,
    referenceMaterials: restoreDraftReferences(item.contentSnapshot.materialRefs),
    coverItems: restoreContentReviewFiles(item.files, 'cover'),
    attachmentItems: restoreContentReviewFiles(item.files, 'deliverable'),
    coverFileId: item.files?.find(file => file.fieldKey === 'cover')?.infraFileId,
  }))
}

/** Normalize historical server snapshot field names without looking up today's account profile. */
export function restoreDraftAccounts(batch: ContentReviewBatch) {
  const snapshots = (Array.isArray(batch.contextSnapshot.accountSnapshots)
    ? batch.contextSnapshot.accountSnapshots : []) as Array<Record<string, unknown>>
  const accountIds = batch.accountIds?.length ? batch.accountIds : [batch.accountId]
  const rows = accountIds.map(id => {
    const source = snapshots.find(row => Number(row.id) === id) || {}
    return { ...source, id,
      accountNo: String(source.accountNo || ''), nickname: String(source.nickname || ''),
      platformLabel: String(source.platformLabel || ''),
      stageLabelSnapshot: String(source.stageLabelSnapshot ?? source.sStageLabel ?? ''),
      currentStatusLabelSnapshot: String(source.currentStatusLabelSnapshot ?? source.currentStatusLabel ?? ''),
      primaryProblems: Array.isArray(source.primaryProblems) ? source.primaryProblems as Array<{ value: string; labelSnapshot: string }> : [],
    }
  })
  return { accountIds, accounts: rows,
    accountSnapshots: Object.fromEntries(rows.map(row => [String(row.id), row])) }
}

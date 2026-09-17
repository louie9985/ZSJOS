import type { PositioningCard } from './api'

type DraftSummary = { id: number; accountId?: number | null }

/** A student-level draft has a null account; its service relation is still authoritative. */
export async function loadPositioningDraft(
  drafts: DraftSummary[], accountId: number | undefined, serviceRelationId: number,
  load: (id: number) => Promise<PositioningCard>, explicitId?: number,
) {
  const candidates = explicitId ? [{ id: explicitId }] : drafts.filter(row => (row.accountId ?? null) === (accountId ?? null))
  const cards = await Promise.all(candidates.map(row => load(row.id)))
  const match = cards.find(card => (card.accountId ?? null) === (accountId ?? null)
    && card.serviceRelationId === serviceRelationId && card.status === 'co_creating')
  if (explicitId && !match) throw new Error('草稿与当前账号或课程服务不一致，请刷新后重试')
  return match
}

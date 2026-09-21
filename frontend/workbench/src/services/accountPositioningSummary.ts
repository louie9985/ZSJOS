import type { PositioningCard } from './api'
import { formatPositioningSnapshotValue } from '../components/ProductionTicketPositioningCard'

// Approved account-sheet projection; field values remain owned by the applied submission.
// This is not a second editable template or a source of business enumeration choices.
const fields = [
  ['pc_join_goal', '学员加入目标'],
  ['pc_learning_stage', '当前学习/资格阶段'],
  ['pc_primary_track', '主赛道'],
  ['pc_secondary_track', '辅助赛道'],
  ['pc_cooperation', '学员配合等级'],
  ['pc_shoot_time', '连续可拍摄时间'],
  ['pc_appearance', '出镜意愿'],
  ['pc_expression', '表达能力等级'],
  ['pc_assets', '专业优势和案例资产'],
  ['pc_trust', '信任证据'],
  ['pc_risk', '执行主要风险'],
] as const

/** Field types whose frozen snapshot is a configured dictionary rather than free text. */
const DICTIONARY_TYPES = new Set(['dict', 'select', 'multi_select', 'radio', 'checkbox_group'])

const labelOf = (value: unknown): string | undefined => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined
  const label = (value as { labelSnapshot?: unknown }).labelSnapshot
  return label == null || label === '' ? undefined : String(label)
}

/**
 * Frozen dictionary labels for one summary row, one entry per selection.
 * `dictSnapshot` holds an array for multi-select fields and a single object for single-select
 * fields (see DirectorFormTemplateService), so both shapes are accepted here.
 * The field type is only a hint: the snapshot itself proves the value came from a configured
 * dictionary, so a retyped field keeps its chips. Free-text rows return an empty list.
 */
export function positioningSummaryTags(card: PositioningCard | undefined, key: string): string[] {
  if (!card) return []
  const snapshot = card.dictSnapshot?.[key]
  if (snapshot == null) return []
  const field = (card.fieldsSnapshot || []).find(item => item.key === key)
  const labelled = (Array.isArray(snapshot) ? snapshot : [snapshot]).map(labelOf)
  if (!DICTIONARY_TYPES.has(field?.type || '') && !labelled.some(Boolean)) return []
  const labels = labelled.filter((item): item is string => Boolean(item))
  if (!labels.length) return []
  // Legacy submissions froze every selection as one combined label; split only in that case.
  return labels.length === 1 ? labels[0].split('、').map(item => item.trim()).filter(Boolean) : labels
}

export function accountPositioningSummary(card?: PositioningCard) {
  return fields.map(([key, label]) => ({ key, label,
    value: card ? formatPositioningSnapshotValue(card.valuesSnapshot?.[key], card.dictSnapshot?.[key]) : '尚未应用定位卡',
    tags: positioningSummaryTags(card, key),
  }))
}

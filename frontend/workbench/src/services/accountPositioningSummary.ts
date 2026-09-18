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

export function accountPositioningSummary(card?: PositioningCard) {
  return fields.map(([key, label]) => ({ key, label,
    value: card ? formatPositioningSnapshotValue(card.valuesSnapshot?.[key], card.dictSnapshot?.[key]) : '尚未应用定位卡',
  }))
}

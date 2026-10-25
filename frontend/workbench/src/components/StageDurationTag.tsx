import { Tag, Tooltip } from 'antd'

export type StageDuration = {
  sStageEnteredAt?: unknown; sStageDurationAsOf?: unknown; sStageDurationDays?: unknown; sStageTimingSource?: unknown
}

const time = (value: number) => new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
}).format(value)

export function stageDurationDescription(value: StageDuration, preview = false) {
  const { sStageDurationDays: days, sStageEnteredAt: start, sStageDurationAsOf: end } = value
  if (typeof days !== 'number' || !Number.isSafeInteger(days) || days < 0
    || typeof start !== 'number' || !Number.isFinite(start) || !Number.isFinite(new Date(start).getTime())
    || typeof end !== 'number' || !Number.isFinite(end) || !Number.isFinite(new Date(end).getTime()) || start > end
    || !['RECORDED', 'HISTORY_RECONSTRUCTED'].includes(String(value.sStageTimingSource))) return undefined
  return `进入期段：${time(start)}；${preview ? '截至预览时，正式提交时重新计算' : '截至本轮提交时'}：${time(end)}（北京时间）${value.sStageTimingSource === 'HISTORY_RECONSTRUCTED' ? '；根据诊断历史恢复' : ''}`
}

export default function StageDurationTag({ value, preview = false }: { value: StageDuration; preview?: boolean }) {
  const description = stageDurationDescription(value, preview)
  return description ? <Tooltip title={description}><Tag color="blue" aria-label={description}>已维持 {String(value.sStageDurationDays)} 天</Tag></Tooltip>
    : <Tag>阶段时长未记录</Tag>
}

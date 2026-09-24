import type { Dayjs } from 'dayjs'
import { appendQuickNote } from './leadFollowUp'

export type FollowUpValues = { salesStage?: string; method: string; result: string; leadCategory?: string; remark: string; nextFollowUpAt?: Dayjs | null }
export type FollowUpOption = { value: string; label: string; disabled?: boolean }
export const FOLLOW_UP_VISIBLE_OPTIONS = 6
export const FOLLOW_UP_REMARK_LIMIT = 2000

export function followUpOptionsWithSnapshot(options: FollowUpOption[], value?: string, snapshot?: string): FollowUpOption[] {
  if (!value) return options
  const label = snapshot || '未记录'
  if (!options.some(option => option.value === value)) return [{ value, label, disabled: true }, ...options]
  return options.map(option => option.value === value ? { ...option, label } : option)
}

export function followUpChoiceView(options: FollowUpOption[], value: string | undefined, expanded: boolean) {
  return {
    visible: expanded ? options : options.slice(0, FOLLOW_UP_VISIBLE_OPTIONS),
    hiddenSelection: !expanded && options.findIndex(option => option.value === value) >= FOLLOW_UP_VISIBLE_OPTIONS
      ? options.find(option => option.value === value)?.label : undefined,
  }
}

export function appendFollowUpNote(current: string, note: string) {
  const next = appendQuickNote(current, note)
  return next.length <= FOLLOW_UP_REMARK_LIMIT ? next : undefined
}

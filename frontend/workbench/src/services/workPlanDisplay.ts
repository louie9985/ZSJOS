// WorkPlanConstants and WorkPlanTemplateService own these fixed lifecycle values.
const periods: Record<string, string> = { day: '日', week: '周', month: '月', quarter: '季度', year: '年', custom: '自定义' }
const statuses: Record<string, string> = { draft: '草稿', published: '已发布', disabled: '已停用' }

export function workPlanPeriodLabel(value?: string) {
  return !value ? '—' : Object.hasOwn(periods, value) ? periods[value] : '未知周期'
}
export function workPlanTemplateStatusLabel(value?: string) {
  return !value ? '—' : Object.hasOwn(statuses, value) ? statuses[value] : '未知状态'
}

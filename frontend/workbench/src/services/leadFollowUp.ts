export const appendQuickNote = (current: string, note: string) => current.trim() ? `${current.trim()} ${note}` : note

export const addFollowUpDays = (now: Date, days: number) => {
  const result = new Date(now)
  result.setDate(result.getDate() + days)
  return result
}

export const shouldBlockLeadSwitch = (dirty: boolean) => dirty

export type LeadDetailTab = 'overview' | 'follow-ups'

export const defaultLeadDetailTab = (openFollowUp: boolean): LeadDetailTab =>
  openFollowUp ? 'follow-ups' : 'overview'

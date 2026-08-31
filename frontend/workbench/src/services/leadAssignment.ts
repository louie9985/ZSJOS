import type { PendingLead } from './api'

export const ASSIGNMENT_REFRESH_RETRY_DELAYS_MS = [0, 300, 900] as const

const ASSIGNMENT_OPEN_EVENT_TYPES = new Set(['assigned', 'reassigned', 'transferred'])

export const shouldFocusAssignmentEvent = (eventType: unknown) =>
  typeof eventType === 'string' && ASSIGNMENT_OPEN_EVENT_TYPES.has(eventType)

export const hasPendingLead = (items: PendingLead[], leadId: number) =>
  items.some(item => item.id === leadId)

export const remainingSecondsAt = (lead: PendingLead, elapsedSeconds: number) =>
  lead.remainingSeconds == null ? undefined : Math.max(0, lead.remainingSeconds - elapsedSeconds)

export const isPendingLeadExpired = (lead: PendingLead, elapsedSeconds: number) =>
  lead.remainingSeconds != null && remainingSecondsAt(lead, elapsedSeconds) === 0

export function sortPendingLeads(items: PendingLead[], elapsedSeconds = 0) {
  return items
    .filter(item => !isPendingLeadExpired(item, elapsedSeconds))
    .slice()
    .sort((left, right) => {
      const leftAuto = left.remainingSeconds != null
      const rightAuto = right.remainingSeconds != null
      if (leftAuto !== rightAuto) return leftAuto ? -1 : 1
      if (leftAuto && rightAuto) {
        const remaining = remainingSecondsAt(left, elapsedSeconds)! - remainingSecondsAt(right, elapsedSeconds)!
        if (remaining !== 0) return remaining
      }
      return left.submittedAt - right.submittedAt
    })
}

export const formatCountdown = (seconds?: number) => {
  if (seconds == null) return ''
  const minutes = Math.floor(seconds / 60)
  const remainder = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
}

export const shouldShowAssignmentModal = (hasCurrent: boolean, businessOverlayCount: number) =>
  hasCurrent && businessOverlayCount === 0

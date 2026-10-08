// Retained pages must re-fetch server projections when another page adds a follow-up.
export const LEAD_FOLLOW_UP_CHANGED = 'zsjos:lead-follow-up-changed'

export const notifyLeadFollowUpChanged = (leadId: number) =>
  window.dispatchEvent(new CustomEvent<number>(LEAD_FOLLOW_UP_CHANGED, { detail: leadId }))

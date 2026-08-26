export const WORKBENCH_COMPONENT = {
  LEAD_APPEAL: 'leadAppeal',
  SUBORDINATE_SALES: 'subordinateSales',
  SUBORDINATE_PARTNER: 'subordinatePartner',
  MEDIA_CALENDAR: 'mediaCalendar'
} as const

export type WorkbenchComponent = typeof WORKBENCH_COMPONENT[keyof typeof WORKBENCH_COMPONENT]

const COMPONENT_REGISTRY: Record<string, WorkbenchComponent> = {
  'zsjos/leadAppeal/index': WORKBENCH_COMPONENT.LEAD_APPEAL,
  'zsjos/subordinateSales/index': WORKBENCH_COMPONENT.SUBORDINATE_SALES,
  'zsjos/subordinatePartner/index': WORKBENCH_COMPONENT.SUBORDINATE_PARTNER,
  'zsjos/mediaCalendar/index': WORKBENCH_COMPONENT.MEDIA_CALENDAR
}

export function resolveWorkbenchComponent(component?: string) {
  return component ? COMPONENT_REGISTRY[component.trim()] : undefined
}

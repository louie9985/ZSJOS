export const WORKBENCH_COMPONENT = {
  LEAD_APPEAL: 'leadAppeal',
  SUBORDINATE_SALES: 'subordinateSales',
  SUBORDINATE_PARTNER: 'subordinatePartner',
  MEDIA_CALENDAR: 'mediaCalendar',
  PERSONAL_CALENDAR: 'personalCalendar',
  EXAM_CALENDAR: 'examCalendar',
  COURSE_CALENDAR: 'courseCalendar'
  ,CLASS_MANAGEMENT: 'classManagement'
  ,MY_CLASSES: 'myClasses'
} as const

export type WorkbenchComponent = typeof WORKBENCH_COMPONENT[keyof typeof WORKBENCH_COMPONENT]

const COMPONENT_REGISTRY: Record<string, WorkbenchComponent> = {
  'zsjos/leadAppeal/index': WORKBENCH_COMPONENT.LEAD_APPEAL,
  'zsjos/subordinateSales/index': WORKBENCH_COMPONENT.SUBORDINATE_SALES,
  'zsjos/subordinatePartner/index': WORKBENCH_COMPONENT.SUBORDINATE_PARTNER,
  'zsjos/mediaCalendar/index': WORKBENCH_COMPONENT.MEDIA_CALENDAR,
  'zsjos/personalCalendar/index': WORKBENCH_COMPONENT.PERSONAL_CALENDAR,
  'zsjos/examCalendar/index': WORKBENCH_COMPONENT.EXAM_CALENDAR,
  'zsjos/courseCalendar/index': WORKBENCH_COMPONENT.COURSE_CALENDAR
  ,'zsjos/class-management': WORKBENCH_COMPONENT.CLASS_MANAGEMENT
  ,'zsjos/my-classes': WORKBENCH_COMPONENT.MY_CLASSES
}

export function resolveWorkbenchComponent(component?: string) {
  return component ? COMPONENT_REGISTRY[component.trim()] : undefined
}

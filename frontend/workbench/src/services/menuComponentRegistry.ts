export const WORKBENCH_COMPONENT = {
  STUDENT_ENTRY: 'studentEntry',
  LEAD_FOLLOW_UP_CALENDAR: 'leadFollowUpCalendar',
  DELIVERY_PERFORMANCE: 'deliveryPerformance',
  DELIVERY_PERFORMANCE_TARGET: 'deliveryPerformanceTarget',
  SALES_PERFORMANCE: 'salesPerformance',
  SALES_PERFORMANCE_TARGET: 'salesPerformanceTarget',
  MEDIA_LEAD_ANALYSIS: 'mediaLeadAnalysis',
  MEDIA_LEAD_TARGET: 'mediaLeadTarget',
  LEAD_APPEAL: 'leadAppeal',
  SUBORDINATE_SALES: 'subordinateSales',
  EDUCATION_TEAM: 'educationTeam',
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
  'zsjos/studentEntry/index': WORKBENCH_COMPONENT.STUDENT_ENTRY,
  'zsjos/leadFollowUpCalendar/index': WORKBENCH_COMPONENT.LEAD_FOLLOW_UP_CALENDAR,
  'zsjos/deliveryPerformance/index': WORKBENCH_COMPONENT.DELIVERY_PERFORMANCE,
  'zsjos/deliveryPerformanceTarget/index': WORKBENCH_COMPONENT.DELIVERY_PERFORMANCE_TARGET,
  'zsjos/salesPerformance/index': WORKBENCH_COMPONENT.SALES_PERFORMANCE,
  'zsjos/salesPerformanceTarget/index': WORKBENCH_COMPONENT.SALES_PERFORMANCE_TARGET,
  'zsjos/mediaLeadAnalysis/index': WORKBENCH_COMPONENT.MEDIA_LEAD_ANALYSIS,
  'zsjos/mediaLeadTarget/index': WORKBENCH_COMPONENT.MEDIA_LEAD_TARGET,
  'zsjos/leadAppeal/index': WORKBENCH_COMPONENT.LEAD_APPEAL,
  'zsjos/subordinateSales/index': WORKBENCH_COMPONENT.SUBORDINATE_SALES,
  'zsjos/educationTeam/index': WORKBENCH_COMPONENT.EDUCATION_TEAM,
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

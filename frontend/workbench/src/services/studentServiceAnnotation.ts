import { http, unwrap } from './api'

export const STUDENT_ANNOTATION_READ = 'zsjos:student-service-annotation:read'
export const STUDENT_ANNOTATION_UPDATE = 'zsjos:student-service-annotation:update'
export const STUDENT_ANNOTATION_CONFLICT = 1900094001
export type AnnotationChoice = 'true' | 'false' | null
export interface StudentAnnotationValues {
  proxyStudent: AnnotationChoice
  agentName: string | null
  serviceNeeded: AnnotationChoice
}
export interface StudentServiceAnnotation extends StudentAnnotationValues {
  serviceRelationId: number
  version: number
  proxyStudentLabel: string | null
  serviceNeededLabel: string | null
  canEdit: boolean
}
export interface AnnotationOption { value: string; label: string }
const path = (relationId: number) => `/zsjos/student/service/${relationId}/annotation`
export const studentServiceAnnotationApi = {
  get: async (id: number) => unwrap<StudentServiceAnnotation>(await http.get(path(id))),
  options: async (id: number) => unwrap<AnnotationOption[]>(await http.get(`${path(id)}/options`)),
  save: async (id: number, values: StudentAnnotationValues, version: number) =>
    unwrap<StudentServiceAnnotation>(await http.put(path(id), { ...values, version })),
}
export function annotationValues(row: StudentAnnotationValues): StudentAnnotationValues {
  return { proxyStudent: row.proxyStudent ?? null, agentName: row.proxyStudent === 'true' ? (row.agentName ?? '').trim() : null, serviceNeeded: row.serviceNeeded ?? null }
}
export function annotationChanged(a: StudentAnnotationValues, b: StudentAnnotationValues) {
  return JSON.stringify(annotationValues(a)) !== JSON.stringify(annotationValues(b))
}
export function annotationOptions(options: AnnotationOption[], value: AnnotationChoice, snapshot: string | null) {
  if (!value) return options
  // The current selection retains its recorded label even after dictionary maintenance.
  const current = { value, label: snapshot || '历史标签未记录', disabled: !options.some(item => item.value === value) }
  return options.some(item => item.value === value)
    ? options.map(item => item.value === value ? current : item) : [...options, current]
}

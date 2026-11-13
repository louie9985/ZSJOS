import type { CollectionField, CollectionImage } from '../api/studentInfo'
import { validCollectionIdentity } from './studentInfoValidation.ts'

/** Existing tasks may retain WeChat in their immutable snapshot; public collection no longer requests it. */
export const effectiveExamFields = (fields: CollectionField[]) => fields.filter(field => field.enabled && field.key !== 'wechat')

export const EXAM_ATTENDANCE_KEY = 'exam_attendance', EXAM_ABSENCE_REASON_KEY = 'absence_reason'
const IDENTITY_KEYS = ['name', 'mobile', 'id_card']
/** Mirrors the server rule: identity always shows; "是" reveals the task fields and "否" only the absence reason. Older tasks have no attendance field. */
export function visibleExamFields(fields: CollectionField[], answers: Record<string, string | string[]>) {
  const effective = effectiveExamFields(fields)
  if (!effective.some(field => field.key === EXAM_ATTENDANCE_KEY)) return effective
  const attendance = answers[EXAM_ATTENDANCE_KEY]
  return effective.filter(field => IDENTITY_KEYS.includes(field.key) || field.key === EXAM_ATTENDANCE_KEY
    || (attendance === '否' ? field.key === EXAM_ABSENCE_REASON_KEY : attendance === '是' && field.key !== EXAM_ABSENCE_REASON_KEY))
}

/** Validate effective task fields without introducing unselected material inputs. */
export function validateExamCollection(fields: CollectionField[], answers: Record<string, string | string[]>, images: CollectionImage[]) {
  const errors: Record<string, string> = {}
  const values: Record<string, string | string[] | number[]> = {}
  for (const field of visibleExamFields(fields, answers)) {
    if (field.type === 'images') {
      const selected = images.filter(i => i.fieldKey === field.key)
      const identity = field.key === 'identity_images'
      if (field.required && selected.length < (identity ? 2 : 1)) errors[field.key] = `请上传${field.label}`
      if (selected.length > (identity ? 2 : field.maxImages || 9)) errors[field.key] = `${field.label}数量超过限制`
      if (identity && (selected.some(i => !['front', 'back'].includes(i.side)) || new Set(selected.map(i => i.side)).size !== selected.length)) errors[field.key] = '请分别上传身份证正面和反面'
      values[field.key] = selected.map(i => i.id)
      continue
    }
    if (field.type === 'radio' || field.type === 'checkbox') {
      const raw = answers[field.key]
      const choices = Array.isArray(raw) ? raw : raw ? [raw] : []
      if (field.required && !choices.length) errors[field.key] = `请选择${field.label}`
      else if ((field.type === 'radio' && choices.length > 1) || new Set(choices).size !== choices.length || choices.some(choice => !field.options?.includes(choice))) errors[field.key] = `请重新选择${field.label}`
      values[field.key] = field.type === 'checkbox' ? choices : choices[0] || ''
      continue
    }
    const raw = answers[field.key]
    let value = typeof raw === 'string' ? raw.trim() : ''
    if (field.key === 'id_card') value = value.toUpperCase()
    if (field.required && !value) errors[field.key] = `请填写${field.label}`
    else if (field.key === 'id_card' && value && !validCollectionIdentity(value)) errors[field.key] = '请输入有效的18位身份证号码'
    else if (field.key === 'mobile' && value && !/^1[3-9][0-9]{9}$/.test(value)) errors[field.key] = '请输入有效的11位手机号码'
    else if (value.length > (field.type === 'textarea' ? 2000 : 200)) errors[field.key] = `${field.label}内容过长`
    values[field.key] = value
  }
  return { errors, values }
}

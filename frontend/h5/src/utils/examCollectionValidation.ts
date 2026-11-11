import type { CollectionField, CollectionImage } from '../api/studentInfo'
import { validCollectionIdentity } from './studentInfoValidation.ts'

/** Existing tasks may retain WeChat in their immutable snapshot; public collection no longer requests it. */
export const effectiveExamFields = (fields: CollectionField[]) => fields.filter(field => field.enabled && field.key !== 'wechat')

/** Validate effective task fields without introducing unselected material inputs. */
export function validateExamCollection(fields: CollectionField[], answers: Record<string, string>, images: CollectionImage[]) {
  const errors: Record<string, string> = {}
  const values: Record<string, string | number[]> = {}
  for (const field of effectiveExamFields(fields)) {
    if (field.type === 'images') {
      const selected = images.filter(i => i.fieldKey === field.key)
      const identity = field.key === 'identity_images'
      if (field.required && selected.length < (identity ? 2 : 1)) errors[field.key] = `请上传${field.label}`
      if (selected.length > (identity ? 2 : field.maxImages || 9)) errors[field.key] = `${field.label}数量超过限制`
      if (identity && (selected.some(i => !['front', 'back'].includes(i.side)) || new Set(selected.map(i => i.side)).size !== selected.length)) errors[field.key] = '请分别上传身份证正面和反面'
      values[field.key] = selected.map(i => i.id)
      continue
    }
    let value = (answers[field.key] || '').trim()
    if (field.key === 'id_card') value = value.toUpperCase()
    if (field.required && !value) errors[field.key] = `请填写${field.label}`
    else if (field.key === 'id_card' && value && !validCollectionIdentity(value)) errors[field.key] = '请输入有效的18位身份证号码'
    else if (field.key === 'mobile' && value && !/^1[3-9][0-9]{9}$/.test(value)) errors[field.key] = '请输入有效的11位手机号码'
    else if (value.length > (field.type === 'textarea' ? 2000 : 200)) errors[field.key] = `${field.label}内容过长`
    values[field.key] = value
  }
  return { errors, values }
}

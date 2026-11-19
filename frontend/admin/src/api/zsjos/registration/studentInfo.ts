import request from '@/config/axios'
import type { Field } from '../studentInfoFormConfig'
export interface CollectionLink {
  formId: number
  status: string
  url?: string
  registrationVersion: number
  sentAt?: string
  canRegenerate: boolean
  canRevoke: boolean
}
export interface CollectionImage {
  id: number
  source?: 'registration' | 'exam' | 'service'
  fieldKey: string
  side: string
  contentType: string
  name: string
}
export interface CollectionDetail {
  agreementVersion?: number
  agreementBody?: string
  status: string
  fields: Field[]
  values: Record<string, string>
  images: CollectionImage[]
  canReadSensitive: boolean
  canExport: boolean
}
export interface CollectionCommand {
  version: number
  formId?: number
  sent?: boolean
  idempotencyKey: string
}
const base = (id: number) => '/zsjos/registration/' + id + '/student-info-form'
export const link = (id: number): Promise<CollectionLink | null> =>
  request.get({ url: base(id) + '/link' })
export const command = (
  id: number,
  operation: string,
  data: CollectionCommand
): Promise<CollectionLink> =>
  request.post({ url: base(id) + (operation ? '/' + operation : ''), data })
export const detail = (id: number, sensitive = false): Promise<CollectionDetail> =>
  request.get({ url: base(id) + (sensitive ? '/sensitive' : '/detail') })
export const image = async (id: number, item: CollectionImage) => {
  const blob = await request.download<Blob>({ url: base(id) + '/images/' + item.id })
  if (blob.type.includes('json')) throw new Error('图片读取失败或没有权限')
  return URL.createObjectURL(new Blob([blob], { type: item.contentType }))
}
export const download = (id: number): Promise<Blob> =>
  request.download({ url: base(id) + '/export' })

export interface StudentInfoTask {
  id: number
  version: number
  formId?: number
  status: string
  canProcess: boolean
  candidates: Array<{ formId: number; revision: number; missing: string[] }>
}
export interface StudentInfoGenerationConfig {
  configVersionId: number
  fields: Field[]
  selectableMaterialKeys: string[]
  defaultSelectedKeys?: string[]
}
const tasks = '/zsjos/student-info-task'
export const taskByRegistration = (caseId: number) =>
  request.get<StudentInfoTask>({ url: `${tasks}/by-registration/${caseId}` })
export const taskLink = (id: number) =>
  request.get<CollectionLink | null>({ url: `${tasks}/${id}/link` })
export const taskGenerationConfig = (id: number) =>
  request.get<StudentInfoGenerationConfig>({ url: `${tasks}/${id}/generation-config` })
export const taskCommand = (
  task: StudentInfoTask,
  operation: 'generate' | 'reuse' | 'sent' | 'regenerate' | 'revoke',
  options: { configVersionId?: number; materialKeys?: string[]; sourceFormId?: number; sourceRevision?: number } = {}
) => request.post<StudentInfoTask>({
  url: `${tasks}/${task.id}/${operation}`,
  data: { version: task.version, idempotencyKey: crypto.randomUUID(), ...options }
})
export const taskArchive = (id: number, formId: number, revision: number, sensitive: boolean) =>
  request.get<CollectionDetail>({ url: `${tasks}/${id}/archive`, params: { formId, revision, sensitive } })

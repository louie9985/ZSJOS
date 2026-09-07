import request from '@/config/axios'

export interface Field {
  key: string
  label: string
  type: 'text' | 'textarea' | 'dict' | 'area'
  enabled: boolean
  required: boolean
  sort: number
  note?: string
  dictType?: string
  sensitive: boolean
}
export interface Version {
  id: number
  versionNo: number
  revision: number
  status: string
  fields: Field[]
}
export interface Config {
  draft?: Version
  published?: Version
  presets: Field[]
}
export interface Save {
  id?: number
  revision: number
  fields: Field[]
}
const url = '/zsjos/student-info-form/config'
export const getConfig = (): Promise<Config> => request.get({ url })
export const saveDraft = (data: Save): Promise<Version> =>
  request.post({ url: `${url}/draft`, data })
export const publish = (data: { id: number; revision: number }): Promise<boolean> =>
  request.post({ url: `${url}/publish`, data })

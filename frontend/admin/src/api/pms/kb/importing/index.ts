import request from '@/config/axios'
import type { PmsKnowledgeLibraryVO } from '../library'
export interface ImportPolicy {
  maxFiles: number
  maxFileSize: number
  maxTotalSize: number
  maxDepth: number
  concurrency: number
  fileTypes: string[]
}
export interface ImportEntry {
  relativePath: string
  fileSize: number
  lastModified: number
  digest?: string
}
export interface ImportItem {
  id: number
  relativePath: string
  fileSize: number
  status: string
  reason?: string
  documentId?: number
}
export interface ImportTask {
  id: number
  libraryId: number
  folderId: number
  rootName: string
  mode: 'NEW' | 'EXISTING'
  status: string
  total: number
  success: number
  skipped: number
  failed: number
  items: ImportItem[]
}
export interface ImportRequest {
  mode: 'NEW' | 'EXISTING'
  requestKey: string
  rootName: string
  libraryId?: number
  folderId?: number
  library?: PmsKnowledgeLibraryVO
  files: ImportEntry[]
}
const base = '/pms/kb/import'
export const getPolicy = () =>
  request.get<ImportPolicy>({ preserveBusinessError: true, url: `${base}/policy` })
export const prepare = (data: ImportRequest) =>
  request.post<ImportItem[]>({ preserveBusinessError: true, url: `${base}/prepare`, data })
export const create = (data: ImportRequest) =>
  request.post<ImportTask>({ preserveBusinessError: true, url: `${base}/create`, data })
export const getTask = (id: number) =>
  request.get<ImportTask>({ preserveBusinessError: true, url: `${base}/get`, params: { id } })
export const page = (pageNo: number) =>
  request.get<{ list: ImportTask[]; total: number }>({
    preserveBusinessError: true,
    url: `${base}/page`,
    params: { pageNo, pageSize: 10 }
  })
export const resume = (taskId: number, files: ImportEntry[]) =>
  request.post<ImportTask>({
    preserveBusinessError: true,
    url: `${base}/resume`,
    data: { taskId, files }
  })
export const finish = (id: number) =>
  request.post<ImportTask>({ preserveBusinessError: true, url: `${base}/finish`, params: { id } })
export const upload = (
  taskId: number,
  itemId: number,
  file: File,
  onProgress: (percent: number) => void
) => {
  const data = new FormData()
  data.append('taskId', String(taskId))
  data.append('itemId', String(itemId))
  data.append('file', file)
  return request.post<ImportTask>({
    preserveBusinessError: true,
    url: `${base}/upload`,
    data,
    headersType: 'multipart/form-data',
    timeout: 600000,
    onUploadProgress: (event: { loaded: number; total?: number }) =>
      onProgress(Math.round((event.loaded * 100) / (event.total || file.size || 1)))
  })
}

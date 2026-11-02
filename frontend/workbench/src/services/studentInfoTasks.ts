import type { ProductSpec } from './productSpecs'
import { http, unwrap, type PageResult } from './api'
import type { StudentInfoLink, StudentInfoDetail, StudentInfoImage } from './studentInfo'
export interface CollectionCandidate { formId: number; productName: string; revision: number; missing: string[] }
export interface CollectionTask {
  id: number; orderId: number; orderItemId: number; personId: number; formId?: number; registrationCaseId?: number; version: number
  orderNo: string; studentName: string; productName: string; orderStatus: string; status: string
  specs?: ProductSpec[] | null; archiveStatus: string; canProcess: boolean; candidates: CollectionCandidate[]
}
export interface CollectionCommand { version: number; idempotencyKey: string; sourceFormId?: number; sourceRevision?: number }
const base = '/zsjos/student-info-task'
export const collectionTasksApi = {
  byRegistration: async (caseId:number) => unwrap<CollectionTask>(await http.get(`${base}/by-registration/${caseId}`)),
  detail: async (id: number) => unwrap<CollectionTask>(await http.get(`${base}/${id}`)),
  page: async (pageNo: number, keyword: string, personId?: number, status?: string, orderStatus?: string) => unwrap<PageResult<CollectionTask>>(await http.get(`${base}/page`, { params: { pageNo, pageSize: 20, keyword, personId, status, orderStatus } })),
  command: async (id: number, operation: 'generate' | 'reuse' | 'sent' | 'regenerate' | 'revoke', command: CollectionCommand) => unwrap<CollectionTask>(await http.post(`${base}/${id}/${operation}`, command)),
  link: async (id: number) => unwrap<StudentInfoLink | null>(await http.get(`${base}/${id}/link`)),
}
export const currentArchiveReader = (formId:number,revision:number,onReady?:(ready:boolean)=>void) => ({
  detail: async (id:number,sensitive=false) => {
    onReady?.(false)
    const result=unwrap<StudentInfoDetail>(await http.get(`${base}/${id}/archive`,{params:{formId,revision,sensitive}}))
    if(sensitive||!result.canReadSensitive)onReady?.(true)
    return result
  },
  image: async (id:number,image:StudentInfoImage) => {
    const response=await http.get(`${base}/${id}/archive/images/${image.id}`,{params:{formId,revision},responseType:'blob'})
    if((response.data as Blob).type.includes('json')) {
      const error=JSON.parse(await (response.data as Blob).text()) as {msg?:string}
      throw new Error(error.msg||'档案材料读取失败，请重新打开已有档案')
    }
    return URL.createObjectURL(new Blob([response.data],{type:image.contentType}))
  },
  export: async (_id:number):Promise<void>=>{throw new Error('此入口暂不提供导出')},
})
export const collectionTaskReader = {
  detail: async (id: number, sensitive = false) => unwrap<StudentInfoDetail>(await http.get(`${base}/${id}/information`, { params: { sensitive } })),
  image: async (id: number, image: StudentInfoImage) => {
    const response = await http.get(`${base}/${id}/images/${image.id}`, { responseType: 'blob' })
    if ((response.data as Blob).type.includes('json')) throw new Error('材料读取失败或没有权限')
    return URL.createObjectURL(new Blob([response.data], { type: image.contentType }))
  },
  export: async (_id: number): Promise<void> => { throw new Error('此入口暂不提供导出') },
}

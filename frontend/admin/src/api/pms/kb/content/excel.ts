import request from '@/config/axios'

export interface KnowledgeExcelSource {
  documentId: number
  fileId?: number
  recordId?: number
  shareToken?: string
}
export interface KnowledgeExcelPreview {
  sheets: { index: number; name: string }[]
  sheetIndex: number
  rows: string[][]
  columnCount: number
  truncated: boolean
  notices: string[]
  merges?: { row: number; column: number; rowSpan: number; colSpan: number }[]
}
export function isKnowledgeExcel(name?: string, type?: string) {
  const value = type?.toLowerCase().replace(/^\./, '')
  return ['xls', 'xlsx', 'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'].includes(value || '') ||
    /\.(xls|xlsx)$/i.test(name || '')
}
export const getKnowledgeExcelPreview = (source: KnowledgeExcelSource, sheetIndex = -1) => {
  const publicRead = source.shareToken !== undefined
  return request.get<KnowledgeExcelPreview>({
    url: publicRead ? '/pms/kb/document-share/excel-preview' :
      source.recordId !== undefined ? '/pms/kb/document/excel/recycle-preview' : '/pms/kb/document/excel/preview',
    params: publicRead ? { token: source.shareToken, fileId: source.fileId, sheetIndex } :
      { documentId: source.documentId, fileId: source.fileId, recordId: source.recordId, sheetIndex },
    ...(publicRead ? { headers: { isToken: false } } : {}),
    preserveBusinessError: true
  })
}

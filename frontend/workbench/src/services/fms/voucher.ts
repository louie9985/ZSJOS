import { http, unwrap } from '../api'
import { requestBlob } from '../download'

export type PageResult<T> = { list: T[]; total: number }

// ========== Voucher Types ==========

export interface FmsVoucherAuxiliaryItem {
  type?: number
  typeId: number
  itemId: number
  name?: string
}

export interface FmsVoucherEntry {
  id?: number
  digest: string
  subjectId: number
  quantity?: number
  unitPrice?: number
  debitAmount?: number
  creditAmount?: number
  auxiliaries: FmsVoucherAuxiliaryItem[]
  subjectCode?: string
  subjectName?: string
  sort?: number
  assistCombinationId?: number
}

export interface FmsVoucher {
  id: number
  accountSetId: number
  voucherWordId: number
  voucherNumber: number
  voucherTime: number
  attachmentUrls: string[]
  entries: FmsVoucherEntry[]
  voucherWordName?: string
  attachmentCount: number
  debitAmount: number
  creditAmount: number
  total: number
  status: number
  closingGenerated: boolean
  creatorUserId?: number
  creatorUserName?: string
  reviewerUserId?: number
  reviewerUserName?: string
  createTime: string
}

export interface FmsVoucherSaveReq {
  id?: number
  accountSetId: number
  voucherWordId: number
  voucherNumber: number
  voucherTime: number
  attachmentCount: number
  entries: FmsVoucherEntry[]
}

export interface FmsVoucherSubjectBalance {
  subjectId: number
  balanceDirection?: string
  balance: number
}

export interface FmsVoucherPageReq {
  accountSetId: number
  pageNo?: number
  pageSize?: number
  ids?: number[]
  voucherTime?: string[]
  voucherWordId?: number
  voucherNumber?: number
  digest?: string
  subjectId?: number
  minAmount?: number
  maxAmount?: number
  creatorUserId?: number
  status?: number
}

export interface FmsVoucherTidyReq {
  accountSetId: number
  month: string
  voucherWordId?: number
  startNumber: number
  type: number
}

export interface FmsVoucherMoveReq {
  accountSetId: number
  month: string
  voucherWordId?: number
  sourceNumber?: number
  targetNumber?: number
}

export interface FmsVoucherImportResp {
  totalRowCount: number
  successRowCount: number
  failureRowCount: number
  totalVoucherCount: number
  successVoucherCount: number
  failureVoucherCount: number
  errorFileUrl?: string
}

export interface FmsVoucherStatisticsRow {
  subjectId: number
  subjectCode: string
  subjectName: string
  level: number
  debitAmount: number
  creditAmount: number
}

// ========== Voucher API ==========

export const fmsVoucher = {
  page: async (params: FmsVoucherPageReq): Promise<PageResult<FmsVoucher>> =>
    unwrap<PageResult<FmsVoucher>>(await http.get('/fms/voucher/page', { params })),
  printList: async (params: FmsVoucherPageReq): Promise<FmsVoucher[]> =>
    unwrap<FmsVoucher[]>(await http.get('/fms/voucher/print-list', { params })),
  exportExcel: (params: FmsVoucherPageReq) =>
    requestBlob('/fms/voucher/export-excel', params as unknown as Record<string, unknown>),
  get: async (accountSetId: number, id: number): Promise<FmsVoucher> =>
    unwrap<FmsVoucher>(await http.get('/fms/voucher/get', { params: { accountSetId, id } })),
  subjectBalanceList: async (accountSetId: number, month: string): Promise<FmsVoucherSubjectBalance[]> =>
    unwrap<FmsVoucherSubjectBalance[]>(await http.get('/fms/voucher/subject-balance-list', { params: { accountSetId, month } })),
  auxiliaryBalance: async (accountSetId: number, month: string, subjectId: number, auxiliaryItemIds: number[]): Promise<FmsVoucherSubjectBalance> =>
    unwrap<FmsVoucherSubjectBalance>(await http.get('/fms/voucher/auxiliary-balance', { params: { accountSetId, month, subjectId, auxiliaryItemIds: auxiliaryItemIds.join(',') } })),
  nextNumber: async (accountSetId: number, voucherWordId: number, voucherTime: string): Promise<number> =>
    unwrap<number>(await http.get('/fms/voucher/next-number', { params: { accountSetId, voucherWordId, voucherTime } })),
  create: async (data: FmsVoucherSaveReq): Promise<number> =>
    unwrap<number>(await http.post('/fms/voucher/create', data)),
  update: async (data: FmsVoucherSaveReq): Promise<void> => {
    await unwrap<void>(await http.put('/fms/voucher/update', data))
  },
  updateAttachments: async (data: { id: number; accountSetId: number; attachmentUrls: string[] }): Promise<void> => {
    await unwrap<void>(await http.put('/fms/voucher/update-attachments', data))
  },
  uploadAttachment: async (file: File): Promise<string> => {
    const data = new FormData()
    data.append('file', file)
    return unwrap<string>(await http.post('/infra/file/upload', data, { headers: { 'Content-Type': 'multipart/form-data' } }))
  },
  deleteList: async (accountSetId: number, ids: number[]): Promise<void> => {
    await unwrap<void>(await http.delete('/fms/voucher/delete-list', { params: { accountSetId, ids: ids.join(',') } }))
  },
  updateReviewStatus: async (accountSetId: number, ids: number[], status: number): Promise<void> => {
    await unwrap<void>(await http.put('/fms/voucher/update-review-status', { accountSetId, ids, status }))
  },
  tidy: async (data: FmsVoucherTidyReq): Promise<void> => {
    await unwrap<void>(await http.put('/fms/voucher/tidy', data))
  },
  move: async (data: FmsVoucherMoveReq): Promise<void> => {
    await unwrap<void>(await http.put('/fms/voucher/move', data))
  },
  getVoucherImportTemplate: (accountSetId: number) =>
    requestBlob('/fms/voucher/get-import-template', { accountSetId }),
  importVoucher: async (accountSetId: number, file: File): Promise<FmsVoucherImportResp> => {
    const data = new FormData()
    data.append('accountSetId', String(accountSetId))
    data.append('file', file)
    return unwrap<FmsVoucherImportResp>(await http.post('/fms/voucher/import', data, { headers: { 'Content-Type': 'multipart/form-data' } }))
  },
  statistics: {
    list: async (params: { accountSetId: number; startMonth: string; endMonth: string }): Promise<FmsVoucherStatisticsRow[]> =>
      unwrap<FmsVoucherStatisticsRow[]>(await http.get('/fms/voucher/statistics/list', { params })),
    exportExcel: (params: { accountSetId: number; startMonth: string; endMonth: string }) =>
      requestBlob('/fms/voucher/statistics/export-excel', params as unknown as Record<string, unknown>)
  }
} as const

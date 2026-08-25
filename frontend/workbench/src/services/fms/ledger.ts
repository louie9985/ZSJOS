import { http, unwrap } from '../api'
import { requestBlob } from '../download'
import type {
  FmsLedgerListParams,
  FmsLedgerAuxiliaryListParams,
  FmsLedgerGeneral,
  FmsLedgerDetail,
  FmsSubjectBalance,
  FmsLedgerAuxiliaryBalance,
  FmsMultiColumn,
  FmsSubjectVO
} from './types'

/** FMS 账簿 API */
export const fmsLedger = {
  general: {
    list: async (params: FmsLedgerListParams) =>
      unwrap<FmsLedgerGeneral[]>(await http.get('/fms/ledger/general/list', { params })),
    exportExcel: (params: FmsLedgerListParams) =>
      requestBlob('/fms/ledger/general/export-excel', params as unknown as Record<string, unknown>)
  },
  detail: {
    /** 查询指定期间有发生额的科目精简列表（明细账/多栏账侧栏） */
    subjectList: async (params: { accountSetId: number; startMonth: string; endMonth: string }): Promise<FmsSubjectVO[]> =>
      unwrap<FmsSubjectVO[]>(await http.get('/fms/ledger/detail/subject-list', { params })),
    list: async (params: FmsLedgerListParams) =>
      unwrap<FmsLedgerDetail[]>(await http.get('/fms/ledger/detail/list', { params })),
    exportExcel: (params: FmsLedgerListParams) =>
      requestBlob('/fms/ledger/detail/export-excel', params as unknown as Record<string, unknown>)
  },
  subjectBalance: {
    list: async (params: FmsLedgerListParams) =>
      unwrap<FmsSubjectBalance[]>(await http.get('/fms/ledger/subject-balance/list', { params })),
    exportExcel: (params: FmsLedgerListParams) =>
      requestBlob('/fms/ledger/subject-balance/export-excel', params as unknown as Record<string, unknown>)
  },
  multiColumn: {
    list: async (params: FmsLedgerListParams) =>
      unwrap<FmsMultiColumn>(await http.get('/fms/ledger/multi-column/list', { params })),
    exportExcel: (params: FmsLedgerListParams) =>
      requestBlob('/fms/ledger/multi-column/export-excel', params as unknown as Record<string, unknown>)
  },
  auxiliaryDetail: {
    list: async (params: FmsLedgerAuxiliaryListParams) =>
      unwrap<FmsLedgerDetail[]>(await http.get('/fms/ledger/auxiliary-detail/list', { params })),
    exportExcel: (params: FmsLedgerAuxiliaryListParams) =>
      requestBlob('/fms/ledger/auxiliary-detail/export-excel', params as unknown as Record<string, unknown>)
  },
  auxiliaryBalance: {
    list: async (params: FmsLedgerAuxiliaryListParams) =>
      unwrap<FmsLedgerAuxiliaryBalance[]>(await http.get('/fms/ledger/auxiliary-balance/list', { params })),
    exportExcel: (params: FmsLedgerAuxiliaryListParams) =>
      requestBlob('/fms/ledger/auxiliary-balance/export-excel', params as unknown as Record<string, unknown>)
  },
  quantityDetail: {
    list: async (params: FmsLedgerListParams) =>
      unwrap<FmsLedgerDetail[]>(await http.get('/fms/ledger/quantity-detail/list', { params })),
    exportExcel: (params: FmsLedgerListParams) =>
      requestBlob('/fms/ledger/quantity-detail/export-excel', params as unknown as Record<string, unknown>)
  },
  quantityGeneral: {
    list: async (params: FmsLedgerListParams) =>
      unwrap<FmsSubjectBalance[]>(await http.get('/fms/ledger/quantity-general/list', { params })),
    exportExcel: (params: FmsLedgerListParams) =>
      requestBlob('/fms/ledger/quantity-general/export-excel', params as unknown as Record<string, unknown>)
  }
} as const

import { http, unwrap } from '../api'
import { requestBlob } from '../download'
import type {
  FmsAccountSetVO,
  FmsAccountSetFullVO,
  FmsAccountSetCreateReqVO,
  FmsAccountSetInitializeReqVO,
  FmsAccountUserVO,
  FmsAccountUserUpdateReqVO,
  FmsCurrencyVO,
  FmsDigestVO,
  FmsVoucherWordVO,
  FmsFinanceIndicatorVO,
  FmsAuxiliaryTypeVO,
  FmsFinanceParameter,
  FmsSubjectVO,
  FmsSubjectUsage,
  FmsAuxiliaryItemOptionVO,
  FmsVoucherTemplateVO,
  FmsVoucherTemplateCategoryVO,
  FmsInitialBalance,
  FmsInitialBalanceUpdate,
  FmsTrialBalance
} from './types'

// ========== Account Set ==========

async function accountSetList(): Promise<FmsAccountSetVO[]> {
  return unwrap<FmsAccountSetVO[]>(await http.get('/fms/config/account-set/list'))
}

async function accountSetGet(id: number): Promise<FmsAccountSetFullVO> {
  return unwrap<FmsAccountSetFullVO>(await http.get('/fms/config/account-set/get', { params: { id } }))
}

async function accountSetCreate(data: FmsAccountSetCreateReqVO): Promise<number> {
  return unwrap<number>(await http.post('/fms/config/account-set/create', data))
}

async function accountSetUpdate(data: Partial<FmsAccountSetFullVO> & { id: number }): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/account-set/update', data))
}

async function accountSetInitialize(data: FmsAccountSetInitializeReqVO): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/account-set/initialize', data))
}

// ========== Account User ==========

async function accountUserList(accountSetId: number): Promise<FmsAccountUserVO[]> {
  return unwrap<FmsAccountUserVO[]>(await http.get('/fms/config/account-user/list', { params: { accountSetId } }))
}

async function accountUserUpdate(data: FmsAccountUserUpdateReqVO): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/account-user/update', data))
}

async function accountUserUpdateDefaultStatus(accountSetId: number): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/account-user/update-default-status', undefined, { params: { accountSetId } }))
}

// ========== Currency ==========

async function currencyList(accountSetId: number): Promise<FmsCurrencyVO[]> {
  return unwrap<FmsCurrencyVO[]>(await http.get('/fms/config/currency/list', { params: { accountSetId } }))
}

async function currencyCreate(data: FmsCurrencyVO): Promise<number> {
  return unwrap<number>(await http.post('/fms/config/currency/create', data))
}

async function currencyUpdate(data: FmsCurrencyVO): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/currency/update', data))
}

async function currencyDelete(accountSetId: number, id: number): Promise<void> {
  await unwrap<void>(await http.delete('/fms/config/currency/delete', { params: { accountSetId, id } }))
}

// ========== Digest ==========

async function digestList(accountSetId: number): Promise<FmsDigestVO[]> {
  return unwrap<FmsDigestVO[]>(await http.get('/fms/config/digest/list', { params: { accountSetId } }))
}

async function digestCreate(data: FmsDigestVO): Promise<number> {
  return unwrap<number>(await http.post('/fms/config/digest/create', data))
}

async function digestUpdate(data: FmsDigestVO): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/digest/update', data))
}

async function digestDelete(accountSetId: number, id: number): Promise<void> {
  await unwrap<void>(await http.delete('/fms/config/digest/delete', { params: { accountSetId, id } }))
}

// ========== Voucher Word ==========

async function voucherWordList(accountSetId: number): Promise<FmsVoucherWordVO[]> {
  return unwrap<FmsVoucherWordVO[]>(await http.get('/fms/config/voucher-word/list', { params: { accountSetId } }))
}

async function voucherWordCreate(data: FmsVoucherWordVO): Promise<number> {
  return unwrap<number>(await http.post('/fms/config/voucher-word/create', data))
}

async function voucherWordUpdate(data: FmsVoucherWordVO): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/voucher-word/update', data))
}

async function voucherWordDelete(accountSetId: number, id: number): Promise<void> {
  await unwrap<void>(await http.delete('/fms/config/voucher-word/delete', { params: { accountSetId, id } }))
}

// ========== Finance Indicator ==========

async function financeIndicatorList(accountSetId: number): Promise<FmsFinanceIndicatorVO[]> {
  return unwrap<FmsFinanceIndicatorVO[]>(await http.get('/fms/config/finance-indicator/list', { params: { accountSetId } }))
}

async function financeIndicatorGet(accountSetId: number, id: number): Promise<FmsFinanceIndicatorVO> {
  return unwrap<FmsFinanceIndicatorVO>(await http.get('/fms/config/finance-indicator/get', { params: { accountSetId, id } }))
}

async function financeIndicatorCreate(data: FmsFinanceIndicatorVO): Promise<number> {
  return unwrap<number>(await http.post('/fms/config/finance-indicator/create', data))
}

async function financeIndicatorUpdate(data: FmsFinanceIndicatorVO): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/finance-indicator/update', data))
}

async function financeIndicatorDelete(accountSetId: number, id: number): Promise<void> {
  await unwrap<void>(await http.delete('/fms/config/finance-indicator/delete', { params: { accountSetId, id } }))
}

// ========== Auxiliary Type ==========

async function auxiliaryTypeList(accountSetId: number): Promise<FmsAuxiliaryTypeVO[]> {
  return unwrap<FmsAuxiliaryTypeVO[]>(await http.get('/fms/config/auxiliary-type/list', { params: { accountSetId } }))
}

async function auxiliaryTypeCreate(data: FmsAuxiliaryTypeVO): Promise<number> {
  return unwrap<number>(await http.post('/fms/config/auxiliary-type/create', data))
}

async function auxiliaryTypeUpdate(data: FmsAuxiliaryTypeVO): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/auxiliary-type/update', data))
}

async function auxiliaryTypeDelete(accountSetId: number, id: number): Promise<void> {
  await unwrap<void>(await http.delete('/fms/config/auxiliary-type/delete', { params: { accountSetId, id } }))
}

// ========== Subject ==========

async function subjectSimpleList(accountSetId: number, type?: number): Promise<FmsSubjectVO[]> {
  return unwrap<FmsSubjectVO[]>(await http.get('/fms/config/subject/simple-list', { params: { accountSetId, type } }))
}

async function subjectList(accountSetId: number, type?: number): Promise<FmsSubjectVO[]> {
  return unwrap<FmsSubjectVO[]>(await http.get('/fms/config/subject/list', { params: { accountSetId, type } }))
}

async function subjectGet(accountSetId: number, id: number): Promise<FmsSubjectVO> {
  return unwrap<FmsSubjectVO>(await http.get('/fms/config/subject/get', { params: { accountSetId, id } }))
}

async function subjectUsage(accountSetId: number, id: number): Promise<FmsSubjectUsage> {
  return unwrap<FmsSubjectUsage>(await http.get('/fms/config/subject/get-usage', { params: { accountSetId, id } }))
}

async function subjectCreate(data: FmsSubjectVO): Promise<number> {
  return unwrap<number>(await http.post('/fms/config/subject/create', data))
}

async function subjectUpdate(data: FmsSubjectVO): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/subject/update', data))
}

async function subjectDelete(accountSetId: number, ids: number[]): Promise<void> {
  await unwrap<void>(await http.delete('/fms/config/subject/delete-list', { data: { accountSetId, ids } }))
}

async function subjectUpdateStatus(data: { accountSetId: number; ids: number[]; status: number }): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/subject/update-status', data))
}

async function subjectExport(accountSetId: number, type?: number): Promise<Blob> {
  return requestBlob('/fms/config/subject/export-excel', { accountSetId, type })
}

// ========== Auxiliary Item ==========

async function auxiliaryItemList(accountSetId: number, auxiliaryTypeId: number): Promise<FmsAuxiliaryItemOptionVO[]> {
  return unwrap<FmsAuxiliaryItemOptionVO[]>(
    await http.get('/fms/config/auxiliary-item/simple-list', { params: { accountSetId, auxiliaryTypeId } })
  )
}

async function auxiliaryItemPage(params: { accountSetId: number; auxiliaryTypeId: number; search?: string; pageNo?: number; pageSize?: number }): Promise<{ list: FmsAuxiliaryItemOptionVO[]; total: number }> {
  return unwrap<{ list: FmsAuxiliaryItemOptionVO[]; total: number }>(
    await http.get('/fms/config/auxiliary-item/page', { params })
  )
}

async function auxiliaryItemCreate(data: FmsAuxiliaryItemOptionVO): Promise<number> {
  return unwrap<number>(await http.post('/fms/config/auxiliary-item/create', data))
}

async function auxiliaryItemUpdate(data: FmsAuxiliaryItemOptionVO): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/auxiliary-item/update', data))
}

async function auxiliaryItemDelete(accountSetId: number, ids: number[]): Promise<void> {
  await unwrap<void>(await http.delete('/fms/config/auxiliary-item/delete-list', { params: { accountSetId, ids: ids.join(',') } }))
}

async function auxiliaryItemUpdateStatus(accountSetId: number, id: number, status: number): Promise<void> {
  await unwrap<void>(await http.put('/fms/config/auxiliary-item/update-status', { accountSetId, id, status }))
}

async function auxiliaryItemExport(params: { accountSetId: number; auxiliaryTypeId: number; search?: string }): Promise<Blob> {
  return requestBlob('/fms/config/auxiliary-item/export-excel', params as unknown as Record<string, unknown>)
}

async function auxiliaryItemGetImportTemplate(): Promise<Blob> {
  return requestBlob('/fms/config/auxiliary-item/get-import-template')
}

async function auxiliaryItemImport(accountSetId: number, file: File): Promise<Record<string, unknown>> {
  const data = new FormData()
  data.append('accountSetId', String(accountSetId))
  data.append('file', file)
  return unwrap<Record<string, unknown>>(await http.post('/fms/config/auxiliary-item/import', data, { headers: { 'Content-Type': 'multipart/form-data' } }))
}

// ========== Export ==========

export const fmsConfig = {
  accountSet: {
    list: accountSetList,
    get: accountSetGet,
    create: accountSetCreate,
    update: accountSetUpdate,
    initialize: accountSetInitialize
  },
  accountUser: {
    list: accountUserList,
    update: accountUserUpdate,
    updateDefaultStatus: accountUserUpdateDefaultStatus
  },
  currency: {
    list: currencyList,
    create: currencyCreate,
    update: currencyUpdate,
    delete: currencyDelete
  },
  digest: {
    list: digestList,
    create: digestCreate,
    update: digestUpdate,
    delete: digestDelete
  },
  voucherWord: {
    list: voucherWordList,
    create: voucherWordCreate,
    update: voucherWordUpdate,
    delete: voucherWordDelete
  },
  financeIndicator: {
    get: financeIndicatorGet,
    list: financeIndicatorList,
    create: financeIndicatorCreate,
    update: financeIndicatorUpdate,
    delete: financeIndicatorDelete
  },
  auxiliaryType: {
    list: auxiliaryTypeList,
    create: auxiliaryTypeCreate,
    update: auxiliaryTypeUpdate,
    delete: auxiliaryTypeDelete
  },
  subject: {
    list: subjectList,
    simpleList: subjectSimpleList,
    get: subjectGet,
    usage: subjectUsage,
    create: subjectCreate,
    update: subjectUpdate,
    delete: subjectDelete,
    updateStatus: subjectUpdateStatus,
    exportExcel: subjectExport,
    getImportTemplate: () =>
      requestBlob('/fms/config/subject/get-import-template'),
    import: async (accountSetId: number, file: File): Promise<Record<string, unknown>> => {
      const data = new FormData()
      data.append('accountSetId', String(accountSetId))
      data.append('file', file)
      return unwrap<Record<string, unknown>>(await http.post('/fms/config/subject/import', data, { headers: { 'Content-Type': 'multipart/form-data' } }))
    }
  },
  initialBalance: {
    list: async (accountSetId: number, subjectType: number): Promise<FmsInitialBalance[]> =>
      unwrap<FmsInitialBalance[]>(await http.get('/fms/config/initial-balance/list', { params: { accountSetId, subjectType } })),
    save: async (accountSetId: number, balances: FmsInitialBalanceUpdate[]): Promise<void> => {
      await unwrap<void>(await http.put('/fms/config/initial-balance/save', { accountSetId, balances }))
    },
    trialBalance: async (accountSetId: number): Promise<FmsTrialBalance> =>
      unwrap<FmsTrialBalance>(await http.get('/fms/config/initial-balance/trial-balance', { params: { accountSetId } })),
    exportExcel: (accountSetId: number) =>
      requestBlob('/fms/config/initial-balance/export-excel', { accountSetId }),
    getImportTemplate: (accountSetId: number) =>
      requestBlob('/fms/config/initial-balance/get-import-template', { accountSetId }),
    import: async (accountSetId: number, file: File): Promise<number> => {
      const data = new FormData()
      data.append('accountSetId', String(accountSetId))
      data.append('file', file)
      return unwrap<number>(await http.post('/fms/config/initial-balance/import', data, { headers: { 'Content-Type': 'multipart/form-data' } }))
    }
  },
  auxiliaryItem: {
    list: auxiliaryItemList,
    page: auxiliaryItemPage,
    create: auxiliaryItemCreate,
    update: auxiliaryItemUpdate,
    delete: auxiliaryItemDelete,
    updateStatus: auxiliaryItemUpdateStatus,
    exportExcel: auxiliaryItemExport,
    getImportTemplate: auxiliaryItemGetImportTemplate,
    import: auxiliaryItemImport
  },
  financeParameter: {
    get: async (accountSetId: number): Promise<FmsFinanceParameter | null> =>
      unwrap<FmsFinanceParameter | null>(await http.get('/fms/config/finance-parameter/get', { params: { accountSetId } })),
    update: async (data: FmsFinanceParameter): Promise<void> => {
      await unwrap<void>(await http.put('/fms/config/finance-parameter/update', data))
    }
  },
  voucherTemplate: {
    list: async (accountSetId: number): Promise<FmsVoucherTemplateVO[]> =>
      unwrap<FmsVoucherTemplateVO[]>(await http.get('/fms/config/voucher-template/list', { params: { accountSetId } })),
    create: async (data: FmsVoucherTemplateVO): Promise<number> =>
      unwrap<number>(await http.post('/fms/config/voucher-template/create', data)),
    update: async (data: FmsVoucherTemplateVO): Promise<void> => {
      await unwrap<void>(await http.put('/fms/config/voucher-template/update', data))
    },
    delete: async (accountSetId: number, id: number): Promise<void> => {
      await unwrap<void>(await http.delete('/fms/config/voucher-template/delete', { params: { accountSetId, id } }))
    }
  },
  voucherTemplateCategory: {
    list: async (accountSetId: number): Promise<FmsVoucherTemplateCategoryVO[]> =>
      unwrap<FmsVoucherTemplateCategoryVO[]>(await http.get('/fms/config/voucher-template-category/list', { params: { accountSetId } })),
    create: async (data: FmsVoucherTemplateCategoryVO): Promise<number> =>
      unwrap<number>(await http.post('/fms/config/voucher-template-category/create', data)),
    update: async (data: FmsVoucherTemplateCategoryVO): Promise<void> => {
      await unwrap<void>(await http.put('/fms/config/voucher-template-category/update', data))
    },
    delete: async (accountSetId: number, id: number): Promise<void> => {
      await unwrap<void>(await http.delete('/fms/config/voucher-template-category/delete', { params: { accountSetId, id } }))
    }
  }
}

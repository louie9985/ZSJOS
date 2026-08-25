import { http, unwrap } from '../api'
import type {
  FmsClosingPeriodParams,
  FmsClosingOverview,
  FmsClosingScheme,
  FmsClosingSchemeSave,
  FmsClosingTemplate,
  FmsProfitLossSettings,
  FmsSpecialClosingSettings
} from './types'

export { type FmsAccountSetVO } from './types'
export { FmsAccountUserLevel } from './types'
export { fmsConfig } from './config'
export { fmsLedger } from './ledger'
export { fmsVoucher } from './voucher'
export { fmsHomeApi } from './home'
export { fmsReport } from './report'

/** FMS 结账 API */
export const fmsClosing = {
  period: {
    /** 获取指定账套的当前会计期间 */
    getCurrentMonth: async (accountSetId: number): Promise<string> =>
      unwrap<string>(await http.get('/fms/closing/period/current-month', { params: { accountSetId } })),
    /** 查询结账概况 */
    getOverview: async (params: FmsClosingPeriodParams): Promise<FmsClosingOverview> =>
      unwrap<FmsClosingOverview>(await http.get('/fms/closing/period/overview', { params })),
    /** 结账 */
    close: async (data: FmsClosingPeriodParams): Promise<void> => {
      await unwrap<void>(await http.put('/fms/closing/period/close', data))
    },
    /** 反结账 */
    cancel: async (params: FmsClosingPeriodParams): Promise<void> => {
      await unwrap<void>(await http.delete('/fms/closing/period/cancel', { params }))
    }
  },
  scheme: {
    list: async (params: FmsClosingPeriodParams): Promise<FmsClosingScheme[]> =>
      unwrap<FmsClosingScheme[]>(await http.get('/fms/closing/scheme/list', { params })),
    create: async (data: FmsClosingSchemeSave): Promise<number> =>
      unwrap<number>(await http.post('/fms/closing/scheme/create', data)),
    update: async (data: FmsClosingSchemeSave): Promise<void> => {
      await unwrap<void>(await http.put('/fms/closing/scheme/update', data))
    },
    updateProfitLossSettings: async (data: FmsProfitLossSettings): Promise<number> =>
      unwrap<number>(await http.put('/fms/closing/scheme/update-profit-loss-settings', data)),
    updateSpecialSettings: async (data: FmsSpecialClosingSettings): Promise<void> => {
      await unwrap<void>(await http.put('/fms/closing/scheme/update-special-settings', data))
    },
    delete: async (accountSetId: number, id: number): Promise<void> => {
      await unwrap<void>(await http.delete('/fms/closing/scheme/delete', { params: { accountSetId, id } }))
    }
  },
  template: {
    list: async (accountSetId: number): Promise<FmsClosingTemplate[]> =>
      unwrap<FmsClosingTemplate[]>(await http.get('/fms/closing/template/list', { params: { accountSetId } })),
    create: async (data: FmsClosingTemplate): Promise<number> =>
      unwrap<number>(await http.post('/fms/closing/template/create', data)),
    update: async (data: FmsClosingTemplate): Promise<void> => {
      await unwrap<void>(await http.put('/fms/closing/template/update', data))
    },
    delete: async (accountSetId: number, id: number): Promise<void> => {
      await unwrap<void>(await http.delete('/fms/closing/template/delete', { params: { accountSetId, id } }))
    }
  },
  voucher: {
    generateProfitLoss: async (data: FmsClosingPeriodParams): Promise<number> =>
      unwrap<number>(await http.post('/fms/closing/voucher/generate-profit-loss', data)),
    generateScheme: async (data: FmsClosingPeriodParams & { id: number }): Promise<number> =>
      unwrap<number>(await http.post('/fms/closing/voucher/generate-scheme', data)),
    generateList: async (data: FmsClosingPeriodParams & { ids: number[] }): Promise<number[]> =>
      unwrap<number[]>(await http.post('/fms/closing/voucher/generate-list', data))
  }
}

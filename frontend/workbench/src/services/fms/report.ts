import { http, unwrap } from '../api'
import { requestBlob } from '../download'
import type {
  FmsReportListParams,
  FmsReportItem,
  FmsBalanceSheetRow,
  FmsBalanceSheetCheck,
  FmsIncomeStatementCheck,
  FmsCashFlowCheck,
  FmsCashFlowAdjustment,
  FmsReportFormulaUpdate,
  FmsCashFlowStatementUpdateItem,
  FmsCashFlowAdjustmentUpdateItem
} from './types'

/**
 * FMS 财务报表 API。
 *
 * ⚠️ 注意：admin 端三张报表的「查询」接口都是 `/get` 而非 `/list`，
 * 与账簿的 `/list` 命名不同。这里统一对外暴露 `list`，内部映射到 `/get`。
 * 各报表另有 `check`（勾稽校验）与 `update`（公式/金额修改）接口。
 */

/** FMS 财务报表 API */
export const fmsReport = {
  balanceSheet: {
    /** 查询资产负债表 */
    list: async (params: FmsReportListParams): Promise<FmsBalanceSheetRow[]> =>
      unwrap<FmsBalanceSheetRow[]>(await http.get('/fms/report/balance-sheet/get', { params })),
    /** 检查资产负债表 */
    check: async (params: FmsReportListParams): Promise<FmsBalanceSheetCheck> =>
      unwrap<FmsBalanceSheetCheck>(await http.get('/fms/report/balance-sheet/check', { params })),
    /** 修改资产负债表公式 */
    update: async (data: FmsReportFormulaUpdate): Promise<void> => {
      await unwrap<void>(await http.put('/fms/report/balance-sheet/update', data))
    },
    /** 导出资产负债表 */
    exportExcel: (params: FmsReportListParams) =>
      requestBlob('/fms/report/balance-sheet/export-excel', params as unknown as Record<string, unknown>)
  },
  incomeStatement: {
    /** 查询利润表 */
    list: async (params: FmsReportListParams): Promise<FmsReportItem[]> =>
      unwrap<FmsReportItem[]>(await http.get('/fms/report/income-statement/get', { params })),
    /** 检查利润表 */
    check: async (params: FmsReportListParams): Promise<FmsIncomeStatementCheck> =>
      unwrap<FmsIncomeStatementCheck>(await http.get('/fms/report/income-statement/check', { params })),
    /** 修改利润表公式 */
    update: async (data: FmsReportFormulaUpdate): Promise<void> => {
      await unwrap<void>(await http.put('/fms/report/income-statement/update', data))
    },
    /** 导出利润表 */
    exportExcel: (params: FmsReportListParams) =>
      requestBlob('/fms/report/income-statement/export-excel', params as unknown as Record<string, unknown>)
  },
  cashFlowStatement: {
    /** 查询现金流量表 */
    list: async (params: FmsReportListParams): Promise<FmsReportItem[]> =>
      unwrap<FmsReportItem[]>(await http.get('/fms/report/cash-flow-statement/get', { params })),
    /** 检查现金流量表 */
    check: async (params: FmsReportListParams): Promise<FmsCashFlowCheck> =>
      unwrap<FmsCashFlowCheck>(await http.get('/fms/report/cash-flow-statement/check', { params })),
    /** 查询现金流量辅助数据（含公式）列表 */
    getFormulaList: async (params: FmsReportListParams): Promise<FmsCashFlowAdjustment[]> =>
      unwrap<FmsCashFlowAdjustment[]>(await http.get('/fms/report/cash-flow-statement/adjustment/list', { params })),
    /** 保存现金流量表公式 */
    saveFormula: async (data: FmsReportFormulaUpdate): Promise<void> => {
      await unwrap<void>(await http.put('/fms/report/cash-flow-statement/adjustment/update-formula', data))
    },
    /** 修改现金流量表（人工调整非公式项目金额） */
    update: async (data: FmsReportListParams & { items: FmsCashFlowStatementUpdateItem[] }): Promise<void> => {
      await unwrap<void>(await http.put('/fms/report/cash-flow-statement/update', data))
    },
    /** 修改现金流量辅助数据（调整模式） */
    updateAdjustment: async (data: { accountSetId: number; items: FmsCashFlowAdjustmentUpdateItem[] }): Promise<void> => {
      await unwrap<void>(await http.put('/fms/report/cash-flow-statement/adjustment/update', data))
    },
    /** 导出现金流量表 */
    exportExcel: (params: FmsReportListParams) =>
      requestBlob('/fms/report/cash-flow-statement/export-excel', params as unknown as Record<string, unknown>)
  }
} as const

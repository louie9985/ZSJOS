import { lazy, Suspense, type ReactNode } from 'react'
import { Spin } from 'antd'
import type { WorkbenchMenu } from '../services/api'
import { APP_ROUTES } from '../constants'

// 代码分割：所有 FMS 页面按需加载（用户进入 /fms 路径时才下载）。
// 非财务员工不会下载这些模块和 echarts。
const FmsHomePage = lazy(() => import('../pages/fms/FmsHomePage'))
const FmsVoucherCreatePage = lazy(() => import('../pages/fms/FmsVoucherCreatePage'))
const FmsVoucherListPage = lazy(() => import('../pages/fms/FmsVoucherListPage'))
const FmsVoucherStatisticsPage = lazy(() => import('../pages/fms/FmsVoucherStatisticsPage'))
const FmsLedgerGeneralPage = lazy(() => import('../pages/fms/FmsLedgerGeneralPage'))
const FmsLedgerDetailPage = lazy(() => import('../pages/fms/FmsLedgerDetailPage'))
const FmsLedgerSubjectBalancePage = lazy(() => import('../pages/fms/FmsLedgerSubjectBalancePage'))
const FmsLedgerQuantityDetailPage = lazy(() => import('../pages/fms/FmsLedgerQuantityDetailPage'))
const FmsLedgerQuantityGeneralPage = lazy(() => import('../pages/fms/FmsLedgerQuantityGeneralPage'))
const FmsLedgerMultiColumnPage = lazy(() => import('../pages/fms/FmsLedgerMultiColumnPage'))
const FmsLedgerAuxiliaryDetailPage = lazy(() => import('../pages/fms/FmsLedgerAuxiliaryDetailPage'))
const FmsLedgerAuxiliaryBalancePage = lazy(() => import('../pages/fms/FmsLedgerAuxiliaryBalancePage'))
const FmsReportBalanceSheetPage = lazy(() => import('../pages/fms/FmsReportBalanceSheetPage'))
const FmsReportIncomeStatementPage = lazy(() => import('../pages/fms/FmsReportIncomeStatementPage'))
const FmsReportCashFlowStatementPage = lazy(() => import('../pages/fms/FmsReportCashFlowStatementPage'))
const FmsClosingPeriodPage = lazy(() => import('../pages/fms/FmsClosingPeriodPage'))
const FmsConfigAccountSetPage = lazy(() => import('../pages/fms/FmsConfigAccountSetPage'))
const FmsConfigSubjectPage = lazy(() => import('../pages/fms/FmsConfigSubjectPage'))
const FmsConfigAuxiliaryPage = lazy(() => import('../pages/fms/FmsConfigAuxiliaryPage'))
const FmsConfigInitialBalancePage = lazy(() => import('../pages/fms/FmsConfigInitialBalancePage'))
const FmsConfigCurrencyPage = lazy(() => import('../pages/fms/FmsConfigCurrencyPage'))
const FmsConfigDigestPage = lazy(() => import('../pages/fms/FmsConfigDigestPage'))
const FmsConfigVoucherWordPage = lazy(() => import('../pages/fms/FmsConfigVoucherWordPage'))
const FmsConfigVoucherTemplatePage = lazy(() => import('../pages/fms/FmsConfigVoucherTemplatePage'))
const FmsConfigFinanceParameterPage = lazy(() => import('../pages/fms/FmsConfigFinanceParameterPage'))
const FmsConfigFinanceIndicatorPage = lazy(() => import('../pages/fms/FmsConfigFinanceIndicatorPage'))

const FmsLoading = <section className="workspace-page fms-page" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 300 }}><Spin size="large"/></section>

/**
 * 解析 FMS 路由。RouteHost 调用此函数；命中 FMS 路径则返回对应页面组件（Suspense 包裹），否则 undefined。
 */
export function resolveFmsRoute(menu: WorkbenchMenu | undefined, permissions: string[]): ReactNode | undefined {
  if (!menu) return undefined
  const p = menu.path
  let page: ReactNode | undefined
  if (p === APP_ROUTES.FMS_HOME) page = <FmsHomePage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_VOUCHER_CREATE) page = <FmsVoucherCreatePage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_VOUCHER_LIST) page = <FmsVoucherListPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_VOUCHER_STATISTICS) page = <FmsVoucherStatisticsPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_LEDGER_GENERAL) page = <FmsLedgerGeneralPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_LEDGER_DETAIL) page = <FmsLedgerDetailPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_LEDGER_SUBJECT_BALANCE) page = <FmsLedgerSubjectBalancePage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_LEDGER_QUANTITY_DETAIL) page = <FmsLedgerQuantityDetailPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_LEDGER_QUANTITY_GENERAL) page = <FmsLedgerQuantityGeneralPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_LEDGER_MULTI_COLUMN) page = <FmsLedgerMultiColumnPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_LEDGER_AUXILIARY_DETAIL) page = <FmsLedgerAuxiliaryDetailPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_LEDGER_AUXILIARY_BALANCE) page = <FmsLedgerAuxiliaryBalancePage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_REPORT_BALANCE_SHEET) page = <FmsReportBalanceSheetPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_REPORT_INCOME_STATEMENT) page = <FmsReportIncomeStatementPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_REPORT_CASH_FLOW_STATEMENT) page = <FmsReportCashFlowStatementPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_CLOSING_PERIOD) page = <FmsClosingPeriodPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_CONFIG_ACCOUNT_SET) page = <FmsConfigAccountSetPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_CONFIG_SUBJECT) page = <FmsConfigSubjectPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_CONFIG_AUXILIARY) page = <FmsConfigAuxiliaryPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_CONFIG_INITIAL_BALANCE) page = <FmsConfigInitialBalancePage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_CONFIG_CURRENCY) page = <FmsConfigCurrencyPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_CONFIG_DIGEST) page = <FmsConfigDigestPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_CONFIG_VOUCHER_WORD) page = <FmsConfigVoucherWordPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_CONFIG_VOUCHER_TEMPLATE) page = <FmsConfigVoucherTemplatePage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_CONFIG_FINANCE_PARAMETER) page = <FmsConfigFinanceParameterPage permissions={permissions}/>
  else if (p === APP_ROUTES.FMS_CONFIG_FINANCE_INDICATOR) page = <FmsConfigFinanceIndicatorPage permissions={permissions}/>
  if (!page) return undefined
  return <Suspense fallback={FmsLoading}>{page}</Suspense>
}

import { Alert, Button, Space } from 'antd'
import { useMemo, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import type { FmsBalanceSheetCheck, FmsCashFlowCheck, FmsIncomeStatementCheck } from '../../services/fms/types'
import { formatMoney } from '../../services/fms/format'

export type FmsReportCheckResult = FmsBalanceSheetCheck & FmsIncomeStatementCheck & FmsCashFlowCheck

export const FMS_REPORT_TYPE_LABEL = {
  BALANCE_SHEET: 1,
  INCOME_STATEMENT: 2,
  CASH_FLOW_STATEMENT: 3
} as const

function reportName(type: number) {
  if (type === FMS_REPORT_TYPE_LABEL.INCOME_STATEMENT) return '利润表'
  if (type === FMS_REPORT_TYPE_LABEL.CASH_FLOW_STATEMENT) return '现金流量表'
  return '资产负债表'
}

interface FmsReportCheckAlertProps {
  result?: FmsReportCheckResult
  reportType: number
}

export default function FmsReportCheckAlert({ result, reportType }: FmsReportCheckAlertProps) {
  const navigate = useNavigate()

  const passed = useMemo(() => {
    if (!result) return false
    if (reportType === FMS_REPORT_TYPE_LABEL.CASH_FLOW_STATEMENT) {
      return result.balanceSheetReady === true
    }
    return (
      result.balanced === true &&
      !result.unmappedSubjects?.length &&
      (reportType === FMS_REPORT_TYPE_LABEL.INCOME_STATEMENT ||
        (result.initialBalanceBalanced === true && result.profitLossTransferred === true))
    )
  }, [result, reportType])

  const description = useMemo(() => {
    if (!result || passed) return undefined
    const unmappedText = (result.unmappedSubjects || [])
      .slice(0, 5)
      .map(s => `${s.code} ${s.name}`)
      .join('、')
    const formatDiff = (amount?: number) => formatMoney(Math.abs(Number(amount || 0))) || '0.00'
    const items: ReactNode[] = []
    if (result.balanced === false && reportType !== FMS_REPORT_TYPE_LABEL.INCOME_STATEMENT) {
      items.push(
        <span key="unbalanced">
          资产负债表不平衡：年初差额 {formatDiff(result.openingDifferenceAmount)}，期末差额 {formatDiff(result.closingDifferenceAmount)}
          <Button type="link" size="small" onClick={() => navigate('/fms/report/balance-sheet')}>查看资产负债表</Button>
        </span>
      )
    }
    if (result.balanced === false && reportType === FMS_REPORT_TYPE_LABEL.INCOME_STATEMENT) {
      items.push(
        <span key="income-diff">
          净利润与未分配利润变动不一致，勾稽差额 {formatDiff(result.differenceAmount)}
          <Button type="link" size="small" onClick={() => navigate('/fms/report/balance-sheet')}>查看资产负债表</Button>
        </span>
      )
    }
    if (result.initialBalanceBalanced === false) {
      items.push(
        <span key="initial">
          初始余额试算不平衡
          <Button type="link" size="small" onClick={() => navigate('/fms/config/initial-balance')}>处理初始余额</Button>
        </span>
      )
    }
    if (result.profitLossTransferred === false) {
      items.push(
        <span key="profit-loss">
          查询期间存在尚未结转的损益余额
          <Button type="link" size="small" onClick={() => navigate('/fms/closing/period')}>前往结转损益</Button>
        </span>
      )
    }
    if ((result.unmappedSubjects?.length || 0) > 0) {
      items.push(
        <span key="unmapped">
          {result.unmappedSubjects!.length} 个一级科目尚未纳入报表公式：{unmappedText}，请编辑当前报表公式
        </span>
      )
    }
    return items.length
      ? <Space direction="vertical" size="small" style={{ width: '100%' }}>{items}</Space>
      : undefined
  }, [result, passed, reportType, navigate])

  if (!result) return null

  return <Alert
    type={passed ? 'success' : 'warning'}
    showIcon
    style={{ marginBlockEnd: 16 }}
    message={passed ? `${reportName(reportType)}检查通过` : `${reportName(reportType)}检查发现问题`}
    description={description}
  />
}

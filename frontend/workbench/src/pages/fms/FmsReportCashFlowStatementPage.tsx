import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, DatePicker, InputNumber, Space, Tabs, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PrinterOutlined, EditOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsReport } from '../../services/fms/report'
import type { FmsCashFlowAdjustment, FmsReportItem } from '../../services/fms/types'
import { formatMoney } from '../../services/fms/format'
import { buildFmsTablePrintHtml, type FmsPrintColumn } from '../../services/fms/print'
import { saveBlob } from '../../services/download'
import FmsPrintPreview, { usePrintPreview } from '../../components/FmsPrintPreview'
import FmsReportCheckAlert, { type FmsReportCheckResult } from '../../components/fms/FmsReportCheckAlert'
import FmsReportFormulaModal, { type FmsReportFormulaTarget } from '../../components/fms/FmsReportFormulaModal'

type Mode = 'main' | 'adjustment' | 'statement'

export default function FmsReportCashFlowStatementPage({ permissions }: { permissions: string[] }) {
  const { accountSet, currentMonth, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const now = dayjs().format('YYYY-MM')
  const [startMonth, setStartMonth] = useState(now)
  const [endMonth, setEndMonth] = useState(now)
  const [list, setList] = useState<FmsReportItem[]>([])
  const [adjustmentList, setAdjustmentList] = useState<FmsCashFlowAdjustment[]>([])
  const [check, setCheck] = useState<FmsReportCheckResult>()
  const [loading, setLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [mode, setMode] = useState<Mode>('main')
  const [formulaOpen, setFormulaOpen] = useState(false)
  const [formulaItem, setFormulaItem] = useState<FmsReportFormulaTarget>()
  const listVersion = useRef(0)
  const printPreview = usePrintPreview()

  const periodLabel = useMemo(
    () => startMonth === endMonth
      ? startMonth.replace(/^(\d{4})-(\d{2})$/, '$1年$2月')
      : `${startMonth.replace(/^(\d{4})-(\d{2})$/, '$1年$2月')} 至 ${endMonth.replace(/^(\d{4})-(\d{2})$/, '$1年$2月')}`,
    [startMonth, endMonth]
  )

  const getList = useCallback(async (sm?: string, em?: string) => {
    const start = sm ?? startMonth
    const end = em ?? endMonth
    if (!accountSetId || !end) {
      setList([])
      setCheck(undefined)
      return
    }
    const v = ++listVersion.current
    setLoading(true)
    try {
      const params = { accountSetId, startMonth: start, endMonth: end }
      const rows = await fmsReport.cashFlowStatement.list(params)
      const checkResult = await fmsReport.cashFlowStatement.check(params)
      if (v !== listVersion.current) return
      setList(rows)
      setCheck(checkResult)
    } catch (e) {
      if (v !== listVersion.current) return
      message.error(e instanceof Error ? e.message : '查询失败')
      setList([])
      setCheck(undefined)
    } finally {
      if (v === listVersion.current) setLoading(false)
    }
  }, [accountSetId, startMonth, endMonth])

  const getAdjustmentList = useCallback(async () => {
    if (!accountSetId) return
    setLoading(true)
    try {
      const rows = await fmsReport.cashFlowStatement.getFormulaList({ accountSetId, startMonth, endMonth })
      setAdjustmentList(rows)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '辅助数据加载失败')
    } finally {
      setLoading(false)
    }
  }, [accountSetId, startMonth, endMonth])

  // 账套加载后回填当前月份
  const defaulted = useRef(false)
  if (accountSetId && currentMonth && !defaulted.current) {
    defaulted.current = true
    setStartMonth(currentMonth)
    setEndMonth(currentMonth)
  }

  // 账套切换时重载
  const lastAccountSetId = useRef<number | undefined>(undefined)
  if (accountSetId !== lastAccountSetId.current) {
    lastAccountSetId.current = accountSetId
    if (accountSetId) {
      setTimeout(() => getList(), 0)
    } else {
      setList([])
      setCheck(undefined)
      setAdjustmentList([])
      setMode('main')
    }
  }

  const handleExport = useCallback(async () => {
    if (!accountSetId) return
    setExportLoading(true)
    try {
      const blob = await fmsReport.cashFlowStatement.exportExcel({ accountSetId, startMonth, endMonth })
      saveBlob(blob, `现金流量表-${periodLabel}.xls`)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, startMonth, endMonth, periodLabel])

  const handlePrint = useCallback(() => {
    if (!accountSet || list.length === 0) return
    const printColumns: FmsPrintColumn<FmsReportItem>[] = [
      { title: '项目', render: r => r.name },
      { title: '行次', align: 'center', render: r => String(r.rowNo) },
      { title: '本年累计金额', align: 'right', render: r => formatMoney(r.yearAmount) },
      { title: '本期金额', align: 'right', render: r => formatMoney(r.currentAmount) }
    ]
    const html = buildFmsTablePrintHtml({
      title: '现金流量表',
      companyName: accountSet.companyName,
      periodLabel,
      columns: printColumns,
      rows: list
    })
    printPreview.show(html)
  }, [accountSet, list, periodLabel, printPreview])

  const isAmountAdjustable = useCallback((item: FmsReportItem) =>
    Boolean(item.rowNo && !item.formula?.includes('L')), [])

  /** 根据辅助数据公式（L行次）即时重算行次公式项目 */
  const recalcAdjustmentLineItems = useCallback((rows: FmsCashFlowAdjustment[]) => {
    const lineMap = new Map(rows.map(item => [item.rowNo, item]))
    rows.forEach(item => {
      if (!item.formula?.includes('L')) return
      item.currentAmount = calcLineAmount(item.formula, lineMap, 'currentAmount')
      item.yearAmount = calcLineAmount(item.formula, lineMap, 'yearAmount')
      lineMap.set(item.rowNo, item)
    })
  }, [])

  const openAdjustment = useCallback(async () => {
    setMode('adjustment')
    await getAdjustmentList()
  }, [getAdjustmentList])

  const saveAdjustment = useCallback(async (next = false) => {
    const items = adjustmentList
      .filter(item => item.editable)
      .map(item => ({ id: item.id, currentAmount: Number(item.currentAmount || 0), yearAmount: Number(item.yearAmount || 0) }))
    if (!accountSetId || !items.length) return
    setSubmitting(true)
    try {
      await fmsReport.cashFlowStatement.updateAdjustment({ accountSetId, items })
      message.success('保存成功')
      if (next) {
        setMode('statement')
        await getList()
      } else {
        setMode('main')
        await getList()
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      setSubmitting(false)
    }
  }, [accountSetId, adjustmentList, getList])

  const clearAdjustment = useCallback(() => {
    const next = adjustmentList.map(item => ({
      ...item,
      currentAmount: item.editable ? 0 : item.currentAmount,
      yearAmount: item.editable ? 0 : item.yearAmount
    }))
    setAdjustmentList(next)
    recalcAdjustmentLineItems(next)
  }, [adjustmentList, recalcAdjustmentLineItems])

  const saveStatement = useCallback(async () => {
    const items = list
      .filter(isAmountAdjustable)
      .map(item => ({ id: item.id, currentAmount: Number(item.currentAmount || 0), yearAmount: Number(item.yearAmount || 0) }))
    if (!accountSetId || !items.length) return
    setSubmitting(true)
    try {
      await fmsReport.cashFlowStatement.update({ accountSetId, startMonth, endMonth, items })
      message.success('保存成功')
      setMode('main')
      await getList()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      setSubmitting(false)
    }
  }, [accountSetId, startMonth, endMonth, list, isAmountAdjustable, getList])

  const returnToAdjustment = useCallback(async () => {
    setMode('adjustment')
    await getAdjustmentList()
  }, [getAdjustmentList])

  const canUpdate = permissions.includes('fms:report:cash-flow-statement:update')
  const canExport = permissions.includes('fms:report:cash-flow-statement:export')
  const canPrint = permissions.includes('fms:report:cash-flow-statement:print')

  const itemClass = useCallback((item: FmsReportItem | FmsCashFlowAdjustment) => ({
    paddingLeft: item.level === 2 ? 20 : item.level === 3 ? 40 : 0,
    fontWeight: !item.editable ? 600 : 400
  }), [])

  const mainColumns: ColumnsType<FmsReportItem> = [
    {
      title: '项目', dataIndex: 'name', width: 480,
      render: (text: string, row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={itemClass(row)}>{text}</span>
          {row.editable && writable && canUpdate && (
            <Button type="link" size="small" icon={<EditOutlined/>} title="编辑公式"
              onClick={() => setFormulaItem({ id: row.id, name: row.name, formula: row.formula })} />
          )}
        </div>
      )
    },
    { title: '行次', dataIndex: 'rowNo', width: 90, align: 'center' },
    { title: '本年累计金额', dataIndex: 'yearAmount', width: 180, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '本期金额', dataIndex: 'currentAmount', width: 180, align: 'right', render: (v: number) => formatMoney(v) }
  ]

  const adjustmentColumns: ColumnsType<FmsCashFlowAdjustment> = [
    {
      title: '项目', dataIndex: 'name', width: 480,
      render: (text: string, row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={itemClass(row)}>{text}</span>
          {row.editable && writable && canUpdate && (
            <Button type="link" size="small" icon={<EditOutlined/>} title="编辑公式"
              onClick={() => setFormulaItem({ id: row.id, name: row.name, formula: row.formula })} />
          )}
        </div>
      )
    },
    { title: '行次', dataIndex: 'rowNo', width: 90, align: 'center' },
    {
      title: '本年数', width: 180, align: 'right',
      render: (_, row) => row.editable && mode === 'adjustment'
        ? <InputNumber value={row.yearAmount} precision={2} controls={false} style={{ width: '100%' }}
            onChange={v => { row.yearAmount = Number(v || 0); recalcAdjustmentLineItems(adjustmentList) }} />
        : formatMoney(row.yearAmount)
    },
    {
      title: '本期数', width: 180, align: 'right',
      render: (_, row) => row.editable && mode === 'adjustment'
        ? <InputNumber value={row.currentAmount} precision={2} controls={false} style={{ width: '100%' }}
            onChange={v => { row.currentAmount = Number(v || 0); recalcAdjustmentLineItems(adjustmentList) }} />
        : formatMoney(row.currentAmount)
    }
  ]

  const statementColumns: ColumnsType<FmsReportItem> = [
    { title: '项目', dataIndex: 'name', width: 480 },
    { title: '行次', dataIndex: 'rowNo', width: 90, align: 'center' },
    {
      title: '本年金额', width: 180, align: 'right',
      render: (_, row) => isAmountAdjustable(row)
        ? <InputNumber value={row.yearAmount} precision={2} controls={false} style={{ width: '100%' }}
            onChange={v => { row.yearAmount = Number(v || 0) }} />
        : formatMoney(row.yearAmount)
    },
    {
      title: '本期金额', width: 180, align: 'right',
      render: (_, row) => isAmountAdjustable(row)
        ? <InputNumber value={row.currentAmount} precision={2} controls={false} style={{ width: '100%' }}
            onChange={v => { row.currentAmount = Number(v || 0) }} />
        : formatMoney(row.currentAmount)
    }
  ]

  return (
    <section className="workspace-page fms-page">
      {/* 搜索条 */}
      <div className="fms-search-area">
        <Space wrap>
          <DatePicker.RangePicker
            picker="month"
            value={[dayjs(startMonth), dayjs(endMonth)]}
            onChange={dates => {
              if (dates?.[0] && dates?.[1]) {
                const sm = dates[0].format('YYYY-MM')
                const em = dates[1].format('YYYY-MM')
                setStartMonth(sm)
                setEndMonth(em)
                if (mode !== 'main') { setMode('main') }
                getList(sm, em)
              }
            }}
            allowClear={false}
          />
          {mode === 'main' && (
            <Button type="primary" onClick={() => getList()}>查询</Button>
          )}
          {mode === 'adjustment' && writable && canUpdate && (
            <>
              <Button type="primary" loading={submitting} onClick={() => saveAdjustment(true)}>下一步</Button>
              <Button loading={submitting} onClick={clearAdjustment}>清空并重算</Button>
              <Button loading={submitting} onClick={() => setMode('main')}>返回</Button>
            </>
          )}
          {mode === 'statement' && (
            <>
              {writable && canUpdate && (
                <>
                  <Button type="primary" loading={submitting} onClick={saveStatement}>保存</Button>
                  <Button loading={submitting} onClick={returnToAdjustment}>上一步</Button>
                  <Button loading={submitting} onClick={() => { setList([]); returnToAdjustment() }}>清空并重算</Button>
                </>
              )}
              <Button loading={submitting} onClick={() => setMode('main')}>返回</Button>
            </>
          )}
          {mode === 'main' && (
            <Button icon={<DownloadOutlined/>} loading={exportLoading} onClick={handleExport} disabled={!endMonth}>导出</Button>
          )}
          {mode === 'main' && (
            <Button icon={<PrinterOutlined/>} onClick={handlePrint} disabled={list.length === 0}>打印</Button>
          )}
          {mode === 'main' && writable && canUpdate && (
            <Button type="primary" onClick={openAdjustment}>调整</Button>
          )}
        </Space>
      </div>

      {/* 表格 */}
      <div className="fms-table-area">
        {mode === 'main' && <FmsReportCheckAlert result={check} reportType={3}/>}
        {mode === 'adjustment' && (
          <Alert type="info" showIcon style={{ marginBlockEnd: 16 }}
            message="辅助数据用于现金流量表 EX 项取数；可编辑公式或直接调整本期、本年金额" />
        )}
        {mode === 'statement' && (
          <Alert type="warning" showIcon style={{ marginBlockEnd: 16 }}
            message="可直接调整非行次公式项目；金额为 0 时重新按公式计算" />
        )}
        {mode === 'main' && (
          <FmsProTable<FmsReportItem>
            rowKey="id" columns={mainColumns} dataSource={list}
            loading={loading} pagination={false} bordered size="small"
            scroll={{ y: 'calc(100vh - 300px)' }} />
        )}
        {mode === 'adjustment' && (
          <FmsProTable<FmsCashFlowAdjustment>
            rowKey="id" columns={adjustmentColumns} dataSource={adjustmentList}
            loading={loading} pagination={false} bordered size="small"
            scroll={{ y: 'calc(100vh - 300px)' }} />
        )}
        {mode === 'statement' && (
          <FmsProTable<FmsReportItem>
            rowKey="id" columns={statementColumns} dataSource={list}
            loading={loading} pagination={false} bordered size="small"
            scroll={{ y: 'calc(100vh - 300px)' }} />
        )}
      </div>

      <FmsReportFormulaModal open={formulaOpen} onClose={() => setFormulaOpen(false)} item={formulaItem} type="cash-flow"/>
      <FmsPrintPreview open={printPreview.open} onClose={printPreview.close} html={printPreview.html} title="现金流量表打印预览"/>
    </section>
  )
}

/** 按后端相同的 +/- L行次 语义计算辅助数据金额 */
function calcLineAmount(
  formula: string,
  lineMap: Map<number, FmsCashFlowAdjustment>,
  amountField: 'currentAmount' | 'yearAmount'
): number {
  let expressions: unknown
  try {
    expressions = JSON.parse(formula)
  } catch {
    return 0
  }
  if (!Array.isArray(expressions) || typeof expressions[0] !== 'string') return 0
  let amount = 0
  for (const match of expressions[0].matchAll(/([+-]?)(L\d+)/g)) {
    const rowAmount = Number(lineMap.get(Number(match[2].slice(1)))?.[amountField] || 0)
    amount += match[1] === '-' ? -rowAmount : rowAmount
  }
  return Number(amount.toFixed(2))
}

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button, DatePicker, Space, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PrinterOutlined, EditOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsReport } from '../../services/fms/report'
import type { FmsBalanceSheetRow } from '../../services/fms/types'
import { formatMoney } from '../../services/fms/format'
import { buildFmsTablePrintHtml, type FmsPrintColumn } from '../../services/fms/print'
import { saveBlob } from '../../services/download'
import FmsPrintPreview, { usePrintPreview } from '../../components/FmsPrintPreview'
import FmsReportCheckAlert, { type FmsReportCheckResult } from '../../components/fms/FmsReportCheckAlert'
import FmsReportFormulaModal, { type FmsReportFormulaTarget } from '../../components/fms/FmsReportFormulaModal'

/** 项目名称样式：按层级缩进，汇总项加粗 */
function itemClass(level?: number, editable?: boolean, rowNo?: number) {
  return {
    paddingLeft: level === 2 ? 16 : level === 3 ? 32 : 0,
    fontWeight: !editable && rowNo ? 600 : 400
  }
}

export default function FmsReportBalanceSheetPage({ permissions }: { permissions: string[] }) {
  const { accountSet, currentMonth, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const now = dayjs().format('YYYY-MM')
  const [month, setMonth] = useState(now)
  const [list, setList] = useState<FmsBalanceSheetRow[]>([])
  const [check, setCheck] = useState<FmsReportCheckResult>()
  const [loading, setLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const [formulaOpen, setFormulaOpen] = useState(false)
  const [formulaItem, setFormulaItem] = useState<FmsReportFormulaTarget>()
  const listVersion = useRef(0)
  const printPreview = usePrintPreview()

  const periodLabel = useMemo(() => month.replace(/^(\d{4})-(\d{2})$/, '$1年$2月'), [month])

  const getList = useCallback(async (target?: string) => {
    const m = target ?? month
    if (!accountSetId || !m) {
      setList([])
      setCheck(undefined)
      return
    }
    const v = ++listVersion.current
    setLoading(true)
    try {
      const params = { accountSetId, startMonth: m, endMonth: m }
      const rows = await fmsReport.balanceSheet.list(params)
      const checkResult = await fmsReport.balanceSheet.check(params)
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
  }, [accountSetId, month])

  // 账套加载后回填当前月份
  const defaulted = useRef(false)
  if (accountSetId && currentMonth && !defaulted.current) {
    defaulted.current = true
    setMonth(currentMonth)
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
    }
  }

  const handleExport = useCallback(async () => {
    if (!accountSetId) return
    setExportLoading(true)
    try {
      const blob = await fmsReport.balanceSheet.exportExcel({ accountSetId, startMonth: month, endMonth: month })
      saveBlob(blob, `资产负债表-${periodLabel}.xls`)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, month, periodLabel])

  const handlePrint = useCallback(() => {
    if (!accountSet || list.length === 0) return
    const printColumns: FmsPrintColumn<FmsBalanceSheetRow>[] = [
      { title: '资产', render: r => r.assetName || '' },
      { title: '行次', align: 'center', render: r => r.assetRowNo ? String(r.assetRowNo) : '' },
      { title: '期末余额', align: 'right', render: r => formatMoney(r.assetClosingAmount) },
      { title: '年初余额', align: 'right', render: r => formatMoney(r.assetOpeningAmount) },
      { title: '负债和所有者权益', render: r => r.liabilityName || '' },
      { title: '行次', align: 'center', render: r => r.liabilityRowNo ? String(r.liabilityRowNo) : '' },
      { title: '期末余额', align: 'right', render: r => formatMoney(r.liabilityClosingAmount) },
      { title: '年初余额', align: 'right', render: r => formatMoney(r.liabilityOpeningAmount) }
    ]
    const html = buildFmsTablePrintHtml({
      title: '资产负债表',
      companyName: accountSet.companyName,
      periodLabel,
      columns: printColumns,
      rows: list
    })
    printPreview.show(html)
  }, [accountSet, list, periodLabel, printPreview])

  const openFormula = useCallback((row: FmsBalanceSheetRow, asset: boolean) => {
    setFormulaItem({
      id: asset ? row.assetId : row.liabilityId,
      name: (asset ? row.assetName : row.liabilityName) || '',
      formula: asset ? row.assetFormula : row.liabilityFormula
    })
    setFormulaOpen(true)
  }, [])

  const columns: ColumnsType<FmsBalanceSheetRow> = [
    {
      title: '资产', dataIndex: 'assetName', width: 210,
      render: (text: string, row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={itemClass(row.assetLevel, row.assetEditable, row.assetRowNo)}>{text}</span>
          {row.assetEditable && writable && permissions.includes('fms:report:balance-sheet:update') && (
            <Button type="link" size="small" icon={<EditOutlined/>} title="编辑公式" onClick={() => openFormula(row, true)}/>
          )}
        </div>
      )
    },
    { title: '行次', width: 64, align: 'center', render: (_, r) => r.assetRowNo || '' },
    { title: '期末余额', width: 140, align: 'right', render: (_, r) => formatMoney(r.assetClosingAmount) },
    { title: '年初余额', width: 140, align: 'right', render: (_, r) => formatMoney(r.assetOpeningAmount) },
    {
      title: '负债和所有者权益', dataIndex: 'liabilityName', width: 250,
      render: (text: string, row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={itemClass(row.liabilityLevel, row.liabilityEditable, row.liabilityRowNo)}>{text}</span>
          {row.liabilityEditable && writable && permissions.includes('fms:report:balance-sheet:update') && (
            <Button type="link" size="small" icon={<EditOutlined/>} title="编辑公式" onClick={() => openFormula(row, false)}/>
          )}
        </div>
      )
    },
    { title: '行次', width: 64, align: 'center', render: (_, r) => r.liabilityRowNo || '' },
    { title: '期末余额', width: 140, align: 'right', render: (_, r) => formatMoney(r.liabilityClosingAmount) },
    { title: '年初余额', width: 140, align: 'right', render: (_, r) => formatMoney(r.liabilityOpeningAmount) }
  ]

  const canExport = permissions.includes('fms:report:balance-sheet:export')
  const canPrint = permissions.includes('fms:report:balance-sheet:print')

  return (
    <section className="workspace-page fms-page">
      {/* 搜索条 */}
      <div className="fms-search-area">
        <Space wrap>
          <DatePicker
            picker="month"
            value={dayjs(month)}
            onChange={d => { if (d) { const m = d.format('YYYY-MM'); setMonth(m); getList(m) } }}
            allowClear={false}
            format="YYYY年MM月"
          />
          <Button type="primary" onClick={() => getList()}>查询</Button>
          {canExport && (
            <Button icon={<DownloadOutlined/>} loading={exportLoading} onClick={handleExport}>导出</Button>
          )}
          {canPrint && (
            <Button icon={<PrinterOutlined/>} onClick={handlePrint} disabled={list.length === 0}>打印</Button>
          )}
        </Space>
      </div>

      {/* 表格 */}
      <div className="fms-table-area">
        <FmsReportCheckAlert result={check} reportType={1}/>
        <FmsProTable<FmsBalanceSheetRow>
          rowKey={(row, index) => `${row.rowId}-${index}`}
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={false}
          bordered
          size="small"
          scroll={{ x: 'max-content', y: 'calc(100vh - 300px)' }}
        />
      </div>

      <FmsReportFormulaModal open={formulaOpen} onClose={() => setFormulaOpen(false)} item={formulaItem} type="balance"/>
      <FmsPrintPreview open={printPreview.open} onClose={printPreview.close} html={printPreview.html} title="资产负债表打印预览"/>
    </section>
  )
}

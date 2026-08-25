import { useCallback, useRef, useState } from 'react'
import { Button, DatePicker, Space, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PrinterOutlined, EditOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsReport } from '../../services/fms/report'
import type { FmsReportItem } from '../../services/fms/types'
import { formatMoney, buildPeriodFilename } from '../../services/fms/format'
import { buildFmsTablePrintHtml, type FmsPrintColumn } from '../../services/fms/print'
import { saveBlob } from '../../services/download'
import FmsPrintPreview, { usePrintPreview } from '../../components/FmsPrintPreview'
import FmsReportCheckAlert, { type FmsReportCheckResult } from '../../components/fms/FmsReportCheckAlert'
import FmsReportFormulaModal, { type FmsReportFormulaTarget } from '../../components/fms/FmsReportFormulaModal'

export default function FmsReportIncomeStatementPage({ permissions }: { permissions: string[] }) {
  const { accountSet, currentMonth, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const now = dayjs().format('YYYY-MM')
  const [startMonth, setStartMonth] = useState(now)
  const [endMonth, setEndMonth] = useState(now)
  const [list, setList] = useState<FmsReportItem[]>([])
  const [check, setCheck] = useState<FmsReportCheckResult>()
  const [loading, setLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const [formulaOpen, setFormulaOpen] = useState(false)
  const [formulaItem, setFormulaItem] = useState<FmsReportFormulaTarget>()
  const listVersion = useRef(0)
  const printPreview = usePrintPreview()

  const getList = useCallback(async (params?: { startMonth?: string; endMonth?: string }) => {
    if (!accountSetId || !endMonth) {
      setList([])
      setCheck(undefined)
      return
    }
    const sm = params?.startMonth ?? startMonth
    const em = params?.endMonth ?? endMonth
    const v = ++listVersion.current
    setLoading(true)
    try {
      const reportParams = { accountSetId, startMonth: sm, endMonth: em }
      const rows = await fmsReport.incomeStatement.list(reportParams)
      const checkResult = await fmsReport.incomeStatement.check(reportParams)
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
    }
  }

  const handleExport = useCallback(async () => {
    if (!accountSetId) return
    setExportLoading(true)
    try {
      const blob = await fmsReport.incomeStatement.exportExcel({ accountSetId, startMonth, endMonth })
      saveBlob(blob, buildPeriodFilename('利润表', startMonth, endMonth))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, startMonth, endMonth])

  const handlePrint = useCallback(() => {
    if (!accountSet || list.length === 0) return
    const printColumns: FmsPrintColumn<FmsReportItem>[] = [
      { title: '项目', render: r => r.name },
      { title: '行次', align: 'center', render: r => String(r.rowNo) },
      { title: '本年累计金额', align: 'right', render: r => formatMoney(r.yearAmount) },
      { title: '本期金额', align: 'right', render: r => formatMoney(r.currentAmount) }
    ]
    const html = buildFmsTablePrintHtml({
      title: '利润表',
      companyName: accountSet.companyName,
      periodLabel: `${startMonth.replace(/^(\d{4})-(\d{2})$/, '$1年第$2期')} 至 ${endMonth.replace(/^(\d{4})-(\d{2})$/, '$1年第$2期')}`,
      columns: printColumns,
      rows: list
    })
    printPreview.show(html)
  }, [accountSet, list, startMonth, endMonth, printPreview])

  const openFormula = useCallback((item: FmsReportItem) => {
    setFormulaItem({ id: item.id, name: item.name, formula: item.formula })
    setFormulaOpen(true)
  }, [])

  const columns: ColumnsType<FmsReportItem> = [
    {
      title: '项目', dataIndex: 'name', width: 420,
      render: (text: string, row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={{ paddingLeft: row.level === 2 ? 20 : row.level === 3 ? 40 : 0, fontWeight: !row.editable ? 600 : 400 }}>{text}</span>
          {row.editable && writable && permissions.includes('fms:report:income-statement:update') && (
            <Button type="link" size="small" icon={<EditOutlined/>} title="编辑公式" onClick={() => openFormula(row)}/>
          )}
        </div>
      )
    },
    { title: '行次', dataIndex: 'rowNo', width: 90, align: 'center' },
    { title: '本年累计金额', dataIndex: 'yearAmount', width: 180, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '本期金额', dataIndex: 'currentAmount', width: 180, align: 'right', render: (v: number) => formatMoney(v) }
  ]

  const canExport = permissions.includes('fms:report:income-statement:export')
  const canPrint = permissions.includes('fms:report:income-statement:print')

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
                getList({ startMonth: sm, endMonth: em })
              }
            }}
            allowClear={false}
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
        <FmsReportCheckAlert result={check} reportType={2}/>
        <FmsProTable<FmsReportItem>
          rowKey="id"
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={false}
          bordered
          size="small"
          scroll={{ y: 'calc(100vh - 300px)' }}
        />
      </div>

      <FmsReportFormulaModal open={formulaOpen} onClose={() => setFormulaOpen(false)} item={formulaItem} type="income"/>
      <FmsPrintPreview open={printPreview.open} onClose={printPreview.close} html={printPreview.html} title="利润表打印预览"/>
    </section>
  )
}

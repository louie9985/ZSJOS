import { useCallback, useRef, useState } from 'react'
import { Button, DatePicker, Space, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PrinterOutlined, SearchOutlined, SortAscendingOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsLedger } from '../../services/fms/ledger'
import type { FmsSubjectBalance, FmsLedgerListParams } from '../../services/fms/types'
import { formatMoney, buildPeriodFilename, formatPeriodLabel } from '../../services/fms/format'
import { buildFmsTablePrintHtml, type FmsPrintColumn } from '../../services/fms/print'
import { saveBlob } from '../../services/download'
import FmsPrintPreview, { usePrintPreview } from '../../components/FmsPrintPreview'
import { APP_ROUTES } from '../../constants'

export default function FmsLedgerSubjectBalancePage({ permissions }: { permissions: string[] }) {
  const navigate = useNavigate()
  const { accountSet, currentMonth: providerMonth } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const now = dayjs().format('YYYY-MM')
  const [startMonth, setStartMonth] = useState(now)
  const [endMonth, setEndMonth] = useState(now)
  const [list, setList] = useState<FmsSubjectBalance[]>([])
  const [loading, setLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const [expandAll, setExpandAll] = useState(false)
  const [tableKey, setTableKey] = useState(0)
  const listVersion = useRef(0)
  const printPreview = usePrintPreview()

  const params = useCallback((): FmsLedgerListParams => ({
    accountSetId: accountSetId ?? 0,
    startMonth,
    endMonth,
    minLevel: 1,
    maxLevel: 8
  }), [accountSetId, startMonth, endMonth])

  const getList = useCallback(async () => {
    if (!accountSetId) { setList([]); return }
    const v = ++listVersion.current
    setLoading(true)
    try {
      const result = await fmsLedger.subjectBalance.list(params())
      if (v !== listVersion.current) return
      setList(result)
    } catch (e) {
      if (v !== listVersion.current) return
      message.error(e instanceof Error ? e.message : '查询失败')
      setList([])
    } finally {
      if (v === listVersion.current) setLoading(false)
    }
  }, [accountSetId, params])

  // 账套加载后回填当前月份
  const defaulted = useRef(false)
  if (providerMonth && !defaulted.current) {
    defaulted.current = true
    setStartMonth(providerMonth)
    setEndMonth(providerMonth)
  }

  // 账套切换时重载
  const lastAccountSetId = useRef<number | undefined>(undefined)
  if (accountSetId !== lastAccountSetId.current) {
    lastAccountSetId.current = accountSetId
    if (accountSetId) setTimeout(() => getList(), 0)
    else setList([])
  }

  const toggleExpandAll = useCallback(() => {
    setExpandAll(prev => !prev)
    setTableKey(k => k + 1)
  }, [])

  const handleExport = useCallback(async () => {
    if (!accountSetId) return
    setExportLoading(true)
    try {
      const blob = await fmsLedger.subjectBalance.exportExcel(params())
      saveBlob(blob, buildPeriodFilename('科目余额表', startMonth, endMonth))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, startMonth, endMonth, params])

  const handlePrint = useCallback(() => {
    if (!accountSet || list.length === 0) return
    const printColumns: FmsPrintColumn<FmsSubjectBalance>[] = [
      { title: '科目编码', align: 'center', render: r => r.subjectCode },
      { title: '科目名称', render: r => r.subjectName },
      { title: '级次', align: 'center', render: r => String(r.level) },
      { title: '期初借方', align: 'right', render: r => formatMoney(r.openingDebitAmount) },
      { title: '期初贷方', align: 'right', render: r => formatMoney(r.openingCreditAmount) },
      { title: '本期借方', align: 'right', render: r => formatMoney(r.periodDebitAmount) },
      { title: '本期贷方', align: 'right', render: r => formatMoney(r.periodCreditAmount) },
      { title: '期末借方', align: 'right', render: r => formatMoney(r.endingDebitAmount) },
      { title: '期末贷方', align: 'right', render: r => formatMoney(r.endingCreditAmount) },
      { title: '余额方向', align: 'center', render: r => r.endingBalanceDirection }
    ]
    const html = buildFmsTablePrintHtml({
      title: '科目余额表',
      companyName: accountSet.companyName,
      periodLabel: formatPeriodLabel(startMonth, endMonth),
      columns: printColumns,
      rows: list
    })
    printPreview.show(html)
  }, [accountSet, list, startMonth, endMonth, printPreview])

  const openDetail = useCallback((row: FmsSubjectBalance) => {
    navigate(`${APP_ROUTES.FMS_LEDGER_DETAIL}?subjectId=${row.subjectId}&startMonth=${startMonth}&endMonth=${endMonth}`)
  }, [navigate, startMonth, endMonth])

  const columns: ColumnsType<FmsSubjectBalance> = [
    { title: '科目编码', dataIndex: 'subjectCode', width: 130,
      render: (v: string, row) => <Button type="link" size="small" onClick={() => openDetail(row)}>{v}</Button> },
    { title: '科目名称', dataIndex: 'subjectName', ellipsis: true },
    { title: '级次', dataIndex: 'level', width: 70, align: 'center' },
    { title: '期初借方', dataIndex: 'openingDebitAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '期初贷方', dataIndex: 'openingCreditAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '本期借方', dataIndex: 'periodDebitAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '本期贷方', dataIndex: 'periodCreditAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '期末借方', dataIndex: 'endingDebitAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '期末贷方', dataIndex: 'endingCreditAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '余额方向', dataIndex: 'endingBalanceDirection', width: 90, align: 'center' }
  ]

  const canExport = permissions.includes('fms:ledger:subject-balance:export')
  const canPrint = permissions.includes('fms:ledger:subject-balance:print')

  return (
    <section className="workspace-page fms-page">
      <div className="fms-search-area">
        <Space wrap>
          <DatePicker.RangePicker
            picker="month"
            value={[dayjs(startMonth), dayjs(endMonth)]}
            onChange={dates => {
              if (dates?.[0] && dates?.[1]) {
                setStartMonth(dates[0].format('YYYY-MM'))
                setEndMonth(dates[1].format('YYYY-MM'))
              }
            }}
            allowClear={false}
          />
          <Button type="primary" icon={<SearchOutlined/>} onClick={getList}>查询</Button>
          <Button icon={<SortAscendingOutlined/>} onClick={toggleExpandAll}>展开/折叠</Button>
          {canExport && (
            <Button icon={<DownloadOutlined/>} loading={exportLoading} onClick={handleExport}>导出</Button>
          )}
          {canPrint && (
            <Button icon={<PrinterOutlined/>} onClick={handlePrint} disabled={list.length === 0}>打印</Button>
          )}
        </Space>
      </div>
      <div className="fms-table-area">
        <FmsProTable<FmsSubjectBalance>
          key={tableKey}
          rowKey={row => row.nodeKey}
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={false}
          bordered
          size="small"
          scroll={{ y: 'calc(100vh - 300px)' }}
          expandable={{ childrenColumnName: 'children', defaultExpandAllRows: expandAll }}
        />
      </div>
      <FmsPrintPreview open={printPreview.open} onClose={printPreview.close} html={printPreview.html} title="科目余额表打印预览"/>
    </section>
  )
}

import { useCallback, useMemo, useRef, useState } from 'react'
import { Button, DatePicker, Space, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PrinterOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsLedger } from '../../services/fms/ledger'
import type { FmsLedgerGeneral, FmsLedgerListParams } from '../../services/fms/types'
import { formatMoney, buildPeriodFilename, formatPeriodLabel } from '../../services/fms/format'
import { buildFmsTablePrintHtml, type FmsPrintColumn } from '../../services/fms/print'
import { saveBlob } from '../../services/download'
import FmsPrintPreview, { usePrintPreview } from '../../components/FmsPrintPreview'
import { APP_ROUTES } from '../../constants'

export default function FmsLedgerGeneralPage({ permissions }: { permissions: string[] }) {
  const navigate = useNavigate()
  const { accountSet, currentMonth } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  // 查询参数
  const now = dayjs().format('YYYY-MM')
  const [startMonth, setStartMonth] = useState(now)
  const [endMonth, setEndMonth] = useState(now)
  const [list, setList] = useState<FmsLedgerGeneral[]>([])
  const [loading, setLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const listVersion = useRef(0)
  const printPreview = usePrintPreview()

  // 当 currentMonth 从 Provider 加载后更新期间
  const initialized = useRef(false)
  if (currentMonth && !initialized.current) {
    initialized.current = true
    // 只在第一次设定，之后由用户操作驱动
    setStartMonth(currentMonth)
    setEndMonth(currentMonth)
  }

  const getList = useCallback(async (params?: Partial<FmsLedgerListParams>) => {
    if (!accountSetId) { setList([]); return }
    const version = ++listVersion.current
    setLoading(true)
    try {
      const result = await fmsLedger.general.list({
        accountSetId,
        startMonth: params?.startMonth ?? startMonth,
        endMonth: params?.endMonth ?? endMonth,
        minLevel: 1,
        maxLevel: 1,
        ...params
      })
      if (version !== listVersion.current) return
      setList(result)
    } catch (e) {
      if (version !== listVersion.current) return
      message.error(e instanceof Error ? e.message : '查询失败')
      setList([])
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [accountSetId, startMonth, endMonth])

  // 账套切换时重载
  const lastAccountSetId = useRef<number | undefined>(undefined)
  if (accountSetId !== lastAccountSetId.current) {
    lastAccountSetId.current = accountSetId
    if (accountSetId) {
      // 延一个 tick 让 currentMonth 先回来
      setTimeout(() => getList(), 0)
    } else {
      setList([])
    }
  }

  // 导出
  const handleExport = useCallback(async () => {
    if (!accountSetId) return
    setExportLoading(true)
    try {
      const blob = await fmsLedger.general.exportExcel({
        accountSetId, startMonth, endMonth, minLevel: 1, maxLevel: 1
      })
      saveBlob(blob, buildPeriodFilename('总账', startMonth, endMonth))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, startMonth, endMonth])

  // 打印
  const handlePrint = useCallback(() => {
    if (!accountSet || list.length === 0) return
    const printColumns: FmsPrintColumn<FmsLedgerGeneral>[] = [
      { title: '科目编码', align: 'center', render: r => r.subjectCode, width: '125px', rowSpan: computeRowSpan },
      { title: '科目名称', render: r => r.subjectName, rowSpan: computeRowSpan },
      { title: '期间', align: 'center', render: r => r.period },
      { title: '摘要', render: r => r.digest },
      { title: '借方', align: 'right', render: r => formatMoney(r.debitAmount) },
      { title: '贷方', align: 'right', render: r => formatMoney(r.creditAmount) },
      { title: '方向', align: 'center', render: r => r.balanceDirection },
      { title: '余额', align: 'right', render: r => formatMoney(r.balance) }
    ]
    const html = buildFmsTablePrintHtml({
      title: '总账',
      companyName: accountSet.companyName,
      periodLabel: formatPeriodLabel(startMonth, endMonth),
      columns: printColumns,
      rows: list
    })
    printPreview.show(html)
  }, [accountSet, list, startMonth, endMonth, printPreview])

  // 合并科目列的 rowSpan
  function computeRowSpan(row: FmsLedgerGeneral, index: number, rows: FmsLedgerGeneral[]) {
    if (index > 0 && rows[index - 1]?.subjectId === row.subjectId) return 0
    let span = 1
    while (rows[index + span]?.subjectId === row.subjectId) span++
    return span
  }

  // 点击科目编码跳明细账
  const openDetail = useCallback((row: FmsLedgerGeneral) => {
    navigate(`${APP_ROUTES.FMS_LEDGER_DETAIL}?subjectId=${row.subjectId}&startMonth=${startMonth}&endMonth=${endMonth}`)
  }, [navigate, startMonth, endMonth])

  // antd 表格列
  const columns: ColumnsType<FmsLedgerGeneral> = useMemo(() => [
    {
      title: '科目编码', dataIndex: 'subjectCode', width: 125, align: 'center',
      onCell: (_, index) => ({ rowSpan: index != null ? computeRowSpan(list[index], index, list) : 1 }),
      render: (text: string, row) => <Button type="link" size="small" onClick={() => openDetail(row)}>{text}</Button>
    },
    {
      title: '科目名称', dataIndex: 'subjectName', ellipsis: true,
      onCell: (_, index) => ({ rowSpan: index != null ? computeRowSpan(list[index], index, list) : 1 })
    },
    { title: '期间', dataIndex: 'period', width: 100, align: 'center' },
    { title: '摘要', dataIndex: 'digest', ellipsis: true },
    { title: '借方', dataIndex: 'debitAmount', width: 140, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '贷方', dataIndex: 'creditAmount', width: 140, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '方向', dataIndex: 'balanceDirection', width: 80, align: 'center' },
    { title: '余额', dataIndex: 'balance', width: 150, align: 'right', render: (v: number) => formatMoney(v) }
  ], [list, openDetail])

  const canExport = permissions.includes('fms:ledger:general:export')
  const canPrint = permissions.includes('fms:ledger:general:print')

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
                setStartMonth(dates[0].format('YYYY-MM'))
                setEndMonth(dates[1].format('YYYY-MM'))
              }
            }}
            allowClear={false}
          />
          <Button type="primary" icon={<SearchOutlined/>} onClick={() => getList()}>查询</Button>
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
        <FmsProTable<FmsLedgerGeneral>
          rowKey={(row, index) => `${row.subjectId}-${index}`}
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={false}
          bordered
          size="small"
          scroll={{ y: 'calc(100vh - 300px)' }}
        />
      </div>

      <FmsPrintPreview open={printPreview.open} onClose={printPreview.close} html={printPreview.html} title="总账打印预览"/>
    </section>
  )
}

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button, DatePicker, Select, Space, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PrinterOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import type { ColumnsType, ColumnType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsLedger } from '../../services/fms/ledger'
import { fmsConfig } from '../../services/fms/config'
import type { FmsMultiColumn, FmsLedgerDetail, FmsLedgerListParams, FmsSubjectVO, FmsMultiColumnSubject } from '../../services/fms/types'
import { formatMoney, buildPeriodFilename, formatPeriodLabel } from '../../services/fms/format'
import { buildFmsTablePrintHtml, type FmsPrintColumn } from '../../services/fms/print'
import { saveBlob } from '../../services/download'
import FmsPrintPreview, { usePrintPreview } from '../../components/FmsPrintPreview'

const FMS_DEBIT_DIRECTION = 1

/** 过滤出含下级科目的科目树，作为多栏账可选科目 */
function filterParentSubjects(items: FmsSubjectVO[]): FmsSubjectVO[] {
  return items.flatMap(item =>
    item.children?.length ? [{ ...item, children: filterParentSubjects(item.children) }] : []
  )
}

function flattenTree(items: FmsSubjectVO[]): FmsSubjectVO[] {
  return items.flatMap(s => [s, ...flattenTree(s.children || [])])
}

export default function FmsLedgerMultiColumnPage({ permissions }: { permissions: string[] }) {
  const navigate = useNavigate()
  const { accountSet, currentMonth: providerMonth } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const now = dayjs().format('YYYY-MM')
  const [startMonth, setStartMonth] = useState(now)
  const [endMonth, setEndMonth] = useState(now)
  const [subjectId, setSubjectId] = useState<number | undefined>(undefined)
  const [subjects, setSubjects] = useState<FmsSubjectVO[]>([])
  const [result, setResult] = useState<FmsMultiColumn>({ columns: [], rows: [] })
  const [loading, setLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const listVersion = useRef(0)
  const printPreview = usePrintPreview()

  const flatSubjects = useMemo(() => flattenTree(subjects), [subjects])

  const params = useCallback((): FmsLedgerListParams => ({
    accountSetId: accountSetId ?? 0,
    startMonth,
    endMonth,
    subjectId
  }), [accountSetId, startMonth, endMonth, subjectId])

  const getList = useCallback(async () => {
    if (!accountSetId || !subjectId) { setResult({ columns: [], rows: [] }); return }
    const v = ++listVersion.current
    setLoading(true)
    try {
      const data = await fmsLedger.multiColumn.list(params())
      if (v !== listVersion.current) return
      setResult(data)
    } catch (e) {
      if (v !== listVersion.current) return
      message.error(e instanceof Error ? e.message : '查询失败')
      setResult({ columns: [], rows: [] })
    } finally {
      if (v === listVersion.current) setLoading(false)
    }
  }, [accountSetId, subjectId, params])

  const loadSubjects = useCallback(async (asId: number) => {
    if (!asId) { setSubjects([]); return }
    try {
      const all = await fmsConfig.subject.simpleList(asId)
      const filtered = filterParentSubjects(all)
      setSubjects(filtered)
      const first = flattenTree(filtered)[0]?.id
      setSubjectId(first)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '科目加载失败')
      setSubjects([])
    }
  }, [])

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
    setResult({ columns: [], rows: [] })
    if (accountSetId) void loadSubjects(accountSetId)
    else setSubjects([])
  }

  // 科目变化（含默认选中）后查询
  useEffect(() => {
    if (accountSetId && subjectId) void getList()
  }, [accountSetId, subjectId, getList])

  const handleExport = useCallback(async () => {
    if (!accountSetId || !subjectId) return
    setExportLoading(true)
    try {
      const blob = await fmsLedger.multiColumn.exportExcel(params())
      saveBlob(blob, buildPeriodFilename('多栏账', startMonth, endMonth))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, subjectId, startMonth, endMonth, params])

  const handlePrint = useCallback(() => {
    if (!accountSet || result.rows.length === 0) return
    const printColumns: FmsPrintColumn<FmsLedgerDetail>[] = [
      { title: '日期', align: 'center', render: r => r.accountDate },
      { title: '凭证字号', align: 'center', render: r => r.voucherNumber || '' },
      { title: '摘要', render: r => r.digest },
      { title: '借方', align: 'right', render: r => formatMoney(r.debitAmount) },
      { title: '贷方', align: 'right', render: r => formatMoney(r.creditAmount) },
      { title: '方向', align: 'center', render: r => r.balanceDirection },
      { title: '余额', align: 'right', render: r => formatMoney(r.balance) },
      ...result.columns.map(col => ({
        title: `${col.subjectCode}/${col.subjectName}`,
        align: 'right' as const,
        render: (r: FmsLedgerDetail) => formatMoney(r.columnAmounts?.[col.subjectId])
      }))
    ]
    const html = buildFmsTablePrintHtml({
      title: '多栏账',
      companyName: accountSet.companyName,
      periodLabel: formatPeriodLabel(startMonth, endMonth),
      columns: printColumns,
      rows: result.rows
    })
    printPreview.show(html)
  }, [accountSet, result, startMonth, endMonth, printPreview])

  function columnColumn(col: FmsMultiColumnSubject): ColumnType<FmsLedgerDetail> {
    return {
      title: `${col.subjectCode}/${col.subjectName}`,
      key: `col-${col.subjectId}`,
      width: 145,
      align: 'right',
      render: (_, row) => formatMoney(row.columnAmounts?.[col.subjectId])
    }
  }

  const debitColumns = result.columns.filter(col => col.balanceDirection === FMS_DEBIT_DIRECTION)
  const creditColumns = result.columns.filter(col => col.balanceDirection !== FMS_DEBIT_DIRECTION)
  const debitCols = debitColumns.map(columnColumn)
  const creditCols = creditColumns.map(columnColumn)

  const columns: ColumnsType<FmsLedgerDetail> = [
    { title: '日期', dataIndex: 'accountDate', width: 110, align: 'center', fixed: 'left' },
    { title: '凭证字号', dataIndex: 'voucherNumber', width: 110, align: 'center', fixed: 'left',
      render: (text: string, row) => row.voucherId ? <Button type="link" size="small" onClick={() => navigate(`/fms/voucher/create?id=${row.voucherId}`)}>{text}</Button> : text },
    { title: '摘要', dataIndex: 'digest', ellipsis: true, fixed: 'left' },
    { title: '借方', dataIndex: 'debitAmount', width: 125, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '贷方', dataIndex: 'creditAmount', width: 125, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '方向', dataIndex: 'balanceDirection', width: 70, align: 'center' },
    { title: '余额', dataIndex: 'balance', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    ...(debitCols.length ? [{ title: '借方专栏', children: debitCols }] : []),
    ...(creditCols.length ? [{ title: '贷方专栏', children: creditCols }] : [])
  ]

  const canExport = permissions.includes('fms:ledger:multi-column:export')
  const canPrint = permissions.includes('fms:ledger:multi-column:print')

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
          <Select
            placeholder="选择科目"
            value={subjectId}
            onChange={v => setSubjectId(v)}
            style={{ width: 220 }}
            options={flatSubjects.map(s => ({ label: `${s.code} ${s.name}`, value: s.id }))}
          />
          <Button type="primary" icon={<SearchOutlined/>} onClick={getList}>查询</Button>
          {canExport && (
            <Button icon={<DownloadOutlined/>} loading={exportLoading} onClick={handleExport}>导出</Button>
          )}
          {canPrint && (
            <Button icon={<PrinterOutlined/>} onClick={handlePrint} disabled={result.rows.length === 0}>打印</Button>
          )}
        </Space>
      </div>
      <div className="fms-table-area">
        <FmsProTable<FmsLedgerDetail>
          rowKey={(row, index) => `${row.entryId ?? row.subjectId}-${index}`}
          columns={columns}
          dataSource={result.rows}
          loading={loading}
          pagination={false}
          bordered
          size="small"
          scroll={{ x: 'max-content', y: 'calc(100vh - 300px)' }}
        />
      </div>
      <FmsPrintPreview open={printPreview.open} onClose={printPreview.close} html={printPreview.html} title="多栏账打印预览"/>
    </section>
  )
}

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button, DatePicker, Select, Space, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PrinterOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsLedger } from '../../services/fms/ledger'
import { fmsConfig } from '../../services/fms/config'
import type { FmsLedgerDetail, FmsLedgerListParams, FmsSubjectVO } from '../../services/fms/types'
import { formatMoney, buildPeriodFilename, formatPeriodLabel } from '../../services/fms/format'
import { buildFmsTablePrintHtml, type FmsPrintColumn } from '../../services/fms/print'
import { saveBlob } from '../../services/download'
import FmsPrintPreview, { usePrintPreview } from '../../components/FmsPrintPreview'

/** 过滤出数量核算科目，保留数量核算父级供选择 */
function filterQuantitySubjects(subjects: FmsSubjectVO[]): FmsSubjectVO[] {
  return subjects.flatMap(subject => {
    const children = filterQuantitySubjects(subject.children || [])
    return subject.quantityAccounting ? [{ ...subject, children }] : children
  })
}

function flattenQuantitySubjects(subjects: FmsSubjectVO[]): FmsSubjectVO[] {
  return subjects.flatMap(s => [s, ...flattenQuantitySubjects(s.children || [])])
}

export default function FmsLedgerQuantityDetailPage({ permissions }: { permissions: string[] }) {
  const navigate = useNavigate()
  const { accountSet, currentMonth: providerMonth } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const now = dayjs().format('YYYY-MM')
  const [startMonth, setStartMonth] = useState(now)
  const [endMonth, setEndMonth] = useState(now)
  const [subjectId, setSubjectId] = useState<number | undefined>(undefined)
  const [subjects, setSubjects] = useState<FmsSubjectVO[]>([])
  const [list, setList] = useState<FmsLedgerDetail[]>([])
  const [loading, setLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const listVersion = useRef(0)
  const printPreview = usePrintPreview()

  const flatSubjects = useMemo(() => flattenQuantitySubjects(subjects), [subjects])

  const params = useCallback((): FmsLedgerListParams => ({
    accountSetId: accountSetId ?? 0,
    startMonth,
    endMonth,
    subjectId
  }), [accountSetId, startMonth, endMonth, subjectId])

  const getList = useCallback(async () => {
    if (!accountSetId || !subjectId) { setList([]); return }
    const v = ++listVersion.current
    setLoading(true)
    try {
      const result = await fmsLedger.quantityDetail.list(params())
      if (v !== listVersion.current) return
      setList(result)
    } catch (e) {
      if (v !== listVersion.current) return
      message.error(e instanceof Error ? e.message : '查询失败')
      setList([])
    } finally {
      if (v === listVersion.current) setLoading(false)
    }
  }, [accountSetId, subjectId, params])

  const loadSubjects = useCallback(async (asId: number) => {
    if (!asId) { setSubjects([]); return }
    try {
      const result = await fmsConfig.subject.simpleList(asId)
      const filtered = filterQuantitySubjects(result)
      setSubjects(filtered)
      const first = flattenQuantitySubjects(filtered)[0]?.id
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
    setList([])
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
      const blob = await fmsLedger.quantityDetail.exportExcel(params())
      saveBlob(blob, buildPeriodFilename('数量金额明细账', startMonth, endMonth))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, subjectId, startMonth, endMonth, params])

  const handlePrint = useCallback(() => {
    if (!accountSet || list.length === 0) return
    const printColumns: FmsPrintColumn<FmsLedgerDetail>[] = [
      { title: '日期', align: 'center', render: r => r.accountDate },
      { title: '凭证字号', align: 'center', render: r => r.voucherNumber || '' },
      { title: '摘要', render: r => r.digest },
      { title: '借方数量', align: 'right', render: r => formatMoney(r.debitQuantity) },
      { title: '借方单价', align: 'right', render: r => formatMoney(debitUnitPrice(r)) },
      { title: '借方金额', align: 'right', render: r => formatMoney(r.debitAmount) },
      { title: '贷方数量', align: 'right', render: r => formatMoney(r.creditQuantity) },
      { title: '贷方单价', align: 'right', render: r => formatMoney(creditUnitPrice(r)) },
      { title: '贷方金额', align: 'right', render: r => formatMoney(r.creditAmount) },
      { title: '方向', align: 'center', render: r => r.balanceDirection },
      { title: '余额数量', align: 'right', render: r => formatMoney(r.balanceQuantity) },
      { title: '余额单价', align: 'right', render: r => formatMoney(balanceUnitPrice(r)) },
      { title: '余额金额', align: 'right', render: r => formatMoney(r.balance) }
    ]
    const html = buildFmsTablePrintHtml({
      title: '数量金额明细账',
      companyName: accountSet.companyName,
      periodLabel: formatPeriodLabel(startMonth, endMonth),
      columns: printColumns,
      rows: list
    })
    printPreview.show(html)
  }, [accountSet, list, startMonth, endMonth, printPreview])

  function debitUnitPrice(row: FmsLedgerDetail) {
    return row.debitQuantity ? row.debitAmount / row.debitQuantity : undefined
  }
  function creditUnitPrice(row: FmsLedgerDetail) {
    return row.creditQuantity ? row.creditAmount / row.creditQuantity : undefined
  }
  function balanceUnitPrice(row: FmsLedgerDetail) {
    return row.balanceQuantity ? row.balance / row.balanceQuantity : undefined
  }

  const columns: ColumnsType<FmsLedgerDetail> = [
    { title: '日期', dataIndex: 'accountDate', width: 110, align: 'center' },
    { title: '凭证字号', dataIndex: 'voucherNumber', width: 110, align: 'center',
      render: (text: string, row) => row.voucherId ? <Button type="link" size="small" onClick={() => navigate(`/fms/voucher/create?id=${row.voucherId}`)}>{text}</Button> : text },
    { title: '摘要', dataIndex: 'digest', ellipsis: true },
    { title: '借方数量', dataIndex: 'debitQuantity', width: 110, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '借方单价', width: 110, align: 'right', render: (v, row) => formatMoney(debitUnitPrice(row as FmsLedgerDetail)) },
    { title: '借方金额', dataIndex: 'debitAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '贷方数量', dataIndex: 'creditQuantity', width: 110, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '贷方单价', width: 110, align: 'right', render: (v, row) => formatMoney(creditUnitPrice(row as FmsLedgerDetail)) },
    { title: '贷方金额', dataIndex: 'creditAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '方向', dataIndex: 'balanceDirection', width: 70, align: 'center' },
    { title: '余额数量', dataIndex: 'balanceQuantity', width: 110, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '余额单价', width: 110, align: 'right', render: (v, row) => formatMoney(balanceUnitPrice(row as FmsLedgerDetail)) },
    { title: '余额金额', dataIndex: 'balance', width: 130, align: 'right', render: (v: number) => formatMoney(v) }
  ]

  const canExport = permissions.includes('fms:ledger:detail:export')
  const canPrint = permissions.includes('fms:ledger:detail:print')

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
            placeholder="数量核算科目"
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
            <Button icon={<PrinterOutlined/>} onClick={handlePrint} disabled={list.length === 0}>打印</Button>
          )}
        </Space>
      </div>
      <div className="fms-table-area">
          <FmsProTable<FmsLedgerDetail>
          rowKey={(row, index) => `${row.entryId ?? row.subjectId}-${index}`}
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={false}
          bordered
          size="small"
          scroll={{ x: 'max-content', y: 'calc(100vh - 300px)' }}
        />
      </div>
      <FmsPrintPreview open={printPreview.open} onClose={printPreview.close} html={printPreview.html} title="数量金额明细账打印预览"/>
    </section>
  )
}

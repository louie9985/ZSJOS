import { useCallback, useRef, useState } from 'react'
import { Button, DatePicker, Select, Space, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PrinterOutlined, SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsLedger } from '../../services/fms/ledger'
import { fmsConfig } from '../../services/fms/config'
import type {
  FmsLedgerAuxiliaryBalance,
  FmsLedgerAuxiliaryListParams,
  FmsAuxiliaryTypeVO,
  FmsSubjectVO
} from '../../services/fms/types'
import { formatMoney, buildPeriodFilename, formatPeriodLabel } from '../../services/fms/format'
import { buildFmsTablePrintHtml, type FmsPrintColumn } from '../../services/fms/print'
import { saveBlob } from '../../services/download'
import FmsPrintPreview, { usePrintPreview } from '../../components/FmsPrintPreview'

export default function FmsLedgerAuxiliaryBalancePage({ permissions }: { permissions: string[] }) {
  const { accountSet, currentMonth: providerMonth } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const now = dayjs().format('YYYY-MM')
  const [startMonth, setStartMonth] = useState(now)
  const [endMonth, setEndMonth] = useState(now)
  const [auxiliaryTypeId, setAuxiliaryTypeId] = useState<number | undefined>(undefined)
  const [subjectId, setSubjectId] = useState<number | undefined>(undefined)
  const [types, setTypes] = useState<FmsAuxiliaryTypeVO[]>([])
  const [subjects, setSubjects] = useState<FmsSubjectVO[]>([])
  const [list, setList] = useState<FmsLedgerAuxiliaryBalance[]>([])
  const [loading, setLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const listVersion = useRef(0)
  const printPreview = usePrintPreview()

  const params = useCallback((): FmsLedgerAuxiliaryListParams => ({
    accountSetId: accountSetId ?? 0,
    startMonth,
    endMonth,
    auxiliaryTypeId: auxiliaryTypeId ?? 0,
    subjectId
  }), [accountSetId, startMonth, endMonth, auxiliaryTypeId, subjectId])

  const getList = useCallback(async () => {
    if (!accountSetId || !auxiliaryTypeId) { setList([]); return }
    const v = ++listVersion.current
    setLoading(true)
    try {
      const result = await fmsLedger.auxiliaryBalance.list(params())
      if (v !== listVersion.current) return
      setList(result)
    } catch (e) {
      if (v !== listVersion.current) return
      message.error(e instanceof Error ? e.message : '查询失败')
      setList([])
    } finally {
      if (v === listVersion.current) setLoading(false)
    }
  }, [accountSetId, auxiliaryTypeId, params])

  const loadTypeOptions = useCallback(async (asId: number) => {
    if (!asId) { setTypes([]); return }
    try {
      const result = await fmsConfig.auxiliaryType.list(asId)
      setTypes(result)
      setAuxiliaryTypeId(prev => result.some(t => t.id === prev) ? prev : result[0]?.id)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '辅助核算类别加载失败')
      setTypes([])
    }
  }, [])

  const loadSubjects = useCallback(async (asId: number) => {
    if (!asId) { setSubjects([]); return }
    try {
      const result = await fmsConfig.subject.simpleList(asId)
      setSubjects(result)
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
    setSubjectId(undefined)
    if (accountSetId) {
      void loadTypeOptions(accountSetId)
      void loadSubjects(accountSetId)
    } else {
      setTypes([])
      setSubjects([])
    }
  }

  const handleSearch = useCallback(() => {
    void getList()
  }, [getList])

  const handleExport = useCallback(async () => {
    if (!accountSetId || !auxiliaryTypeId) return
    setExportLoading(true)
    try {
      const blob = await fmsLedger.auxiliaryBalance.exportExcel(params())
      saveBlob(blob, buildPeriodFilename('核算项目余额表', startMonth, endMonth))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, auxiliaryTypeId, startMonth, endMonth, params])

  const handlePrint = useCallback(() => {
    if (!accountSet || list.length === 0) return
    const printColumns: FmsPrintColumn<FmsLedgerAuxiliaryBalance>[] = [
      { title: '编码', align: 'center', render: r => r.code },
      { title: '项目名称', render: r => r.name },
      { title: '期初借方', align: 'right', render: r => formatMoney(r.openingDebitAmount) },
      { title: '期初贷方', align: 'right', render: r => formatMoney(r.openingCreditAmount) },
      { title: '本期借方', align: 'right', render: r => formatMoney(r.periodDebitAmount) },
      { title: '本期贷方', align: 'right', render: r => formatMoney(r.periodCreditAmount) },
      { title: '本年借方', align: 'right', render: r => formatMoney(r.yearDebitAmount) },
      { title: '本年贷方', align: 'right', render: r => formatMoney(r.yearCreditAmount) },
      { title: '期末借方', align: 'right', render: r => formatMoney(r.endingDebitAmount) },
      { title: '期末贷方', align: 'right', render: r => formatMoney(r.endingCreditAmount) }
    ]
    const html = buildFmsTablePrintHtml({
      title: '核算项目余额表',
      companyName: accountSet.companyName,
      periodLabel: formatPeriodLabel(startMonth, endMonth),
      columns: printColumns,
      rows: list
    })
    printPreview.show(html)
  }, [accountSet, list, startMonth, endMonth, printPreview])

  const columns: ColumnsType<FmsLedgerAuxiliaryBalance> = [
    { title: '编码', dataIndex: 'code', width: 120 },
    { title: '项目名称', dataIndex: 'name', ellipsis: true },
    { title: '期初借方', dataIndex: 'openingDebitAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '期初贷方', dataIndex: 'openingCreditAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '本期借方', dataIndex: 'periodDebitAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '本期贷方', dataIndex: 'periodCreditAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '本年借方', dataIndex: 'yearDebitAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '本年贷方', dataIndex: 'yearCreditAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '期末借方', dataIndex: 'endingDebitAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '期末贷方', dataIndex: 'endingCreditAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) }
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
          <Select
            placeholder="辅助核算类别"
            value={auxiliaryTypeId}
            onChange={v => setAuxiliaryTypeId(v)}
            style={{ width: 180 }}
            options={types.map(t => ({ label: t.name, value: t.id! }))}
          />
          <Select
            placeholder="全部科目"
            value={subjectId}
            onChange={v => setSubjectId(v)}
            allowClear
            style={{ width: 200 }}
            options={subjects.map(s => ({ label: `${s.code} ${s.name}`, value: s.id }))}
          />
          <Button type="primary" icon={<SearchOutlined/>} onClick={handleSearch}>查询</Button>
          {canExport && (
            <Button icon={<DownloadOutlined/>} loading={exportLoading} onClick={handleExport}>导出</Button>
          )}
          {canPrint && (
            <Button icon={<PrinterOutlined/>} onClick={handlePrint} disabled={list.length === 0}>打印</Button>
          )}
        </Space>
      </div>
      <div className="fms-table-area">
        <FmsProTable<FmsLedgerAuxiliaryBalance>
          rowKey={row => row.auxiliaryItemId}
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={false}
          bordered
          size="small"
          scroll={{ y: 'calc(100vh - 300px)' }}
        />
      </div>
      <FmsPrintPreview open={printPreview.open} onClose={printPreview.close} html={printPreview.html} title="核算项目余额表打印预览"/>
    </section>
  )
}

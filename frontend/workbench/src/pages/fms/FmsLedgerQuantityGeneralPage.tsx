import { useCallback, useRef, useState } from 'react'
import { Button, DatePicker, Space, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PrinterOutlined, SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsLedger } from '../../services/fms/ledger'
import type { FmsSubjectBalance, FmsLedgerListParams } from '../../services/fms/types'
import { formatMoney, formatQuantity, buildPeriodFilename, formatPeriodLabel } from '../../services/fms/format'
import { buildFmsTablePrintHtml, type FmsPrintColumn } from '../../services/fms/print'
import { saveBlob } from '../../services/download'
import FmsPrintPreview, { usePrintPreview } from '../../components/FmsPrintPreview'

/** 将余额树拍平成扁平列表，供 rowSpan 合并科目列 */
function treeToList(tree: FmsSubjectBalance[]): FmsSubjectBalance[] {
  return tree.flatMap(node => [node, ...treeToList(node.children || [])])
}

export default function FmsLedgerQuantityGeneralPage({ permissions }: { permissions: string[] }) {
  const { accountSet, currentMonth: providerMonth } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const now = dayjs().format('YYYY-MM')
  const [startMonth, setStartMonth] = useState(now)
  const [endMonth, setEndMonth] = useState(now)
  const [list, setList] = useState<FmsSubjectBalance[]>([])
  const [loading, setLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
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
      const result = await fmsLedger.quantityGeneral.list(params())
      if (v !== listVersion.current) return
      setList(treeToList(result))
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

  const handleExport = useCallback(async () => {
    if (!accountSetId) return
    setExportLoading(true)
    try {
      const blob = await fmsLedger.quantityGeneral.exportExcel(params())
      saveBlob(blob, buildPeriodFilename('数量金额总账', startMonth, endMonth))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, startMonth, endMonth, params])

  const handlePrint = useCallback(() => {
    if (!accountSet || list.length === 0) return
    const printColumns: FmsPrintColumn<FmsSubjectBalance>[] = [
      { title: '科目编码', align: 'center', render: r => r.subjectCode, rowSpan: computeRowSpan },
      { title: '科目名称', render: r => r.subjectName, rowSpan: computeRowSpan },
      { title: '单位', align: 'center', render: r => r.quantityUnit || '' },
      { title: '期初方向', align: 'center', render: r => r.openingBalanceDirection },
      { title: '期初数量', align: 'right', render: r => formatQuantity(r.openingQuantity) },
      { title: '期初单价', align: 'right', render: r => formatMoney(r.openingUnitPrice) },
      { title: '期初金额', align: 'right', render: r => formatMoney(r.openingDebitAmount || r.openingCreditAmount) },
      { title: '本期借方数量', align: 'right', render: r => formatQuantity(r.periodDebitQuantity) },
      { title: '本期借方金额', align: 'right', render: r => formatMoney(r.periodDebitAmount) },
      { title: '本期贷方数量', align: 'right', render: r => formatQuantity(r.periodCreditQuantity) },
      { title: '本期贷方金额', align: 'right', render: r => formatMoney(r.periodCreditAmount) },
      { title: '本期借方数量', align: 'right', render: r => formatQuantity(r.yearDebitQuantity) },
      { title: '本期贷方数量', align: 'right', render: r => formatQuantity(r.yearCreditQuantity) },
      { title: '期末方向', align: 'center', render: r => r.endingBalanceDirection },
      { title: '期末数量', align: 'right', render: r => formatQuantity(r.endingQuantity) },
      { title: '期末单价', align: 'right', render: r => formatMoney(r.endingUnitPrice) },
      { title: '期末金额', align: 'right', render: r => formatMoney(r.endingDebitAmount || r.endingCreditAmount) }
    ]
    const html = buildFmsTablePrintHtml({
      title: '数量金额总账',
      companyName: accountSet.companyName,
      periodLabel: formatPeriodLabel(startMonth, endMonth),
      columns: printColumns,
      rows: list
    })
    printPreview.show(html)
  }, [accountSet, list, startMonth, endMonth, printPreview])

  // 合并科目编码/名称列
  function computeRowSpan(row: FmsSubjectBalance, index: number, rows: FmsSubjectBalance[]) {
    if (index > 0 && rows[index - 1]?.subjectId === row.subjectId) return 0
    let span = 1
    while (rows[index + span]?.subjectId === row.subjectId) span++
    return span
  }

  const columns: ColumnsType<FmsSubjectBalance> = [
    { title: '科目编码', dataIndex: 'subjectCode', width: 120,
      onCell: (_, index) => ({ rowSpan: index != null ? computeRowSpan(list[index], index, list) : 1 }) },
    { title: '科目名称', dataIndex: 'subjectName', ellipsis: true,
      onCell: (_, index) => ({ rowSpan: index != null ? computeRowSpan(list[index], index, list) : 1 }) },
    { title: '单位', dataIndex: 'quantityUnit', width: 70, align: 'center' },
    { title: '期初方向', dataIndex: 'openingBalanceDirection', width: 80, align: 'center' },
    { title: '期初数量', dataIndex: 'openingQuantity', width: 110, align: 'right', render: (v: number, r) => formatQuantity(v, r.quantityAccounting) },
    { title: '期初单价', dataIndex: 'openingUnitPrice', width: 110, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '期初金额', dataIndex: 'openingDebitAmount', width: 130, align: 'right',
      render: (v: number, r) => formatMoney(r.openingDebitAmount || r.openingCreditAmount) },
    { title: '本期借方数量', dataIndex: 'periodDebitQuantity', width: 110, align: 'right', render: (v: number, r) => formatQuantity(v, r.quantityAccounting) },
    { title: '本期借方金额', dataIndex: 'periodDebitAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '本期贷方数量', dataIndex: 'periodCreditQuantity', width: 110, align: 'right', render: (v: number, r) => formatQuantity(v, r.quantityAccounting) },
    { title: '本期贷方金额', dataIndex: 'periodCreditAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '本年借方数量', dataIndex: 'yearDebitQuantity', width: 110, align: 'right', render: (v: number, r) => formatQuantity(v, r.quantityAccounting) },
    { title: '本年借方金额', dataIndex: 'yearDebitAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '本年贷方数量', dataIndex: 'yearCreditQuantity', width: 110, align: 'right', render: (v: number, r) => formatQuantity(v, r.quantityAccounting) },
    { title: '本年贷方金额', dataIndex: 'yearCreditAmount', width: 130, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '期末方向', dataIndex: 'endingBalanceDirection', width: 80, align: 'center' },
    { title: '期末数量', dataIndex: 'endingQuantity', width: 110, align: 'right', render: (v: number, r) => formatQuantity(v, r.quantityAccounting) },
    { title: '期末单价', dataIndex: 'endingUnitPrice', width: 110, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '期末金额', dataIndex: 'endingDebitAmount', width: 130, align: 'right',
      render: (v: number, r) => formatMoney(r.endingDebitAmount || r.endingCreditAmount) }
  ]

  const canExport = permissions.includes('fms:ledger:general:export')
  const canPrint = permissions.includes('fms:ledger:general:print')

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
          rowKey={row => row.nodeKey}
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={false}
          bordered
          size="small"
          scroll={{ x: 'max-content', y: 'calc(100vh - 300px)' }}
        />
      </div>
      <FmsPrintPreview open={printPreview.open} onClose={printPreview.close} html={printPreview.html} title="数量金额总账打印预览"/>
    </section>
  )
}

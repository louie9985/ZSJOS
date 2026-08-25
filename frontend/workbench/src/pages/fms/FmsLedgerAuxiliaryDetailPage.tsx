import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button, DatePicker, Input, Select, Space, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PrinterOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsLedger } from '../../services/fms/ledger'
import { fmsConfig } from '../../services/fms/config'
import type {
  FmsLedgerDetail,
  FmsLedgerAuxiliaryListParams,
  FmsAuxiliaryTypeVO,
  FmsAuxiliaryItemOptionVO
} from '../../services/fms/types'
import { formatMoney, buildPeriodFilename, formatPeriodLabel } from '../../services/fms/format'
import { buildFmsTablePrintHtml, type FmsPrintColumn } from '../../services/fms/print'
import { saveBlob } from '../../services/download'
import FmsPrintPreview, { usePrintPreview } from '../../components/FmsPrintPreview'

export default function FmsLedgerAuxiliaryDetailPage({ permissions }: { permissions: string[] }) {
  const navigate = useNavigate()
  const { accountSet, currentMonth: providerMonth } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const now = dayjs().format('YYYY-MM')
  const [startMonth, setStartMonth] = useState(now)
  const [endMonth, setEndMonth] = useState(now)
  const [auxiliaryTypeId, setAuxiliaryTypeId] = useState<number | undefined>(undefined)
  const [auxiliaryItemId, setAuxiliaryItemId] = useState<number | undefined>(undefined)
  const [types, setTypes] = useState<FmsAuxiliaryTypeVO[]>([])
  const [items, setItems] = useState<FmsAuxiliaryItemOptionVO[]>([])
  const [itemKeyword, setItemKeyword] = useState('')
  const [list, setList] = useState<FmsLedgerDetail[]>([])
  const [loading, setLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const listVersion = useRef(0)
  const printPreview = usePrintPreview()

  const params = useCallback((): FmsLedgerAuxiliaryListParams => ({
    accountSetId: accountSetId ?? 0,
    startMonth,
    endMonth,
    auxiliaryTypeId: auxiliaryTypeId ?? 0,
    auxiliaryItemId
  }), [accountSetId, startMonth, endMonth, auxiliaryTypeId, auxiliaryItemId])

  const getList = useCallback(async () => {
    if (!accountSetId || !auxiliaryItemId || !auxiliaryTypeId) { setList([]); return }
    const v = ++listVersion.current
    setLoading(true)
    try {
      const result = await fmsLedger.auxiliaryDetail.list(params())
      if (v !== listVersion.current) return
      setList(result)
    } catch (e) {
      if (v !== listVersion.current) return
      message.error(e instanceof Error ? e.message : '查询失败')
      setList([])
    } finally {
      if (v === listVersion.current) setLoading(false)
    }
  }, [accountSetId, auxiliaryItemId, auxiliaryTypeId, params])

  const loadTypeOptions = useCallback(async (asId: number) => {
    if (!asId) { setTypes([]); return }
    try {
      const result = await fmsConfig.auxiliaryType.list(asId)
      setTypes(result)
      const next = result.find(t => t.id === auxiliaryTypeId)?.id ?? result[0]?.id
      setAuxiliaryTypeId(next)
      setAuxiliaryItemId(undefined)
      setItems([])
      if (next) {
        try {
          const itemResult = await fmsConfig.auxiliaryItem.list(asId, next)
          setItems(itemResult)
        } catch (e) {
          message.error(e instanceof Error ? e.message : '辅助项目加载失败')
        }
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '辅助核算类别加载失败')
      setTypes([])
    }
  }, [auxiliaryTypeId])

  const handleTypeChange = useCallback(async (value: number) => {
    setAuxiliaryTypeId(value)
    setAuxiliaryItemId(undefined)
    setItems([])
    if (accountSetId && value) {
      try {
        const itemResult = await fmsConfig.auxiliaryItem.list(accountSetId, value)
        setItems(itemResult)
      } catch (e) {
        message.error(e instanceof Error ? e.message : '辅助项目加载失败')
      }
    }
  }, [accountSetId])

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
    setAuxiliaryItemId(undefined)
    setItems([])
    if (accountSetId) void loadTypeOptions(accountSetId)
    else setTypes([])
  }

  // 类别加载后若默认选中项尚未有项目，则重新拉取（覆盖辅助类别列表为空/变化时的场景）
  const loadedTypeRef = useRef<number | undefined>(undefined)
  useEffect(() => {
    if (!accountSetId || !auxiliaryTypeId || loadedTypeRef.current === auxiliaryTypeId) return
    loadedTypeRef.current = auxiliaryTypeId
    void fmsConfig.auxiliaryItem.list(accountSetId, auxiliaryTypeId)
      .then(setItems)
      .catch(e => message.error(e instanceof Error ? e.message : '辅助项目加载失败'))
  }, [accountSetId, auxiliaryTypeId])

  const filteredItems = useMemo(() => {
    const keyword = itemKeyword.trim().toLowerCase()
    if (!keyword) return items
    return items.filter(item => `${item.code} ${item.name}`.toLowerCase().includes(keyword))
  }, [items, itemKeyword])

  const selectedType = useMemo(() => types.find(t => t.id === auxiliaryTypeId), [types, auxiliaryTypeId])
  const selectedItem = useMemo(() => items.find(i => i.id === auxiliaryItemId), [items, auxiliaryItemId])

  const handleSearch = useCallback(() => {
    void getList()
  }, [getList])

  const handleExport = useCallback(async () => {
    if (!accountSetId || !auxiliaryItemId) return
    setExportLoading(true)
    try {
      const blob = await fmsLedger.auxiliaryDetail.exportExcel(params())
      saveBlob(blob, buildPeriodFilename('核算项目明细账', startMonth, endMonth))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, auxiliaryItemId, startMonth, endMonth, params])

  const handlePrint = useCallback(() => {
    if (!accountSet || list.length === 0) return
    const centerText = selectedItem
      ? `辅助项目：${selectedType?.name || ''} ${selectedItem.code}_${selectedItem.name}`
      : ''
    const printColumns: FmsPrintColumn<FmsLedgerDetail>[] = [
      { title: '日期', align: 'center', render: r => r.accountDate },
      { title: '凭证字号', align: 'center', render: r => r.voucherNumber || '' },
      { title: '摘要', render: r => r.digest },
      { title: '借方', align: 'right', render: r => formatMoney(r.debitAmount) },
      { title: '贷方', align: 'right', render: r => formatMoney(r.creditAmount) },
      { title: '方向', align: 'center', render: r => r.balanceDirection },
      { title: '余额', align: 'right', render: r => formatMoney(r.balance) }
    ]
    const html = buildFmsTablePrintHtml({
      title: '核算项目明细账',
      companyName: accountSet.companyName,
      periodLabel: formatPeriodLabel(startMonth, endMonth),
      centerText,
      columns: printColumns,
      rows: list
    })
    printPreview.show(html)
  }, [accountSet, list, startMonth, endMonth, selectedItem, selectedType, printPreview])

  const columns: ColumnsType<FmsLedgerDetail> = [
    { title: '日期', dataIndex: 'accountDate', width: 110, align: 'center' },
    { title: '凭证字号', dataIndex: 'voucherNumber', width: 110, align: 'center',
      render: (text: string, row) => row.voucherId ? <Button type="link" size="small" onClick={() => navigate(`/fms/voucher/create?id=${row.voucherId}`)}>{text}</Button> : text },
    { title: '摘要', dataIndex: 'digest', ellipsis: true },
    { title: '借方', dataIndex: 'debitAmount', width: 140, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '贷方', dataIndex: 'creditAmount', width: 140, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '方向', dataIndex: 'balanceDirection', width: 80, align: 'center' },
    { title: '余额', dataIndex: 'balance', width: 150, align: 'right', render: (v: number) => formatMoney(v) }
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
            placeholder="辅助核算类别"
            value={auxiliaryTypeId}
            onChange={handleTypeChange}
            style={{ width: 180 }}
            options={types.map(t => ({ label: t.name, value: t.id! }))}
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
      <div className="fms-table-area" style={{ display: 'flex', gap: 'var(--crm-sp-3)' }}>
        <div style={{ flex: '0 0 220px', maxHeight: 'calc(100vh - 300px)', overflow: 'auto' }}>
          {selectedType && (
            <div style={{ fontWeight: 600, marginBottom: 'var(--crm-sp-2)' }}>
              {selectedType.name}：{selectedItem ? `${selectedItem.code}_${selectedItem.name}` : ''}
            </div>
          )}
          <Input.Search
            placeholder="搜索辅助项目"
            allowClear
            value={itemKeyword}
            onChange={e => setItemKeyword(e.target.value)}
            style={{ marginBottom: 'var(--crm-sp-2)' }}
          />
          <Space direction="vertical" style={{ width: '100%' }} size="small">
            {filteredItems.map(item => (
              <Button
                key={item.id}
                type={item.id === auxiliaryItemId ? 'primary' : 'default'}
                size="small"
                block
                onClick={() => { setAuxiliaryItemId(item.id); void getList() }}
                style={{ textAlign: 'left', whiteSpace: 'normal', height: 'auto' }}
              >
                {item.code} {item.name}
              </Button>
            ))}
          </Space>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <FmsProTable<FmsLedgerDetail>
            rowKey={(row, index) => `${row.entryId ?? row.subjectId}-${index}`}
            columns={columns}
            dataSource={list}
            loading={loading}
            pagination={false}
            bordered
            size="small"
            scroll={{ y: 'calc(100vh - 300px)' }}
          />
        </div>
      </div>
      <FmsPrintPreview open={printPreview.open} onClose={printPreview.close} html={printPreview.html} title="核算项目明细账打印预览"/>
    </section>
  )
}

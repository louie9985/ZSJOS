import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button, DatePicker, Input, Space, Tree, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, PrinterOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import type { TreeProps } from 'antd'
import type { DataNode } from 'antd/es/tree'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsLedger } from '../../services/fms/ledger'
import type { FmsLedgerDetail, FmsSubjectVO } from '../../services/fms/types'
import { formatMoney, buildPeriodFilename, formatPeriodLabel } from '../../services/fms/format'
import { buildFmsTablePrintHtml, type FmsPrintColumn } from '../../services/fms/print'
import { saveBlob } from '../../services/download'
import FmsPrintPreview, { usePrintPreview } from '../../components/FmsPrintPreview'

export default function FmsLedgerDetailPage({ permissions }: { permissions: string[] }) {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { accountSet, currentMonth: providerMonth } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const now = dayjs().format('YYYY-MM')
  const [startMonth, setStartMonth] = useState(now)
  const [endMonth, setEndMonth] = useState(now)
  const [subjectId, setSubjectId] = useState<number | undefined>(undefined)
  const [subjectTree, setSubjectTree] = useState<FmsSubjectVO[]>([])
  const [subjectKeyword, setSubjectKeyword] = useState('')
  const [list, setList] = useState<FmsLedgerDetail[]>([])
  const [loading, setLoading] = useState(false)
  const [treeLoading, setTreeLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const listVersion = useRef(0)
  const treeVersion = useRef(0)
  const printPreview = usePrintPreview()

  // 从深链 URL 读取 subjectId/startMonth/endMonth（同路径 query 变化也要响应）
  const urlSubject = Number(searchParams.get('subjectId') || 0) || undefined
  const urlStart = searchParams.get('startMonth') || undefined
  const urlEnd = searchParams.get('endMonth') || undefined

  // 当前期间缺省值：URL 参数 > 账套当前月份
  const effectiveStart = useMemo(() => urlStart || startMonth, [urlStart, startMonth])
  const effectiveEnd = useMemo(() => urlEnd || endMonth, [urlEnd, endMonth])

  // 账套加载后，若 URL 未带期间则回填当前月份
  const defaulted = useRef(false)
  useEffect(() => {
    if (!accountSetId || defaulted.current) return
    defaulted.current = true
    if (!urlStart && providerMonth) setStartMonth(providerMonth)
    if (!urlEnd && providerMonth) setEndMonth(providerMonth)
  }, [accountSetId, urlStart, urlEnd, providerMonth])

  const reloadAll = useCallback(async (sm: string, em: string, sid: number | undefined, asId = accountSetId) => {
    if (!asId) {
      setSubjectTree([])
      setList([])
      return
    }
    const treeV = ++treeVersion.current
    setTreeLoading(true)
    try {
      const tree = await fmsLedger.detail.subjectList({ accountSetId: asId, startMonth: sm, endMonth: em })
      if (treeV !== treeVersion.current) return
      setSubjectTree(tree)
    } catch (e) {
      if (treeV !== treeVersion.current) return
      message.error(e instanceof Error ? e.message : '科目加载失败')
      setSubjectTree([])
    } finally {
      if (treeV === treeVersion.current) setTreeLoading(false)
    }

    const v = ++listVersion.current
    setLoading(true)
    try {
      if (!sid) { setList([]); return }
      const result = await fmsLedger.detail.list({ accountSetId: asId, startMonth: sm, endMonth: em, subjectId: sid })
      if (v !== listVersion.current) return
      setList(result)
    } catch (e) {
      if (v !== listVersion.current) return
      message.error(e instanceof Error ? e.message : '查询失败')
      setList([])
    } finally {
      if (v === listVersion.current) setLoading(false)
    }
  }, [accountSetId])

  // 账套切换时重载
  const lastAccountSetId = useRef<number | undefined>(undefined)
  if (accountSetId !== lastAccountSetId.current) {
    lastAccountSetId.current = accountSetId
    if (accountSetId) {
      const sm = urlStart || startMonth
      const em = urlEnd || endMonth
      // 延一个 tick 让 currentMonth 先回来、树才拉到数据
      setTimeout(() => void reloadAll(sm, em, urlSubject), 0)
    } else {
      setSubjectTree([])
      setList([])
    }
  }

  // 深链 query 变化（如同路径从科目余额表带 subjectId 跳转进来）
  useEffect(() => {
    if (!accountSetId) return
    const hasUrlPeriod = Boolean(urlStart && urlEnd)
    const sm = hasUrlPeriod ? urlStart! : effectiveStart
    const em = hasUrlPeriod ? urlEnd! : effectiveEnd
    void reloadAll(sm, em, urlSubject)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountSetId, urlSubject, urlStart, urlEnd])

  // 期间或科目变化（用户操作）时刷新
  const handleSearch = useCallback(() => {
    void reloadAll(startMonth, endMonth, subjectId)
  }, [reloadAll, startMonth, endMonth, subjectId])

  // 科目树点击
  const handleSubjectSelect: TreeProps['onSelect'] = useCallback((keys: React.Key[]) => {
    const id = Number(keys[0]) || undefined
    setSubjectId(id)
    if (id) void reloadAll(startMonth, endMonth, id)
  }, [reloadAll, startMonth, endMonth])

  const filteredTree = useMemo(() => {
    const keyword = subjectKeyword.trim().toLowerCase()
    if (!keyword) return subjectTree
    const filterNode = (nodes: FmsSubjectVO[]): FmsSubjectVO[] =>
      nodes.flatMap(node => {
        const children = filterNode(node.children || [])
        const matched = `${node.code} ${node.name}`.toLowerCase().includes(keyword)
        return matched || children.length ? [{ ...node, children }] : []
      })
    return filterNode(subjectTree)
  }, [subjectTree, subjectKeyword])

  // antd Tree 的 treeData 要求 DataNode[]；fieldNames 映射 key/title/children，实际数据已满足
  const treeData = useMemo(() => filteredTree as unknown as DataNode[], [filteredTree])

  const handleExport = useCallback(async () => {
    if (!accountSetId) return
    setExportLoading(true)
    try {
      const blob = await fmsLedger.detail.exportExcel({
        accountSetId, startMonth: effectiveStart, endMonth: effectiveEnd, subjectId
      })
      saveBlob(blob, buildPeriodFilename('明细账', effectiveStart, effectiveEnd))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, effectiveStart, effectiveEnd, subjectId])

  const handlePrint = useCallback(() => {
    if (!accountSet || list.length === 0) return
    const selected = subjectTree.find(s => s.id === subjectId)
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
      title: '明细账',
      companyName: accountSet.companyName,
      periodLabel: formatPeriodLabel(effectiveStart, effectiveEnd),
      centerText: selected ? `科目：${selected.code} ${selected.name}` : '',
      columns: printColumns,
      rows: list
    })
    printPreview.show(html)
  }, [accountSet, list, effectiveStart, effectiveEnd, subjectId, subjectTree, printPreview])

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
      {/* 搜索条 */}
      <div className="fms-search-area">
        <Space wrap>
          <DatePicker.RangePicker
            picker="month"
            value={[dayjs(effectiveStart), dayjs(effectiveEnd)]}
            onChange={dates => {
              if (dates?.[0] && dates?.[1]) {
                setStartMonth(dates[0].format('YYYY-MM'))
                setEndMonth(dates[1].format('YYYY-MM'))
              }
            }}
            allowClear={false}
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

      {/* 科目树 + 明细表 */}
      <div className="fms-table-area" style={{ display: 'flex', gap: 'var(--crm-sp-3)' }}>
        <div style={{ flex: '0 0 260px', maxHeight: 'calc(100vh - 300px)', overflow: 'auto' }}>
          <Input.Search
            placeholder="搜索科目"
            allowClear
            value={subjectKeyword}
            onChange={e => setSubjectKeyword(e.target.value)}
            style={{ marginBottom: 'var(--crm-sp-2)' }}
          />
          <Tree
            treeData={treeData}
            fieldNames={{ title: 'name', key: 'id', children: 'children' }}
            defaultExpandAll
            selectedKeys={subjectId ? [subjectId] : []}
            onSelect={handleSubjectSelect}
            showLine
            titleRender={node => `${(node as unknown as FmsSubjectVO).code} ${(node as unknown as FmsSubjectVO).name}`}
          />
          {treeLoading && <div style={{ padding: '8px 0', textAlign: 'center' }}>科目加载中…</div>}
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

      <FmsPrintPreview open={printPreview.open} onClose={printPreview.close} html={printPreview.html} title="明细账打印预览"/>
    </section>
  )
}

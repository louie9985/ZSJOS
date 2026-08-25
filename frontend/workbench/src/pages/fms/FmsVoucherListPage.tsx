import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Dropdown, Input, InputNumber, message, Modal, Select, Space, Tag } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, EllipsisOutlined, PaperClipOutlined, PlusOutlined, PrinterOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsVoucher, type FmsVoucher, type FmsVoucherPageReq, type FmsVoucherAuxiliaryItem } from '../../services/fms/voucher'
import { fmsConfig } from '../../services/fms'
import type { FmsVoucherWordVO } from '../../services/fms/types'
import { formatMoney, buildPeriodFilename } from '../../services/fms/format'
import { FMS_VOUCHER_STATUS, FMS_VOUCHER_STATUS_OPTIONS } from '../../services/fms/constants'
import { saveBlob } from '../../services/download'
import { useDict } from '../../services/useDict'
import { DICT_TYPE, APP_ROUTES } from '../../constants'
import FmsImportModal from '../../components/fms/FmsImportModal'
import FmsVoucherPrintSettings from '../../components/fms/FmsVoucherPrintSettings'
import { FmsVoucherAttachmentModal, FmsVoucherMoveModal, FmsVoucherTidyModal } from '../../components/fms/FmsVoucherOperations'

export default function FmsVoucherListPage({ permissions }: { permissions: string[] }) {
  const navigate = useNavigate()
  const { accountSet, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const [list, setList] = useState<FmsVoucher[]>([])
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [loading, setLoading] = useState(false)
  const [listError, setListError] = useState('')
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [monthRange, setMonthRange] = useState<[string, string]>([dayjs().format('YYYY-MM'), dayjs().format('YYYY-MM')])
  const [voucherWordId, setVoucherWordId] = useState<number | undefined>()
  const [voucherNumber, setVoucherNumber] = useState<number | undefined>()
  const [digest, setDigest] = useState('')
  const [subjectId, setSubjectId] = useState<number | undefined>()
  const [minAmount, setMinAmount] = useState<number | undefined>()
  const [maxAmount, setMaxAmount] = useState<number | undefined>()
  const [creatorUserId, setCreatorUserId] = useState<number | undefined>()
  const [status, setStatus] = useState<number | undefined>()
  const [voucherWords, setVoucherWords] = useState<FmsVoucherWordVO[]>([])
  const [batchLoading, setBatchLoading] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const [printLoading, setPrintLoading] = useState(false)
  const [printVouchers, setPrintVouchers] = useState<FmsVoucher[]>([])
  const [printOpen, setPrintOpen] = useState(false)
  const [moveOpen, setMoveOpen] = useState(false)
  const [tidyOpen, setTidyOpen] = useState(false)
  const [attachmentVoucher, setAttachmentVoucher] = useState<FmsVoucher>()
  const version = useRef(0)

  const { options: statusOptions } = useDict(DICT_TYPE.FMS_VOUCHER_STATUS)

  const buildQuery = useCallback((ids?: number[], requestedPage = pageNo): FmsVoucherPageReq => ({
    accountSetId: accountSetId!,
    pageNo: requestedPage, pageSize,
    voucherTime: monthRange,
    voucherWordId, voucherNumber, digest, subjectId, minAmount, maxAmount, creatorUserId, status,
    ...(ids ? { ids } : {})
  }), [accountSetId, pageNo, pageSize, monthRange, voucherWordId, voucherNumber, digest, subjectId, minAmount, maxAmount, creatorUserId, status])

  const getList = useCallback(async (page?: number, ids?: number[]) => {
    if (!accountSetId) { setList([]); setTotal(0); return }
    const v = ++version.current
    setLoading(true)
    setListError('')
    try {
      const result = await fmsVoucher.page(buildQuery(ids, page))
      if (v !== version.current) return
      setList(result.list); setTotal(result.total)
      if (page) setPageNo(page)
    } catch (e) {
      if (v !== version.current) return
      setListError(e instanceof Error ? e.message : '凭证列表查询失败')
    } finally {
      if (v === version.current) setLoading(false)
    }
  }, [accountSetId, buildQuery])

  // 加载凭证字
  const loadVoucherWords = useCallback(async () => {
    if (!accountSetId) return
    try { setVoucherWords(await fmsConfig.voucherWord.list(accountSetId)) }
    catch { /* 凭证字加载失败不阻塞列表 */ }
  }, [accountSetId])

  useEffect(() => {
    if (accountSetId) {
      void getList(1)
      void loadVoucherWords()
    } else {
      setList([]); setTotal(0); setVoucherWords([])
    }
  }, [accountSetId, getList, loadVoucherWords])

  const handleExport = useCallback(async () => {
    if (!accountSetId) return
    setExportLoading(true)
    try {
      const blob = await fmsVoucher.exportExcel(buildQuery())
      saveBlob(blob, `凭证${monthRange[0]}至${monthRange[1]}.xls`)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, buildQuery, monthRange])

  const handleReview = useCallback(async (row: FmsVoucher, reviewStatus: number) => {
    if (!accountSetId) return
    const action = reviewStatus === FMS_VOUCHER_STATUS.APPROVED ? '审核' : '反审核'
    Modal.confirm({
      title: `确认${action}`,
      content: `${row.voucherWordName}-${row.voucherNumber} 是否${action}?`,
      onOk: async () => {
        try {
          await fmsVoucher.updateReviewStatus(accountSetId, [row.id], reviewStatus)
          message.success(`${action}成功`)
          getList()
        } catch (e) {
          message.error(e instanceof Error ? e.message : `${action}失败`)
        }
      }
    })
  }, [accountSetId, getList])

  const handleDelete = useCallback(async (row: FmsVoucher) => {
    if (!accountSetId) return
    Modal.confirm({
      title: '删除凭证',
      content: `确认删除 ${row.voucherWordName}-${row.voucherNumber}?`,
      onOk: async () => {
        try {
          await fmsVoucher.deleteList(accountSetId, [row.id])
          message.success('删除成功')
          getList()
        } catch (e) {
          message.error(e instanceof Error ? e.message : '删除失败')
        }
      }
    })
  }, [accountSetId, getList])

  const handleBatchReview = useCallback(async (reviewStatus: number) => {
    if (!accountSetId || selectedIds.length === 0) return
    const action = reviewStatus === FMS_VOUCHER_STATUS.APPROVED ? '审核' : '反审核'
    const eligibleIds = list.filter(row => selectedIds.includes(row.id) && row.status === (
      reviewStatus === FMS_VOUCHER_STATUS.APPROVED ? FMS_VOUCHER_STATUS.PENDING_REVIEW : FMS_VOUCHER_STATUS.APPROVED
    )).map(row => row.id)
    if (!eligibleIds.length) { message.warning('所选凭证不符合当前审核操作'); return }
    Modal.confirm({
      title: `批量${action}`,
      content: `确认对选中的 ${eligibleIds.length} 张凭证执行${action}?`,
      onOk: async () => {
        setBatchLoading(true)
        try {
          await fmsVoucher.updateReviewStatus(accountSetId, eligibleIds, reviewStatus)
          message.success(`批量${action}成功`)
          setSelectedIds([])
          getList()
        } catch (e) {
          message.error(e instanceof Error ? e.message : `批量${action}失败`)
        } finally {
          setBatchLoading(false)
        }
      }
    })
  }, [accountSetId, list, selectedIds, getList])

  const handleBatchDelete = useCallback(async () => {
    if (!accountSetId || selectedIds.length === 0) return
    const selectedRows = list.filter(row => selectedIds.includes(row.id))
    if (selectedRows.some(row => row.status === FMS_VOUCHER_STATUS.APPROVED)) {
      message.warning('批量删除不能包含已审核凭证')
      return
    }
    Modal.confirm({
      title: '批量删除',
      content: `确认删除选中的 ${selectedIds.length} 张凭证?`,
      onOk: async () => {
        setBatchLoading(true)
        try {
          await fmsVoucher.deleteList(accountSetId, selectedIds)
          message.success('批量删除成功')
          setSelectedIds([])
          getList()
        } catch (e) {
          message.error(e instanceof Error ? e.message : '批量删除失败')
        } finally {
          setBatchLoading(false)
        }
      }
    })
  }, [accountSetId, list, selectedIds, getList])

  const canCreate = writable && permissions.includes('fms:voucher:create')
  const canUpdate = writable && permissions.includes('fms:voucher:update')
  const canExport = permissions.includes('fms:voucher:export')
  const canPrint = permissions.includes('fms:voucher:print')
  const canReview = writable && permissions.includes('fms:voucher:review')
  const canDelete = writable && permissions.includes('fms:voucher:delete')
  const canImport = writable && permissions.includes('fms:voucher:import')
  const canMove = writable && permissions.includes('fms:voucher:move')
  const canTidy = writable && permissions.includes('fms:voucher:tidy')

  const handlePrint = useCallback(async () => {
    if (!accountSetId) return
    setPrintLoading(true)
    try {
      const vouchers = await fmsVoucher.printList(buildQuery(selectedIds.length ? selectedIds : undefined, 1))
      if (!vouchers.length) {
        message.info('没有可打印的凭证')
        return
      }
      setPrintVouchers(vouchers)
      setPrintOpen(true)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '打印数据加载失败')
    } finally {
      setPrintLoading(false)
    }
  }, [accountSetId, buildQuery, selectedIds])

  const columns: ColumnsType<FmsVoucher> = useMemo(() => [
    ...(writable ? [{
      title: '', width: 46, fixed: 'left' as const,
      render: (_: unknown, row: FmsVoucher) => row.closingGenerated
        ? null
        : <input type="checkbox" checked={selectedIds.includes(row.id)} onChange={e => {
          setSelectedIds(prev => e.target.checked ? [...prev, row.id] : prev.filter(id => id !== row.id))
        }}/>
    }] : []),
    { title: '日期', dataIndex: 'voucherTime', width: 110, align: 'center' as const, render: (v: number) => dayjs(v).format('YYYY-MM-DD') },
    {
      title: '凭证字号', width: 110, align: 'center' as const,
      render: (_, row) => <Button type="link" size="small" onClick={() => navigate(`${APP_ROUTES.FMS_VOUCHER_CREATE}?id=${row.id}`)}>{row.voucherWordName}-{row.voucherNumber}</Button>
    },
    {
      title: '附件', width: 72, align: 'center' as const,
      render: (_, row) => row.attachmentUrls?.length || (canUpdate && row.status === FMS_VOUCHER_STATUS.PENDING_REVIEW && !row.closingGenerated)
        ? <Button type="link" size="small" icon={<PaperClipOutlined />} onClick={() => setAttachmentVoucher(row)}>{row.attachmentUrls?.length || 0}</Button>
        : <span><PaperClipOutlined /> 0</span>
    },
    {
      title: '摘要', ellipsis: true,
      render: (_, row) => <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {row.entries.map((e, i) => <span key={i} style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{e.digest}</span>)}
      </div>
    },
    {
      title: '会计科目', ellipsis: true,
      render: (_, row) => <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {row.entries.map((e, i) => <span key={i} style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {e.subjectCode} {e.subjectName}
          {e.auxiliaries?.length > 0 && <span style={{ color: 'var(--crm-text-secondary)' }}> / {e.auxiliaries.map((a: FmsVoucherAuxiliaryItem) => a.name).join('、')}</span>}
        </span>)}
      </div>
    },
    { title: '借方金额', width: 135, align: 'right' as const, render: (_, row) => <>
      {row.entries.map((e, i) => <div key={i} style={{ minHeight: 22, overflow: 'hidden', borderBottom: '1px dashed var(--crm-border)', lineHeight: '22px' }}>{Number(e.debitAmount) ? formatMoney(e.debitAmount) : ''}</div>)}
    </> },
    { title: '贷方金额', width: 135, align: 'right' as const, render: (_, row) => <>
      {row.entries.map((e, i) => <div key={i} style={{ minHeight: 22, overflow: 'hidden', borderBottom: '1px dashed var(--crm-border)', lineHeight: '22px' }}>{Number(e.creditAmount) ? formatMoney(e.creditAmount) : ''}</div>)}
    </> },
    { title: '制单人', dataIndex: 'creatorUserName', width: 100, align: 'center' as const },
    { title: '审核人', dataIndex: 'reviewerUserName', width: 100, align: 'center' as const },
    { title: '状态', width: 90, align: 'center' as const, render: (_, row) =>
      row.closingGenerated
        ? <Tag color="default">结账生成</Tag>
        : <Tag color={row.status === FMS_VOUCHER_STATUS.APPROVED ? 'success' : 'warning'}>{row.status === FMS_VOUCHER_STATUS.APPROVED ? '已审核' : '待审核'}</Tag>
    },
    {
      title: '操作', width: 180, fixed: 'right' as const, align: 'center' as const,
      render: (_, row) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => navigate(`${APP_ROUTES.FMS_VOUCHER_CREATE}?id=${row.id}`)}>
            {row.closingGenerated || !canUpdate ? '查看' : '编辑'}
          </Button>
          {writable && !row.closingGenerated && row.status === FMS_VOUCHER_STATUS.PENDING_REVIEW && canReview && (
            <Button type="link" size="small" onClick={() => handleReview(row, FMS_VOUCHER_STATUS.APPROVED)}>审核</Button>
          )}
          {writable && !row.closingGenerated && row.status === FMS_VOUCHER_STATUS.APPROVED && canReview && (
            <Button type="link" size="small" onClick={() => handleReview(row, FMS_VOUCHER_STATUS.PENDING_REVIEW)}>反审核</Button>
          )}
          {writable && !row.closingGenerated && canDelete && (
            <Button type="link" size="small" danger onClick={() => handleDelete(row)}>删除</Button>
          )}
        </Space>
      )
    }
  ], [writable, canUpdate, canReview, canDelete, selectedIds, navigate, handleReview, handleDelete])

  return (
    <section className="workspace-page fms-page">
      {/* 搜索条 */}
      <div className="fms-search-area">
        <Space wrap style={{ rowGap: 8 }}>
          <DatePicker.RangePicker
            picker="month"
            value={[dayjs(monthRange[0]), dayjs(monthRange[1])]}
            onChange={d => { if (d?.[0] && d?.[1]) setMonthRange([d[0].format('YYYY-MM'), d[1].format('YYYY-MM')]) }}
            allowClear={false}
          />
          <InputNumber placeholder="凭证号" min={1} controls={false} value={voucherNumber} onChange={v => setVoucherNumber(v ?? undefined)} style={{ width: 120 }}/>
          <Input placeholder="摘要" value={digest} onChange={e => setDigest(e.target.value)} onPressEnter={() => getList()} style={{ width: 160 }}/>
          <InputNumber placeholder="最小金额" min={0} precision={2} controls={false} value={minAmount} onChange={v => setMinAmount(v ?? undefined)} style={{ width: 120 }}/>
          <InputNumber placeholder="最大金额" min={0} precision={2} controls={false} value={maxAmount} onChange={v => setMaxAmount(v ?? undefined)} style={{ width: 120 }}/>
          <Select placeholder="状态" allowClear value={status} onChange={setStatus} options={(statusOptions.length ? statusOptions : FMS_VOUCHER_STATUS_OPTIONS.map(o => ({ value: o.value, label: o.label }))) as any} style={{ width: 120 }}/>
          <Button type="primary" icon={<SearchOutlined/>} onClick={() => getList(1)}>搜索</Button>
          <Button icon={<ReloadOutlined/>} onClick={() => { setVoucherNumber(undefined); setDigest(''); setSubjectId(undefined); setMinAmount(undefined); setMaxAmount(undefined); setStatus(undefined); setVoucherWordId(undefined); setMonthRange([dayjs().format('YYYY-MM'), dayjs().format('YYYY-MM')]); getList(1) }}>重置</Button>
          {canCreate && <Button icon={<PlusOutlined/>} onClick={() => navigate(APP_ROUTES.FMS_VOUCHER_CREATE)}>新增</Button>}
          {canExport && <Button icon={<DownloadOutlined/>} loading={exportLoading} onClick={handleExport}>导出</Button>}
          {(canExport || canImport || canPrint || canMove || canTidy) && <Dropdown menu={{ items: [
            ...(canExport ? [{ key: 'export', label: '导出', icon: <DownloadOutlined/> }] : []),
            ...(canImport ? [{ key: 'import', label: '导入', icon: <PlusOutlined/> }] : []),
            ...(canPrint ? [{ key: 'print', label: selectedIds.length ? '打印选中凭证' : '打印查询结果', icon: <PrinterOutlined/> }] : []),
            ...(canMove ? [{ key: 'move', label: '移动凭证' }] : []),
            ...(canTidy ? [{ key: 'tidy', label: '整理凭证' }] : [])
          ], onClick: ({ key }) => {
            if (key === 'export') handleExport()
            if (key === 'import') setImportOpen(true)
            if (key === 'print') void handlePrint()
            if (key === 'move') setMoveOpen(true)
            if (key === 'tidy') setTidyOpen(true)
          } }}><Button icon={<EllipsisOutlined/>} loading={printLoading}>更多</Button></Dropdown>}
        </Space>
      </div>

      {/* 批量操作条 */}
      {selectedIds.length > 0 && writable && (
        <Space style={{ marginBlockEnd: 12 }}>
          <span>已选 {selectedIds.length} 张</span>
          {canReview && <Button size="small" loading={batchLoading} onClick={() => handleBatchReview(FMS_VOUCHER_STATUS.APPROVED)}>批量审核</Button>}
          {canDelete && <Button size="small" danger loading={batchLoading} onClick={handleBatchDelete}>批量删除</Button>}
        </Space>
      )}

      {/* 表格 */}
      <div className="fms-table-area">
        {listError && <Alert type="error" showIcon message="凭证列表加载失败" description={listError} action={<Button size="small" onClick={() => void getList(pageNo)}>重试</Button>} style={{ marginBottom: 12 }} />}
        <FmsProTable<FmsVoucher>
          rowKey="id"
          columns={columns}
          dataSource={list}
          loading={loading}
          scroll={{ x: 1100, y: 'calc(100vh - 320px)' }}
          pagination={{ total, current: pageNo, pageSize, onChange: (p, s) => { setPageNo(p); setPageSize(s); getList(p) }, showSizeChanger: true, showTotal: t => `共 ${t} 条` }}
        />
      </div>

      {/* 导入弹窗 */}
      {accountSetId && canImport && (
        <FmsImportModal
          open={importOpen}
          onClose={() => setImportOpen(false)}
          title="导入凭证"
          onGetTemplate={() => fmsVoucher.getVoucherImportTemplate(accountSetId)}
          onUpload={async (file) => {
            const res = await fmsVoucher.importVoucher(accountSetId, file)
            return {
              totalCount: res.totalRowCount + res.failureRowCount,
              successCount: res.successRowCount,
              failureCount: res.failureRowCount,
              errorFileUrl: res.errorFileUrl
            }
          }}
          onSuccess={() => getList(1)}
        />
      )}
      {accountSetId && <FmsVoucherMoveModal open={moveOpen} accountSetId={accountSetId} defaultMonth={monthRange[0]} voucherWords={voucherWords} onClose={() => setMoveOpen(false)} onSuccess={() => void getList(1)} />}
      {accountSetId && <FmsVoucherTidyModal open={tidyOpen} accountSetId={accountSetId} defaultMonth={monthRange[0]} voucherWords={voucherWords} onClose={() => setTidyOpen(false)} onSuccess={() => void getList(1)} />}
      {accountSetId && <FmsVoucherAttachmentModal
        open={Boolean(attachmentVoucher)}
        accountSetId={accountSetId}
        voucher={attachmentVoucher}
        editable={Boolean(attachmentVoucher && canUpdate && attachmentVoucher.status === FMS_VOUCHER_STATUS.PENDING_REVIEW && !attachmentVoucher.closingGenerated)}
        onClose={() => setAttachmentVoucher(undefined)}
        onSuccess={() => void getList(pageNo)}
      />}
      <FmsVoucherPrintSettings open={printOpen} vouchers={printVouchers} companyName={accountSet?.companyName || ''} onClose={() => setPrintOpen(false)} />
    </section>
  )
}

import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Input, Select, Tag } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import type { ColumnsState, ProColumns } from '@ant-design/pro-components'
import { ApiError, AuthenticationError } from '../services/api'
import BusinessTable from '../components/BusinessTable'
import { filterSelectDisplayProps } from '../components/filterSelectDisplay'
import { hasPermission } from '../services/managementAccess'
import { studentSummaryApi, visibleSummaryColumns, type SummaryColumn, type SummaryRow } from '../services/studentSummary'
import { examSummaryApi } from '../services/examCollection'

const preferenceKey = 'crm-table:student-summary:columns'
function savedColumns(): Record<string, ColumnsState> {
  try { const raw = JSON.parse(localStorage.getItem(preferenceKey) || '{}'); return raw && typeof raw === 'object' && !Array.isArray(raw) ? raw : {} } catch { return {} }
}
const isDenied = (error: unknown) => error instanceof AuthenticationError || error instanceof ApiError && [401,403,1900170002].includes(error.code)
const errorText = (error: unknown) => error instanceof Error ? error.message : '加载失败，请重试'
export default function StudentSummaryPage({ permissions, examTaskId, initialClassIds, taskTitle, onBack }: { permissions: string[]; examTaskId?: number; initialClassIds?: number[]; taskTitle?: string; onBack?:()=>void }) {
  const allowed = hasPermission(permissions, examTaskId ? 'zsjos:exam-collection:query' : 'zsjos:student-summary:query')
  const canExport = allowed && (Boolean(examTaskId) || hasPermission(permissions, 'zsjos:student-summary:export'))
  const client = useMemo(()=>examTaskId ? examSummaryApi(examTaskId) : studentSummaryApi,[examTaskId])
  const [denied, setDenied] = useState(false)
  const [catalog, setCatalog] = useState<SummaryColumn[]>([])
  const [classes, setClasses] = useState<{value: number; label: string}[]>([])
  const [metadataError, setMetadataError] = useState(''), [metadataLoading, setMetadataLoading] = useState(false)
  const [classIds, setClassIds] = useState<number[]>(initialClassIds || []), [keyword, setKeyword] = useState('')
  const [filled, setFilled] = useState<boolean>()
  const [pageNo, setPageNo] = useState(1), [pageSize, setPageSize] = useState(20)
  const [rows, setRows] = useState<SummaryRow[]>([]), [total, setTotal] = useState(0)
  const [coverage, setCoverage] = useState<{ submitted: number; members: number }>()
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [exportError, setExportError] = useState('')
  const [busy, setBusy] = useState(false), [reload, setReload] = useState(0), [metadataReload, setMetadataReload] = useState(0)
  const [columnState, setColumnState] = useState<Record<string, ColumnsState>>(savedColumns)
  const query = useMemo(() => ({ classIds, keyword, pageNo, pageSize, filled }), [classIds, keyword, pageNo, pageSize, filled])
  useEffect(() => {
    let active = true
    if (!allowed) { setCatalog([]); setClasses([]); return }
    setMetadataLoading(true); setMetadataError('')
    Promise.all([client.columns(), client.classes()]).then(([fields, options]) => {
      if (active) { setCatalog(fields); setClasses(options) }
    }).catch(e => { if (active) { setMetadataError(errorText(e)); if (isDenied(e)) { setDenied(true); setRows([]) } } }).finally(() => { if (active) setMetadataLoading(false) })
    return () => { active = false }
  }, [allowed, client, metadataReload])
  useEffect(() => {
    let active = true
    setRows([]); setTotal(0); setCoverage(undefined)
    if (!allowed) return
    setLoading(true); setError(''); setDenied(false)
    Promise.all([client.page(query), client.coverage(query)]).then(([result, counts]) => { if (active) { setRows(result.list); setTotal(result.total); setCoverage(counts) } })
      .catch(e => { if (active) { setError(errorText(e)); if (isDenied(e)) setDenied(true) } }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [allowed, client, query, reload])
  const columns: ProColumns<SummaryRow>[] = catalog.map(column => ({
    key: column.key, dataIndex: ['values', column.key], title: column.label, width: 180,
    ...(column.key === 'collectionStatus' ? {
      filters: column.filters, filterMultiple: false,
      filteredValue: filled === undefined ? null : [String(filled)],
    } : {}),
    render: (_, row) => column.key === 'collectionStatus'
      ? <Tag color={row.values[column.key] === column.filters?.find(option => option.value === 'true')?.text ? 'success' : 'warning'}>{row.values[column.key] || '—'}</Tag>
      : row.values[column.key] || '—',
  }))
  const exportKeys = visibleSummaryColumns(catalog, columnState)
  const exportRows = async () => {
    setBusy(true); setExportError('')
    try { await client.export(query, exportKeys) } catch (e) { setExportError(errorText(e)) } finally { setBusy(false) }
  }
  return <section className="workspace-page">
    {onBack&&<Button style={{alignSelf:'flex-start'}} onClick={onBack}>返回考试收集任务</Button>}
    {examTaskId&&<Alert type="info" showIcon title={taskTitle} description="本次填写状态按任务固定名单和提交记录统计，已有报名档案不直接计为本次已填写。"/>}
    {allowed && metadataError && <Alert type="error" showIcon title="字段或班级加载失败" description={metadataError} action={<Button onClick={() => setMetadataReload(v => v + 1)}>重试</Button>} />}
    {allowed && exportError && <Alert type="error" showIcon title="导出失败" description={exportError} closable onClose={() => setExportError('')} />}
    <BusinessTable<SummaryRow> tableKey="student-summary" rowKey="serviceRelationId" headerTitle={`学员档案汇总（${total} 条记录）${classIds.length || examTaskId ? ` · ${examTaskId ? '本次已填写/应收人数' : '已有资料/班级人数'}：${coverage ? `${coverage.submitted}/${coverage.members}` : "—"}` : ""}`}
      columns={columns} dataSource={rows} loading={loading || metadataLoading} unauthorized={!allowed || denied} error={error}
      onChange={(_, filters, _sorter, extra) => {
        if (extra.action === 'filter') {
          const value = filters.collectionStatus?.[0]
          setFilled(value == null ? undefined : value === 'true'); setPageNo(1)
        }
      }}
      onReload={() => setReload(v => v + 1)}
      columnsState={{ value: columnState, onChange: state => { setColumnState(state); try { localStorage.setItem(preferenceKey, JSON.stringify(state)) } catch { /* Preferences are optional. */ } } }}
      filters={<>
        <Select {...filterSelectDisplayProps} mode="multiple" maxTagCount="responsive" aria-label="按班级筛选" placeholder={examTaskId?'本任务全部班级':'全部班级'} allowClear showSearch optionFilterProp="label"
          style={{ minWidth: 240 }} options={classes} value={classIds} loading={metadataLoading} disabled={!allowed || !!metadataError}
          onChange={value => { setClassIds(value); setPageNo(1) }} />
        <Input.Search aria-label="搜索学员" placeholder="姓名、手机号、学员编号" allowClear style={{ width: 260 }} disabled={!allowed}
          onSearch={value => { setKeyword(value); setPageNo(1) }} />
      </>}
      actions={canExport && !denied ? <Button icon={<DownloadOutlined />} loading={busy} disabled={loading || metadataLoading || !!metadataError || !!error || !exportKeys.length} onClick={() => void exportRows()}>导出筛选结果</Button> : undefined}
      pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, onChange: (page, size) => { setPageNo(size !== pageSize ? 1 : page); setPageSize(size) } }} />
  </section>
}

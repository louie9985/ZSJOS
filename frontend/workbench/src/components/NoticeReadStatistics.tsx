import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Input, Select, Space, Tabs } from 'antd'
import BusinessTable from './BusinessTable'
import DateTimeText from './DateTimeText'
import { ApiError, AuthenticationError } from '../services/api'
import { noticeReadStatistics, type NoticeReadPerson, type NoticeReadScope, type NoticeReadSummary } from '../services/noticeManagement'

export default function NoticeReadStatistics({ id }: { id: number }) {
  const [summary, setSummary] = useState<NoticeReadSummary>()
  const [rows, setRows] = useState<NoticeReadPerson[]>([])
  const [scope, setScope] = useState<NoticeReadScope>('EXPECTED')
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [name, setName] = useState('')
  const [deptId, setDeptId] = useState<number>()
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [denied, setDenied] = useState(false)
  const generation = useRef(0)
  const summaryRequest = useRef<{ id: number; promise: Promise<NoticeReadSummary> } | undefined>(undefined)
  const load = useCallback(async (refreshSummary = false) => {
    const current = ++generation.current
    setLoading(true); setError(''); setDenied(false); setRows([]); setTotal(0)
    try {
      // Page changes reuse this open notice's roster metadata; explicit refresh reloads both resources.
      if (refreshSummary || summaryRequest.current?.id !== id) {
        setSummary(undefined)
        const request = { id, promise: noticeReadStatistics.summary(id) }
        summaryRequest.current = request
        void request.promise.catch(() => { if (summaryRequest.current === request) summaryRequest.current = undefined })
      }
      const stats = await summaryRequest.current!.promise
      if (current !== generation.current) return
      const data = stats.published ? await noticeReadStatistics.page({ id, scope: stats.rosterComplete ? scope : 'ACTUAL', pageNo, pageSize, name: name || undefined, deptId }) : { list: [], total: 0 }
      if (current !== generation.current) return
      setSummary(stats); setRows(data.list); setTotal(data.total)
    } catch (cause) {
      if (current !== generation.current) return
      setDenied(cause instanceof ApiError && cause.code === 403)
      if (cause instanceof AuthenticationError || cause instanceof ApiError && cause.code === 403) { setSummary(undefined); summaryRequest.current = undefined }
      setError(cause instanceof AuthenticationError ? '登录状态已失效，请重新登录' : cause instanceof Error ? cause.message : '阅读情况加载失败')
    } finally { if (current === generation.current) setLoading(false) }
  }, [id, scope, pageNo, pageSize, name, deptId])
  useEffect(() => { void load(); return () => { generation.current++ } }, [load])
  const departments = scope === 'EXTRA' && summary?.rosterComplete ? summary.extraDepartments : summary?.departments
  if (summary && !summary.published) return <Alert type="info" showIcon title="发布后生成阅读统计" />
  return <section>
    {error && !denied && <Alert type="error" showIcon title={error} action={<Button onClick={() => void load(true)}>重试</Button>} />}
    {summary && <>
      {!summary.rosterComplete && <Alert type="info" showIcon title="无法统计应读、未读及阅读率：发布时未记录名单" />}
      <Descriptions size="small" column={{ xs: 1, sm: 2, md: 3 }} items={summary.rosterComplete ? [
        { key: 'expected', label: '应读人数', children: summary.expectedCount },
        { key: 'read', label: '已读人数', children: summary.readCount },
        { key: 'unread', label: '未读人数', children: summary.unreadCount },
        { key: 'rate', label: '阅读率', children: summary.readRate == null ? '—' : (summary.readRate * 100).toFixed(1) + '%' },
        { key: 'extra', label: '名单外阅读人数', children: summary.extraReadCount }
      ] : [{ key: 'actual', label: '实际阅读人数', children: summary.actualReadCount }]} />
    </>}
    <Tabs activeKey={summary && !summary.rosterComplete ? 'ACTUAL' : scope} items={summary && !summary.rosterComplete ? [{ key: 'ACTUAL', label: '实际阅读人员' }] : [
      { key: 'EXPECTED', label: '全部应读' }, { key: 'READ', label: '已读' }, { key: 'UNREAD', label: '未读' }, { key: 'EXTRA', label: '名单外阅读' }
    ]} onChange={key => { setScope(key as NoticeReadScope); setDeptId(undefined); setPageNo(1) }} />
    <BusinessTable<NoticeReadPerson> tableKey="notice-read-statistics" mode="compact" rowKey="userId" dataSource={rows}
      loading={loading} unauthorized={denied} onReload={() => void load(true)} scroll={{ x: 820 }}
      filters={<Space wrap><Input.Search allowClear placeholder="搜索姓名" onSearch={value => { setName(value); setPageNo(1) }} />
        <Select allowClear showSearch optionFilterProp="label" aria-label="筛选部门" placeholder="筛选部门" style={{ minWidth: 150 }} value={deptId}
          options={(departments || []).map(dept => ({ value: dept.id, label: dept.name || '部门名称未记录' }))}
          onChange={value => { setDeptId(value); setPageNo(1) }} /><Button onClick={() => void load(true)}>刷新</Button></Space>}
      pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, onChange: (page, size) => { setPageNo(size !== pageSize ? 1 : page); setPageSize(size) } }}
      columns={[
        { title: '姓名', dataIndex: 'userName', render: (_, row) => row.userName || (row.accountDeleted ? '账号已删除' : '姓名未记录') },
        { title: '部门', dataIndex: 'deptName', render: (_, row) => row.deptName || (row.deptId == null ? '未分配部门' : '部门名称未记录') },
        { title: '资料口径', dataIndex: 'profileSource', render: (_, row) => row.profileSource === 'SNAPSHOT' ? '发布时资料' : '当前资料（非历史快照）' },
        { title: '当前账号状态', key: 'account', render: (_, row) => row.accountDeleted ? '已删除' : row.accountStatus === 0 ? '启用' : row.accountStatus === 1 ? '停用' : '未知' },
        { title: '阅读状态', key: 'read', render: (_, row) => row.readTime == null ? '未读' : '已读' },
        { title: '首次阅读时间', dataIndex: 'readTime', render: (_, row) => <DateTimeText value={row.readTime} /> }
      ]} />
  </section>
}

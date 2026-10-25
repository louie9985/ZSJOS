import { DownOutlined, RightOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Empty, Popconfirm, Space, Spin, Tag, Typography, message } from 'antd'
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import BusinessTable from '../components/BusinessTable'
import { ClassGroupEditor, ClassGroupOrganizer } from '../components/DeliveryClassGroupDialogs'
import { APP_ROUTES } from '../constants'
import type { DeliveryClass, DeliveryClassPageQuery, DeliveryClassProject } from '../services/api'
import { classDate, classGroups, hasClassPermission, isClassGroupDenied, type ClassGroup, type ClassGroupNode } from '../services/deliveryClassGroups'
import { createClassReadQueue, restoreClassPages } from '../services/classGroupRestore'

type Query = Omit<DeliveryClassPageQuery, 'pageNo' | 'pageSize'>
export type ClassGroupListHandle = { refresh: () => Promise<void>; createGroup: () => void }
type ChildState = { rows: DeliveryClass[]; total: number; page: number; loading: boolean; error: string; denied?: boolean }
type TableRow = { key: string; node?: ClassGroupNode; row?: DeliveryClass; parent?: string; more?: boolean; children?: TableRow[] }
const SIZE = 12
const emptyChild = (): ChildState => ({ rows: [], total: 0, page: 0, loading: false, error: '' })
const errorMessage = (e: unknown) => e instanceof Error ? e.message : '班级加载失败'
const isSystemClass = (r: DeliveryClass) => r.classKind ? r.classKind !== 'NORMAL' : r.systemClass
const statusTag = (r: DeliveryClass) => <Tag color={isSystemClass(r) ? 'blue' : r.status === 'SERVING' ? 'green' : 'default'}>{isSystemClass(r) ? '系统班级' : r.status === 'SERVING' ? '服务中' : '已结课'}</Tag>
const projects = (items: DeliveryClassProject[] = []) => items.length ? <Space wrap>{items.slice(0, 3).map(p => <Tag key={p.id} title={p.path}>{p.name}</Tag>)}{items.length > 3 && <Tag>另 {items.length - 3} 项</Tag>}</Space> : null
const isSystemRow = (r: TableRow) => r.node?.type === 'SYSTEM' || !!r.row && isSystemClass(r.row)

export default forwardRef<ClassGroupListHandle, { query: Query; managed: boolean; table: boolean; permissions: string[]; onEdit: (row: DeliveryClass) => void; onAdd: (group: ClassGroup) => void; onStudent: (row: DeliveryClass) => void; onComplete: (row: DeliveryClass) => Promise<void> }>(function DeliveryClassGroups({ query, managed, table, permissions, onEdit, onAdd, onComplete, onStudent }, ref) {
  const navigate = useNavigate()
  const [nodes, setNodes] = useState<ClassGroupNode[]>([])
  const [total, setTotal] = useState(0)
  const [classTotal, setClassTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [denied, setDenied] = useState(false)
  const [children, setChildren] = useState<Record<string, ChildState>>({})
  const [expanded, setExpanded] = useState<string[]>([])
  const [editor, setEditor] = useState<{ open: boolean; group?: ClassGroup }>({ open: false })
  const [organizer, setOrganizer] = useState<{ open: boolean; group?: ClassGroup; single?: DeliveryClass }>({ open: false })
  const sentinel = useRef<HTMLDivElement>(null)
  const generation = useRef(0)
  const rootRequest = useRef(0)
  const childRequests = useRef<Record<string, number>>({})
  const restoreChildPages = useRef<Record<string, number>>({})
  const restoreScroll = useRef<number | undefined>(undefined)
  const childCurrent = useRef(children); childCurrent.current = children
  const rootBusy = useRef(false)
  const readPage = useMemo(createClassReadQueue, [])
  const failedLoad = useRef({ page: 1, replace: true, restore: false })
  const queryKey = JSON.stringify([query, managed, permissions.slice().sort()])
  const storageKey = `delivery-class-groups:${queryKey}`
  const activeQuery = useRef(queryKey); activeQuery.current = queryKey
  const canQueryStudents = hasClassPermission(permissions, 'zsjos:student:query-my')
  const openStudents = (r: DeliveryClass) => { if (canQueryStudents) navigate(APP_ROUTES.MY_STUDENTS, { state: { classId: r.id } }) }
  const filtered = !!(query.keyword || query.homeroomUserId || query.projectCategoryId || query.examDateStart || query.examDateEnd)
  const loadChildren = useCallback(async (key: string, target = 1) => {
    const epoch = generation.current
    const id = (childRequests.current[key] || 0) + 1; childRequests.current[key] = id
    setChildren(old => ({ ...old, [key]: { ...(old[key] || emptyChild()), loading: true, error: '' } }))
    try {
      const restore = target === 1 ? restoreChildPages.current[key] || 1 : 1
      const current = () => epoch === generation.current && id === childRequests.current[key]
      const read = (pageNo: number, pageSize: number) => readPage(() => {
        if (!current()) throw new Error('班级查询已切换')
        return classGroups.children(key, { ...query, pageNo, pageSize }, managed)
      })
      const result = restore > 1
        ? await restoreClassPages(restore, read, current)
        : await read(target, SIZE)
      if (!result || !current()) return
      delete restoreChildPages.current[key]
      setChildren(old => ({ ...old, [key]: { rows: target === 1 ? result.list : [...(old[key]?.rows || []), ...result.list], total: result.total, page: restore > 1 ? Math.min(restore, Math.max(1, Math.ceil(result.total / SIZE))) : target, loading: false, error: '' } }))
    } catch (e) {
      if (epoch === generation.current && id === childRequests.current[key]) setChildren(old => ({ ...old, [key]: { ...(isClassGroupDenied(e) ? emptyChild() : old[key] || emptyChild()), loading: false, denied: isClassGroupDenied(e), error: errorMessage(e) } }))
    }
  }, [query, managed, readPage])
  const load = useCallback(async (target = 1, replace = table, restore = false) => {
    const id = ++rootRequest.current; const epoch = generation.current; const active = queryKey
    rootBusy.current = true; setLoading(true); setError(''); setDenied(false)
    try {
      // Restore all previously loaded card pages without skipping earlier groups.
      const current = () => epoch === generation.current && id === rootRequest.current && active === activeQuery.current
      const read = (pageNo: number, pageSize: number) => readPage(() => {
        if (!current()) throw new Error('班级查询已切换')
        return classGroups.page({ ...query, pageNo, pageSize }, managed)
      })
      const result = restore && !table && target > 1
        ? await restoreClassPages(target, read, current)
        : await read(target, SIZE)
      if (!result || !current()) return
      if (table && target > 1 && !result.list.length) {
        rootBusy.current = false
        await loadRef.current(Math.max(1, Math.ceil(result.total / SIZE)), true)
        return
      }
      const incoming = result.list
      setNodes(old => replace || restore || target === 1 ? incoming : [...old, ...incoming])
      setTotal(result.total); setClassTotal(result.classTotal); setPage(restore && !table ? Math.min(target, Math.max(1, Math.ceil(result.total / SIZE))) : target)
      setExpanded(old => [...new Set([...old, ...incoming.filter(n => n.autoExpand).map(n => n.key)])])
    } catch (e) { if (epoch === generation.current && id === rootRequest.current) { failedLoad.current = { page: target, replace, restore }; setError(errorMessage(e)); setDenied(isClassGroupDenied(e)); if (isClassGroupDenied(e)) { setNodes([]); setChildren({}); setExpanded([]) } } }
    finally { if (epoch === generation.current && id === rootRequest.current) { rootBusy.current = false; setLoading(false) } }
  }, [query, managed, table, queryKey, readPage])
  const loadRef = useRef(load); loadRef.current = load
  const retryLoad = () => { const failed = failedLoad.current; return load(failed.page, failed.replace, failed.restore) }
  useEffect(() => {
    generation.current++; rootBusy.current = false; childRequests.current = {}; setNodes([]); setChildren({}); setTotal(0); setClassTotal(0)
    let saved: { expanded?: string[]; page?: number; childPages?: Record<string, number>; scroll?: number } = {}
    try { saved = JSON.parse(sessionStorage.getItem(storageKey) || '{}') } catch { /* Session state is optional. */ }
    restoreChildPages.current = saved.childPages || {}; restoreScroll.current = saved.scroll
    setExpanded(saved.expanded || []); void loadRef.current(Math.max(1, Math.min(100, saved.page || 1)), true, true)
    return () => { generation.current++; rootRequest.current++ }
  }, [queryKey])
  const previousTable = useRef(table)
  useEffect(() => {
    if (previousTable.current === table) return
    previousTable.current = table
    generation.current++; rootBusy.current = false; setChildren({}); childRequests.current = {}
    void loadRef.current(1, true)
  }, [table])
  useEffect(() => {
    for (const key of expanded) if (nodes.some(n => n.key === key) && !children[key]) void loadChildren(key)
  }, [expanded, nodes, children, loadChildren])
  useEffect(() => {
    if (loading || error) return
    const save = () => { try { sessionStorage.setItem(storageKey, JSON.stringify({ expanded, page, childPages: Object.fromEntries(Object.entries(childCurrent.current).map(([key, value]) => [key, value.page])), scroll: window.scrollY })) } catch { /* Optional session position. */ } }
    save(); window.addEventListener('scroll', save, { passive: true }); return () => window.removeEventListener('scroll', save)
  }, [storageKey, expanded, page, loading, error, children])
  useEffect(() => {
    if (restoreScroll.current === undefined || loading || expanded.some(key => nodes.some(n => n.key === key) && (!children[key] || children[key].loading))) return
    const top = restoreScroll.current; restoreScroll.current = undefined
    const id = requestAnimationFrame(() => window.scrollTo({ top })); return () => cancelAnimationFrame(id)
  }, [loading, nodes, expanded, children])
  useEffect(() => {
    const element = sentinel.current
    if (table || !element || loading || error || nodes.length >= total) return
    const observer = new IntersectionObserver(entries => { if (entries[0]?.isIntersecting && !rootBusy.current) void loadRef.current(page + 1, false) })
    observer.observe(element); return () => observer.disconnect()
  }, [table, loading, error, nodes.length, total, page])
  const refresh = async () => {
    generation.current++; rootBusy.current = false; setChildren({}); childRequests.current = {}
    await loadRef.current(table ? page : 1, true)
  }
  useImperativeHandle(ref, () => ({ refresh, createGroup: () => setEditor({ open: true }) }))
  const toggle = (key: string) => setExpanded(old => old.includes(key) ? old.filter(k => k !== key) : [...old, key])
  const rowActions = (r: DeliveryClass) => <Space wrap>
    {hasClassPermission(permissions, 'zsjos:student:create') && r.status === 'SERVING' && r.classKind !== 'EXCEPTION' && <Button type="link" onClick={() => onStudent(r)}>添加学员</Button>}
    {canQueryStudents && <Button type="link" onClick={() => openStudents(r)}>查看学员</Button>}
    {hasClassPermission(permissions, 'zsjos:delivery-class:update') && <>
      {r.availableActions?.includes('UPDATE') && <Button type="link" onClick={() => onEdit(r)}>编辑</Button>}
      {r.availableActions?.includes('MOVE_GROUP') && <Button type="link" onClick={() => setOrganizer({ open: true, single: r })}>调整归组</Button>}
    </>}
    {r.availableActions?.includes('COMPLETE') && hasClassPermission(permissions, 'zsjos:delivery-class:complete') && <Popconfirm title="确认结课该班级？" onConfirm={() => onComplete(r)}><Button type="link" danger>结课</Button></Popconfirm>}
  </Space>
  const groupActions = (n: ClassGroupNode) => <Space wrap>
    <Button type="link" onClick={() => toggle(n.key)}>{expanded.includes(n.key) ? '收起' : '展开'}</Button>
    {n.group?.canCreate && hasClassPermission(permissions, 'zsjos:delivery-class:create') && <Button type="link" onClick={() => onAdd(n.group!)}>添加小班</Button>}
    {n.group?.canUpdate && hasClassPermission(permissions, 'zsjos:delivery-class:update') && <>
      <Button type="link" onClick={() => setEditor({ open: true, group: n.group })}>编辑班级组</Button>
      <Button type="link" onClick={() => setOrganizer({ open: true, group: n.group })}>整理小班</Button>
    </>}
    {n.group?.canDelete && hasClassPermission(permissions, 'zsjos:delivery-class:group-delete') && n.classCount === 0 && <Popconfirm title="确认删除这个空班级组？" onConfirm={async () => {
      try { await classGroups.remove(n.group!.id, n.group!.version); message.success('空班级组已删除'); await refresh() } catch (e) { message.error(errorMessage(e)) }
    }}><Button type="link" danger>删除空组</Button></Popconfirm>}
  </Space>
  const more = (key: string) => {
    const child = children[key]
    if (!child || child.loading) return <Spin size="small" />
    if (child.denied) return <Alert type="warning" showIcon message={child.error} />
    if (child.error) return <Alert type="error" showIcon message={child.error} action={<Button onClick={() => void loadChildren(key, child.page + 1)}>重试</Button>} />
    if (!child.rows.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无符合条件的小班" />
    return child.rows.length < child.total ? <Button onClick={() => void loadChildren(key, child.page + 1)}>加载更多小班（{child.rows.length}/{child.total}）</Button> : <Typography.Text type="secondary">已加载全部小班</Typography.Text>
  }
  const card = (r: DeliveryClass) => <Card key={`class:${r.id}`} className={`delivery-class-card${isSystemClass(r) ? ' delivery-class-system' : ''}`} hoverable={canQueryStudents} onClick={() => openStudents(r)}>
    <div className="delivery-class-card-head"><Typography.Title level={5}>{r.className}</Typography.Title>{statusTag(r)}</div>
    {r.homeroomUserNameSnapshot && <Typography.Text type="secondary" className="delivery-class-card-homeroom">班主任：{r.homeroomUserNameSnapshot}</Typography.Text>}
    {!isSystemClass(r) && <>
      {projects(r.projects)}
      <div className="delivery-class-card-meta"><span>考期</span><strong>{classDate(r)}</strong></div>
    </>}
    <div className="delivery-class-card-foot"><span>{r.studentCount} 条服务</span>{!isSystemClass(r) && <span>{r.classNo}</span>}</div>
    <div onClick={e => e.stopPropagation()}>{rowActions(r)}</div>
  </Card>
  const tableRows = useMemo(() => nodes.map<TableRow>(node => {
    const result: TableRow = { key: node.key, node, row: node.single }
    if (node.type !== 'CLASS') {
      result.children = (children[node.key]?.rows || []).map(row => ({ key: `child:${node.key}:${row.id}`, row: { ...row, groupName: node.group?.name }, parent: node.key }))
      result.children.push({ key: `more:${node.key}`, parent: node.key, more: true })
    }
    return result
  }), [nodes, children, expanded])
  const groupStatus = (n: ClassGroupNode) => n.type === 'SYSTEM' ? <Tag color="blue">系统分组</Tag> : n.servingCount && n.completedCount ? `服务中 ${n.servingCount}／已结课 ${n.completedCount}` : n.completedCount ? '已结课' : n.classCount ? '服务中' : '空组'
  return <>
    <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
      <Typography.Text type="secondary" aria-live="polite">{loading ? '查询中…' : error ? '查询未完成' : `${filtered ? '筛选结果：' : ''}共 ${total} 个展示条目 · ${classTotal} 个班级`}</Typography.Text>
      <Button disabled={!expanded.length} onClick={() => setExpanded([])}>收起全部</Button>
    </Space>
    {table ? <BusinessTable<TableRow> tableKey="delivery-class-management" rowKey="key" dataSource={tableRows} loading={loading} error={error} unauthorized={denied} onReload={() => error ? retryLoad() : load(page, true)} emptyText="暂无符合条件的班级"
      expandable={{ expandedRowKeys: expanded, onExpand: (_open, row) => toggle(row.key), showExpandColumn: false, indentSize: 0 }}
      rowClassName={r => [r.node && r.node.type !== 'CLASS' ? 'delivery-group-row' : r.parent ? 'delivery-group-child-row' : '', isSystemRow(r) ? 'delivery-class-system-row' : ''].filter(Boolean).join(' ')}
      pagination={{ current: page, pageSize: SIZE, total, showSizeChanger: false, onChange: p => void load(p, true) }}
      columns={[
        { title: '班级名称', key: 'name', width: 250, ellipsis: false, render: (_, r) => r.more ? more(r.parent!) : r.node && r.node.type !== 'CLASS' ? <Button className="delivery-group-name" type="text" icon={expanded.includes(r.key) ? <DownOutlined /> : <RightOutlined />} onClick={() => toggle(r.key)}>{r.node.name} · {r.node.classCount}个班级</Button> : canQueryStudents ? <Typography.Link className={`delivery-class-name${r.parent ? ' delivery-group-child-name' : ''}`} onClick={() => openStudents(r.row!)}>{r.row?.className}</Typography.Link> : r.row?.className },
        { title: '班级编号', key: 'no', width: 190, render: (_, r) => r.more || isSystemRow(r) ? '' : r.row?.classNo || '—' },
        { title: '状态', key: 'status', width: 170, render: (_, r) => r.more ? '' : r.row ? statusTag(r.row) : groupStatus(r.node!) },
        { title: '项目', key: 'projects', width: 220, ellipsis: false, render: (_, r) => r.more || isSystemRow(r) ? '' : projects(r.row?.projects || r.node?.projects) },
        { title: '班主任', key: 'homeroom', width: 150, render: (_, r) => r.more ? '' : r.row ? r.row.homeroomUserNameSnapshot || (isSystemClass(r.row) ? '' : '未记录') : `${r.node?.homeroomCount}位班主任` },
        { title: '考期', key: 'date', width: 210, render: (_, r) => r.more || isSystemRow(r) ? '' : r.row ? classDate(r.row) : r.node?.examDate },
        { title: '服务数量', key: 'count', width: 110, render: (_, r) => r.more ? '' : r.row ? r.row.studentCount : r.node?.serviceCount },
        { title: '操作', key: 'action', width: managed ? 390 : 110, ellipsis: false, render: (_, r) => r.more ? '' : r.row ? rowActions(r.row) : groupActions(r.node!) },
      ]} /> : <>
      {error && <Alert type={denied ? 'warning' : 'error'} showIcon message={error} action={!denied && <Button onClick={() => void retryLoad()}>重试</Button>} />}
      {loading && !nodes.length ? <Spin /> : !nodes.length && !error ? <Empty description="暂无符合条件的班级" /> : <div className="delivery-class-grid">{nodes.map(n => n.type === 'CLASS' ? card(n.single!) : <section key={n.key} className={expanded.includes(n.key) ? 'delivery-group-block delivery-group-block-expanded' : 'delivery-group-block'}>
        <Card className={`delivery-group-card${n.type === 'SYSTEM' ? ' delivery-class-system' : ''}`} hoverable onClick={() => toggle(n.key)}>
          <div className="delivery-class-card-head"><Typography.Title level={5}>{expanded.includes(n.key) ? <DownOutlined /> : <RightOutlined />} {n.name}</Typography.Title><span>{groupStatus(n)}</span></div>
          {n.type !== 'SYSTEM' && <div>考期：{n.examDate}</div>}
          <div>{n.classCount} 个班级 · {n.serviceCount} 条服务</div>
          {n.type !== 'SYSTEM' && projects(n.projects)}
          <div onClick={e => e.stopPropagation()}>{groupActions(n)}</div>
        </Card>
        {expanded.includes(n.key) && <div className="delivery-group-children"><div className="delivery-class-grid">{children[n.key]?.rows.map(r => card({ ...r, groupName: n.group?.name }))}</div><div className="delivery-group-more">{more(n.key)}</div></div>}
      </section>)}</div>}
      <div ref={sentinel} className="delivery-class-sentinel">{loading && nodes.length ? '加载中…' : nodes.length < total ? '加载更多' : nodes.length ? '已加载全部班级组及独立班级' : ''}</div>
    </>}
    <ClassGroupEditor {...editor} onClose={() => setEditor({ open: false })} onSaved={() => void refresh()} />
    <ClassGroupOrganizer {...organizer} onClose={() => setOrganizer({ open: false })} onSaved={() => void refresh()} />
  </>
})

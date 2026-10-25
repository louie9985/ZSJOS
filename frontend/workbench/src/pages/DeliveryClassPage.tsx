import StudentEntryDialog from '../components/StudentEntryDialog'
import { PlusOutlined, QuestionCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, DatePicker, Form, Input, Modal, Segmented, Select, Space, Typography, message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import DeliveryClassGroups, { type ClassGroupListHandle } from './DeliveryClassGroups'
import { GroupPicker } from '../components/DeliveryClassGroupDialogs'
import { classGroups, type ClassGroup, type GroupMode } from '../services/deliveryClassGroups'
import { useInboxTableLayout } from '../services/inboxLayout'
import { api, type DeliveryClass, type DeliveryClassFilterContext, type HomeroomCandidate } from '../services/api'
import DeliveryClassFilters, { defaultClassFilters, type ClassFilters } from './DeliveryClassFilters'
import DeliveryClassExamCalendar from './DeliveryClassExamCalendar'

type ClassForm = { className: string; startDate?: Dayjs; homeroomUserId?: number; homeroomUserIds?: number[]; groupMode: GroupMode; groupId?: number; groupName?: string; referenceDate?: Dayjs }
const has = (permissions: string[], permission: string) => permissions.includes('*:*:*') || permissions.includes(permission)
function savedView(key: string): { status?: string; keyword?: string; filters?: ClassFilters } {
  try { return JSON.parse(sessionStorage.getItem(key) || '{}') } catch { return {} }
}
export default function DeliveryClassPage({ permissions = [] }: { permissions?: string[]; manage?: boolean }) {
  const [entry, setEntry] = useState<{ open: boolean; classId?: number }>({ open: false })
  const { useTableLayout } = useInboxTableLayout()
  const manage = has(permissions, 'zsjos:delivery-class:query-managed')
  const managedRead = manage || has(permissions, 'zsjos:delivery-class:query-all') && has(permissions, 'zsjos:delivery-class:query')
  const viewKey = `delivery-class-view:${manage}:${permissions.slice().sort().join(',')}`
  const [status, setStatus] = useState(() => savedView(viewKey).status ?? 'SERVING')
  const [keyword, setKeyword] = useState(() => savedView(viewKey).keyword ?? '')
  const [search, setSearch] = useState(() => savedView(viewKey).keyword ?? '')
  const [filters, setFilters] = useState<ClassFilters>(() => savedView(viewKey).filters ?? defaultClassFilters)
  useEffect(() => { try { sessionStorage.setItem(viewKey, JSON.stringify({ status, keyword, filters })) } catch { /* Optional session preferences. */ } }, [viewKey, status, keyword, filters])
  const [context, setContext] = useState<DeliveryClassFilterContext>()
  const [contextLoading, setContextLoading] = useState(true)
  const [contextError, setContextError] = useState('')
  const canQuery = has(permissions, 'zsjos:delivery-class:query') || manage || has(permissions, 'zsjos:delivery-class:query-my')
  const contextRequest = useRef(0)
  const listRef = useRef<ClassGroupListHandle>(null)
  const [fixedGroup, setFixedGroup] = useState<ClassGroup>()
  const groupModeChosen = useRef(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<DeliveryClass>()
  const [saving, setSaving] = useState(false)
  const saveLock = useRef(false)
  const [calendarOpen, setCalendarOpen] = useState(false)
  const [candidates, setCandidates] = useState<HomeroomCandidate[]>([])
  const [form] = Form.useForm<ClassForm>()
  const selectedHomerooms = Form.useWatch('homeroomUserIds', form) as number[] | undefined
  useEffect(() => { const timer = window.setTimeout(() => setSearch(keyword.trim()), 300); return () => window.clearTimeout(timer) }, [keyword])
  const query = useMemo(() => ({ status: status || undefined, keyword: search || undefined, projectCategoryId: filters.projectCategoryId,
    homeroomUserId: filters.homeroomUserId, examDateStart: filters.dateRange?.[0], examDateEnd: filters.dateRange?.[1] }),
    [status, search, filters.projectCategoryId, filters.homeroomUserId, filters.dateRange])
  const loadContext = useCallback(async () => {
    const id = ++contextRequest.current
    if (!canQuery) { setContext(undefined); setContextLoading(false); return }
    setContextLoading(true); setContextError('')
    try { const data = await api.deliveryClasses.filterContext(managedRead); if (id === contextRequest.current) setContext(data) }
    catch (e) { if (id === contextRequest.current) { setContext(undefined); setContextError(e instanceof Error ? e.message : '筛选选项加载失败') } }
    finally { if (id === contextRequest.current) setContextLoading(false) }
  }, [canQuery, managedRead])
  useEffect(() => { setContext(undefined); void loadContext(); return () => { contextRequest.current++ } }, [loadContext])
  const load = async () => { await listRef.current?.refresh() }
  const openEditor = async (row?: DeliveryClass, parent?: ClassGroup) => {
    if (row ? !has(permissions, 'zsjos:delivery-class:update') || !row.availableActions?.includes('UPDATE')
      : !has(permissions, 'zsjos:delivery-class:create') || !!parent && !parent.canCreate) return
    setFixedGroup(parent); groupModeChosen.current = !!parent
    setEditing(row); setEditOpen(true); setSaving(true)
    try {
      const candidateRows = await api.deliveryClasses.candidates()
      setCandidates(candidateRows)
      form.resetFields()
      form.setFieldsValue(row ? { className: row.className, startDate: row.startDate ? dayjs(row.startDate) : undefined,
        homeroomUserId: row.homeroomUserId } : { className: parent?.name, startDate: parent?.referenceDate ? dayjs(parent.referenceDate) : undefined, groupMode: parent ? 'EXISTING' : 'NONE', groupId: parent?.id })
    } catch (e) { message.error(e instanceof Error ? e.message : '创建班级所需数据加载失败，请重试'); setEditOpen(false) }
    finally { setSaving(false) }
  }
  const save = async () => {
    if (editing ? !has(permissions, 'zsjos:delivery-class:update') || !editing.availableActions?.includes('UPDATE')
      : !has(permissions, 'zsjos:delivery-class:create')) return
    if (saveLock.current || saving) return
    saveLock.current = true
    setSaving(true)
    try {
      const value = await form.validateFields().catch(() => undefined)
      if (!value) return
      const payload = { className: value.className.trim(), startDate: value.startDate?.format('YYYY-MM-DD') }
      if (editing) {
        await api.deliveryClasses.update(editing.id, { ...payload, preserveExamDate: payload.startDate === (editing.startDate || undefined),
          homeroomUserId: value.homeroomUserId!, version: editing.version })
        message.success('班级已更新')
      } else {
        const result = await classGroups.createClasses({ ...payload, homeroomUserIds: value.homeroomUserIds!, groupMode: value.groupMode,
          groupId: value.groupMode === 'EXISTING' ? fixedGroup?.id ?? value.groupId : undefined,
          newGroup: value.groupMode === 'NEW' ? { name: value.groupName!.trim(), referenceDate: value.referenceDate?.format('YYYY-MM-DD') } : undefined })
        message.success(`已创建 ${result.classIds.length} 个班级`)
      }
      setEditOpen(false); form.resetFields(); await Promise.all([load(), loadContext()])
    } catch (e) { message.error(e instanceof Error ? e.message : '班级保存失败') }
    finally { saveLock.current = false; setSaving(false) }
  }
  const complete = async (row: DeliveryClass) => {
    if (!has(permissions, 'zsjos:delivery-class:complete') || !row.availableActions?.includes('COMPLETE')) return
    try { await api.deliveryClasses.complete(row.id); message.success('班级已结课'); await Promise.all([load(), loadContext()]) }
    catch (e) { message.error(e instanceof Error ? e.message : '结课失败') }
  }
  if (!canQuery) return <Alert type="warning" showIcon message="暂无班级查询权限" />
  return <section className="workspace-page">
    <StudentEntryDialog {...entry} type="teaching" onClose={() => setEntry({ open: false })} onSaved={() => { void load(); void loadContext() }} />
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>班级管理</Typography.Title>
        <Space wrap>
          {has(permissions, 'zsjos:student:create') && <Button icon={<PlusOutlined />} onClick={() => setEntry({ open: true })}>添加学员</Button>}
          <Button icon={<ReloadOutlined />} aria-label="刷新" onClick={() => { void load(); void loadContext() }} />
          {manage && has(permissions, 'zsjos:delivery-class:create') && <Button onClick={() => listRef.current?.createGroup()}>创建班级组</Button>}
          {has(permissions, 'zsjos:delivery-class:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => void openEditor()}>创建班级</Button>}
        </Space>
      </Space>
      <DeliveryClassFilters value={filters} onChange={setFilters} context={context} loading={contextLoading} keyword={keyword} onKeyword={setKeyword}
        onSearch={() => { if (keyword.trim() === search) void load(); else setSearch(keyword.trim()) }}
        onReset={() => { setFilters(defaultClassFilters); setKeyword(''); setSearch(''); setStatus('SERVING') }} />
      {contextError && <Alert type="error" showIcon message={contextError} action={<Button onClick={() => void loadContext()}>重试筛选选项</Button>} />}
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Segmented value={status} onChange={value => setStatus(String(value))} options={[{ label: '服务中', value: 'SERVING' }, { label: '已结课', value: 'COMPLETED' }, { label: '全部', value: '' }]} />
      </Space>
      <DeliveryClassGroups onStudent={row => setEntry({ open: true, classId: row.id })} ref={listRef} query={query} managed={managedRead} table={useTableLayout} permissions={permissions}
        onEdit={row => void openEditor(row)} onAdd={parent => void openEditor(undefined, parent)} onComplete={complete} />
      {contextLoading && !context && <Typography.Text type="secondary">筛选选项加载中…</Typography.Text>}
    </Space>
    <Modal open={editOpen} title={editing ? '编辑班级' : '创建班级'} confirmLoading={saving}
      okText={editing ? '保存' : selectedHomerooms?.length ? `确认创建 ${selectedHomerooms.length} 个班级` : '创建班级'}
      onOk={() => void save()} onCancel={() => { if (!saving) setEditOpen(false) }} closable={!saving} keyboard={!saving} maskClosable={!saving} destroyOnHidden>
      <Form form={form} layout="vertical" disabled={saving} onValuesChange={changed => {
        if ('groupMode' in changed || 'groupName' in changed || 'referenceDate' in changed) groupModeChosen.current = true
        if (changed.groupMode === 'NEW') form.setFieldsValue({ groupName: form.getFieldValue('groupName') || form.getFieldValue('className'), referenceDate: form.getFieldValue('referenceDate') || form.getFieldValue('startDate') })
        if (!editing && manage && 'homeroomUserIds' in changed && !groupModeChosen.current) {
          const multiple = (changed.homeroomUserIds?.length || 0) > 1
          form.setFieldsValue({ groupMode: multiple ? 'NEW' : 'NONE' })
          if (multiple) form.setFieldsValue({ groupName: form.getFieldValue('className'), referenceDate: form.getFieldValue('startDate') })
        }
        if (!editing && !groupModeChosen.current && form.getFieldValue('groupMode') === 'NEW') {
          if ('className' in changed) form.setFieldValue('groupName', changed.className)
          if ('startDate' in changed) form.setFieldValue('referenceDate', changed.startDate)
        }
      }}>
        <Form.Item name="className" label="班级名称" rules={[{ required: true, whitespace: true, message: '请填写班级名称' }]}><Input maxLength={100} placeholder="请填写班级名称" /></Form.Item>
        <Form.Item name="startDate" label="考期日期" extra="可暂不填写，后续通过编辑班级补填。"><DatePicker style={{ width: '100%' }} placeholder="选择日期" /></Form.Item>
        <Form.Item><Button onClick={() => setCalendarOpen(true)}>选择已有考期</Button></Form.Item>
        {editing ? <Form.Item name="homeroomUserId" label="班主任" rules={[{ required: true, message: '请选择班主任' }]}>
          <Select showSearch optionFilterProp="label" options={candidates.map(row => ({ label: `${row.name}${row.deptName ? ` · ${row.deptName}` : ''}`, value: row.id }))} />
        </Form.Item> : <Form.Item name="homeroomUserIds" label="班主任" rules={[{ required: true, type: 'array', min: 1, message: '请选择至少一位班主任' }]}
          extra="每位学习规划师分别创建一个同名班级，考期日期相同。">
          <Select mode="multiple" showSearch optionFilterProp="label" placeholder="可选择多位班主任" options={candidates.map(row => ({ label: `${row.name}${row.deptName ? ` · ${row.deptName}` : ''}`, value: row.id }))} />
        </Form.Item>}
        {!editing && !manage && <Form.Item name="groupMode" hidden initialValue="NONE"><Input /></Form.Item>}
        {!editing && manage && <>
          <Form.Item name="groupMode" label="所属班级组" rules={[{ required: true }]} tooltip={{
            icon: <QuestionCircleOutlined tabIndex={0} aria-label="班级组说明" />,
            trigger: ['hover', 'focus', 'click'],
            title: <Space direction="vertical" size={4}>
              <span>班级组是用于收纳多个小班的父级。</span>
              <span>独立创建：只创建小班，不加入班级组。</span>
              <span>加入已有班级组：将新建的小班放入可维护的同部门班级组。</span>
              <span>新建班级组并创建：新建父级，再将本次创建的小班统一放入其中。</span>
              <span>多选班主任时，每位班主任创建一个小班，默认新建班级组收纳。</span>
              <span>小班的班主任、学员和考期仍独立；父级参考考期仅作为后续新建小班的默认日期，不覆盖已有小班。</span>
            </Space>,
          }}>
            <Select disabled={!!fixedGroup} options={[{ label: '独立创建', value: 'NONE' }, { label: '加入已有班级组', value: 'EXISTING' }, { label: '新建班级组并创建', value: 'NEW' }]} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(before, after) => before.groupMode !== after.groupMode}>
            {({ getFieldValue }) => getFieldValue('groupMode') === 'EXISTING' ? fixedGroup ? <Typography.Paragraph>所属班级组：{fixedGroup.name}</Typography.Paragraph> :
              <Form.Item name="groupId" label="已有班级组" rules={[{ required: true, message: '请选择班级组' }]}><GroupPicker purpose="create" onSelected={g => { if (g.referenceDate) form.setFieldValue('startDate', dayjs(g.referenceDate)) }} /></Form.Item> :
              getFieldValue('groupMode') === 'NEW' ? <>
                <Form.Item name="groupName" label="父级名称" rules={[{ required: true, whitespace: true, message: '请填写父级名称' }]}><Input maxLength={100} /></Form.Item>
                <Form.Item name="referenceDate" label="父级参考考期" extra="只作为后续新建小班的默认值，不覆盖已有小班。"><DatePicker style={{ width: '100%' }} /></Form.Item>
              </> : null}
          </Form.Item>
        </>}
      </Form>
    </Modal>
    <DeliveryClassExamCalendar open={calendarOpen} initialDate={form.getFieldValue('startDate')?.format('YYYY-MM-DD')}
      onClose={() => setCalendarOpen(false)} onSelect={date => {
        const selected = dayjs(date)
        form.setFieldsValue({ startDate: selected, ...(!groupModeChosen.current && form.getFieldValue('groupMode') === 'NEW' ? { referenceDate: selected } : {}) }); setCalendarOpen(false)
      }} />
  </section>
}

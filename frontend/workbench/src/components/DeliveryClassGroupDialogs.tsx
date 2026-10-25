import { Alert, Button, DatePicker, Form, Input, Modal, Select, Space, Typography, message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useEffect, useRef, useState } from 'react'
import BusinessTable from './BusinessTable'
import { classGroups, type ClassGroup } from '../services/deliveryClassGroups'
import type { DeliveryClass } from '../services/api'

export function GroupPicker({ value, onChange, onSelected, purpose = 'update' }: { value?: number; onChange?: (id?: number) => void; onSelected?: (group: ClassGroup) => void; purpose?: 'create' | 'update' }) {
  const [rows, setRows] = useState<ClassGroup[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const request = useRef(0)
  const load = async (search: string, target = 1) => {
    const id = ++request.current; setLoading(true); setError('')
    try {
      const result = await classGroups.options(search, target)
      if (id !== request.current) return
      setRows(old => target === 1 ? result.list : [...old, ...result.list]); setPage(target); setTotal(result.total)
    } catch (e) { if (id === request.current) { setRows([]); setError(e instanceof Error ? e.message : '班级组加载失败') } }
    finally { if (id === request.current) setLoading(false) }
  }
  useEffect(() => { const timer = window.setTimeout(() => void load(keyword), 250); return () => { window.clearTimeout(timer); request.current++ } }, [keyword])
  return <Space direction="vertical" style={{ width: '100%' }}>
    <Select aria-label="选择已有班级组" value={value} onChange={id => { onChange?.(id); const group = rows.find(r => r.id === id); if (group) onSelected?.(group) }} showSearch filterOption={false} onSearch={setKeyword} loading={loading} style={{ width: '100%' }}
      placeholder="搜索同部门班级组" options={rows.filter(r => purpose === 'create' ? r.canCreate : r.canUpdate).map(r => ({ value: r.id, label: r.name }))}
      notFoundContent={loading ? '加载中…' : error || '暂无可维护的班级组'}
      onPopupScroll={e => { const el = e.currentTarget; if (!loading && !error && rows.length < total && el.scrollTop + el.clientHeight >= el.scrollHeight - 12) void load(keyword, page + 1) }} />
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void load(keyword)}>重试</Button>} />}
  </Space>
}

export function ClassGroupEditor({ group, open, onClose, onSaved }: { group?: ClassGroup; open: boolean; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm<{ name: string; referenceDate?: Dayjs }>()
  const [saving, setSaving] = useState(false)
  const saveLock = useRef(false)
  useEffect(() => { if (open) { form.resetFields(); form.setFieldsValue({ name: group?.name, referenceDate: group?.referenceDate ? dayjs(group.referenceDate) : undefined }) } }, [open, group, form])
  const save = async () => {
    if (saving || saveLock.current) return
    saveLock.current = true
    setSaving(true)
    try {
      const value = await form.validateFields().catch(() => undefined); if (!value) return
      const data = { name: value.name.trim(), referenceDate: value.referenceDate?.format('YYYY-MM-DD'), version: group?.version }
      if (group) await classGroups.update(group.id, data); else await classGroups.create(data)
      message.success(group ? '班级组已更新' : '班级组已创建'); onSaved(); onClose()
    } catch (e) { message.error(e instanceof Error ? e.message : '班级组保存失败') }
    finally { saveLock.current = false; setSaving(false) }
  }
  return <Modal title={group ? '编辑班级组' : '创建班级组'} open={open} onOk={() => void save()} onCancel={() => { if (!saving) onClose() }} cancelButtonProps={{ disabled: saving }} confirmLoading={saving} closable={!saving} maskClosable={!saving} keyboard={!saving} destroyOnHidden>
    <Form form={form} layout="vertical" disabled={saving}>
      <Form.Item name="name" label="班级组名称" rules={[{ required: true, whitespace: true, message: '请填写班级组名称' }]}><Input maxLength={100} /></Form.Item>
      <Form.Item name="referenceDate" label="参考考期" extra="仅作为新建小班的默认日期，修改不会覆盖已有小班。"><DatePicker style={{ width: '100%' }} /></Form.Item>
      <Typography.Text type="secondary">归属当前部门；可以先创建空组，再添加小班。</Typography.Text>
    </Form>
  </Modal>
}

export function ClassGroupOrganizer({ open, group, single, onClose, onSaved }: { open: boolean; group?: ClassGroup; single?: DeliveryClass; onClose: () => void; onSaved: () => void }) {
  const [rows, setRows] = useState<DeliveryClass[]>([])
  const [selected, setSelected] = useState<DeliveryClass[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [search, setSearch] = useState('')
  const [target, setTarget] = useState<number>()
  const [targetName, setTargetName] = useState('')
  const [destination, setDestination] = useState<'GROUP' | 'NONE'>('GROUP')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [confirm, setConfirm] = useState(false)
  const saveLock = useRef(false)
  const request = useRef(0)
  const load = async (targetPage = page) => {
    const id = ++request.current; setLoading(true); setError('')
    try { const result = await classGroups.candidates(search, targetPage); if (id === request.current) {
      setRows(result.list); setTotal(result.total); setPage(targetPage)
      setSelected(old => old.map(row => result.list.find(current => current.id === row.id) || row))
    } }
    catch (e) { if (id === request.current) { setRows([]); setError(e instanceof Error ? e.message : '班级加载失败') } }
    finally { if (id === request.current) setLoading(false) }
  }
  useEffect(() => {
    if (!open) return
    setSelected(single ? [single] : []); setTarget(group?.id); setTargetName(group?.name || ''); setDestination('GROUP'); setKeyword(''); setSearch(''); setPage(1); setConfirm(false)
  }, [open, single, group])
  useEffect(() => { if (open && !single) void load(1); return () => { request.current++ } }, [open, single, search])
  const save = async () => {
    if (saving || saveLock.current) return
    saveLock.current = true
    setSaving(true)
    try { await classGroups.move(selected, destination === 'NONE' ? undefined : target); message.success('班级归属已调整'); onSaved(); onClose() }
    catch (e) { message.error(e instanceof Error ? e.message : '调整失败，请刷新班级后重试'); setConfirm(false) }
    finally { saveLock.current = false; setSaving(false) }
  }
  // Review shares the selection dialog so sibling portal layers cannot hide its content.
  return <Modal title={confirm ? '确认调整班级归属' : single ? '调整所属班级组' : '整理小班'} open={open} width={900}
      onCancel={() => { if (!saving) { if (confirm) setConfirm(false); else onClose() } }}
      cancelText={confirm ? '返回修改' : '取消'} cancelButtonProps={{ disabled: saving }} confirmLoading={saving} closable={!saving} maskClosable={!saving} keyboard={!saving} destroyOnHidden
      okText={confirm ? '确认调整' : '核对调整'} okButtonProps={{ disabled: saving || selected.length === 0 || selected.length > 200 || (destination === 'GROUP' && !target) }} onOk={() => { if (confirm) void save(); else setConfirm(true) }}>
      {confirm ? <>
        <Typography.Paragraph>共 {selected.length} 个小班将{destination === 'NONE' ? '移出为独立班级' : '加入目标班级组'}。已有归属的小班会从原组移出。</Typography.Paragraph>
        <div className="delivery-group-confirm-list">{selected.map(r => <div key={r.id}>{r.className} · {r.classNo}：{r.groupName || (r.groupId ? '已有班级组' : '独立班级')} → {destination === 'NONE' ? '独立班级' : targetName}</div>)}</div>
      </> : <Space direction="vertical" style={{ width: '100%' }}>
        <Alert type="info" showIcon message="只改变班级收纳关系，不改变学员、班主任、考期或服务状态；每次最多调整200个班级。" />
        {single ? <Typography.Text>{single.className} · {single.classNo}</Typography.Text> : <>
          <Input.Search value={keyword} onChange={e => setKeyword(e.target.value)} placeholder="搜索班级名称或编号" onSearch={() => { if (search === keyword.trim()) void load(1); else setSearch(keyword.trim()) }} />
          <BusinessTable<DeliveryClass> tableKey="delivery-group-member-picker" mode="compact" rowKey="id" dataSource={rows} loading={loading} error={error} onReload={() => load()} emptyText="暂无可整理的普通班级"
            rowSelection={{ selectedRowKeys: selected.map(r => r.id), preserveSelectedRowKeys: true, onChange: (_keys, items) => setSelected(items), getCheckboxProps: () => ({ disabled: saving }) }}
            pagination={{ current: page, total, pageSize: 12, showSizeChanger: false, onChange: p => void load(p) }}
            columns={[{ title: '班级名称', dataIndex: 'className' }, { title: '班级编号', dataIndex: 'classNo' }, { title: '班主任', dataIndex: 'homeroomUserNameSnapshot' }, { title: '当前归属', key: 'group', render: (_, r) => r.groupName || (r.groupId ? '已有班级组' : '独立班级') }]} />
        </>}
        <Select aria-label="调整方式" disabled={saving} value={destination} onChange={setDestination} style={{ width: '100%' }} options={[{ label: '加入或移入班级组', value: 'GROUP' }, { label: '移出为独立班级', value: 'NONE' }]} />
        {destination === 'GROUP' && (group ? <Typography.Text>目标班级组：{group.name}</Typography.Text> : <GroupPicker value={target} onChange={setTarget} onSelected={g => setTargetName(g.name)} />)}
        <Typography.Text>已选 {selected.length} 个班级</Typography.Text>
      </Space>}
    </Modal>
}

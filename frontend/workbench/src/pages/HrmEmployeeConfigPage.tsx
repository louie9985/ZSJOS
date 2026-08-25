import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Skeleton, Space, Switch, Tabs, Tag, message } from 'antd'
import { ReloadOutlined, SaveOutlined } from '@ant-design/icons'
import { api, type HrmEmployeeFieldConfig } from '../services/api'
import { ENTRY_STATUS } from '../services/hrm'

type CreateField = HrmEmployeeFieldConfig & { pendingVisible?: boolean }

export default function HrmEmployeeConfigPage({ permissions }: { permissions: string[] }) {
  const [createFields, setCreateFields] = useState<CreateField[]>([])
  const [archiveFields, setArchiveFields] = useState<HrmEmployeeFieldConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const canUpdate = permissions.includes('hrm:employee:config:update')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const [active, pending, archive] = await Promise.all([
        api.hrm.employee.config.createFieldList(ENTRY_STATUS.ACTIVE),
        api.hrm.employee.config.createFieldList(ENTRY_STATUS.PENDING_ENTRY),
        api.hrm.employee.config.archiveFieldList()
      ])
      const pendingMap = new Map(pending.map(item => [item.name, item]))
      setCreateFields(active.map(item => ({ ...item, pendingVisible: pendingMap.get(item.name)?.visible ?? item.visible })))
      setArchiveFields(archive)
    } catch (e) { setError(e instanceof Error ? e.message : '员工字段配置加载失败') }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { void load() }, [load])

  const save = async () => {
    setSaving(true)
    try {
      await Promise.all([
        api.hrm.employee.config.saveCreateField(ENTRY_STATUS.ACTIVE, createFields.map(item => ({ name: item.name, visible: item.visible }))),
        api.hrm.employee.config.saveCreateField(ENTRY_STATUS.PENDING_ENTRY, createFields.map(item => ({ name: item.name, visible: item.pendingVisible ?? item.visible }))),
        api.hrm.employee.config.saveArchiveField(archiveFields.map(item => ({ name: item.name, visible: item.visible, editable: item.editable })))
      ])
      message.success('配置已保存'); await load()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const content = loading ? <Skeleton active paragraph={{ rows: 8 }}/> : error
    ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
    : <Tabs items={[
      { key: 'create', label: '新建员工字段', children: <HrmProTable advanced persistenceKey="employee-config" onReload={load} rowKey="name" size="small" pagination={false} dataSource={createFields} columns={[
        { title: '字段分组', dataIndex: 'groupName', width: 180 }, { title: '字段名称', dataIndex: 'title' },
        { title: '在职员工', width: 120, render: (_, row) => <Switch checked={row.visible} disabled={!canUpdate || row.visibleLocked} onChange={value => setCreateFields(items => items.map(item => item.name === row.name ? { ...item, visible: value } : item))}/> },
        { title: '待入职员工', width: 120, render: (_, row) => <Switch checked={row.pendingVisible} disabled={!canUpdate || row.visibleLocked} onChange={value => setCreateFields(items => items.map(item => item.name === row.name ? { ...item, pendingVisible: value } : item))}/> }
      ]}/> },
      { key: 'archive', label: '员工档案字段', children: <HrmProTable advanced persistenceKey="employee-config" onReload={load} rowKey="name" size="small" pagination={false} dataSource={archiveFields} columns={[
        { title: '字段分组', dataIndex: 'groupName', width: 180 }, { title: '字段名称', dataIndex: 'title' },
        { title: '显示', width: 100, render: (_, row) => <Switch checked={row.visible} disabled={!canUpdate || row.visibleLocked} onChange={value => setArchiveFields(items => items.map(item => item.name === row.name ? { ...item, visible: value } : item))}/> },
        { title: '可编辑', width: 100, render: (_, row) => <Switch checked={row.editable} disabled={!canUpdate || row.editableLocked} onChange={value => setArchiveFields(items => items.map(item => item.name === row.name ? { ...item, editable: value } : item))}/> }
      ]}/> }
    ]}/>

  return <section className="workspace-page hrm-page hrm-employee-config-page">
    <div className="page-heading"><Tag color="blue">员工设置</Tag><Space><Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>{canUpdate && <Button type="primary" icon={<SaveOutlined/>} loading={saving} onClick={() => void save()}>保存</Button>}</Space></div>
    <div className="hrm-table-area">{content}</div>
  </section>
}

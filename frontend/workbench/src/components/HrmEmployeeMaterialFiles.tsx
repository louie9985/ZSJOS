import HrmProTable from './HrmProTable'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Empty, List, Modal, Skeleton, Space, Upload, message } from 'antd'
import { DeleteOutlined, FolderOpenOutlined, UploadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { api, type HrmEmployeeFile } from '../services/api'
import { EMPLOYEE_FILE_GROUPS } from '../services/hrm'

type FileOption = { group: string; label: string; value: number }

function fileName(url: string) {
  const name = url.split('?')[0]?.split('/').pop() || url
  try { return decodeURIComponent(name) } catch { return name }
}

export default function HrmEmployeeMaterialFiles({ employeeId, canUpdate }: { employeeId: number; canUpdate: boolean }) {
  const [items, setItems] = useState<HrmEmployeeFile[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<FileOption>()
  const [urls, setUrls] = useState<string[]>([])
  const [uploading, setUploading] = useState(false)
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setItems(await api.hrm.employee.file.list(employeeId)) }
    catch (e) { setError(e instanceof Error ? e.message : '材料附件加载失败') }
    finally { setLoading(false) }
  }, [employeeId])
  useEffect(() => { void load() }, [load])

  const options = useMemo<FileOption[]>(() => EMPLOYEE_FILE_GROUPS.flatMap(group =>
    group.options.map(option => ({ group: group.label, label: option.label, value: option.value }))), [])
  const open = (option: FileOption) => {
    setSelected(option)
    setUrls(items.filter(item => item.type === option.value).map(item => item.url))
  }
  const upload = async (file: File) => {
    const extension = file.name.split('.').pop()?.toLowerCase()
    if (!extension || !['png', 'jpg', 'jpeg', 'pdf', 'doc', 'docx', 'xls', 'xlsx'].includes(extension)) {
      message.error('仅支持图片、PDF、Word 和 Excel 文件'); return Upload.LIST_IGNORE
    }
    if (file.size > 20 * 1024 * 1024) { message.error('单个文件不能超过 20MB'); return Upload.LIST_IGNORE }
    if (urls.length >= 20) { message.warning('同类材料最多 20 个'); return Upload.LIST_IGNORE }
    setUploading(true)
    try {
      const url = await api.hrm.employee.uploadFile(file)
      setUrls(current => [...current, url]); message.success('文件已上传')
    }
    catch (e) { message.error(e instanceof Error ? e.message : '上传失败') }
    finally { setUploading(false) }
    return Upload.LIST_IGNORE
  }
  const save = async () => {
    if (!selected) return
    setSaving(true)
    try {
      await api.hrm.employee.file.save({ employeeId, type: selected.value, fileUrls: urls })
      message.success('材料附件已保存'); setSelected(undefined); await load()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  const columns: ColumnsType<FileOption> = [
    { title: '分组', dataIndex: 'group', width: 140, onCell: (_, index) => {
      const current = options[index || 0]
      const previous = index ? options[index - 1] : undefined
      if (previous?.group === current?.group) return { rowSpan: 0 }
      return { rowSpan: options.filter(option => option.group === current?.group).length }
    } },
    { title: '材料类型', dataIndex: 'label' },
    { title: '文件数', width: 90, align: 'center', render: (_, row) => items.filter(item => item.type === row.value).length },
    { title: '操作', width: 100, align: 'center', render: (_, row) => <Button type="link" size="small" icon={<FolderOpenOutlined/>} onClick={() => open(row)}>查看</Button> }
  ]

  if (loading && !items.length) return <Skeleton active paragraph={{ rows: 6 }}/>
  if (error) return <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>
  return <>
    <HrmProTable<FileOption> size="small" rowKey="value" columns={columns} dataSource={options} pagination={false}/>
    <Modal title={selected?.label || '材料附件'} open={!!selected} onCancel={() => setSelected(undefined)} width="min(960px, 96vw)"
      onOk={canUpdate ? () => void save() : undefined} okText="保存" confirmLoading={saving}
      footer={canUpdate ? undefined : <Button onClick={() => setSelected(undefined)}>关闭</Button>} destroyOnHidden>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {canUpdate && <Upload showUploadList={false} multiple beforeUpload={file => { void upload(file); return Upload.LIST_IGNORE }}>
          <Button icon={<UploadOutlined/>} loading={uploading} disabled={urls.length >= 20}>上传材料</Button>
        </Upload>}
        {urls.length ? <List size="small" bordered dataSource={urls} renderItem={(url, index) => <List.Item actions={canUpdate ? [
          <Button key="delete" type="text" danger icon={<DeleteOutlined/>} aria-label="移除文件" onClick={() => setUrls(current => current.filter((_, itemIndex) => itemIndex !== index))}/>
        ] : undefined}><a href={url} target="_blank" rel="noreferrer">{fileName(url)}</a></List.Item>}/>
          : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无材料附件"/>}
      </Space>
    </Modal>
  </>
}

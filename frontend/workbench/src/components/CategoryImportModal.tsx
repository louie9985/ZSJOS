import { useState } from 'react'
import { Alert, Button, Modal, Space, Statistic, Steps, Table, Tag, Upload, message } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { api, type EamCategoryImportItem, type EamCategoryImportResult } from '../services/api'
import { downloadBlob } from '../services/download'
import type { ColumnsType } from 'antd/es/table'
import type { UploadFile } from 'antd/es/upload/interface'

const ACTION_LABELS: Record<EamCategoryImportItem['action'], string> = {
  CREATE: '新增', UPDATE: '更新', SKIP: '跳过', CONFLICT: '冲突'
}
const ACTION_COLORS: Record<EamCategoryImportItem['action'], string> = {
  CREATE: 'success', UPDATE: 'warning', SKIP: 'default', CONFLICT: 'error'
}

export default function CategoryImportModal({ open, onClose, onImported }: {
  open: boolean
  onClose: () => void
  onImported: () => void
}) {
  const [fileList, setFileList] = useState<UploadFile[]>([])
  const [selectedFile, setSelectedFile] = useState<File>()
  const [result, setResult] = useState<EamCategoryImportResult>()
  const [loading, setLoading] = useState(false)
  const [templateLoading, setTemplateLoading] = useState(false)
  const [error, setError] = useState('')

  const reset = () => { setFileList([]); setSelectedFile(undefined); setResult(undefined); setError('') }
  const close = () => { reset(); onClose() }

  const preview = async () => {
    if (!selectedFile) return
    setLoading(true); setError('')
    try { setResult(await api.eam.category.importPreview(selectedFile)) }
    catch (e) { setError(e instanceof Error ? e.message : '预检失败') }
    finally { setLoading(false) }
  }

  const commit = async () => {
    if (!selectedFile || !result || result.conflictCount > 0) return
    setLoading(true); setError('')
    try {
      const committed = await api.eam.category.importCommit(selectedFile)
      setResult(committed)
      message.success(`分类配置已导入：新增 ${committed.createCount}，更新 ${committed.updateCount}，跳过 ${committed.skipCount}`)
      onImported(); close()
    } catch (e) { setError(e instanceof Error ? e.message : '导入失败') }
    finally { setLoading(false) }
  }

  const downloadTemplate = async () => {
    setTemplateLoading(true)
    try { await downloadBlob('/eam/category/get-import-template', '中世健EAM分类配置模板.xlsx') }
    catch (e) { message.error(e instanceof Error ? e.message : '模板下载失败') }
    finally { setTemplateLoading(false) }
  }

  const columns: ColumnsType<EamCategoryImportItem> = [
    { title: '类型', width: 90, align: 'center', render: (_, row) => row.kind === 'CATEGORY' ? '分类' : '字段' },
    { title: '编码/标识', dataIndex: 'code', width: 150, ellipsis: true },
    { title: '名称', dataIndex: 'name', width: 180, ellipsis: true },
    { title: '处理', width: 90, align: 'center', render: (_, row) => <Tag color={ACTION_COLORS[row.action]}>{ACTION_LABELS[row.action]}</Tag> },
    { title: '说明', dataIndex: 'message', width: 220, ellipsis: true }
  ]

  return <Modal title="导入分类配置" open={open} onCancel={close} width={920} destroyOnClose
    footer={<Space>
      <Button loading={loading} disabled={!selectedFile} onClick={preview}>预检</Button>
      <Button type="primary" loading={loading} disabled={!result || result.conflictCount > 0} onClick={commit}>确认导入</Button>
      <Button onClick={close}>关闭</Button>
    </Space>}>
    <Steps size="small" current={result ? 1 : 0} className="eam-import-steps" items={[
      { title: '选择配置文件' }, { title: '预检差异' }, { title: '确认导入' }
    ]}/>

    <Upload.Dragger fileList={fileList} accept=".xlsx" maxCount={1} disabled={loading}
      beforeUpload={file => { setSelectedFile(file); setFileList([file as unknown as UploadFile]); setResult(undefined); return false }}
      onRemove={() => { setSelectedFile(undefined); setFileList([]); setResult(undefined) }}>
      <p className="ant-upload-drag-icon"><InboxOutlined/></p>
      <p className="ant-upload-text">拖入分类配置，或点击选择文件</p>
      <p className="ant-upload-hint">模板包含「分类」和「字段」两个工作表</p>
    </Upload.Dragger>

    <div className="eam-import-options">
      <Button type="link" size="small" loading={templateLoading} onClick={downloadTemplate}>下载配置模板</Button>
    </div>

    {error && <Alert className="eam-inline-alert" type="error" showIcon message={error}
      action={<Button size="small" onClick={result ? commit : preview}>重试</Button>}/>}

    {result && <>
      <div className="eam-import-stats">
        <Statistic title="分类总数" value={result.categoryCount}/>
        <Statistic title="子分类" value={result.leafCategoryCount}/>
        <Statistic title="字段总数" value={result.fieldCount}/>
        <Statistic title="新增" value={result.createCount}/>
        <Statistic title="更新" value={result.updateCount}/>
        <Statistic title="跳过" value={result.skipCount}/>
        <Statistic title="冲突" value={result.conflictCount}/>
      </div>
      <div className="eam-import-checks">
        <Alert type={result.leafCategoryCount > 0 ? 'success' : 'warning'}
          message={result.leafCategoryCount > 0 ? `子分类已识别 ${result.leafCategoryCount} 个` : '未识别到子分类'}/>
        <Alert type={result.legacyFieldCount === 0 ? 'success' : 'error'}
          message={result.legacyFieldCount === 0 ? '未发现旧原表字段' : `发现旧原表字段 ${result.legacyFieldCount} 个`}/>
        <Alert type={result.credentialFieldCount === 0 ? 'success' : 'error'}
          message={result.credentialFieldCount === 0 ? '未发现密码/凭据字段' : `发现凭据字段 ${result.credentialFieldCount} 个`}/>
      </div>
      <Alert className="eam-inline-alert" type={result.allManagementFieldsOptional ? 'success' : 'error'}
        message={result.allManagementFieldsOptional ? '管理端字段全部为选填' : '存在管理端必填字段，请检查模板'}/>
      {result.conflictCount > 0 && <Alert className="eam-inline-alert" type="error" showIcon
        message="存在冲突，请修正模板后重新预检"/>}
      <Table<EamCategoryImportItem> rowKey={row => `${row.kind}-${row.code}`} columns={columns} dataSource={result.items}
        pagination={false} scroll={{ x: 800, y: 400 }} size="small" bordered/>
    </>}
  </Modal>
}

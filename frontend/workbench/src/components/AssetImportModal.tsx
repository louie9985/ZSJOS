import { useState } from 'react'
import { Alert, Badge, Button, Checkbox, Descriptions, Modal, Space, Statistic, Steps, Table, Tag, Typography, Upload, message } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { api, type EamAssetImportPreview, type EamAssetImportRow } from '../services/api'
import { downloadBlob } from '../services/download'
import type { ColumnsType } from 'antd/es/table'
import type { UploadFile } from 'antd/es/upload/interface'

const ACTION_LABELS: Record<EamAssetImportRow['action'], string> = {
  CREATE: '新增', UPDATE: '更新', SKIP_EXISTING: '已有跳过', SKIP_SAME_FILE: '重复跳过', ERROR: '错误'
}
const ACTION_COLORS: Record<EamAssetImportRow['action'], string> = {
  CREATE: 'success', UPDATE: 'warning', SKIP_EXISTING: 'default', SKIP_SAME_FILE: 'default', ERROR: 'error'
}

export default function AssetImportModal({ open, onClose, onImported }: {
  open: boolean
  onClose: () => void
  onImported: () => void
}) {
  const [fileList, setFileList] = useState<UploadFile[]>([])
  const [selectedFile, setSelectedFile] = useState<File>()
  const [updateExisting, setUpdateExisting] = useState(false)
  const [result, setResult] = useState<EamAssetImportPreview>()
  const [committed, setCommitted] = useState(false)
  const [loading, setLoading] = useState(false)
  const [templateLoading, setTemplateLoading] = useState(false)
  const [error, setError] = useState('')

  const step = committed ? 3 : result ? 2 : selectedFile ? 1 : 0

  const reset = () => {
    setFileList([]); setSelectedFile(undefined); setUpdateExisting(false)
    setResult(undefined); setCommitted(false); setError('')
  }

  const close = () => { reset(); onClose() }

  const preview = async () => {
    if (!selectedFile) return
    setLoading(true); setError('')
    try { setResult(await api.eam.asset.importPreview(selectedFile, updateExisting)) }
    catch (e) { setError(e instanceof Error ? e.message : '预检失败') }
    finally { setLoading(false) }
  }

  const commit = async () => {
    if (!selectedFile || !result || result.errorCount > 0) return
    setLoading(true); setError('')
    try {
      const committedResult = await api.eam.asset.importCommit(selectedFile, updateExisting)
      setResult(committedResult); setCommitted(true)
      message.success(`台账导入完成：新增 ${committedResult.createCount}，更新 ${committedResult.updateCount}，跳过 ${committedResult.skipCount}`)
      onImported()
    } catch (e) { setError(e instanceof Error ? e.message : '导入失败') }
    finally { setLoading(false) }
  }

  const downloadTemplate = async () => {
    setTemplateLoading(true)
    try { await downloadBlob('/eam/asset/get-import-template', '中世健资产台账导入模板.xlsx') }
    catch (e) { message.error(e instanceof Error ? e.message : '模板下载失败') }
    finally { setTemplateLoading(false) }
  }

  const columns: ColumnsType<EamAssetImportRow> = [
    { title: 'Excel 行', dataIndex: 'rowNum', width: 85, align: 'center' },
    { title: '资产标签', width: 130, render: (_, row) => row.assetCode || <span className="eam-muted">自动生成</span> },
    { title: '资产名称', dataIndex: 'name', width: 150, ellipsis: true },
    { title: '分类', dataIndex: 'categoryName', width: 200, ellipsis: true },
    { title: '数量', dataIndex: 'quantity', width: 80, align: 'center' },
    { title: '人员匹配', width: 150, ellipsis: true, render: (_, row) => row.matchedUserName || row.useUserName || '-' },
    { title: '处理', width: 100, align: 'center', render: (_, row) => <Tag color={ACTION_COLORS[row.action]}>{ACTION_LABELS[row.action]}</Tag> },
    { title: '警告', width: 80, align: 'center', render: (_, row) => row.warnings.length ? <Badge count={row.warnings.length}/> : '-' }
  ]

  return <Modal title="导入资产台账" open={open} onCancel={close} width={1040} destroyOnClose
    footer={<Space>
      <Button loading={loading} disabled={!selectedFile || committed} onClick={preview}>预检</Button>
      <Button type="primary" loading={loading} disabled={!result || result.errorCount > 0 || committed} onClick={commit}>确认导入</Button>
      <Button onClick={close}>关闭</Button>
    </Space>}>
    <Steps size="small" current={step} className="eam-import-steps" items={[
      { title: '上传台账' }, { title: '预检结果' }, { title: '确认导入' }
    ]}/>

    <Upload.Dragger fileList={fileList} accept=".xlsx" maxCount={1} disabled={loading || committed}
      beforeUpload={file => { setSelectedFile(file); setFileList([file as unknown as UploadFile]); setResult(undefined); setCommitted(false); return false }}
      onRemove={() => { setSelectedFile(undefined); setFileList([]); setResult(undefined) }}>
      <p className="ant-upload-drag-icon"><InboxOutlined/></p>
      <p className="ant-upload-text">拖入 EAM V3 资产台账，或点击选择文件</p>
      <p className="ant-upload-hint">仅读取「资产台账」工作表，按分类编码和表头名称预检</p>
    </Upload.Dragger>

    <div className="eam-import-options">
      <Checkbox checked={updateExisting} disabled={loading || committed}
        onChange={event => { setUpdateExisting(event.target.checked); setResult(undefined) }}>
        更新已有资产标签
      </Checkbox>
      <Typography.Text type="secondary">默认跳过已有资产；勾选后预检会显示待更新行</Typography.Text>
      <Button type="link" size="small" loading={templateLoading} onClick={downloadTemplate}>下载 V3 模板</Button>
    </div>

    {error && <Alert className="eam-inline-alert" type="error" showIcon message={error}
      action={<Button size="small" onClick={result ? commit : preview}>重试</Button>}/>}

    {result && <>
      <div className="eam-import-stats">
        <Statistic title="有效行" value={result.totalRows}/>
        <Statistic title="新增" value={result.createCount}/>
        <Statistic title="更新" value={result.updateCount}/>
        <Statistic title="跳过" value={result.skipCount}/>
        <Statistic title="警告" value={result.warningCount}/>
        <Statistic title="错误" value={result.errorCount}/>
      </div>
      {result.errorCount > 0 && !committed && <Alert className="eam-inline-alert" type="error" showIcon
        message={`发现 ${result.errorCount} 行错误，修正 Excel 后重新预检，确认导入已禁用`}/>}
      {result.warningCount > 0 && !committed && <Alert className="eam-inline-alert" type="warning" showIcon
        message="警告不会阻止导入；请展开对应行核对默认值和人员匹配结果"/>}
      {committed && <Alert className="eam-inline-alert" type="success" showIcon
        message={`导入完成，批次号 ${result.batchId ?? '-'}`}/>}
      <Table<EamAssetImportRow> rowKey="rowNum" columns={columns} dataSource={result.rows} pagination={false}
        scroll={{ x: 1000, y: 460 }} size="small" bordered
        expandable={{ expandedRowRender: row => <div className="eam-import-row-detail">
          <Descriptions column={2} bordered size="small"
            items={Object.entries(row.mappedFields).map(([key, value]) => ({ key, label: key, children: String(value ?? '-') }))}/>
          {row.defaultedFields.length > 0 && <div className="eam-import-tag-block">
            <div className="eam-import-tag-title">自动默认值</div>
            <Space wrap size={[4, 4]}>{row.defaultedFields.map(item => <Tag key={item}>{item}</Tag>)}</Space>
          </div>}
          {row.warnings.length > 0 && <div className="eam-import-tag-block">
            <div className="eam-import-tag-title">警告</div>
            <Space wrap size={[4, 4]}>{row.warnings.map(item => <Tag key={item} color="warning">{item}</Tag>)}</Space>
          </div>}
          {row.errors.length > 0 && <div className="eam-import-tag-block">
            <div className="eam-import-tag-title">错误</div>
            <Space wrap size={[4, 4]}>{row.errors.map(item => <Tag key={item} color="error">{item}</Tag>)}</Space>
          </div>}
        </div> }}/>
    </>}
  </Modal>
}

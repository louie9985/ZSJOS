import { useCallback, useState } from 'react'
import { Alert, Button, Modal, Space, Typography, Upload, message } from 'antd'
import { DownloadOutlined, InboxOutlined } from '@ant-design/icons'
import type { UploadProps } from 'antd'
import { saveBlob } from '../../services/download'

/** 通用导入结果 */
export interface FmsImportResult {
  totalCount?: number
  successCount?: number
  failureCount?: number
  errorFileUrl?: string
}

interface FmsImportModalProps {
  open: boolean
  onClose: () => void
  title?: string
  /** 文件接受类型，如 '.xlsx,.xls' */
  accept?: string
  /** 下载导入模板 */
  onGetTemplate: () => Promise<Blob>
  /** 上传导入，返回结果 */
  onUpload: (file: File) => Promise<FmsImportResult | number | void>
  /** 导入成功后刷新回调 */
  onSuccess?: () => void
}

/**
 * 通用 FMS 导入弹窗：Drag 上传 + 模板下载 + 结果展示。
 * onUpload 返回统一结果（或纯数字成功条数），弹窗展示成败与错误文件下载链接。
 */
export default function FmsImportModal({ open, onClose, title = '导入', accept = '.xlsx,.xls', onGetTemplate, onUpload, onSuccess }: FmsImportModalProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState<FmsImportResult>()
  const [templateLoading, setTemplateLoading] = useState(false)

  const reset = useCallback(() => {
    setSelectedFile(null)
    setResult(undefined)
    setUploading(false)
  }, [])

  const getTemplate = useCallback(async () => {
    setTemplateLoading(true)
    try {
      const blob = await onGetTemplate()
      saveBlob(blob, '导入模板.xlsx')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '模板下载失败')
    } finally {
      setTemplateLoading(false)
    }
  }, [onGetTemplate])

  const handleUpload = useCallback(async () => {
    if (!selectedFile) { message.warning('请先选择文件'); return }
    setUploading(true)
    setResult(undefined)
    try {
      const res = await onUpload(selectedFile)
      if (typeof res === 'number') {
        setResult({ successCount: res })
      } else if (res) {
        setResult(res)
      } else {
        setResult({})
      }
      message.success('导入完成')
      onSuccess?.()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导入失败')
    } finally {
      setUploading(false)
    }
  }, [selectedFile, onUpload, onSuccess])

  const props: UploadProps = {
    accept,
    maxCount: 1,
    beforeUpload: (file) => { setSelectedFile(file); return false }, // 不自动上传
    onRemove: () => { setSelectedFile(null); setResult(undefined); return true }
  }

  const hasFailure = (result?.failureCount ?? 0) > 0

  return (
    <Modal
      open={open}
      onCancel={() => { reset(); onClose() }}
      title={title}
      confirmLoading={uploading}
      onOk={handleUpload}
      okButtonProps={{ disabled: !selectedFile }}
      okText="开始导入"
      cancelText="取消"
      destroyOnClose
      width={760}
    >
      <Space style={{ marginBlockEnd: 12 }}>
        <Button size="small" icon={<DownloadOutlined/>} loading={templateLoading} onClick={getTemplate}>下载导入模板</Button>
      </Space>
      <Upload.Dragger {...props} style={{ marginBlockEnd: 12 }}>
        <p className="ant-upload-drag-icon"><InboxOutlined/></p>
        <p className="ant-upload-text">点击或拖拽文件到此区域</p>
        <p className="ant-upload-hint">支持 {accept} 格式，单文件</p>
      </Upload.Dragger>
      {result && (
        <Alert
          type={hasFailure ? 'warning' : 'success'}
          showIcon
          message="导入结果"
          description={
            <Space direction="vertical" size="small">
              {result.totalCount !== undefined && <span>总条数：{result.totalCount}</span>}
              {result.successCount !== undefined && <span>成功：{result.successCount}</span>}
              {result.failureCount !== undefined && <span>失败：{result.failureCount}</span>}
              {hasFailure && result.errorFileUrl && (
                <Button type="link" size="small" href={result.errorFileUrl} target="_blank" download>下载错误数据文件</Button>
              )}
            </Space>
          }
        />
      )}
      <Typography.Paragraph type="secondary" style={{ marginBlockStart: 8, fontSize: 12 }}>
        导入前请先下载模板，按模板格式填写数据后上传。
      </Typography.Paragraph>
    </Modal>
  )
}

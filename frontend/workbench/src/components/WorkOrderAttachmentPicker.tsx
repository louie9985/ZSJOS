import { DeleteOutlined, PictureOutlined, UploadOutlined } from '@ant-design/icons'
import { Button, List, Space, Upload, message, type UploadProps } from 'antd'
import { useRef } from 'react'
import { workOrderApi, type WorkOrderFile } from '../services/workOrderApi'
import { useClipboardPasteTarget } from './ClipboardPasteTarget'

export default function WorkOrderAttachmentPicker({ value, onChange, disabled = false }: {
  value: WorkOrderFile[]
  onChange: (files: WorkOrderFile[]) => void
  disabled?: boolean
}) {
  const filesRef = useRef(value)
  const pendingRef = useRef(0)
  filesRef.current = value
  const uploadFile = async (file: File) => {
    try {
      const uploaded = await workOrderApi.upload(file)
      filesRef.current = [...filesRef.current, uploaded]
      onChange(filesRef.current)
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message : '附件上传失败')
    } finally {
      pendingRef.current = Math.max(0, pendingRef.current - 1)
    }
  }
  const uploadFiles = (files: File[]) => {
    const available = Math.max(0, 20 - value.length - pendingRef.current)
    const accepted = files.slice(0, available)
    pendingRef.current += accepted.length
    accepted.forEach(file => { void uploadFile(file) })
  }
  const customRequest: UploadProps['customRequest'] = async options => {
    try {
      const uploaded = await workOrderApi.upload(options.file as File)
      filesRef.current = [...filesRef.current, uploaded]
      onChange(filesRef.current)
      options.onSuccess?.(uploaded)
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message : '附件上传失败')
      options.onError?.(cause as Error)
    }
  }
  const { targetRef, targetProps, pasteButtonProps } = useClipboardPasteTarget({ disabled, canPaste: () => value.length < 20, onFiles: uploadFiles })
  return <div ref={targetRef} {...targetProps}><Space direction="vertical" style={{ width: '100%' }}>
    <div className="attachment-upload-actions">
      <Upload customRequest={customRequest} fileList={[]} showUploadList={false} multiple disabled={disabled || value.length >= 20}>
        <Button icon={<UploadOutlined />}>上传附件</Button>
      </Upload>
      <Button {...pasteButtonProps} icon={<PictureOutlined />}>上传剪贴板截图</Button>
    </div>
    {value.length > 0 && <List size="small" dataSource={value} renderItem={file => <List.Item actions={[<Button key="remove" type="text" danger icon={<DeleteOutlined />} aria-label={`删除 ${file.name}`} onClick={() => { filesRef.current = filesRef.current.filter(item => item.id !== file.id); onChange(filesRef.current) }} />]}>{file.name}</List.Item>} />}
  </Space></div>
}

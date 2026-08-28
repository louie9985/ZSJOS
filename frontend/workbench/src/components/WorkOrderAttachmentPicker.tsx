import { DeleteOutlined, UploadOutlined } from '@ant-design/icons'
import { Button, List, Space, Upload, message, type UploadProps } from 'antd'
import { workOrderApi, type WorkOrderFile } from '../services/workOrderApi'

export default function WorkOrderAttachmentPicker({ value, onChange, disabled = false }: {
  value: WorkOrderFile[]
  onChange: (files: WorkOrderFile[]) => void
  disabled?: boolean
}) {
  const customRequest: UploadProps['customRequest'] = async options => {
    try {
      const uploaded = await workOrderApi.upload(options.file as File)
      onChange([...value, uploaded])
      options.onSuccess?.(uploaded)
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message : '附件上传失败')
      options.onError?.(cause as Error)
    }
  }
  return <Space direction="vertical" style={{ width: '100%' }}>
    <Upload customRequest={customRequest} fileList={[]} showUploadList={false} multiple disabled={disabled || value.length >= 20}>
      <Button icon={<UploadOutlined />}>上传附件</Button>
    </Upload>
    {value.length > 0 && <List size="small" dataSource={value} renderItem={file => <List.Item actions={[<Button key="remove" type="text" danger icon={<DeleteOutlined />} aria-label={`删除 ${file.name}`} onClick={() => onChange(value.filter(item => item.id !== file.id))} />]}>{file.name}</List.Item>} />}
  </Space>
}

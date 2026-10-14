import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import WorkOrderAttachmentPicker from '../src/components/WorkOrderAttachmentPicker'
import { type WorkOrderFile } from '../src/services/workOrderApi'
import { http } from '../src/services/api'

const metrics = { requests: 0, busy: false }
const actualSize = Object.getOwnPropertyDescriptor(Blob.prototype, 'size')!.get!
Object.defineProperty(File.prototype, 'size', {
  get(this: File) {
    if (this.name === 'oversized.bin') return 100 * 1024 * 1024 + 1
    if (this.name === 'boundary.bin') return 100 * 1024 * 1024
    return actualSize.call(this)
  },
})
http.defaults.adapter = async config => {
  if (config.url !== '/zsjos/work-order/file/upload') throw new Error('Unexpected fixture request')
  metrics.requests++
  const file = (config.data as FormData).get('file') as File
  return { config, headers: {}, status: 200, statusText: 'OK', data: metrics.busy
    ? { code: 1900003113, msg: '工单附件上传繁忙，请稍后重试' }
    : { code: 0, data: { id: metrics.requests, name: file.name, size: file.size, type: file.type } } }
}
Object.assign(window, { workOrderUploadMetrics: metrics })
function Fixture() {
  const [files, setFiles] = useState<WorkOrderFile[]>([])
  return <div style={{ padding: 16, maxWidth: 640 }}>
    <WorkOrderAttachmentPicker value={files} onChange={setFiles}/>
    <output data-testid="file-count">{files.length}</output>
  </div>
}
createRoot(document.getElementById('root')!).render(<ConfigProvider><App><Fixture/></App></ConfigProvider>)

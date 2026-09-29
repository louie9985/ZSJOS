import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Button, ConfigProvider } from 'antd'
import DeferredAttachmentPicker from '../src/components/DeferredAttachmentPicker'
import { uploadDeferredFiles, type DeferredUploadItem } from '../src/services/deferredUpload'
import '../src/styles/index.css'

function Fixture() {
  const [files, setFiles] = useState<DeferredUploadItem<string>[]>([])
  const [calls, setCalls] = useState<string[]>([])
  const [locked, setLocked] = useState(false)
  const [fail, setFail] = useState(true)
  const mixed = new URLSearchParams(location.search).has('mixed')
  return <ConfigProvider><div style={{ padding: 16 }}>
    <DeferredAttachmentPicker value={files} onChange={setFiles} disabled={locked}
      imageOnly={!mixed} maxCount={mixed ? 6 : 9} accept={mixed ? 'image/png,application/pdf' : 'image/jpeg,image/png,image/webp'} />
    <Button onClick={async () => {
      setLocked(true)
      await uploadDeferredFiles(files, async file => {
        setCalls(current => [...current, file.name])
        if (fail && file.name === 'b.png') throw new Error('测试上传失败')
        return file.name
      }, setFiles)
      setFail(false)
      setLocked(false)
    }}>确认上传</Button>
    <pre id="state">{JSON.stringify(files.map(file => ({ name: file.name, status: file.status })))}</pre>
    <pre id="calls">{JSON.stringify(calls)}</pre>
  </div></ConfigProvider>
}
createRoot(document.getElementById('root')!).render(<Fixture />)

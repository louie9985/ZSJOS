import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App } from 'antd'
import PositioningAttachmentPicker, { type AttachmentItem } from '../src/components/PositioningAttachmentPicker'
import '../src/styles/index.css'

function Fixture() {
  const [items, setItems] = useState<AttachmentItem[]>([])
  const immediate = location.search.includes('immediate')
  return <App><div style={{ padding: 16 }}><PositioningAttachmentPicker items={items} onChange={setItems}
    hint="本组附件总大小不超过 1GB"
    onUpload={immediate ? files => setItems(previous => [...previous, ...files.map((file, index) => ({
      key: `stored-${file.name}`, name: file.name, size: file.size, uploaded: { id: previous.length + index + 1, name: file.name, size: file.size },
    }))]) : undefined}
    onDownload={async () => ({ url: 'https://example.test/file' })} /></div></App>
}
createRoot(document.getElementById('root')!).render(<Fixture />)

// UTF-8. Isolated synthetic files rendered by production announcement components.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import NoticeAttachments from '../src/components/NoticeAttachments'
import '../src/styles/index.css'
const files = [{ infraFileId: 1, fileName: '公告测试.pdf', mimeType: 'application/pdf', fileSize: 600, sort: 0, downloadUrl: '/test/notice-preview-file.pdf' }]
function Fixture() {
  const [visible, setVisible] = useState(true)
  return <App><Button onClick={() => setVisible(value => !value)}>切换公告</Button>{visible && <NoticeAttachments files={files} reload={async () => files} />}</App>
}
createRoot(document.getElementById('root')!).render(<ConfigProvider locale={zhCN}><Fixture /></ConfigProvider>)

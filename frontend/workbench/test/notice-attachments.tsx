import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, ConfigProvider, Space } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import NoticeEditorDialog from '../src/components/NoticeEditorDialog'
import NoticeManagementDetail from '../src/components/NoticeManagementDetail'
import { AnnouncementDetail } from '../src/pages/AnnouncementCenterPage'
import { api, type AnnouncementAttachment } from '../src/services/api'
import { noticeManagement, type ManagedNotice } from '../src/services/noticeManagement'
import '../src/styles/index.css'

const files: AnnouncementAttachment[] = [
  { infraFileId: 1, fileName: '验收图片.png', fileSize: 1024, sort: 0, mimeType: 'image/png', downloadUrl: '/src/assets/payment-logo.png' },
  { infraFileId: 2, fileName: '验收文档.docx', fileSize: 2048, sort: 1, downloadUrl: '/src/assets/payment-logo.png' },
  { infraFileId: 3, fileName: '已移除附件.png', fileSize: 1024, sort: 2 },
]
const notice: ManagedNotice = { id: 3, title: '中世健公告附件验收', type: 2, content: '<p>隔离测试，不发送公告。</p>', audienceType: 'ALL', targetDeptIds: [], targetUserIds: [], attachments: files, publishStatus: 'DRAFT' }
let reads = 0, saves = 0, reject = false
noticeManagement.types = async () => [{ id: 1, label: '公告', value: '2', dictType: 'system_notice_type', sort: 0, status: 0 }]
noticeManagement.recipients = async () => ({ departments: [], users: [] })
noticeManagement.get = async () => { reads++; if (reject) throw new Error('无权读取附件'); return notice }
api.announcement = async () => { reads++; if (reject) throw new Error('无权读取附件'); return { ...notice, read: true, publishTime: '2026-09-28 10:00:00' } }
noticeManagement.update = async () => { saves++; return true }
noticeManagement.upload = async (file, progress) => {
  progress(25)
  await new Promise(resolve => setTimeout(resolve, 1500))
  progress(100)
  return { infraFileId: Date.now(), fileName: file.name, fileSize: file.size, mimeType: file.type, sort: 0, downloadUrl: '/src/assets/payment-logo.png' }
}
function Fixture() {
  const [mode, setMode] = useState('reader')
  const [status, setStatus] = useState('')
  return <App><div style={{ padding: 16 }}><Space wrap>
    <Button onClick={() => setMode('reader')}>用户详情</Button>
    <Button onClick={() => setMode('manage')}>管理详情</Button>
    <Button onClick={() => setMode('editor')}>编辑公告</Button>
    <Button onClick={() => { reject = !reject; setStatus(reject ? '拒绝读取' : '允许读取') }}>切换读取权限</Button>
    <Button onClick={() => setStatus(`读取 ${reads} 次，保存 ${saves} 次`)}>读取计数</Button>
    <span>{status}</span>
  </Space>
  {mode === 'reader' && <AnnouncementDetail item={{ ...notice, read: true, publishTime: '2026-09-28 10:00:00' }} />}
  {mode === 'manage' && <NoticeManagementDetail notice={notice} />}
  {mode === 'editor' && <NoticeEditorDialog initial={notice} permissions={['system:notice:update']} onClose={() => setMode('reader')} onChanged={() => {}} />}
  </div></App>
}
createRoot(document.getElementById('root')!).render(<ConfigProvider locale={zhCN}><Fixture /></ConfigProvider>)

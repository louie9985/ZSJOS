import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MessageDetail from '../src/components/MessageDetail'
import '../src/styles/index.css'
createRoot(document.getElementById('root')!).render(<ConfigProvider><App><MemoryRouter><MessageDetail categories={[]} message={{ id: 1, templateNickname: '中世健消息中心', templateTitle: '收到知识库文档分享', templateSummary: '分享老师向您分享了《春季品牌活动复盘》。', templateContent: '分享老师向您分享了知识库文档《春季品牌活动复盘》。', templateType: 2, readStatus: true, createTime: 1, templateParams: { publicUrl: 'https://public.example.test/public-api/pms/kb/document-share/page?token=fixture', documentRoute: '/pms/kb/library/1/document/2' } }}/></MemoryRouter></App></ConfigProvider>)

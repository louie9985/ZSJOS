import ConfigurableRichTextEditor from './ConfigurableRichTextEditor'
import { noticeManagement } from '../services/noticeManagement'
import { normalizeNoticeTableHtml } from './noticeTableHtml'
import { installNoticeTableResize } from './noticeTableResize'
import './notice-table-editor.css'

const tableAdapter = { normalizeHtml: normalizeNoticeTableHtml, install: installNoticeTableResize }

export default function NoticeRichTextEditor(props: {
  value?: string; onChange?: (html: string) => void; onUploadChange: (delta: number) => void; onError: (text: string) => void
}) {
  return <div className="notice-rich-text-editor"><ConfigurableRichTextEditor {...props} tableAdapter={tableAdapter} uploadFile={noticeManagement.uploadContent} /></div>
}

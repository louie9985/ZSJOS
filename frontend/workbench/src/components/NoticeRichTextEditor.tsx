import ConfigurableRichTextEditor from './ConfigurableRichTextEditor'
import { noticeManagement } from '../services/noticeManagement'
import { normalizeNoticeTableHtml } from './noticeTableHtml'
import { installNoticeTableResize } from './noticeTableResize'
import './notice-table-editor.css'
import { useMemo, useRef } from 'react'

export default function NoticeRichTextEditor(props: {
  value?: string; onChange?: (html: string) => void; onUploadChange: (delta: number) => void; onError: (text: string) => void
}) {
  const root = useRef<HTMLDivElement>(null)
  const tableAdapter = useMemo(() => ({
    normalizeHtml: (html: string) => normalizeNoticeTableHtml(html, Math.min(1352, Math.max(1, root.current?.clientWidth || 1352))),
    install: installNoticeTableResize
  }), [])
  return <div ref={root} className="notice-rich-text-editor"><ConfigurableRichTextEditor {...props} tableAdapter={tableAdapter} uploadFile={noticeManagement.uploadContent} /></div>
}

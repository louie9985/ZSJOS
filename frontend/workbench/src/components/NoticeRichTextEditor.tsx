import ConfigurableRichTextEditor from './ConfigurableRichTextEditor'
import { noticeManagement } from '../services/noticeManagement'

export default function NoticeRichTextEditor(props: {
  value?: string; onChange?: (html: string) => void; onUploadChange: (delta: number) => void; onError: (text: string) => void
}) {
  return <div className="notice-rich-text-editor"><ConfigurableRichTextEditor {...props} uploadFile={noticeManagement.uploadContent} /></div>
}

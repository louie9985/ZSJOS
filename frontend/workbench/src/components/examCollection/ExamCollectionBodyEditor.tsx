import { useMemo, useRef } from 'react';
import ConfigurableRichTextEditor from '../ConfigurableRichTextEditor';
import { normalizeNoticeTableHtml } from '../noticeTableHtml';
import { installNoticeTableResize } from '../noticeTableResize';
import { examCollectionApi } from '../../services/examCollection';
import '../notice-table-editor.css';

export default function ExamCollectionBodyEditor(props: {
  value?: string; onChange?: (html: string) => void;
  onUploadChange: (delta: number) => void; onError: (text: string) => void;
  onFailureChange: (count: number) => void;
}) {
  const root = useRef<HTMLDivElement>(null);
  const tableAdapter = useMemo(() => ({
    normalizeHtml: (html: string) => normalizeNoticeTableHtml(html, Math.min(1352, Math.max(1, root.current?.clientWidth || 1352))),
    install: installNoticeTableResize
  }), []);
  return <div ref={root} className="notice-rich-text-editor"><ConfigurableRichTextEditor {...props} tableAdapter={tableAdapter} uploadFile={examCollectionApi.uploadContent}/></div>;
}

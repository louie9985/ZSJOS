import AttachmentCard from './AttachmentCard'
import { useRef } from 'react'
import type { LeadAppealEvidence } from '../services/api'

function AppealAttachment({ item, reload }: { item: LeadAppealEvidence; reload: () => Promise<LeadAppealEvidence[]> }) {
  const initial = useRef(true)
  return <AttachmentCard name={item.originalName} load={async () => {
    // The initial card uses the already-authorized response; explicit preview/download renews its URL.
    const current = initial.current ? item : (await reload()).find(file => file.infraFileId === item.infraFileId)
    initial.current = false
    if (!current) throw new Error('附件已不可访问，请刷新申诉记录')
    return { name: current.originalName, url: current.fileUrl, type: current.contentType, size: current.fileSize }
  }} />
}

export default function LeadAppealAttachments({ items, reload }: {
  items?: LeadAppealEvidence[]
  reload: () => Promise<LeadAppealEvidence[]>
}) {
  if (!items?.length) return null
  return <div className="appeal-attachment-list">{items.map(item => <AppealAttachment
    key={`${item.infraFileId}:${item.fileUrl}`} item={item} reload={reload} />)}</div>
}

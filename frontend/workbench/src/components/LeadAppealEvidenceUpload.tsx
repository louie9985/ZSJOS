import { type LeadAppealEvidence } from '../services/api'
import DeferredAttachmentPicker from './DeferredAttachmentPicker'
import type { DeferredUploadItem } from '../services/deferredUpload'

export default function LeadAppealEvidenceUpload({ value, onChange, disabled = false }: {
  value: DeferredUploadItem<LeadAppealEvidence>[]
  onChange: (value: DeferredUploadItem<LeadAppealEvidence>[]) => void
  disabled?: boolean
}) {
  return <DeferredAttachmentPicker value={value} onChange={onChange}
    accept="image/jpeg,image/png,image/webp" maxCount={9} imageOnly disabled={disabled}/>
}

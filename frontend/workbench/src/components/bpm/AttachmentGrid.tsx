import { Image, Typography } from 'antd'
import type { BpmApprovalAttachment } from '../../services/api'

export type AttachmentGridProps = {
  attachments?: BpmApprovalAttachment[]
}

/**
 * 审批内容里的附件展示：图片走缩略图 + 点击预览，其余走下载链接。
 *
 * 这是员工端统一的附件呈现方式（与反馈详情、线索证据一致）：图片要能直接看清内容，
 * 文档给一个可点开的链接。签名地址由后端下发，前端不再拼接。
 */
export default function AttachmentGrid({ attachments = [] }: AttachmentGridProps) {
  if (attachments.length === 0) return null
  const images = attachments.filter(isImage)
  const files = attachments.filter(item => !isImage(item))

  return <div className="bpm-attachment-grid">
    {images.length > 0 && <Image.PreviewGroup>
      <div className="bpm-attachment-images">
        {images.map((item, index) => item.url
          ? <Image key={`${item.name}-${index}`} width={112} height={84} src={item.url}
            alt={item.name || `图片 ${index + 1}`}/>
          : <span className="bpm-attachment-missing" key={`${item.name}-${index}`}>
            {item.name || `图片 ${index + 1}`}
          </span>)}
      </div>
    </Image.PreviewGroup>}
    {files.length > 0 && <div className="bpm-attachment-files">
      {files.map((item, index) => item.url
        ? <Typography.Link key={`${item.name}-${index}`} href={item.url} target="_blank" rel="noreferrer">
          {item.name || `附件 ${index + 1}`}
        </Typography.Link>
        : <span key={`${item.name}-${index}`}>{item.name || `附件 ${index + 1}`}</span>)}
    </div>}
  </div>
}

/**
 * 仅凭 MIME 判断是否按图片内联预览。后端可能不返回 contentType，
 * 此时退化为下载链接——宁可多点一次，也不要在网格里放一个裂图。
 */
function isImage(item: BpmApprovalAttachment) {
  return !!item.contentType && item.contentType.toLowerCase().startsWith('image/')
}

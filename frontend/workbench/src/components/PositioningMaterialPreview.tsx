import { ResourceLinkPresentation } from './ResourceLink'
import { Alert, Image, Typography } from 'antd'
import { useState } from 'react'
import type { MaterialVersion } from '../services/materialApi'
import { MaterialFields } from '../pages/MaterialLibraryPage'
import { retryImageUrl } from '../services/imageUrl'

export function positioningMaterialCover(version?: MaterialVersion) {
  return version?.coverPreviewUrl || version?.files.find(file => file.fieldKey === '__cover__')?.previewUrl
}

export default function PositioningMaterialPreview({ version }: { version: MaterialVersion }) {
  const [imageError, setImageError] = useState(false)
  const [retryAttempt, setRetryAttempt] = useState(0)
  const cover = version.files.find(file => file.fieldKey === '__cover__')
  const coverUrl = positioningMaterialCover(version)
  return <div className="positioning-material-preview">
    <section className="positioning-material-preview-cover">
      <Typography.Title level={5}>素材封面</Typography.Title>
      {coverUrl && !imageError ? <Image src={retryAttempt ? retryImageUrl(coverUrl, retryAttempt) : coverUrl} alt={cover?.name || '素材封面'} onError={() => setImageError(true)} onLoad={() => setImageError(false)} />
        : coverUrl && imageError ? <button type="button" className="positioning-material-preview-retry" onClick={() => { setRetryAttempt(value => value + 1); setImageError(false) }} aria-label="图片加载失败，点击重试"><Image preview={false} src={retryImageUrl(coverUrl, retryAttempt + 1)} alt="图片加载失败，点击重试" /><span>图片加载失败，点击重试</span></button>
        : <Typography.Text type="secondary">暂无可预览的封面图片</Typography.Text>}
      {imageError && <Alert type="warning" showIcon message="封面加载失败，请点击图片重试" />}
    </section>
    <ResourceLinkPresentation.Provider value={true}><MaterialFields version={version} /></ResourceLinkPresentation.Provider>
  </div>
}

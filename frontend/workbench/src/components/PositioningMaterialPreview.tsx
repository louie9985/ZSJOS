import { ResourceLinkPresentation } from './ResourceLink'
import { Alert, Image, Typography } from 'antd'
import { useState } from 'react'
import type { MaterialVersion } from '../services/materialApi'
import { MaterialFields } from '../pages/MaterialLibraryPage'

export function positioningMaterialCover(version?: MaterialVersion) {
  return version?.coverPreviewUrl || version?.files.find(file => file.fieldKey === '__cover__')?.previewUrl
}

export default function PositioningMaterialPreview({ version }: { version: MaterialVersion }) {
  const [imageError, setImageError] = useState(false)
  const cover = version.files.find(file => file.fieldKey === '__cover__')
  const coverUrl = positioningMaterialCover(version)
  return <div className="positioning-material-preview">
    <section className="positioning-material-preview-cover">
      <Typography.Title level={5}>素材封面</Typography.Title>
      {coverUrl ? <Image src={coverUrl} alt={cover?.name || '素材封面'} onError={() => setImageError(true)} onLoad={() => setImageError(false)} />
        : <Typography.Text type="secondary">暂无可预览的封面图片</Typography.Text>}
      {imageError && <Alert type="warning" showIcon message="封面加载失败，请点击弹窗中的刷新预览重试" />}
    </section>
    <ResourceLinkPresentation.Provider value={true}><MaterialFields version={version} /></ResourceLinkPresentation.Provider>
  </div>
}

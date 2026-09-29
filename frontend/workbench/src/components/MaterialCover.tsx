import { useState } from 'react'
import { BookOutlined } from '@ant-design/icons'

export default function MaterialCover({ src }: { src?: string }) {
  const [failedSrc, setFailedSrc] = useState<string>()
  return <span className={`material-library-cover${!src || src === failedSrc ? ' material-library-cover-empty' : ''}`}>
    {src && src !== failedSrc
      ? <img src={src} alt="素材封面" loading="lazy" onError={() => setFailedSrc(src)} />
      : <span><BookOutlined /> {src ? '图片加载失败' : '暂无封面'}</span>}
  </span>
}

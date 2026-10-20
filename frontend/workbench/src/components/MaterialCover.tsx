import { useEffect, useState } from 'react'
import { BookOutlined } from '@ant-design/icons'
import { retryImageUrl } from '../services/imageUrl'

export default function MaterialCover({ src }: { src?: string }) {
  const [imageSrc, setImageSrc] = useState(src)
  const [failed, setFailed] = useState(false)
  const [retryAttempt, setRetryAttempt] = useState(0)
  useEffect(() => { setImageSrc(src); setFailed(false); setRetryAttempt(0) }, [src])
  const retry = () => {
    if (!src) return
    const next = retryAttempt + 1
    setRetryAttempt(next)
    setImageSrc(retryImageUrl(src, next))
    setFailed(false)
  }
  return <span className={`material-library-cover${!src || failed ? ' material-library-cover-empty' : ''}`}>
    {src && !failed
      ? <img src={imageSrc} alt="素材封面" loading="lazy" onError={() => setFailed(true)} />
      : src
        ? <button type="button" className="material-library-cover-retry" aria-label="图片加载失败，点击重试" onClick={retry}><BookOutlined /> 图片加载失败，点击重试</button>
        : <span><BookOutlined /> 暂无封面</span>}
  </span>
}

// UTF-8. Synthetic content, production renderer, no business requests.
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import SafeRichText from '../src/components/SafeRichText'
const html = '<p>公告图片</p><img width="160" height="100" src="https://notice-image.test/1.png" alt="第一张"><a href="https://notice-image.test/link"><img width="160" height="100" src="https://notice-image.test/2.png" alt="第二张">普通链接</a>'
function Fixture() {
  const [content, setContent] = useState(html)
  return <><button onClick={() => setContent('<p>另一篇公告</p>')}>更换正文</button><SafeRichText html={content} announcementTables /><div id="other"><SafeRichText html={html} /></div></>
}
createRoot(document.getElementById('root')!).render(<Fixture />)

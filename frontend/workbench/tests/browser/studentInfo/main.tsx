import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Button, ConfigProvider } from 'antd'
import StudentInfoPanel from '../../../src/components/StudentInfoPanel'
import StudentInfoLinkModal from '../../../src/components/StudentInfoLinkModal'
function Page() {
  const [open, setOpen] = useState(false)
  return <ConfigProvider><main style={{ maxWidth: 960, margin: '0 auto', padding: 16 }}>
    <Button onClick={() => setOpen(true)}>生成信息收集表</Button>
    <StudentInfoPanel leadId={1} />
    {open && <StudentInfoLinkModal leadId={1} mode="generate" onClose={() => setOpen(false)} />}
  </main></ConfigProvider>
}
createRoot(document.getElementById('root')!).render(<Page />)

import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import StudentArchivePanel from '../src/components/StudentArchivePanel'
import '../src/styles/index.css'

function Fixture() {
  const [relationId, setRelationId] = useState(2)
  return <main style={{ padding: 12 }}>
    <Button onClick={() => setRelationId(id => id === 2 ? 3 : 2)}>切换报名</Button>
    <StudentArchivePanel relationId={relationId}/>
  </main>
}

createRoot(document.getElementById('root')!).render(<ThemeProvider><App><Fixture/></App></ThemeProvider>)

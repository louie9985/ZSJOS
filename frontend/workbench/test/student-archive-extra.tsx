import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import StudentArchivePanel from '../src/components/StudentArchivePanel'
import { studentArchiveApi } from '../src/services/studentArchive'
import '../src/styles/index.css'
studentArchiveApi.detail = async () => ({ personId: 1, serviceRelationId: 2, revision: 0, canMaintain: false, history: [], information: { status: 'NONE', fields: [], values: {}, images: [], canReadSensitive: true, canExport: false } })
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><StudentArchivePanel relationId={2} /></App></ThemeProvider>)

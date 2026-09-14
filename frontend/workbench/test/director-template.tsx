// Synthetic acceptance entry, not imported by the production application.
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import { DirectorTemplateConfigPage } from '../src/pages/DirectorConfigPages'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'
const mode = new URLSearchParams(location.search).get('permission')
const permissions = mode === 'none' ? [] : ['query', ...(mode === 'read' ? [] : ['update', 'publish'])].map(x => `zsjos:director-interview-template:${x}`)
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><DirectorTemplateConfigPage permissions={permissions} /></App></ThemeProvider>)

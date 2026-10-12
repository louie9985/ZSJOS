// UTF-8. Only synthetic reference data; the fixture never writes to a business API.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import LeadSubmissionPage from '../src/pages/LeadSubmissionPage'
import { api } from '../src/services/api'
api.leadCatalog = async () => ({ categoryTree: [], spus: [], skus: [] })
api.areaTree = async () => []
api.dictDataByType = async () => []
api.newMediaProviders = async () => [{ id: 999, nickname: '验证新媒体', deptId: 1 }]
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><LeadSubmissionPage selfSourced educationSelfSourced={location.search.includes('education')}/></App></ThemeProvider></BrowserRouter>)

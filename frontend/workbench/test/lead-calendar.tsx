// UTF-8. Isolated fixture; synthetic data is supplied by the browser test only.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import LeadFollowUpCalendarPage from '../src/pages/LeadFollowUpCalendarPage'
const permissions = location.search.includes('denied') ? [] : ['zsjos:lead-follow-up-calendar:query', 'zsjos:lead:query', 'zsjos:lead-follow-up:create']
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><LeadFollowUpCalendarPage permissions={permissions} /></App></ThemeProvider></BrowserRouter>)

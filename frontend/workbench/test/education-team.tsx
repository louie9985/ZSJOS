// UTF-8. Synthetic browser fixture; no live business responses.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import EducationTeamPage from '../src/pages/EducationTeamPage'
const permissions=new URLSearchParams(location.search).has('denied')?['zsjos:subordinate-sales:account-status','zsjos:subordinate-sales:lead-transfer']:['query','account-status','lead-transfer','lead-restore','lead-recycle','lead-overturn-valid'].map(action=>'zsjos:education-team:'+action)
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App><EducationTeamPage permissions={permissions}/></App></ThemeProvider></BrowserRouter>)

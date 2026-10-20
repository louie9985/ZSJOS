// UTF-8. Synthetic browser fixture only; responses are supplied by Playwright.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import DeliveryPerformancePage from '../src/pages/DeliveryPerformancePage'
import DeliveryPerformanceTargetPage from '../src/pages/DeliveryPerformanceTargetPage'
const params = new URLSearchParams(location.search)
const permissions = ['query','self','department','center','detail'].map(action => `zsjos:delivery-performance:${action}`)
permissions.push(...['query','update','configure'].map(action => `zsjos:delivery-performance-target:${action}`))
const allowed = params.has('denied') ? ['zsjos:sales-performance-target:update','zsjos:sales-performance-target:configure'] : permissions
createRoot(document.getElementById('root')!).render(<BrowserRouter><ThemeProvider><App>
 {params.has('target') ? <DeliveryPerformanceTargetPage permissions={allowed}/> : <DeliveryPerformancePage permissions={allowed}/>}
</App></ThemeProvider></BrowserRouter>)

import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import AccountProfilePanel from '../src/components/AccountProfilePanel'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import type { MediaStudentDetail } from '../src/services/api'
import '../src/styles/index.css'
// Synthetic account only; API fixtures are intercepted by the acceptance runner.
const account = { id: 777, accountNo: 'TEST-777', version: 0, availableActions: ['MAINTAIN_ACCOUNT'] } as MediaStudentDetail['accounts'][number]
createRoot(document.getElementById('root')!).render(<ThemeProvider><ConfigProvider><App><AccountProfilePanel account={account} canQuery canMaintain onSaved={async () => {}} /></App></ConfigProvider></ThemeProvider>)

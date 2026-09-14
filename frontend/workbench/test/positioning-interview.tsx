// Browser-only acceptance fixture; excluded from the production entry point.
import React from 'react'
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import PositioningInterviewDialog from '../src/components/PositioningInterviewDialog'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import { MemoryRouter } from 'react-router-dom'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'
const fixturePermissions = [
  'zsjos:media-account:create',
  'zsjos:content:create',
  'zsjos:production-ticket:create',
  'zsjos:partner-invitation:create-student',
]
createRoot(document.getElementById('root')!).render(<ThemeProvider><ConfigProvider><App>{location.search.includes('overview') ? <MemoryRouter initialEntries={[`/${location.search}`]}><div style={{ height: '100vh' }}><MediaStudentsPage permissions={fixturePermissions} /></div></MemoryRouter> : <PositioningInterviewDialog relationId={900001} onClose={() => {}} onChanged={() => {}} />}</App></ConfigProvider></ThemeProvider>)

// Browser acceptance entry only; never imported by the production application.
import React from 'react'
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import MediaStudentsPage from '../src/pages/MediaStudentsPage'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'
const permissions = [...(location.search.includes('denied') ? [] : ['zsjos:media-account:query']), 'zsjos:media-account:edit', 'zsjos:media-account:maintenance', ...(location.search.includes('operator') ? [] : ['zsjos:media-account:create'])]
createRoot(document.getElementById('root')!).render(<ThemeProvider><ConfigProvider><App><MemoryRouter initialEntries={['/?personId=900001']}><div style={{ height: '100vh' }}><MediaStudentsPage permissions={permissions} /></div></MemoryRouter></App></ConfigProvider></ThemeProvider>)

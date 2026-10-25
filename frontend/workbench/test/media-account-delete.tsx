// Isolated browser fixture using the production panel and typed HTTP client; never calls a live API.
import React from 'react'
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import AccountProfilePanel from '../src/components/AccountProfilePanel'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { http, type MediaAccount } from '../src/services/api'
import type { AccountProfile } from '../src/services/mediaAccountProfile'
import '../src/styles/index.css'

const query = new URLSearchParams(location.search)
const account: MediaAccount = { id: 900001, accountNo: 'TEST-DELETE', nickname: '', platformValue: '', platformLabelSnapshot: '', version: 1,
  availableActions: query.has('denied') ? [] : [query.has('operator') ? 'REQUEST_DELETE_ACCOUNT' : 'DELETE_ACCOUNT_DIRECTLY'] }
const requests: Array<{ url: string; method?: string; body?: unknown }> = []
let saved = 0
let fail = query.has('fail')
http.defaults.adapter = async config => {
  const url = config.url || ''
  if (config.method === 'post') {
    requests.push({ url, method: config.method, body: JSON.parse(config.data) })
    if (fail) { fail = false; return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 1900011003, msg: '账号已被其他人修改，请刷新后重试' } } }
    account.availableActions = []
    if (url.endsWith('/request-delete')) account.deleteStatus = 'pending'
    else account.deleteStatus = 'approved'
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data: url.endsWith('/request-delete') ? 'fixture-process' : true } }
  }
  if (!url.endsWith('/profile')) throw new Error(`Unexpected fixture request: ${url}`)
  const data: AccountProfile = { account: { ...account }, config: { id: 1, versionNo: 1, fields: [] }, values: {},
    snapshots: [], editableFields: [], missingFields: [], missingByOwner: {}, sourceNotes: {}, files: {},
    canViewHistory: false, studentName: '测试学员', directorName: '所属编导', operatorName: '责任运营' }
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
Object.assign(window, { deletionFixture: { requests, get saved() { return saved } } })
createRoot(document.getElementById('root')!).render(<ThemeProvider><ConfigProvider><App>
  <AccountProfilePanel account={account} canQuery={!query.has('noQuery')} canMaintain={false} onSaved={async () => { saved++ }} />
</App></ConfigProvider></ThemeProvider>)

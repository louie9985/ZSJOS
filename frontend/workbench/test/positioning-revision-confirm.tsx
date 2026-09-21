// UTF-8. Actual components with isolated synthetic transport; no shared business writes.
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import ServicePositioningCard from '../src/components/ServicePositioningCard'
import MediaFeaturePage from '../src/pages/MediaFeaturePage'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import { http, type PositioningCard } from '../src/services/api'
import '../src/styles/index.css'

const query = new URLSearchParams(location.search)
const card: PositioningCard = {
  id: 900001, cardNo: 'PC-TEST', serviceRelationId: 900001, submissionId: 900002,
  status: query.has('draft') ? 'co_creating' : 'confirmed', version: 7, submissionNo: 1,
  fieldsSnapshot: [], valuesSnapshot: {},
  availableActions: query.has('denied') ? [] : [query.has('draft') ? 'EDIT_POSITIONING_DRAFT' : 'START_POSITIONING_REVISION'],
}
const state = { writes: 0, edits: 0, reads: 0 }
Object.assign(window, { revisionFixture: state })
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown
  if (url.endsWith('/start-revision')) {
    state.writes++
    await new Promise(resolve => setTimeout(resolve, 350))
    if (query.has('fail')) return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 1900014003, msg: '定位卡版本已变化，请刷新后重试' } }
    card.status = 'co_creating'; card.version++; card.availableActions = ['EDIT_POSITIONING_DRAFT']
    data = { id: card.id, version: card.version }
  } else if (url.endsWith('/service-overview')) {
    state.reads++
    data = { masterCardId: card.id, current: card, effective: null, candidates: [], history: [], canCreate: false, canSelectMaster: false }
  } else if (url.endsWith('/page')) {
    state.reads++; data = { list: [card], total: 1 }
  } else if (url.endsWith('/get')) data = card
  else throw new Error(`Unexpected fixture request: ${url}`)
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><ConfigProvider><App><MemoryRouter>
  <div style={{ padding: 16 }}>{query.has('list') ? <MediaFeaturePage feature="positioning" /> :
    <ServicePositioningCard serviceRelationId={900001} canQuery refresh={0} onEdit={() => { state.edits++ }} />}</div>
</MemoryRouter></App></ConfigProvider></ThemeProvider>)

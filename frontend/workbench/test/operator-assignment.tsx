// UTF-8. Synthetic browser fixture; no request reaches a business server.
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App, Button } from 'antd'
import OperatorAssignmentDialog from '../src/components/OperatorAssignmentDialog'
import { http } from '../src/services/api'
const mode = new URLSearchParams(location.search).get('mode') || 'change'
let loads = 0, attempts = 0
http.defaults.adapter = async config => {
  const url = config.url || ''
  let data: unknown = true, code = 0, msg = ''
  if (url.endsWith('contact-context')) {
    loads++
    if (mode === 'loading') await new Promise(resolve => setTimeout(resolve, 3000))
    if (mode === 'load-error' && loads === 1) throw new Error('上下文加载失败（测试）')
    data = { serviceRelationId: 10, version: loads, operatorUserId: ['first', 'server-required'].includes(mode) ? undefined : 8,
      operatorUserName: ['first', 'server-required'].includes(mode) ? undefined : '原运营',
      operatorAssignmentConflict: mode === 'conflict', availableActions: mode === 'denied' ? [] : ['ASSIGN_OPERATOR'] }
  } else if (url.endsWith('collaborator-candidates')) {
    data = mode === 'empty' ? [] : [{ id: 8, nickname: '原运营' }, { id: 9, nickname: '新运营' }]
  } else if (url.endsWith('collaborators')) {
    attempts++
    const body = JSON.parse(config.data)
    document.getElementById('result')!.textContent = JSON.stringify({ attempts, ...body })
    if (attempts === 1 && mode === 'server-required') { code = 1900010035; msg = '变更已有协作者时必须填写原因' }
    if (attempts === 1 && mode === 'version') { code = 1900010024; msg = '版本冲突' }
    if (attempts === 1 && mode === 'network') throw new Error('网络异常（测试）')
  } else throw new Error(`Unexpected fixture request: ${url}`)
  return { data: { code, data, msg }, status: 200, statusText: 'OK', headers: {}, config }
}
function Fixture() {
  const [open, setOpen] = useState(true)
  return <App><div id="result">尚未提交</div><Button onClick={() => setOpen(true)}>打开</Button>
    {open && <OperatorAssignmentDialog relationId={10} studentName="测试学员" studentNo="TEST-10" onCancel={() => setOpen(false)} onSaved={async () => { setOpen(false) }} />}</App>
}
createRoot(document.getElementById('root')!).render(<Fixture />)

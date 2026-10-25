import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { HomeRestDayImport } from '../src/features/zsjkqt/ui/HomeRestDayImport'
import { mergeHomeRestDays } from '../src/features/zsjkqt/core/homeRestDays'
import { calendarRestDays } from '../src/services/calendarRestDays'
import '../src/styles/index.css'
import '../src/features/zsjkqt/styles/zsjkqt.css'
let mode = 'success'
Object.assign(window, { restImportFixture: { setMode(value: string) { mode = value } } })
calendarRestDays.list = async (start, end) => {
  if (start !== '2026-06-01' || end !== '2026-06-30') throw Error('wrong month')
  if (mode === 'error') throw Error('读取失败，请重试')
  if (mode === 'empty') return []
  return ['2026-06-02','2026-06-03','2026-06-04','2026-06-06','2026-07-01'].map(date => ({ date, rest: true, version: 1 }))
}
function Fixture() {
  const [value, setValue] = useState({ restDays: [2], paidRestDays: [3] })
  return <div className="zsjkqt-root"><HomeRestDayImport year={2026} month={6} allowed={!location.search.includes('denied')} onImport={(days, kind) => setValue(v => mergeHomeRestDays(v.restDays,v.paidRestDays,days,kind))} /><output>{JSON.stringify(value)}</output></div>
}
createRoot(document.getElementById('root')!).render(<Fixture />)

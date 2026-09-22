import { useRef, type ReactNode } from 'react'
import { Routes, Route, useLocation } from 'react-router-dom'

/** Only the review/media pair uses retention. A frozen route location prevents hidden pages reacting to another tab's query. */
export default function RetainedReviewRoute({ active, children }: { active: boolean; children: ReactNode }) {
  const location = useLocation()
  const retained = useRef(location)
  if (active) retained.current = location
  return <div hidden={!active} style={active ? { height: '100%', minHeight: 0 } : { display: 'none' }}>
    <Routes location={retained.current}><Route path="*" element={children} /></Routes>
  </div>
}

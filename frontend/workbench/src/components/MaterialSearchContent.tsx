import { useEffect, useMemo, useRef, useState } from 'react'
import type { Material } from '../services/materialApi'
import { materialSearchEntries, materialSearchHits, materialSearchSnippet } from '../services/materialSearch'

export function MaterialHighlight({ text, keyword }: { text: string; keyword: string }) {
  if (!keyword) return <>{text}</>
  const pieces = []
  let cursor = 0
  const lower = text.toLocaleLowerCase(), term = keyword.toLocaleLowerCase()
  for (let index = lower.indexOf(term); index !== -1; index = lower.indexOf(term, cursor)) {
    pieces.push(text.slice(cursor, index), <mark key={index}>{text.slice(index, index + keyword.length)}</mark>)
    cursor = index + keyword.length
  }
  pieces.push(text.slice(cursor))
  return <>{pieces}</>
}

export default function MaterialSearchContent({ material, keyword, detail = false, target, onSearch, onLocate }: {
  material: Material; keyword: string; detail?: boolean; target?: string
  onSearch?: (term: string) => void; onLocate?: (key: string) => void
}) {
  const entries = useMemo(() => materialSearchEntries(material), [material])
  const fields = detail ? entries : entries.filter(entry => entry.searchable)
  const hits = materialSearchHits(entries, keyword)
  const [expanded, setExpanded] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!detail || !target || !materialSearchHits(entries, keyword).some(entry => entry.key === target)) return
    // Wait for Drawer motion before scrolling inside its body; never scroll the background feed.
    const timer = window.setTimeout(() => {
      const node = Array.from(root.current?.querySelectorAll<HTMLElement>('[data-material-field]') || [])
        .find(element => element.dataset.materialField === target)
      node?.scrollIntoView({ block: 'nearest' })
      node?.focus({ preventScroll: true })
    }, 350)
    return () => window.clearTimeout(timer)
  }, [detail, target, entries, keyword])
  return <div ref={root} className={`material-search-content${detail ? ' material-search-detail' : ''}`}>
    {detail ? <strong>可检索内容</strong> : <button type="button" className="material-search-link" aria-expanded={expanded}
      onClick={() => setExpanded(!expanded)}>{expanded ? '收起关键词 / 可检索内容' : `展开关键词 / 可检索内容（${fields.length}项）`}</button>}
    {(detail || expanded) && !fields.length && <span className="material-search-muted">暂无可展示的可检索字段</span>}
    {(detail || expanded ? fields : []).map(entry => <div key={entry.key}
      data-material-field={entry.key} tabIndex={detail ? -1 : undefined}
      className={`material-search-field${detail && target === entry.key && hits.includes(entry) ? ' material-search-target' : ''}`}>
      <span className="material-search-label">{entry.label}</span>
      {!detail && entry.terms.length ? <div className="material-search-terms">{entry.terms.map((term, index) =>
        <button type="button" key={index} onClick={() => onSearch?.(term)} aria-label={`查找：${term}`}>
          <MaterialHighlight text={term} keyword={keyword} />
        </button>)}</div> : <span className="material-search-value"><MaterialHighlight
        text={detail ? entry.text : materialSearchSnippet(entry.text, keyword)} keyword={keyword} /></span>}
    </div>)}
    {!detail && keyword && <div className="material-search-hits">
      {hits.length ? hits.map(entry => <button type="button" key={entry.key} onClick={() => onLocate?.(entry.key)}>
        <span>命中：{entry.label}</span><span><MaterialHighlight text={materialSearchSnippet(entry.text, keyword)} keyword={keyword} /></span>
      </button>) : <span className="material-search-muted">当前可见内容中未定位到命中位置</span>}
    </div>}
    {detail && target && !entries.some(entry => entry.key === target && materialSearchHits([entry], keyword).length) &&
      <span className="material-search-muted">当前详情内容已变化，未定位到原命中内容</span>}
  </div>
}

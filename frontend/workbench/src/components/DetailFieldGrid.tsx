import type { ReactNode } from 'react'

export type DetailFieldItem = {
  key: string
  label: ReactNode
  value: ReactNode
  span?: 1 | 2
}

export type DetailFieldGridProps = {
  items: DetailFieldItem[]
  columns?: 1 | 2 | 3
  className?: string
}

const displayValue = (value: ReactNode) => {
  if (value === null || value === undefined || value === '') return '-'
  return typeof value === 'boolean' ? String(value) : value
}

export default function DetailFieldGrid({ items, columns = 2, className }: DetailFieldGridProps) {
  const classes = ['detail-field-grid', `columns-${columns}`, className].filter(Boolean).join(' ')

  return <dl className={classes}>
    {items.map(item => <div key={item.key} className={`detail-field${item.span === 2 ? ' span-2' : ''}`}>
      <dt>{item.label}</dt>
      <dd>{displayValue(item.value)}</dd>
    </div>)}
  </dl>
}

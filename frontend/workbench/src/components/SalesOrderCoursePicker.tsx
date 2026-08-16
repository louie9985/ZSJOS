import { Cascader, Col, Row, Select, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import type { LeadCatalog, LeadCategoryNode } from '../services/api'

export function selectedSkuAttrValues(catalog: LeadCatalog, value?: string): Record<string, string> {
  const [spuRef, skuRef] = value?.split('::') || []
  return catalog.skus.find(item => item.spuRef === spuRef && item.skuRef === skuRef)?.attrValues || {}
}

function categoryOptions(items: LeadCategoryNode[]): any[] {
  return items.map(item => ({ label: item.name, value: item.id, children: item.children?.length ? categoryOptions(item.children) : undefined }))
}

export default function SalesOrderCoursePicker({ catalog, value, onChange, disabled }: {
  catalog: LeadCatalog
  value?: string
  onChange?: (value: string) => void
  disabled?: boolean
}) {
  const [categoryPath, setCategoryPath] = useState<number[]>([])
  const [spuRef, setSpuRef] = useState<string>()
  const [attrValues, setAttrValues] = useState<Record<string, string>>({})
  const [skuRef, setSkuRef] = useState<string>()
  useEffect(() => {
    const [nextSpu, nextSku] = value?.split('::') || []
    const spu = catalog.spus.find(item => item.spuRef === nextSpu)
    setSpuRef(nextSpu || undefined); setSkuRef(nextSku || undefined)
    setAttrValues(selectedSkuAttrValues(catalog, value))
    setCategoryPath(spu?.categoryPath.map(item => item.id) || [])
  }, [catalog, value])
  const selectedSpu = catalog.spus.find(item => item.spuRef === spuRef)
  const selectedSkus = useMemo(() => catalog.skus.filter(item => item.spuRef === spuRef), [catalog.skus, spuRef])
  const selectedSku = selectedSkus.find(item => item.skuRef === skuRef)
  const spuOptions = catalog.spus.filter(item => item.categoryId === categoryPath.at(-1))
    .map(item => ({ label: item.spuName, value: item.spuRef }))
  const chooseSku = (nextSku: string) => { setSkuRef(nextSku); onChange?.(spuRef && nextSku ? `${spuRef}::${nextSku}` : '') }
  const chooseAttr = (key: string, next: string) => {
    const nextValues = { ...attrValues, [key]: next }; setAttrValues(nextValues)
    if (selectedSpu && selectedSpu.attrs.filter(item => item.required).every(item => nextValues[item.attrKey])) {
      const matched = selectedSkus.find(sku => selectedSpu.attrs.every(attr => !attr.required || sku.attrValues[attr.attrKey] === nextValues[attr.attrKey]))
      if (matched) { setSkuRef(matched.skuRef); onChange?.(`${spuRef}::${matched.skuRef}`) }
    }
  }
  return <div className="sales-order-course-picker">
    <Row gutter={[8, 8]}>
      <Col xs={24} md={8}><Typography.Text type="secondary">课程分类</Typography.Text><Cascader value={categoryPath} options={categoryOptions(catalog.categoryTree)} disabled={disabled} showSearch placeholder="请选择课程分类" onChange={path => { setCategoryPath(Array.from(path) as number[]); setSpuRef(undefined); setSkuRef(undefined); setAttrValues({}); onChange?.('') }}/></Col>
      <Col xs={24} md={8}><Typography.Text type="secondary">课程</Typography.Text><Select className="w-full" value={spuRef} options={spuOptions} disabled={disabled || !categoryPath.length} placeholder="请选择课程" onChange={next => { setSpuRef(next); setSkuRef(undefined); setAttrValues({}); onChange?.('') }}/></Col>
      {selectedSpu?.attrs.map(attr => <Col xs={24} md={8} key={attr.attrKey}><Typography.Text type="secondary">{attr.attrName}</Typography.Text><Select className="w-full" value={attrValues[attr.attrKey]} options={attr.values.map(item => ({ label: item.label, value: item.value }))} disabled={disabled} placeholder={`请选择${attr.attrName}`} onChange={next => chooseAttr(attr.attrKey, next)}/></Col>)}
      {selectedSpu && !selectedSpu.attrs.length && <Col xs={24} md={8}><Typography.Text type="secondary">具体班次/方案</Typography.Text><Select className="w-full" value={skuRef} options={selectedSkus.map(item => ({ label: `${item.skuName}（¥${item.price}）`, value: item.skuRef }))} disabled={disabled} placeholder="请选择具体班次/方案" onChange={chooseSku}/></Col>}
    </Row>
    {selectedSku && <Typography.Text type="secondary">已选：{selectedSku.skuName} · 参考价 ¥{Number(selectedSku.price).toFixed(2)}</Typography.Text>}
  </div>
}

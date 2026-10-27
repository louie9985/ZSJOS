import { Button, Cascader, Col, Row, Select, Typography } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { LeadCatalog, LeadCategoryNode, SalesOrder } from '../services/api'
import ProductSpecs from './ProductSpecs'
import { catalogSpecs } from '../services/productSpecs'

export function selectedSkuAttrValues(catalog: LeadCatalog, value?: string): Record<string, string> {
  const [spuRef, skuRef] = value?.split('::') || []
  return catalog.skus.find(item => item.spuRef === spuRef && item.skuRef === skuRef)?.attrValues || {}
}

function categoryOptions(items: LeadCategoryNode[]): any[] {
  return items.map(item => ({ label: item.name, value: item.id, children: item.children?.length ? categoryOptions(item.children) : undefined }))
}

export function resolveOrderSku(catalog: LeadCatalog, spuRef: string | undefined, values: Record<string, string>) {
  const spu = catalog.spus.find(item => item.spuRef === spuRef)
  if (!spu || spu.attrs.some(attr => attr.required && !values[attr.attrKey])) return undefined
  const matches = catalog.skus.filter(sku => sku.spuRef === spuRef
    && Object.entries(values).every(([key, value]) => !value || sku.attrValues[key] === value))
  return matches.length === 1 ? matches[0] : undefined
}

export function selectableOrderAttrValues(catalog: LeadCatalog, spuRef: string | undefined, attrKey: string) {
  const attr = catalog.spus.find(item => item.spuRef === spuRef)?.attrs.find(item => item.attrKey === attrKey)
  return attr?.values.filter(value => catalog.skus.some(sku => sku.spuRef === spuRef
    && sku.attrValues[attrKey] === value.value)) ?? []
}

export default function SalesOrderCoursePicker({ catalog, value, onChange, disabled, historicalItems }: {
  historicalItems?: SalesOrder['items']
  catalog: LeadCatalog
  value?: string
  onChange?: (value: string) => void
  disabled?: boolean
}) {
  const [categoryPath, setCategoryPath] = useState<number[]>([])
  const [spuRef, setSpuRef] = useState<string>()
  const [attrValues, setAttrValues] = useState<Record<string, string>>({})
  const [skuRef, setSkuRef] = useState<string>()
  const emitted = useRef<{ value: string; catalog: LeadCatalog } | undefined>(undefined)
  const emitSelection = (next: string) => { emitted.current = { value: next, catalog }; onChange?.(next) }
  useEffect(() => {
    // Clearing the submitted SKU must not erase the user's partially selected attributes.
    const echo = emitted.current
    emitted.current = undefined
    if (echo?.catalog === catalog && echo.value === value) return
    if (!value) {
      setSpuRef(undefined); setSkuRef(undefined); setAttrValues({})
      return
    }
    const [nextSpu, nextSku] = value?.split('::') || []
    const historicalSelection = historicalItems?.some(item => `${item.productRef}::${item.skuRef}` === value)
    if (!historicalSelection && !catalog.skus.some(item => item.spuRef === nextSpu && item.skuRef === nextSku)) {
      setSkuRef(undefined); setAttrValues({}); onChange?.('')
      return
    }
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
  const chooseSku = (nextSku: string) => {
    const matched = selectedSkus.find(sku => sku.skuRef === nextSku)
    setSkuRef(matched?.skuRef); emitSelection(matched ? `${spuRef}::${matched.skuRef}` : '')
  }
  const chooseAttr = (key: string, next: string) => {
    const nextValues = { ...attrValues, [key]: next }; setAttrValues(nextValues)
    const matched = resolveOrderSku(catalog, spuRef, nextValues)
    setSkuRef(matched?.skuRef)
    emitSelection(matched ? `${spuRef}::${matched.skuRef}` : '')
  }
  const historical = historicalItems?.find(item => `${item.productRef}::${item.skuRef}` === value)
  if (historical) return <div className="sales-order-course-picker">
    <Typography.Text>{historical.productName || '历史未记录'}{historical.skuName ? ` / ${historical.skuName}` : ''}</Typography.Text>
    <ProductSpecs product={historical}/>
    <Button type="link" disabled={disabled} onClick={() => onChange?.('')}>重新选择课程</Button>
  </div>
  return <div className="sales-order-course-picker">
    <Row gutter={[8, 8]}>
      <Col xs={24} md={8}><Typography.Text type="secondary">课程分类</Typography.Text><Cascader classNames={{ popup: { root: "sales-order-course-popup" } }} value={categoryPath} options={categoryOptions(catalog.categoryTree)} disabled={disabled} showSearch placeholder="请选择课程分类" onChange={path => { setCategoryPath(Array.from(path) as number[]); setSpuRef(undefined); setSkuRef(undefined); setAttrValues({}); emitSelection('') }}/></Col>
      <Col xs={24} md={8}><Typography.Text type="secondary">课程</Typography.Text><Select virtual={false} classNames={{ popup: { root: "sales-order-course-popup" } }} className="w-full" value={spuRef} options={spuOptions} disabled={disabled || !categoryPath.length} placeholder="请选择课程" onChange={next => { setSpuRef(next); setSkuRef(undefined); setAttrValues({}); emitSelection('') }}/></Col>
      {selectedSpu?.attrs.map(attr => <Col xs={24} md={8} key={attr.attrKey}><Typography.Text type="secondary">{attr.attrName}</Typography.Text><Select virtual={false} classNames={{ popup: { root: "sales-order-course-popup" } }} className="w-full" value={attrValues[attr.attrKey]} options={selectableOrderAttrValues(catalog, spuRef, attr.attrKey).map(item => ({ label: item.label, value: item.value }))} disabled={disabled} placeholder={`请选择${attr.attrName}`} onChange={next => chooseAttr(attr.attrKey, next)}/></Col>)}
      {selectedSpu && !selectedSpu.attrs.length && <Col xs={24} md={8}><Typography.Text type="secondary">具体班次/方案</Typography.Text><Select virtual={false} classNames={{ popup: { root: "sales-order-course-popup" } }} className="w-full" value={skuRef} options={selectedSkus.map(item => ({ label: `${item.skuName}（¥${item.price}）`, value: item.skuRef }))} disabled={disabled} placeholder="请选择具体班次/方案" onChange={chooseSku}/></Col>}
    </Row>
    {selectedSpu && !selectedSku && Object.values(attrValues).some(Boolean)
      && selectedSpu.attrs.filter(attr => attr.required).every(attr => attrValues[attr.attrKey])
      && <Typography.Text type="warning">当前规格组合不可用，请重新选择</Typography.Text>}
    {selectedSku && <div><ProductSpecs product={{ specs: selectedSku.specs ?? catalogSpecs(selectedSku.attrValues, selectedSpu?.attrs) }} /><Typography.Text type="secondary">{selectedSku.skuName} · 参考价 ¥{Number(selectedSku.price).toFixed(2)}</Typography.Text></div>}
  </div>
}

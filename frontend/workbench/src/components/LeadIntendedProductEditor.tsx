import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { App, Button, Card, Cascader, Checkbox, Col, Empty, Radio, Row, Select, Space, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'
import type { LeadCatalog, LeadCategoryNode, ManagedLeadProduct } from '../services/api'

export type IntendedProductSelection = {
  key: string; spuRef?: string; skuRef?: string; spuUnknown: boolean; skuUnknown: boolean
  spuName: string; skuName: string; path: string; price?: number
}

export function selectionFromManagedProduct(product: ManagedLeadProduct): IntendedProductSelection {
  const key = product.spuRef ? `${product.spuRef}|${product.skuRef || 'UNKNOWN'}` : 'UNKNOWN'
  return {
    key, spuRef: product.spuRef, skuRef: product.skuRef, spuUnknown: !product.spuRef,
    skuUnknown: !product.skuRef, spuName: product.spuName || '未明确课程',
    skuName: product.skuName || '未明确具体班次/方案', path: product.categoryName || '未明确课程',
    price: product.price
  }
}

export default function LeadIntendedProductEditor({ catalog, value, primaryKey, onChange, onPrimaryChange, disabled }: {
  catalog: LeadCatalog; value: IntendedProductSelection[]; primaryKey?: string
  onChange: (value: IntendedProductSelection[]) => void; onPrimaryChange: (key?: string) => void; disabled?: boolean
}) {
  const { message } = App.useApp()
  const [categoryPathIds, setCategoryPathIds] = useState<number[]>([])
  const [spuUnknown, setSpuUnknown] = useState(false)
  const [spuRef, setSpuRef] = useState<string>()
  const [attrValues, setAttrValues] = useState<Record<string, string>>({})
  const [skuRef, setSkuRef] = useState<string>()
  const [skuUnknown, setSkuUnknown] = useState(false)
  const categoryOptions = useMemo(() => catalog.categoryTree.map(function mapCategory(item: LeadCategoryNode): any {
    return { label: item.name, value: item.id, children: item.children?.length ? item.children.map(mapCategory) : undefined }
  }), [catalog.categoryTree])
  const selectedCategoryId = categoryPathIds.at(-1)
  const spuOptions = useMemo(() => catalog.spus.filter(item => item.categoryId === selectedCategoryId)
    .map(item => ({ label: item.spuName, value: item.spuRef })), [catalog.spus, selectedCategoryId])
  const selectedSpu = catalog.spus.find(item => item.spuRef === spuRef)
  const selectedSpuSkus = useMemo(() => catalog.skus.filter(sku => sku.spuRef === spuRef), [catalog.skus, spuRef])
  const matchedSku = selectedSpuSkus.find(sku => selectedSpu?.attrs.every(attr => !attr.required
    || sku.attrValues[attr.attrKey] === attrValues[attr.attrKey]))
  const selectedSku = selectedSpuSkus.find(sku => sku.skuRef === (selectedSpu?.attrs.length ? matchedSku?.skuRef : skuRef))
  const canAdd = spuUnknown || Boolean(selectedSpu && (skuUnknown || selectedSku))
  const resetDraft = () => { setCategoryPathIds([]); setSpuUnknown(false); setSpuRef(undefined); setAttrValues({}); setSkuRef(undefined); setSkuUnknown(false) }
  const add = () => {
    if (!canAdd) return
    const key = spuUnknown ? 'UNKNOWN' : `${selectedSpu!.spuRef}|${skuUnknown ? 'UNKNOWN' : selectedSku!.skuRef}`
    if (value.some(item => item.key === key)) return void message.warning('该意向课程已经添加')
    const next = spuUnknown
      ? { key, spuUnknown: true, skuUnknown: true, spuName: '未明确课程', skuName: '未明确具体班次/方案', path: '未明确课程' }
      : { key, spuRef: selectedSpu!.spuRef, skuRef: skuUnknown ? undefined : selectedSku!.skuRef,
          spuUnknown: false, skuUnknown, spuName: selectedSpu!.spuName,
          skuName: skuUnknown ? '未明确具体班次/方案' : selectedSku!.skuName,
          path: selectedSpu!.categoryPath.map(node => node.name).join(' / '), price: skuUnknown ? undefined : selectedSku!.price }
    onChange([...value, next]); if (!primaryKey) onPrimaryChange(key); resetDraft()
  }
  const remove = (key: string) => {
    const next = value.filter(item => item.key !== key); onChange(next)
    if (primaryKey === key) onPrimaryChange(next[0]?.key)
  }
  return <div className="lead-product-editor">
    <Row gutter={[12, 0]} align="bottom">
      <Col xs={24} md={8}><Cascader disabled={disabled || spuUnknown} value={categoryPathIds} options={categoryOptions}
        showSearch placeholder="请选择课程分类" onChange={path => { setCategoryPathIds(Array.from(path) as number[]); setSpuRef(undefined); setAttrValues({}); setSkuRef(undefined); setSkuUnknown(false) }}/></Col>
      <Col xs={24} md={8}><Select className="w-full" disabled={disabled || !selectedCategoryId || spuUnknown} value={spuRef}
        options={spuOptions} placeholder="请选择课程" onChange={next => { setSpuRef(next); setAttrValues({}); setSkuRef(undefined); setSkuUnknown(false) }}/></Col>
      <Col xs={24} md={6}><Button block type="primary" icon={<PlusOutlined/>} disabled={disabled || !canAdd} onClick={add}>添加意向课程</Button></Col>
      <Col xs={24}><Checkbox disabled={disabled} checked={spuUnknown} onChange={event => { setSpuUnknown(event.target.checked); if (event.target.checked) { setCategoryPathIds([]); setSpuRef(undefined); setAttrValues({}); setSkuRef(undefined); setSkuUnknown(true) } }}>未明确课程</Checkbox></Col>
      {selectedSpu?.attrs.map(attr => <Col xs={24} md={6} key={attr.attrKey}><Select className="w-full" disabled={disabled || skuUnknown}
        placeholder={attr.attrName} value={attrValues[attr.attrKey]} options={attr.values.map(item => ({ label: item.label, value: item.value }))}
        onChange={next => setAttrValues(current => ({ ...current, [attr.attrKey]: next }))}/></Col>)}
      {selectedSpu && !selectedSpu.attrs.length && <Col xs={24} md={8}><Select className="w-full" disabled={disabled || skuUnknown}
        placeholder="请选择具体班次/方案" value={skuRef} options={selectedSpuSkus.map(sku => ({ label: `${sku.skuName}（¥${sku.price}）`, value: sku.skuRef }))} onChange={setSkuRef}/></Col>}
      {selectedSpu && <Col xs={24} md={8}><Checkbox disabled={disabled} checked={skuUnknown} onChange={event => { setSkuUnknown(event.target.checked); if (event.target.checked) { setSkuRef(undefined); setAttrValues({}) } }}>未明确具体班次/方案</Checkbox></Col>}
    </Row>
    {!value.length ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请添加至少一条意向课程"/> :
      <Radio.Group value={primaryKey} onChange={event => onPrimaryChange(event.target.value)} className="w-full">
        <Space direction="vertical" className="w-full">{value.map(item => <Card key={item.key} size="small">
          <div className="lead-product-row"><div><Radio value={item.key}>主意向</Radio><strong>{item.spuName}</strong>
            <div><Typography.Text type="secondary">{item.path} · {item.skuName}</Typography.Text></div>
            <div>{item.price == null ? <Tag>价格待确认</Tag> : <Tag color="green">¥{item.price.toFixed(2)}</Tag>}</div></div>
            <Button danger type="text" icon={<DeleteOutlined/>} aria-label="删除意向课程" onClick={() => remove(item.key)}/></div>
        </Card>)}</Space>
      </Radio.Group>}
  </div>
}

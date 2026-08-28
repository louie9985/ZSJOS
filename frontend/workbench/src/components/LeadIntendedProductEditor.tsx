import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { App, Button, Card, Cascader, Checkbox, Empty, Radio, Segmented, Select, Tag, Typography } from 'antd'
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
  const categoryTitle = selectedSpu?.categoryPath.map(node => node.name).join(' / ')
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
    {/* 「未明确课程」会让下面两个选择器整体失效，是模式切换而非分类字段的附属选项，
        故用 Segmented 显式摆出两条路径，而不是一个容易被忽略的小勾选框。 */}
    <Segmented className="lead-product-mode" disabled={disabled} value={spuUnknown ? 'unknown' : 'select'}
      onChange={next => {
        const unknown = next === 'unknown'
        setSpuUnknown(unknown); setCategoryPathIds([]); setSpuRef(undefined); setAttrValues({}); setSkuRef(undefined)
        setSkuUnknown(unknown)
      }}
      options={[{ label: '选择具体课程', value: 'select' }, { label: '未明确课程', value: 'unknown' }]}/>
    <div className={`lead-product-primary-grid${spuUnknown ? ' unknown' : ''}`}>
      {spuUnknown
        // 灰着的选择器仍在暗示「我能填」，故整块换成占位说明
        ? <div className="lead-product-unknown-hint"><Typography.Text type="secondary">不指定课程分类与课程，直接添加一条「未明确课程」意向</Typography.Text></div>
        : <>
          <div className="lead-product-field" title={categoryTitle}><Typography.Text type="secondary">课程分类</Typography.Text>
            <Cascader className="lead-product-control" popupClassName="lead-product-dropdown" popupMatchSelectWidth value={categoryPathIds} options={categoryOptions}
            disabled={disabled} showSearch placeholder="请选择课程分类" onChange={path => { setCategoryPathIds(Array.from(path) as number[]); setSpuRef(undefined); setAttrValues({}); setSkuRef(undefined); setSkuUnknown(false) }}/></div>
          <div className="lead-product-field" title={selectedSpu?.spuName}><Typography.Text type="secondary">课程</Typography.Text><Select className="lead-product-control" popupClassName="lead-product-dropdown" popupMatchSelectWidth disabled={disabled || !selectedCategoryId} value={spuRef}
            options={spuOptions} placeholder="请选择课程" onChange={next => { setSpuRef(next); setAttrValues({}); setSkuRef(undefined); setSkuUnknown(false) }}/></div>
        </>}
      {/* 选好但未加入列表是这个编辑器最容易漏的一步，故按钮文案随选择变化并常驻高亮。 */}
      <Button className={`lead-product-add${canAdd ? ' ready' : ''}`} type="primary" icon={<PlusOutlined/>} disabled={disabled || !canAdd} onClick={add}>{spuUnknown ? '添加未明确课程' : '添加意向课程'}</Button>
    </div>
    {selectedSpu && <div className="lead-product-secondary-grid">
      {selectedSpu.attrs.map(attr => {
        const selectedAttr = attr.values.find(item => item.value === attrValues[attr.attrKey])
        return <div className="lead-product-field" title={selectedAttr?.label} key={attr.attrKey}><Typography.Text type="secondary">{attr.attrName}</Typography.Text><Select className="lead-product-control" popupClassName="lead-product-dropdown" popupMatchSelectWidth disabled={disabled || skuUnknown}
          placeholder={attr.attrName} value={attrValues[attr.attrKey]} options={attr.values.map(item => ({ label: item.label, value: item.value }))}
          onChange={next => setAttrValues(current => ({ ...current, [attr.attrKey]: next }))}/></div>
      })}
      {!selectedSpu.attrs.length && <div className="lead-product-field" title={selectedSku?.skuName}><Typography.Text type="secondary">具体班次/方案</Typography.Text><Select className="lead-product-control" popupClassName="lead-product-dropdown" popupMatchSelectWidth disabled={disabled || skuUnknown}
        placeholder="请选择具体班次/方案" value={skuRef} options={selectedSpuSkus.map(sku => ({ label: `${sku.skuName}（¥${sku.price}）`, value: sku.skuRef }))} onChange={setSkuRef}/></div>}
      <div className="lead-product-checkbox"><Checkbox className="lead-product-checkbox-control" disabled={disabled} checked={skuUnknown} onChange={event => { setSkuUnknown(event.target.checked); if (event.target.checked) { setSkuRef(undefined); setAttrValues({}) } }}>未明确具体班次/方案</Checkbox></div>
    </div>}
    {!value.length ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={canAdd ? `已选好，点上方「${spuUnknown ? '添加未明确课程' : '添加意向课程'}」加入列表` : '请添加至少一条意向课程'}/> :
      <Radio.Group value={primaryKey} onChange={event => onPrimaryChange(event.target.value)} className="w-full">
        <div className="lead-product-list">{value.map(item => <Card className="lead-product-card" key={item.key} size="small">
          <div className="lead-product-row"><div className="lead-product-copy"><div className="lead-product-title"><Radio value={item.key}>主意向</Radio><strong title={item.spuName}>{item.spuName}</strong></div>
            <Typography.Text className="lead-product-path" type="secondary" title={`${item.path} · ${item.skuName}`}>{item.path} · {item.skuName}</Typography.Text>
            <div>{item.price == null ? <Tag>价格待确认</Tag> : <Tag color="green">¥{item.price.toFixed(2)}</Tag>}</div></div>
            <Button danger type="text" icon={<DeleteOutlined/>} aria-label="删除意向课程" onClick={() => remove(item.key)}/></div>
        </Card>)}</div>
      </Radio.Group>}
  </div>
}

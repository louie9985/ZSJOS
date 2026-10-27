import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, Form } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import SalesOrderCoursePicker from '../src/components/SalesOrderCoursePicker'
import type { LeadCatalog, SalesOrder } from '../src/services/api'
import '../src/styles/index.css'
const initial: LeadCatalog = {
  categoryTree: [{ id: 1, name: '测试分类' }],
  spus: [{ categoryId: 1, categoryName: '测试分类', categoryPath: [{ id: 1, name: '测试分类' }], spuRef: 'course', spuName: '测试课程', attrs: [
    { attrKey: 'level', attrName: '等级', required: true, values: [{ value: 'a', label: '甲级' }, { value: 'b', label: '乙级' }, { value: 'retired', label: '已停用历史等级' }] },
    { attrKey: 'class', attrName: '班型', required: true, values: [{ value: 'x', label: 'X班' }, { value: 'y', label: 'Y班' }] },
  ] }],
  skus: [{ spuRef: 'course', skuRef: 'ax', skuName: '甲级X班', price: 100, attrValues: { level: 'a', class: 'x' } },
    { spuRef: 'course', skuRef: 'by', skuName: '乙级Y班', price: 200, attrValues: { level: 'b', class: 'y' } }],
}
function Fixture() {
  const [catalog, setCatalog] = useState(initial)
  const [historical, setHistorical] = useState<SalesOrder['items']>()
  const [submitted, setSubmitted] = useState('未提交')
  const [form] = Form.useForm()
  const value = Form.useWatch('courseKey', form)
  return <ThemeProvider><App><main style={{ maxWidth: 900, padding: 16, margin: 'auto' }}>
    <Form form={form} initialValues={{ courseKey: 'course::ax' }} onFinish={v => setSubmitted(v.courseKey)}>
      <Form.Item name="courseKey" rules={[{ required: true, message: '请选择成交课程' }]}><SalesOrderCoursePicker catalog={catalog} historicalItems={historical}/></Form.Item>
      <Button htmlType="submit">提交测试</Button>
    </Form>
    <Button onClick={() => setCatalog({ ...initial, skus: initial.skus.slice(0, 1) })}>移除乙级SKU</Button>
    <Button onClick={() => setCatalog({ ...initial, skus: [] })}>清空启用SKU</Button>
    <Button onClick={() => { setHistorical([{ productRef: 'course', skuRef: 'retired', productName: '历史课程', skuName: '历史停用规格', specs: [] }] as unknown as SalesOrder['items']); form.setFieldValue('courseKey', 'course::retired') }}>查看历史订单</Button>
    <output data-testid="selection">{value || 'EMPTY'}</output><output data-testid="submitted">{submitted}</output>
  </main></App></ThemeProvider>
}
createRoot(document.getElementById('root')!).render(<Fixture/> )

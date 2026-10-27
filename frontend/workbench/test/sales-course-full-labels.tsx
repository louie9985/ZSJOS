import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import SalesOrderCoursePicker from '../src/components/SalesOrderCoursePicker'
import type { LeadCatalog } from '../src/services/api'
import '../src/styles/index.css'
const label = '中国心理卫生协会｜心理咨询师高级技能培训｜VIP高级全程服务方案（含考前辅导与专项实践课程）'
const category = '职业考证与心理家庭教育专项课程分类'
const catalog: LeadCatalog = {
  categoryTree: [{ id: 1, name: category }],
  spus: [{ categoryId: 1, categoryName: category, categoryPath: [{ id: 1, name: category }], spuRef: 'course', spuName: '心理咨询师专业能力提升与家庭教育指导综合课程', attrs: [{ attrKey: 'spec', attrName: '销售规格', required: true, values: Array.from({ length: 12 }, (_, i) => ({ value: String(i), label: `${label} ${i}` })) }] }],
  skus: Array.from({ length: 12 }, (_, i) => ({ spuRef: 'course', skuRef: String(i), skuName: `${label} ${i}`, price: 100, attrValues: { spec: String(i) } })),
}
function Fixture() {
  const [value, setValue] = useState('course::0')
  return <ThemeProvider><App><main style={{ maxWidth: 800, margin: '24px auto', padding: 12 }}><h1>成交课程长文本验证（测试数据）</h1><SalesOrderCoursePicker catalog={catalog} value={value} onChange={setValue}/><output>{value}</output></main></App></ThemeProvider>
}
createRoot(document.getElementById('root')!).render(<Fixture/>)

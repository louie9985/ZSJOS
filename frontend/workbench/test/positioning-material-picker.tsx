// Browser regression: an untouched Form.Item supplies undefined, not an empty array.
import { Component, useState, type ReactNode } from 'react'
import { createRoot } from 'react-dom/client'
import { App, Button, ConfigProvider, Form } from 'antd'
import PositioningCardMaterialPicker from '../src/components/PositioningCardMaterialPicker'
import { http } from '../src/services/api'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import '../src/styles/index.css'

http.defaults.adapter = async config => {
  const data = config.url?.includes('/material-type/') ? [{ id: 1, code: 'viral_account', name: '参考账号', status: 0 }]
    : config.url?.endsWith('/page') ? { list: [{ id: 11, title: '测试参考账号', currentEffectiveVersionId: 101 }], total: 1 }
    : config.url?.includes('/version/') ? { id: 101, title: '测试参考账号' } : []
  return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data } }
}
class Boundary extends Component<{ children: ReactNode }, { error?: string }> {
  state: { error?: string } = {}
  static getDerivedStateFromError(error: Error) { return { error: error.message } }
  render() { return this.state.error ? <p role="alert">渲染失败：{this.state.error}</p> : this.props.children }
}
function Fixture() {
  const [form] = Form.useForm()
  const [round, setRound] = useState(0)
  return <><Button onClick={() => setRound(value => value + 1)}>父组件重渲染 {round}</Button>
    <Button onClick={() => form.resetFields()}>重置空表单</Button>
    <Form form={form}>{Array.from({ length: 10 }, (_, i) => <Form.Item key={i} name={`refs_${i}`} label={`参考素材 ${i + 1}`}>
      <PositioningCardMaterialPicker canQuery field={{ key: `refs_${i}`, title: '参考素材', type: 'material_picker', enabled: true, required: false, sort: i, materialTypeCode: 'viral_account' }} />
    </Form.Item>)}</Form></>
}
createRoot(document.getElementById('root')!).render(<ThemeProvider><ConfigProvider><App><Boundary><Fixture /></Boundary></App></ConfigProvider></ThemeProvider>)

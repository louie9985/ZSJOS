import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Card, DatePicker, Form, Select, Skeleton, Space, message } from 'antd'
import { ReloadOutlined, SaveOutlined } from '@ant-design/icons'
import { api, type HrmSalaryConfig } from '../services/api'
import dayjs from 'dayjs'

const SOCIAL_SECURITY_MONTH_TYPE = [
  { value: 0, label: '上月' },
  { value: 1, label: '当月' },
  { value: 2, label: '次月' }
]

/** 计薪设置：计薪周期、社保对应月、工资起始年月。首次需创建，后续仅可改社保对应月。 */
export default function HrmSalaryConfigPage({ permissions }: { permissions: string[] }) {
  const [config, setConfig] = useState<HrmSalaryConfig>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()

  const canUpdate = permissions.includes('hrm:salary:config:update')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const result = await api.hrm.salaryCfg.config.get()
      setConfig(result)
      form.setFieldsValue({
        cycleStartDay: result.cycleStartDay,
        socialSecurityMonthType: result.socialSecurityMonthType,
        startMonth: result.startYear != null && result.startMonth != null ? dayjs(new Date(result.startYear, result.startMonth - 1, 1)) : undefined
      })
    } catch (e) { setError(e instanceof Error ? e.message : '计薪配置加载失败') }
    finally { setLoading(false) }
  }, [form])

  useEffect(() => { void load() }, [load])

  const handleSave = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (!config) {
        const start = values.startMonth as dayjs.Dayjs
        await api.hrm.salaryCfg.config.create({
          cycleStartDay: values.cycleStartDay,
          socialSecurityMonthType: values.socialSecurityMonthType,
          startYear: start.year(),
          startMonth: start.month() + 1
        })
      } else {
        await api.hrm.salaryCfg.config.update({ socialSecurityMonthType: values.socialSecurityMonthType })
      }
      message.success('已保存')
      void load()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setSaving(false) }
  }

  if (loading && !config) return <section className="workspace-page hrm-page"><Skeleton active paragraph={{ rows: 6 }}/></section>

  return <section className="workspace-page hrm-page hrm-salary-config-page">
    <div className="page-heading">
      <span className="hrm-muted">计薪设置对全公司生效，改动前请确认薪资核算影响</span>
      <Button icon={<ReloadOutlined/>} onClick={() => void load()}>刷新</Button>
    </div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>重试</Button>}/>}
    {config && <Card className="hrm-table-area">
      <Form form={form} layout="vertical" className="hrm-edit-grid">
        <Form.Item name="cycleStartDay" label="计薪周期开始日" rules={[{ required: true, message: '请选择开始日' }]}
          extra="每月从该日开始计算上一计薪周期，例如 26 号">
          <Select disabled={!!config} options={Array.from({ length: 31 }, (_, i) => ({ value: i + 1, label: `${i + 1} 号` }))}/>
        </Form.Item>
        <Form.Item name="socialSecurityMonthType" label="社保对应月份类型" rules={[{ required: true, message: '请选择' }]}
          extra="工资表对应的是当月还是上月社保">
          <Select placeholder="请选择" options={SOCIAL_SECURITY_MONTH_TYPE}/>
        </Form.Item>
        <Form.Item name="startMonth" label="工资开始月份" rules={[{ required: true, message: '请选择' }]}
          extra="系统从该月开始产生工资表">
          <DatePicker picker="month" disabled={!!config} style={{ width: '100%' }}/>
        </Form.Item>
        <Form.Item label=" " >
          <Space>
            <Button type="primary" icon={<SaveOutlined/>} loading={saving} disabled={!canUpdate && !!config} onClick={() => void handleSave()}>保存</Button>
          </Space>
        </Form.Item>
      </Form>
      {!config && <Alert className="hrm-inline-alert" message="首次配置将创建计薪设置，创建后周期开始日与开始月份不可再修改" type="warning" showIcon/>}
    </Card>}
  </section>
}

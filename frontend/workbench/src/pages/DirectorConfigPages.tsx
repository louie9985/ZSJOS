import { App, Alert, Button, Card, Checkbox, Col, Empty, Form, Input, InputNumber, Result, Row, Select, Space, Spin, Tabs, Tag, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, type DictData, type DirectorConfig, type DirectorTemplate, type StudentContactFormField } from '../services/api'
import { materialApi, type MaterialType } from '../services/materialApi'
import { DICT_TYPE } from '../constants'

const enumTypes = new Set(['select', 'multi_select', 'radio', 'checkbox_group'])

export function DirectorTemplateConfigPage({ positioning = false, permissions }: { positioning?: boolean; permissions: string[] }) {
  const { message } = App.useApp()
  const prefix = positioning ? 'zsjos:positioning-template' : 'zsjos:director-interview-template'
  const canQuery = permissions.includes(`${prefix}:query`)
  const canUpdate = permissions.includes(`${prefix}:update`)
  const canPublish = permissions.includes(`${prefix}:publish`)
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [error, setError] = useState('')
  const [templates, setTemplates] = useState<DirectorTemplate[]>([]), [selected, setSelected] = useState<number>()
  const [fields, setFields] = useState<StudentContactFormField[]>([]), [active, setActive] = useState(0)
  const [historyId, setHistoryId] = useState<number>(), [dirty, setDirty] = useState(false)
  const [dicts, setDicts] = useState<Record<string, DictData[]>>({})
  const [materialTypes, setMaterialTypes] = useState<MaterialType[]>([])
  const [platforms, setPlatforms] = useState<DictData[]>([]), [stages, setStages] = useState<DictData[]>([])
  const current = useMemo(() => templates.find(x => x.id === selected), [templates, selected])
  const version = historyId ? current?.versions.find(x => x.id === historyId) : current?.draft || current?.published
  const editable = canUpdate && !!current?.draft && !historyId && !busy && !loading
  const field = fields[active]
  const load = useCallback(async () => {
    if (!canQuery) { setTemplates([]); setLoading(false); return }
    setLoading(true); setError('')
    try {
      const rows = await api.directorConfig.templates(positioning)
      setTemplates(rows); setSelected(value => rows.some(x => x.id === value) ? value : rows[0]?.id)
      setHistoryId(undefined); setDirty(false)
      if (positioning) {
        const [types, platformOptions, stageOptions] = await Promise.all([materialApi.types(), api.dictDataByType('zsjos_account_platform'), api.dictDataByType(DICT_TYPE.MEDIA_ACCOUNT_STAGE)])
        setMaterialTypes(types); setPlatforms(platformOptions); setStages(stageOptions)
      }
    } catch (e) { setError(e instanceof Error ? e.message : '加载失败，请重试') }
    finally { setLoading(false) }
  }, [positioning, canQuery])
  useEffect(() => { void load() }, [load])
  useEffect(() => { setFields((version?.fields || []).map(x => ({ ...x }))); setActive(0); setDirty(false) }, [version])
  useEffect(() => {
    if (!positioning || !field?.dictType || dicts[field.dictType]) return
    api.dictDataByType(field.dictType).then(rows => setDicts(x => ({ ...x, [field.dictType!]: rows })))
      .catch(() => setError('字典加载失败，请重试'))
  }, [positioning, field?.dictType, dicts])
  const update = (patch: Partial<StudentContactFormField>) => {
    if (!editable) return
    setFields(rows => rows.map((x, i) => i === active ? { ...x, ...patch } : x)); setDirty(true)
  }
  const move = (index: number, delta: number) => {
    if (!editable) return
    const rows = [...fields]; [rows[index], rows[index + delta]] = [rows[index + delta], rows[index]]
    setFields(rows.map((x, i) => ({ ...x, sort: (i + 1) * 10 }))); setActive(index + delta); setDirty(true)
  }
  const run = async (command: () => Promise<unknown>, success: string) => {
    if (busy) return
    setBusy(true); setError('')
    try { await command(); message.success(success); await load() }
    catch (e) { setError(e instanceof Error ? e.message : '操作失败，请重试') }
    finally { setBusy(false) }
  }
  const save = async () => {
    if (!editable || !current?.draft) return
    if (fields.some(x => !x.key.trim() || !x.title.trim()) || new Set(fields.map(x => x.key)).size !== fields.length) {
      message.error('字段编码不能重复，编码和标题不能为空'); return
    }
    if (positioning && fields.some(x => enumTypes.has(x.type) && !x.dictType)) {
      message.error('枚举字段必须关联系统字典'); return
    }
    await run(() => api.directorConfig.saveDraft(positioning, current.id, {
      versionId: current.draft!.id, version: current.draft!.version, name: current.name,
      defaultTemplate: current.defaultTemplate, fields
    }), '草稿已保存')
  }
  const add = () => {
    if (!editable) return
    let key = `field_${fields.length + 1}`
    while (fields.some(x => x.key === key)) key += '_new'
    setFields(rows => [...rows, { key, title: positioning ? '新增定位项' : '新增访谈项', type: 'text', enabled: true, required: false,
      systemField: false, sort: (rows.length + 1) * 10, allowRemark: true, interviewNote: '' }])
    setActive(fields.length); setDirty(true)
  }
  if (!canQuery) return <Result status="403" title="无权查看定位访谈配置" />
  if (loading && !current) return <Spin />
  return <div className="page-stack">
    <Typography.Title level={3}>{positioning ? '定位卡模板配置' : '定位访谈大纲配置'}</Typography.Title>
    <Typography.Text type="secondary">已发布版本只读。复制为草稿后修改，保存并发布；已开始的访谈继续使用原版本。</Typography.Text>
    {error && <Alert type="error" showIcon message={error} />}
    <Button disabled={busy || loading} onClick={() => void load()}>重新加载</Button>
    {!current ? (!error && <Empty description="暂无可用模板" />) : <>
      <Tabs activeKey={String(selected)} onChange={v => { setSelected(Number(v)); setHistoryId(undefined) }}
        items={templates.map(x => ({ key: String(x.id), label: x.name, disabled: busy || dirty }))} />
      <Space wrap>
        <Tag>{version?.status === 'draft' ? '草稿' : historyId ? '历史版本（只读）' : '已发布（只读）'}</Tag>
        <Select aria-label="查看版本" value={historyId || 0} disabled={busy || dirty} style={{ minWidth: 180 }}
          onChange={value => setHistoryId(value || undefined)} options={[
            { value: 0, label: '当前版本' }, ...(current.versions || []).map(x => ({ value: x.id, label: `V${x.versionNo}（只读）` }))
          ]} />
        {canUpdate && <Button disabled={busy || loading || !!current.draft} onClick={() => void run(
          () => api.directorConfig.copyDraft(positioning, current.id, current.version), '已复制为草稿')}>复制为草稿</Button>}
        {canUpdate && <Button type="primary" disabled={!editable} loading={busy} onClick={() => void save()}>保存草稿</Button>}
        {canPublish && <Button disabled={busy || loading || !current.draft || !!historyId || dirty}
          onClick={() => void run(() => api.directorConfig.publish(positioning, current.id, {
            versionId: current.draft!.id, version: current.draft!.version
          }), '已发布')}>发布</Button>}
      </Space>
      {dirty && <Alert type="info" message="有未保存的修改，请先保存草稿再发布。" />}
      <Row gutter={[16, 16]} style={{ marginInline: 0 }}>
        <Col xs={24} lg={8}><Card title="字段列表" extra={canUpdate && <Button disabled={!editable} onClick={add}>新增字段</Button>}>
          <Space orientation="vertical" style={{ width: '100%' }}>{fields.map((x, i) => <div key={i}>
            <Button type={active === i ? 'primary' : 'text'} block onClick={() => setActive(i)}
              style={{ height: 'auto', whiteSpace: 'normal', textAlign: 'left' }}>{x.title}</Button>
            <Space><Button size="small" disabled={!editable || !i} onClick={() => move(i, -1)}>上移</Button>
              <Button size="small" disabled={!editable || i === fields.length - 1} onClick={() => move(i, 1)}>下移</Button></Space>
          </div>)}</Space>
        </Card></Col>
        <Col xs={24} lg={16}>{field && <Card title={<Space>字段属性{field.systemField && <Tag>系统字段</Tag>}</Space>}>
          <Form layout="vertical" disabled={!editable}>
            <Form.Item label="字段标题"><Input aria-label="字段标题" value={field.title} onChange={e => update({ title: e.target.value })} /></Form.Item>
            <Form.Item label="字段编码"><Input aria-label="字段编码" disabled={!editable || field.systemField} value={field.key} onChange={e => update({ key: e.target.value })} /></Form.Item>
            {!positioning && <Form.Item label="访谈注意"><Input.TextArea aria-label="访谈注意" autoSize={{ minRows: 3, maxRows: 10 }} value={field.interviewNote} onChange={e => update({ interviewNote: e.target.value })} /></Form.Item>}
            <Form.Item label="填写备注"><Input.TextArea value={field.description} maxLength={500} showCount onChange={e => update({ description: e.target.value })} /></Form.Item>
            {positioning && <Form.Item label="字段类型"><Select value={field.type} disabled={field.systemField} onChange={type => update({ type })} options={[
              { value: 'text', label: '单行文本' }, { value: 'textarea', label: '多行文本' }, { value: 'multi_select', label: '字典多选' },
              { value: 'attachment', label: '附件' }, { value: 'material_picker', label: '素材选择' }, { value: 'system_history', label: '系统历史（只读）' }
            ]} /></Form.Item>}
            {positioning && enumTypes.has(field.type) && <Form.Item label="系统字典"><Select showSearch value={field.dictType} onChange={dictType => update({ dictType })} options={Object.entries(dicts).map(([type, items]) => ({ value: type, label: `${type}（${items.length}项）` }))} /><div>当前启用项 {(dicts[field.dictType || ''] || []).length} 个</div></Form.Item>}
            {positioning && field.type === 'material_picker' && <>
              <Form.Item label="素材类型"><Select value={field.materialTypeCode} onChange={materialTypeCode => update({ materialTypeCode })} options={materialTypes.filter(item => item.status === 0 && ['viral_account', 'viral_content'].includes(item.code)).map(item => ({ value: item.code, label: item.name }))} /></Form.Item>
              <Form.Item label="默认平台"><Select allowClear value={field.defaultPlatform} options={platforms.map(item => ({ value: item.value, label: item.label }))} onChange={defaultPlatform => update({ defaultPlatform })} /></Form.Item>
              <Form.Item label="默认阶段"><Select allowClear value={field.defaultStage} options={stages.map(item => ({ value: item.value, label: item.label }))} onChange={defaultStage => update({ defaultStage })} /></Form.Item>
              <Form.Item label="参考字段关联"><Select allowClear value={field.referenceFor} options={fields.filter(x => x.key !== field.key && x.type !== 'material_picker').map(x => ({ value: x.key, label: x.title }))} onChange={referenceFor => update({ referenceFor })} /></Form.Item>
              <Form.Item label="推荐数量提示"><Input value={field.recommendedCount} onChange={e => update({ recommendedCount: e.target.value })} /></Form.Item>
              <Checkbox checked={field.filterAdjustable !== false} onChange={e => update({ filterAdjustable: e.target.checked })}>允许调整筛选</Checkbox>
            </>}
            <Space wrap><Checkbox checked={field.enabled} onChange={e => update({ enabled: e.target.checked })}>启用</Checkbox>
              <Checkbox checked={field.required} onChange={e => update({ required: e.target.checked })}>必填</Checkbox>
              {!positioning && <><Checkbox checked={field.allowRemark} onChange={e => update({ allowRemark: e.target.checked })}>允许备注</Checkbox>
                <Checkbox checked={field.requireAttachment} onChange={e => update({ requireAttachment: e.target.checked })}>要求附件</Checkbox>
                <Checkbox checked={field.studentVisible} onChange={e => update({ studentVisible: e.target.checked })}>学员可见</Checkbox></>}
            </Space>
          </Form>
          {!positioning && <Card size="small" title="访谈注意预览"><Typography.Text style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{field.interviewNote || '—'}</Typography.Text></Card>}
        </Card>}</Col>
      </Row>
    </>}
  </div>
}

export function DirectorSlaConfigPage(){const{message}=App.useApp();const[form]=Form.useForm<DirectorConfig>();const[loading,setLoading]=useState(true),[error,setError]=useState('');const load=useCallback(async()=>{setLoading(true);setError('');try{form.setFieldsValue(await api.directorConfig.get())}catch(e){setError(e instanceof Error?e.message:'加载失败')}finally{setLoading(false)}},[form]);useEffect(()=>{void load()},[load]);const save=async()=>{const values=await form.validateFields();await api.directorConfig.update(values);message.success('已保存');await load()};return <Card title="编导时效配置" loading={loading}>{error&&<Alert type="error" message={error} action={<Button onClick={()=>void load()}>重试</Button>}/>}<Form form={form} layout="vertical" style={{maxWidth:520}}><Form.Item name="interviewAppointmentHours" label="采访预约默认时限（小时）" rules={[{required:true}]}><InputNumber min={1} max={720}/></Form.Item><Form.Item name="positioningDueHours" label="定位任务默认时限（小时）" rules={[{required:true}]}><InputNumber min={1} max={720}/></Form.Item><Form.Item name="trialDays" label="试运行默认周期（天）" rules={[{required:true}]}><InputNumber min={1} max={365}/></Form.Item><Form.Item name="version" hidden><Input/></Form.Item><Button type="primary" onClick={()=>void save()}>保存</Button></Form></Card>}

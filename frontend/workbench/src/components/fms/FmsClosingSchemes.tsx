import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Tag,
  message
} from 'antd'
import FmsProTable from './FmsProTable'
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { APP_ROUTES, DICT_TYPE } from '../../constants'
import { fmsClosing, fmsConfig } from '../../services/fms'
import type {
  FmsClosingScheme,
  FmsClosingSchemeSave,
  FmsClosingSubjectRule,
  FmsClosingTemplate,
  FmsProfitLossSettings,
  FmsSubjectVO,
  FmsVoucherWordVO
} from '../../services/fms/types'
import {
  FMS_CLOSING_TYPE,
  FMS_DEBIT_CREDIT_DIRECTION,
  FMS_FORMULA_RULE
} from '../../services/fms/constants'
import { formatMoney } from '../../services/fms/format'
import { useDict } from '../../services/useDict'

const PROFIT_LOSS_SELECTION_ID = -1

interface Props {
  accountSetId: number
  month: string
  currentPeriod: boolean
  closed: boolean
  writable: boolean
  voucherCount: number
  profitLossBalance: number
  permissions: string[]
  onChanged: () => void
}

const emptyRule = (direction: number = FMS_DEBIT_CREDIT_DIRECTION.DEBIT): FmsClosingSubjectRule => ({
  digest: '期末结转', direction, amountRatio: 100
})

function validateRules(rules: FmsClosingSubjectRule[], special = false): boolean {
  if (rules.length < 2 || rules.some(rule => !rule.digest || !rule.subjectId)) return false
  const total = (direction: number) => rules
    .filter(rule => rule.direction === direction)
    .reduce((sum, rule) => sum + Number(rule.amountRatio || 0), 0)
  const debit = total(FMS_DEBIT_CREDIT_DIRECTION.DEBIT)
  const credit = total(FMS_DEBIT_CREDIT_DIRECTION.CREDIT)
  return special
    ? debit > 0 && debit <= 100 && Math.abs(debit - credit) < 0.001
    : Math.abs(debit - 100) < 0.001 && Math.abs(credit - 100) < 0.001
}

export default function FmsClosingSchemes(props: Props) {
  const navigate = useNavigate()
  const [schemes, setSchemes] = useState<FmsClosingScheme[]>([])
  const [templates, setTemplates] = useState<FmsClosingTemplate[]>([])
  const [subjects, setSubjects] = useState<FmsSubjectVO[]>([])
  const [voucherWords, setVoucherWords] = useState<FmsVoucherWordVO[]>([])
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [loading, setLoading] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState('')
  const [schemeOpen, setSchemeOpen] = useState(false)
  const [schemeSpecial, setSchemeSpecial] = useState(false)
  const [profitOpen, setProfitOpen] = useState(false)
  const [templateOpen, setTemplateOpen] = useState(false)
  const [templateEditOpen, setTemplateEditOpen] = useState(false)
  const [schemeForm] = Form.useForm<FmsClosingSchemeSave>()
  const [profitForm] = Form.useForm<FmsProfitLossSettings>()
  const [templateForm] = Form.useForm<FmsClosingTemplate>()

  const formulaDict = useDict(DICT_TYPE.FMS_FORMULA_RULE)
  const timeTypeDict = useDict(DICT_TYPE.FMS_CLOSING_TIME_TYPE)
  const voucherTypeDict = useDict(DICT_TYPE.FMS_CLOSING_VOUCHER_TYPE)
  const templateCategoryDict = useDict(DICT_TYPE.FMS_CLOSING_TEMPLATE_CATEGORY)

  const canGenerate = props.writable && props.permissions.includes('fms:closing:profit-loss')
  const canUpdate = props.writable && props.permissions.includes('fms:closing:update')
  const profitScheme = schemes.find(item => item.type === FMS_CLOSING_TYPE.PROFIT_LOSS)
  const otherSchemes = schemes.filter(item => item.type !== FMS_CLOSING_TYPE.PROFIT_LOSS)
  const allIds = [PROFIT_LOSS_SELECTION_ID, ...otherSchemes.map(item => item.id)]

  const subjectOptions = useMemo(() => {
    const parentIds = new Set(subjects.map(subject => subject.parentId))
    return subjects
      .filter(subject => !parentIds.has(subject.id))
      .map(subject => ({ value: subject.id, label: `${subject.code} ${subject.name}` }))
  }, [subjects])
  const voucherWordOptions = voucherWords.map(item => ({ value: item.id, label: item.name }))

  const loadReferenceData = useCallback(async () => {
    const [subjectList, wordList, templateList] = await Promise.all([
      fmsConfig.subject.simpleList(props.accountSetId),
      fmsConfig.voucherWord.list(props.accountSetId),
      fmsClosing.template.list(props.accountSetId)
    ])
    setSubjects(subjectList)
    setVoucherWords(wordList)
    setTemplates(templateList)
  }, [props.accountSetId])

  const loadSchemes = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const list = await fmsClosing.scheme.list({ accountSetId: props.accountSetId, month: props.month })
      setSchemes(list)
      setSelectedIds(previous => previous.filter(id => id === PROFIT_LOSS_SELECTION_ID || list.some(item => item.id === id)))
    } catch (e) {
      setError(e instanceof Error ? e.message : '结账方案加载失败')
    } finally {
      setLoading(false)
    }
  }, [props.accountSetId, props.month])

  useEffect(() => {
    void Promise.all([loadReferenceData(), loadSchemes()]).catch(e => {
      setError(e instanceof Error ? e.message : '结账配置加载失败')
    })
  }, [loadReferenceData, loadSchemes])

  const refresh = useCallback(async () => {
    await loadSchemes()
    props.onChanged()
  }, [loadSchemes, props])

  const openVoucher = (id: number) => navigate(`${APP_ROUTES.FMS_VOUCHER_CREATE}?id=${id}`)

  const generateProfitLoss = async () => {
    if (!profitScheme) {
      message.warning('请先完成结转损益参数设置')
      openProfitSettings()
      return
    }
    setGenerating(true)
    try {
      const id = await fmsClosing.voucher.generateProfitLoss({ accountSetId: props.accountSetId, month: props.month })
      message.success('结转损益凭证已生成')
      await refresh()
      openVoucher(id)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '生成失败')
    } finally {
      setGenerating(false)
    }
  }

  const generateScheme = async (scheme: FmsClosingScheme) => {
    setGenerating(true)
    try {
      const id = await fmsClosing.voucher.generateScheme({ accountSetId: props.accountSetId, month: props.month, id: scheme.id })
      message.success('结转凭证已生成')
      await refresh()
      openVoucher(id)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '生成失败')
    } finally {
      setGenerating(false)
    }
  }

  const generateSelected = async () => {
    const selected = selectedIds.length ? selectedIds : allIds
    if (selected.includes(PROFIT_LOSS_SELECTION_ID) && !profitScheme) {
      message.warning('请先完成结转损益参数设置')
      openProfitSettings()
      return
    }
    const ids = selected.map(id => id === PROFIT_LOSS_SELECTION_ID ? profitScheme!.id : id)
    if (!ids.length) return
    setGenerating(true)
    try {
      const voucherIds = await fmsClosing.voucher.generateList({ accountSetId: props.accountSetId, month: props.month, ids })
      message.success(`已生成 ${voucherIds.length} 张结转凭证`)
      await refresh()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '批量生成失败')
    } finally {
      setGenerating(false)
    }
  }

  const openProfitSettings = () => {
    profitForm.setFieldsValue({
      accountSetId: props.accountSetId,
      voucherWordId: profitScheme?.voucherWordId,
      digest: profitScheme?.digest || '结转本期损益',
      voucherType: profitScheme?.voucherType,
      priorYearAdjustmentSubjectId: profitScheme?.priorYearAdjustmentSubjectId,
      adjustmentClosingSubjectId: profitScheme?.adjustmentClosingSubjectId,
      otherClosingSubjectId: profitScheme?.otherClosingSubjectId,
      reverseBalance: profitScheme?.reverseBalance ?? false,
      closingDay: profitScheme?.closingDay || 31
    })
    setProfitOpen(true)
  }

  const saveProfitSettings = async () => {
    const values = await profitForm.validateFields()
    try {
      await fmsClosing.scheme.updateProfitLossSettings({ ...values, accountSetId: props.accountSetId })
      message.success('结转损益参数已保存')
      setProfitOpen(false)
      await refresh()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    }
  }

  const openScheme = (scheme?: FmsClosingScheme, template?: FmsClosingTemplate) => {
    const source = scheme || template
    const special = Boolean(scheme && scheme.type !== FMS_CLOSING_TYPE.REGULAR)
    setSchemeSpecial(special)
    schemeForm.setFieldsValue({
      id: scheme?.id,
      accountSetId: props.accountSetId,
      name: source?.name || '',
      periodEnd: source?.periodEnd ?? true,
      subjectId: source?.subjectId,
      formulaRule: source?.formulaRule ?? FMS_FORMULA_RULE.BALANCE,
      timeType: source?.timeType ?? 1,
      voucherWordId: (scheme as FmsClosingScheme | undefined)?.voucherWordId,
      subjects: source?.subjects?.map(rule => ({ ...rule })) || [emptyRule(), emptyRule(Number(FMS_DEBIT_CREDIT_DIRECTION.CREDIT))]
    })
    setSchemeOpen(true)
  }

  const saveScheme = async () => {
    const values = await schemeForm.validateFields()
    if (!validateRules(values.subjects || [], schemeSpecial)) {
      message.warning(schemeSpecial ? '借贷比例必须相等且在 0 至 100% 之间' : '借方和贷方比例必须分别等于 100%')
      return
    }
    try {
      if (schemeSpecial && values.id) {
        await fmsClosing.scheme.updateSpecialSettings({
          id: values.id,
          accountSetId: props.accountSetId,
          voucherWordId: values.voucherWordId,
          subjects: values.subjects
        })
      } else if (values.id) {
        await fmsClosing.scheme.update({ ...values, accountSetId: props.accountSetId })
      } else {
        await fmsClosing.scheme.create({ ...values, accountSetId: props.accountSetId })
      }
      message.success('结账方案已保存')
      setSchemeOpen(false)
      await refresh()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    }
  }

  const deleteScheme = (scheme: FmsClosingScheme) => Modal.confirm({
    title: '删除结账方案',
    content: `确认删除“${scheme.name}”吗？`,
    onOk: async () => {
      await fmsClosing.scheme.delete(props.accountSetId, scheme.id)
      message.success('结账方案已删除')
      await refresh()
    }
  })

  const openTemplateEditor = (template?: FmsClosingTemplate) => {
    templateForm.setFieldsValue(template ? {
      ...template,
      subjects: template.subjects.map(rule => ({ ...rule }))
    } : {
      accountSetId: props.accountSetId,
      name: '', category: templateCategoryDict.options[0]?.value,
      periodEnd: true, formulaRule: FMS_FORMULA_RULE.BALANCE, timeType: 1,
      subjects: [emptyRule(), emptyRule(Number(FMS_DEBIT_CREDIT_DIRECTION.CREDIT))], sort: 0
    })
    setTemplateEditOpen(true)
  }

  const saveTemplate = async () => {
    const values = await templateForm.validateFields()
    if (!validateRules(values.subjects || [])) {
      message.warning('借方和贷方比例必须分别等于 100%')
      return
    }
    try {
      const data = { ...values, accountSetId: props.accountSetId }
      if (data.id) await fmsClosing.template.update(data)
      else await fmsClosing.template.create(data)
      message.success('结账模板已保存')
      setTemplateEditOpen(false)
      setTemplates(await fmsClosing.template.list(props.accountSetId))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    }
  }

  const deleteTemplate = (template: FmsClosingTemplate) => template.id && Modal.confirm({
    title: '删除结账模板',
    content: `确认删除“${template.name}”吗？`,
    onOk: async () => {
      await fmsClosing.template.delete(props.accountSetId, template.id!)
      setTemplates(await fmsClosing.template.list(props.accountSetId))
      message.success('结账模板已删除')
    }
  })

  const columns = [
    {
      title: '', width: 46,
      render: (_: unknown, row: FmsClosingScheme | { id: number }) => <Checkbox
        checked={selectedIds.includes(row.id)}
        disabled={!canGenerate}
        onChange={event => setSelectedIds(current => event.target.checked
          ? [...new Set([...current, row.id])]
          : current.filter(id => id !== row.id))}/>
    },
    { title: '方案', dataIndex: 'name', ellipsis: true },
    { title: '待结转金额', dataIndex: 'balance', align: 'right' as const, width: 160, render: (value: number) => formatMoney(value || 0) },
    { title: '已生成凭证', dataIndex: 'voucherIds', width: 180, render: (ids: number[]) => ids?.length
      ? <Space wrap>{ids.map(id => <Button key={id} type="link" size="small" onClick={() => openVoucher(id)}>#{id}</Button>)}</Space>
      : <Tag>未生成</Tag> },
    {
      title: '操作', width: 230,
      render: (_: unknown, row: FmsClosingScheme) => <Space size={4}>
        {canGenerate && <Button type="link" size="small" disabled={props.closed || !props.currentPeriod} onClick={() => row.id === PROFIT_LOSS_SELECTION_ID ? generateProfitLoss() : generateScheme(row)}>生成</Button>}
        {canUpdate && <Button type="link" size="small" icon={<EditOutlined/>} onClick={() => row.id === PROFIT_LOSS_SELECTION_ID ? openProfitSettings() : openScheme(row)}>设置</Button>}
        {canUpdate && row.id !== PROFIT_LOSS_SELECTION_ID && row.type === FMS_CLOSING_TYPE.REGULAR && <Button type="link" size="small" danger icon={<DeleteOutlined/>} onClick={() => deleteScheme(row)}>删除</Button>}
      </Space>
    }
  ]

  const tableRows: FmsClosingScheme[] = [{
    id: PROFIT_LOSS_SELECTION_ID,
    accountSetId: props.accountSetId,
    name: '结转损益',
    periodEnd: true,
    formulaRule: FMS_FORMULA_RULE.PROFIT_LOSS_AMOUNT,
    timeType: 1,
    subjects: [],
    type: FMS_CLOSING_TYPE.PROFIT_LOSS,
    balance: profitScheme?.balance ?? props.profitLossBalance,
    voucherIds: profitScheme?.voucherIds || []
  }, ...otherSchemes]

  const dictError = formulaDict.error || timeTypeDict.error || voucherTypeDict.error || templateCategoryDict.error

  return <div className="fms-table-area" style={{ marginBlockEnd: 'var(--crm-sp-3)' }}>
    <div className="page-heading">
      <div>
        <h4>期末结转方案</h4>
        <span style={{ color: 'var(--crm-text-secondary)' }}>{props.month} 共录入凭证 {props.voucherCount || 0} 张</span>
      </div>
      <Space wrap>
        {canGenerate && <Checkbox
          checked={allIds.length > 0 && allIds.every(id => selectedIds.includes(id))}
          indeterminate={selectedIds.length > 0 && !allIds.every(id => selectedIds.includes(id))}
          onChange={event => setSelectedIds(event.target.checked ? allIds : [])}>全选</Checkbox>}
        {canGenerate && <Button type="primary" loading={generating} disabled={props.closed || !props.currentPeriod} onClick={generateSelected}>批量生成</Button>}
        {canUpdate && <Button icon={<PlusOutlined/>} onClick={() => setTemplateOpen(true)}>新增方案/模板</Button>}
        <Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void loadSchemes()}>刷新</Button>
      </Space>
    </div>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void loadSchemes()}>重试</Button>} style={{ marginBlockEnd: 12 }}/>}
    {dictError && <Alert type="warning" showIcon message="部分结账字典加载失败，请重试后再编辑参数" style={{ marginBlockEnd: 12 }}/>}
    <FmsProTable rowKey="id" columns={columns} dataSource={tableRows} loading={loading} pagination={false} size="small"/>

    <Modal title={schemeSpecial ? '专用结转设置' : '结账方案'} open={schemeOpen} onCancel={() => setSchemeOpen(false)} onOk={saveScheme} width={900} destroyOnClose>
      <Form form={schemeForm} layout="vertical">
        <Space wrap align="start" style={{ width: '100%' }}>
          <Form.Item name="id" hidden><InputNumber/></Form.Item>
          {!schemeSpecial && <Form.Item name="name" label="方案名称" rules={[{ required: true }]}><Input style={{ width: 220 }}/></Form.Item>}
          <Form.Item name="voucherWordId" label="凭证字" rules={[{ required: true }]}><Select options={voucherWordOptions} style={{ width: 180 }}/></Form.Item>
          {!schemeSpecial && <Form.Item name="subjectId" label="来源科目"><Select allowClear showSearch optionFilterProp="label" options={subjectOptions} style={{ width: 240 }}/></Form.Item>}
          {!schemeSpecial && <Form.Item name="formulaRule" label="取数规则" rules={[{ required: true }]}><Select options={formulaDict.options} style={{ width: 180 }}/></Form.Item>}
          {!schemeSpecial && <Form.Item name="timeType" label="取数期间" rules={[{ required: true }]}><Select options={timeTypeDict.options} style={{ width: 140 }}/></Form.Item>}
          {!schemeSpecial && <Form.Item name="periodEnd" label="期末结转" valuePropName="checked"><Checkbox>用于期末结账</Checkbox></Form.Item>}
        </Space>
        <RuleEditor subjectOptions={subjectOptions}/>
      </Form>
    </Modal>

    <Modal title="结转损益参数" open={profitOpen} onCancel={() => setProfitOpen(false)} onOk={saveProfitSettings} width={720} destroyOnClose>
      <Form form={profitForm} layout="vertical">
        <Form.Item name="voucherWordId" label="凭证字" rules={[{ required: true }]}><Select options={voucherWordOptions}/></Form.Item>
        <Form.Item name="digest" label="凭证摘要" rules={[{ required: true }]}><Input/></Form.Item>
        <Form.Item name="voucherType" label="结转方式" rules={[{ required: true }]}><Select options={voucherTypeDict.options}/></Form.Item>
        <Form.Item name="priorYearAdjustmentSubjectId" label="以前年度损益调整科目"><Select allowClear showSearch optionFilterProp="label" options={subjectOptions}/></Form.Item>
        <Form.Item name="adjustmentClosingSubjectId" label="以前年度损益调整结转科目"><Select allowClear showSearch optionFilterProp="label" options={subjectOptions}/></Form.Item>
        <Form.Item name="otherClosingSubjectId" label="其他损益结转科目"><Select allowClear showSearch optionFilterProp="label" options={subjectOptions}/></Form.Item>
        <Space wrap>
          <Form.Item name="reverseBalance" valuePropName="checked"><Checkbox>按余额反向结转</Checkbox></Form.Item>
          <Form.Item name="closingDay" label="结转日期" rules={[{ required: true }]}><InputNumber min={1} max={31}/></Form.Item>
        </Space>
      </Form>
    </Modal>

    <Modal title="选择结转模板" open={templateOpen} onCancel={() => setTemplateOpen(false)} footer={null} width={820}>
      <Space style={{ marginBlockEnd: 12 }}>
        <Button type="primary" onClick={() => { setTemplateOpen(false); openScheme() }}>空白方案</Button>
        <Button icon={<PlusOutlined/>} onClick={() => openTemplateEditor()}>新增模板</Button>
      </Space>
      <FmsProTable rowKey={row => row.id || row.presetCode || row.name} dataSource={templates} pagination={false} size="small" columns={[
        { title: '模板名称', dataIndex: 'name' },
        { title: '分类', dataIndex: 'category', width: 130, render: value => templateCategoryDict.labels[String(value)] || value },
        { title: '分录数', dataIndex: 'subjects', width: 90, render: value => value.length },
        { title: '操作', width: 210, render: (_, row) => <Space>
          <Button type="link" size="small" onClick={() => { setTemplateOpen(false); openScheme(undefined, row) }}>使用</Button>
          <Button type="link" size="small" onClick={() => openTemplateEditor(row)}>编辑</Button>
          {!row.presetCode && <Button type="link" size="small" danger onClick={() => deleteTemplate(row)}>删除</Button>}
        </Space> }
      ]}/>
    </Modal>

    <Modal title="结账模板" open={templateEditOpen} onCancel={() => setTemplateEditOpen(false)} onOk={saveTemplate} width={900} destroyOnClose>
      <Form form={templateForm} layout="vertical">
        <Space wrap align="start">
          <Form.Item name="id" hidden><InputNumber/></Form.Item>
          <Form.Item name="name" label="模板名称" rules={[{ required: true }]}><Input style={{ width: 220 }}/></Form.Item>
          <Form.Item name="category" label="模板分类" rules={[{ required: true }]}><Select options={templateCategoryDict.options} style={{ width: 160 }}/></Form.Item>
          <Form.Item name="sort" label="显示顺序" rules={[{ required: true }]}><InputNumber min={0}/></Form.Item>
          <Form.Item name="subjectId" label="来源科目"><Select allowClear showSearch optionFilterProp="label" options={subjectOptions} style={{ width: 240 }}/></Form.Item>
          <Form.Item name="formulaRule" label="取数规则"><Select allowClear options={formulaDict.options} style={{ width: 180 }}/></Form.Item>
          <Form.Item name="timeType" label="取数期间"><Select allowClear options={timeTypeDict.options} style={{ width: 140 }}/></Form.Item>
          <Form.Item name="periodEnd" label="期末结转" valuePropName="checked"><Checkbox>用于期末结账</Checkbox></Form.Item>
        </Space>
        <RuleEditor subjectOptions={subjectOptions}/>
      </Form>
    </Modal>
  </div>
}

function RuleEditor({ subjectOptions }: { subjectOptions: Array<{ value: number; label: string }> }) {
  return <Form.List name="subjects">
    {(fields, { add, remove }) => <>
      <div className="page-heading">
        <strong>凭证分录规则</strong>
        <Button type="link" icon={<PlusOutlined/>} onClick={() => add(emptyRule())}>添加分录</Button>
      </div>
      {fields.map(field => <Space key={field.key} wrap align="start" style={{ width: '100%', marginBlockEnd: 8 }}>
        <Form.Item {...field} name={[field.name, 'digest']} rules={[{ required: true }]}><Input placeholder="摘要" style={{ width: 220 }}/></Form.Item>
        <Form.Item {...field} name={[field.name, 'direction']} rules={[{ required: true }]}><Select style={{ width: 90 }} options={[
          { value: FMS_DEBIT_CREDIT_DIRECTION.DEBIT, label: '借' },
          { value: FMS_DEBIT_CREDIT_DIRECTION.CREDIT, label: '贷' }
        ]}/></Form.Item>
        <Form.Item {...field} name={[field.name, 'subjectId']} rules={[{ required: true }]}><Select showSearch optionFilterProp="label" placeholder="科目" options={subjectOptions} style={{ width: 260 }}/></Form.Item>
        <Form.Item {...field} name={[field.name, 'amountRatio']} rules={[{ required: true }]}><InputNumber min={0.01} max={100} precision={2} addonAfter="%" style={{ width: 140 }}/></Form.Item>
        <Button danger type="text" icon={<DeleteOutlined/>} onClick={() => remove(field.name)}/>
      </Space>)}
    </>}
  </Form.List>
}

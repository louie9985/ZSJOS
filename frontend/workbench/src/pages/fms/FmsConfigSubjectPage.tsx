import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Checkbox, Dropdown, Form, Input, Modal, Select, Space, Tag, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DownloadOutlined, EllipsisOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { DICT_TYPE } from '../../constants'
import FmsImportModal from '../../components/fms/FmsImportModal'
import { saveBlob } from '../../services/download'
import { fmsConfig } from '../../services/fms'
import { FMS_DEBIT_CREDIT_DIRECTION, FMS_SUBJECT_PARENT_ID_ROOT, FMS_SUBJECT_STATUS, FMS_SUBJECT_TYPE } from '../../services/fms/constants'
import type { FmsAuxiliaryItemOptionVO, FmsAuxiliaryTypeVO, FmsCurrencyVO, FmsSubjectUsage, FmsSubjectVO } from '../../services/fms/types'
import { useDict } from '../../services/useDict'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'

type SubjectFormValues = FmsSubjectVO & Record<string, unknown>
const EMPTY_USAGE: FmsSubjectUsage = { childCount: 0, voucherEntryCount: 0, initialBalanceCount: 0, auxiliaryCombinationCount: 0, quantityDataCount: 0, used: false }

function flattenSubjects(subjects: FmsSubjectVO[]): FmsSubjectVO[] {
  return subjects.flatMap(subject => [subject, ...flattenSubjects(subject.children || [])])
}

function confirmAction(title: string, content: string): Promise<boolean> {
  return new Promise(resolve => Modal.confirm({ title, content, onOk: () => resolve(true), onCancel: () => resolve(false) }))
}

export default function FmsConfigSubjectPage({ permissions }: { permissions: string[] }) {
  const { accountSet, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const subjectTypes = useDict(DICT_TYPE.FMS_SUBJECT_TYPE)
  const subjectCategories = useDict(DICT_TYPE.FMS_SUBJECT_CATEGORY)
  const directions = useDict(DICT_TYPE.FMS_DEBIT_CREDIT_DIRECTION)
  const [list, setList] = useState<FmsSubjectVO[]>([])
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [exportLoading, setExportLoading] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [subjectType, setSubjectType] = useState<number | undefined>()
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [batchLoading, setBatchLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [formLoading, setFormLoading] = useState(false)
  const [editing, setEditing] = useState<FmsSubjectVO>()
  const [parentSubject, setParentSubject] = useState<FmsSubjectVO>()
  const [usage, setUsage] = useState<FmsSubjectUsage>(EMPTY_USAGE)
  const [originalAuxiliaryTypeIds, setOriginalAuxiliaryTypeIds] = useState<number[]>([])
  const [auxiliaryTypes, setAuxiliaryTypes] = useState<FmsAuxiliaryTypeVO[]>([])
  const [currencies, setCurrencies] = useState<FmsCurrencyVO[]>([])
  const [migrationOptions, setMigrationOptions] = useState<Record<number, FmsAuxiliaryItemOptionVO[]>>({})
  const [subjectCodeRule, setSubjectCodeRule] = useState('')
  const [form] = Form.useForm<SubjectFormValues>()
  const version = useRef(0)

  const canCreate = writable && permissions.includes('fms:config:subject:create')
  const canUpdate = writable && permissions.includes('fms:config:subject:update')
  const canDelete = writable && permissions.includes('fms:config:subject:delete')
  const canExport = permissions.includes('fms:config:subject:export')
  const canImport = writable && permissions.includes('fms:config:subject:import')

  const getList = useCallback(async () => {
    if (!accountSetId) { setList([]); return }
    const current = ++version.current
    setLoading(true); setLoadError('')
    try {
      const result = await fmsConfig.subject.list(accountSetId, subjectType)
      if (version.current === current) setList(result)
    } catch (e) {
      if (version.current === current) setLoadError(e instanceof Error ? e.message : '科目加载失败')
    } finally {
      if (version.current === current) setLoading(false)
    }
  }, [accountSetId, subjectType])

  useEffect(() => { if (accountSetId) void getList(); else setList([]) }, [accountSetId, getList])

  const allSubjects = useMemo(() => flattenSubjects(list), [list])
  const watchedType = Form.useWatch('type', form) as number | undefined
  const watchedAuxiliaryTypeIds = (Form.useWatch('auxiliaryTypeIds', form) || []) as number[]
  const watchedQuantity = Boolean(Form.useWatch('quantityAccounting', form))
  const categoryOptions = subjectCategories.items.filter(item => item.value.startsWith(`${watchedType}-`)).map(item => ({ value: Number(item.value.split('-')[1]), label: item.label }))
  const parentUsed = !editing && Boolean(parentSubject) && usage.used
  const parentMigrationBlocked = parentUsed && usage.childCount > 0
  const auxiliaryDisabled = parentUsed || usage.childCount > 0 || (usage.used && originalAuxiliaryTypeIds.length > 0)
  const auxiliaryMigrationRequired = Boolean(editing && usage.voucherEntryCount > 0 && originalAuxiliaryTypeIds.length === 0 && watchedAuxiliaryTypeIds.length > 0)

  const loadFormResources = useCallback(async () => {
    if (!accountSetId) return
    const [parameter, auxiliaryList, currencyList] = await Promise.all([
      fmsConfig.financeParameter.get(accountSetId), fmsConfig.auxiliaryType.list(accountSetId), fmsConfig.currency.list(accountSetId)
    ])
    const nextSubjectCodeRule = parameter?.subjectCodeRule || ''
    setSubjectCodeRule(nextSubjectCodeRule)
    setAuxiliaryTypes(auxiliaryList)
    setCurrencies(currencyList)
    return nextSubjectCodeRule
  }, [accountSetId])

  const openForm = useCallback(async (row?: FmsSubjectVO, parent?: FmsSubjectVO) => {
    if (!accountSetId) return
    setModalOpen(true); setFormLoading(true); setEditing(row); setParentSubject(parent); setUsage(EMPTY_USAGE); setOriginalAuxiliaryTypeIds([]); setMigrationOptions({}); form.resetFields()
    try {
      const loadedSubjectCodeRule = await loadFormResources()
      if (row) {
        const [detail, currentUsage] = await Promise.all([fmsConfig.subject.get(accountSetId, row.id), fmsConfig.subject.usage(accountSetId, row.id)])
        setEditing(detail)
        setParentSubject(allSubjects.find(subject => subject.id === detail.parentId))
        setUsage(currentUsage)
        setOriginalAuxiliaryTypeIds([...(detail.auxiliaryTypeIds || [])])
        form.setFieldsValue({ ...detail, auxiliaryMappings: [] })
      } else {
        const type = parent?.type || subjectType || FMS_SUBJECT_TYPE.ASSET
        const parentUsage = parent ? await fmsConfig.subject.usage(accountSetId, parent.id) : EMPTY_USAGE
        setUsage(parentUsage)
        form.setFieldsValue({
          id: 0, accountSetId, code: parent ? suggestChildCode(parent, loadedSubjectCodeRule || '') : '', name: '',
          parentId: parent?.id || FMS_SUBJECT_PARENT_ID_ROOT, type,
          category: parent?.category, balanceDirection: parent?.balanceDirection || FMS_DEBIT_CREDIT_DIRECTION.DEBIT,
          auxiliaryTypeIds: [...(parent?.auxiliaryTypeIds || [])], currencyIds: [...(parent?.currencyIds || [])],
          quantityAccounting: Boolean(parent?.quantityAccounting), quantityUnit: parent?.quantityUnit, cash: Boolean(parent?.cash),
          migrateParentData: false, auxiliaryMappings: []
        })
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '科目表单加载失败')
      setModalOpen(false)
    } finally { setFormLoading(false) }
  }, [accountSetId, allSubjects, form, loadFormResources, subjectType])

  const resolveParentFromCode = async () => {
    if (editing || !accountSetId) return
    const code = String(form.getFieldValue('code') || '').trim()
    const rules = subjectCodeRule.split('-').map(Number)
    const parentLength = rules.reduce((total, length) => total + length < code.length ? total + length : total, 0)
    const parent = parentLength > 0 ? allSubjects.find(subject => subject.code === code.slice(0, parentLength)) : undefined
    setParentSubject(parent)
    if (!parent) { form.setFieldValue('parentId', FMS_SUBJECT_PARENT_ID_ROOT); setUsage(EMPTY_USAGE); return }
    form.setFieldsValue({
      parentId: parent.id, type: parent.type, category: parent.category, balanceDirection: parent.balanceDirection,
      auxiliaryTypeIds: [...(parent.auxiliaryTypeIds || [])], currencyIds: [...(parent.currencyIds || [])],
      quantityAccounting: parent.quantityAccounting, quantityUnit: parent.quantityUnit, cash: parent.cash
    })
    try { setUsage(await fmsConfig.subject.usage(accountSetId, parent.id)) }
    catch (e) { message.error(e instanceof Error ? e.message : '上级科目使用情况加载失败') }
  }

  useEffect(() => {
    if (!auxiliaryMigrationRequired || !accountSetId) return
    const missing = watchedAuxiliaryTypeIds.filter(typeId => !migrationOptions[typeId])
    if (!missing.length) return
    void Promise.all(missing.map(async typeId => [typeId, await fmsConfig.auxiliaryItem.list(accountSetId, typeId)] as const))
      .then(entries => setMigrationOptions(current => ({ ...current, ...Object.fromEntries(entries) })))
      .catch(e => message.error(e instanceof Error ? e.message : '迁移项目加载失败'))
  }, [accountSetId, auxiliaryMigrationRequired, migrationOptions, watchedAuxiliaryTypeIds])

  const submit = async () => {
    if (!accountSetId) return
    const values = await form.validateFields()
    const duplicate = !editing && allSubjects.find(subject => subject.parentId === values.parentId && subject.name === values.name.trim())
    if (duplicate && !await confirmAction('科目名称重复', `同级已有“${duplicate.name}”（${duplicate.code}），是否仍要继续？`)) return
    if (parentMigrationBlocked) { message.error('上级科目已有业务数据和下级科目，当前状态不能继续新增下级'); return }
    if (parentUsed && !await confirmAction('迁移上级科目历史数据', `将迁移 ${usage.voucherEntryCount} 条凭证分录、${usage.initialBalanceCount} 条初始余额和 ${usage.auxiliaryCombinationCount} 个辅助组合，且无法撤销。是否继续？`)) return
    if (auxiliaryMigrationRequired && !await confirmAction('迁移历史辅助核算数据', `将把 ${usage.voucherEntryCount} 条凭证分录迁入所选辅助项目，且无法撤销。是否继续？`)) return

    const payload: FmsSubjectVO = {
      ...values,
      id: editing?.id || 0,
      accountSetId,
      name: values.name.trim(),
      quantityUnit: values.quantityAccounting ? values.quantityUnit : undefined,
      migrateParentData: parentUsed,
      auxiliaryMappings: auxiliaryMigrationRequired
        ? watchedAuxiliaryTypeIds.map(typeId => ({ typeId, itemId: Number(values[`mapping_${typeId}`]) }))
        : undefined
    }
    setFormLoading(true)
    try {
      if (editing) await fmsConfig.subject.update(payload)
      else await fmsConfig.subject.create(payload)
      message.success(editing ? '科目更新成功' : '科目创建成功')
      setModalOpen(false); await getList()
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
    finally { setFormLoading(false) }
  }

  const handleExport = async () => {
    if (!accountSetId) return
    setExportLoading(true)
    try { saveBlob(await fmsConfig.subject.exportExcel(accountSetId, subjectType), '会计科目.xls') }
    catch (e) { message.error(e instanceof Error ? e.message : '导出失败') }
    finally { setExportLoading(false) }
  }

  const deleteSubjects = (ids: number[]) => Modal.confirm({
    title: ids.length > 1 ? '批量删除科目' : '删除科目', content: `确认删除选中的 ${ids.length} 个科目？`, okType: 'danger',
    onOk: async () => { if (!accountSetId) return; await fmsConfig.subject.delete(accountSetId, ids); message.success('删除成功'); setSelectedIds([]); await getList() }
  })
  const updateStatus = async (status: number) => {
    if (!accountSetId || !selectedIds.length) return
    setBatchLoading(true)
    try { await fmsConfig.subject.updateStatus({ accountSetId, ids: selectedIds, status }); message.success('状态更新成功'); setSelectedIds([]); await getList() }
    catch (e) { message.error(e instanceof Error ? e.message : '状态更新失败') }
    finally { setBatchLoading(false) }
  }

  const typeLabels = subjectTypes.labels
  const directionLabels = directions.labels
  const columns = useMemo<ColumnsType<FmsSubjectVO>>(() => [
    ...(writable && (canUpdate || canDelete) ? [{ title: '', width: 48, render: (_: unknown, row: FmsSubjectVO) => <input type="checkbox" checked={selectedIds.includes(row.id)} onChange={event => setSelectedIds(current => event.target.checked ? [...current, row.id] : current.filter(id => id !== row.id))} /> }] : []),
    { title: '编码', dataIndex: 'code', width: 140 },
    { title: '名称', dataIndex: 'name', width: 220 },
    { title: '类型', dataIndex: 'type', width: 110, render: value => <Tag>{typeLabels[String(value)] || value}</Tag> },
    { title: '方向', dataIndex: 'balanceDirection', width: 80, render: value => directionLabels[String(value)] || value },
    { title: '辅助核算', width: 180, render: (_, row) => row.auxiliaryTypeNames?.join('、') || '-' },
    { title: '外币', width: 100, render: (_, row) => row.currencyIds?.length || '-' },
    { title: '数量', width: 100, render: (_, row) => row.quantityAccounting ? row.quantityUnit : '-' },
    { title: '现金项', width: 80, render: (_, row) => row.cash ? '是' : '否' },
    { title: '状态', width: 80, render: (_, row) => <Tag color={row.status === FMS_SUBJECT_STATUS.ENABLED ? 'success' : 'default'}>{row.status === FMS_SUBJECT_STATUS.ENABLED ? '启用' : '禁用'}</Tag> },
    ...(canCreate || canUpdate || canDelete ? [{ title: '操作', width: 210, fixed: 'right' as const, render: (_: unknown, row: FmsSubjectVO) => <Space size={2}>{canCreate && <Button type="link" size="small" onClick={() => void openForm(undefined, row)}>新增下级</Button>}{canUpdate && <Button type="link" size="small" onClick={() => void openForm(row)}>编辑</Button>}{canDelete && <Button type="link" size="small" danger onClick={() => deleteSubjects([row.id])}>删除</Button>}</Space> }] : [])
  ], [canCreate, canDelete, canUpdate, directionLabels, openForm, selectedIds, typeLabels, writable])

  return <section className="workspace-page fms-page">
    <div className="fms-search-area"><Space wrap>
      <Select placeholder="科目类型" allowClear value={subjectType} onChange={setSubjectType} options={subjectTypes.options} loading={subjectTypes.loading} style={{ width: 160 }} />
      {canCreate && <Button icon={<PlusOutlined />} onClick={() => void openForm()}>新增科目</Button>}
      {canExport && <Button icon={<DownloadOutlined />} loading={exportLoading} onClick={() => void handleExport()}>导出</Button>}
      {canImport && <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>导入</Button>}
      {(canUpdate || canDelete) && <Dropdown menu={{ items: [
        ...(canUpdate ? [{ key: 'enable', label: '批量启用' }, { key: 'disable', label: '批量禁用' }] : []),
        ...(canDelete ? [{ key: 'delete', label: '批量删除', danger: true }] : [])
      ], onClick: ({ key }) => key === 'delete' ? deleteSubjects(selectedIds) : void updateStatus(key === 'enable' ? FMS_SUBJECT_STATUS.ENABLED : FMS_SUBJECT_STATUS.DISABLED) }}><Button disabled={!selectedIds.length} loading={batchLoading} icon={<EllipsisOutlined />}>批量操作</Button></Dropdown>}
    </Space></div>
    {(subjectTypes.error || subjectCategories.error || directions.error) && <Alert type="error" showIcon message="科目字典加载失败" description={subjectTypes.error || subjectCategories.error || directions.error} action={<Button size="small" onClick={() => { void subjectTypes.reload(); void subjectCategories.reload(); void directions.reload() }}>重试</Button>} style={{ marginBottom: 12 }} />}
    {loadError && <Alert type="error" showIcon message="科目加载失败" description={loadError} action={<Button size="small" onClick={() => void getList()}>重试</Button>} style={{ marginBottom: 12 }} />}
<div className="fms-table-area"><FmsProTable rowKey="id" columns={columns} dataSource={list} loading={loading} pagination={false} bordered size="small" defaultExpandAllRows scroll={{ x: 1280, y: 'calc(100vh - 300px)' }} /></div>

    <Modal title={editing ? '编辑科目' : parentSubject ? '新增下级科目' : '新增科目'} open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => void submit()} okButtonProps={{ disabled: parentMigrationBlocked }} confirmLoading={formLoading} width={860} destroyOnHidden>
      {parentUsed && <Alert type="warning" showIcon message={parentMigrationBlocked ? '上级科目已有业务数据和下级科目，当前状态不允许新增下级' : `新增后将迁移上级科目的 ${usage.voucherEntryCount} 条凭证、${usage.initialBalanceCount} 条初始余额和 ${usage.auxiliaryCombinationCount} 个辅助组合`} style={{ marginBottom: 12 }} />}
      {editing && (usage.used || usage.childCount > 0) && <Alert type="warning" showIcon message={usage.used ? '该科目已有业务数据，余额方向不能修改；首次启用辅助核算需指定历史数据迁移项目' : '该科目已有下级，类别、编码和辅助核算不能修改'} style={{ marginBottom: 12 }} />}
      <Form form={form} layout="vertical" disabled={formLoading}>
        <Form.Item name="code" label="科目编码" rules={[{ required: true, message: '请输入科目编码' }]} extra={`科目级次：${subjectCodeRule || '未配置'}`}><Input maxLength={64} disabled={Boolean(editing && usage.childCount > 0)} onBlur={() => void resolveParentFromCode()} /></Form.Item>
        <Form.Item name="name" label="科目名称" rules={[{ required: true, message: '请输入科目名称' }]}><Input maxLength={255} /></Form.Item>
        <Form.Item label="上级科目"><Input value={parentSubject ? `${parentSubject.code} ${parentSubject.name}` : '无上级科目'} disabled /></Form.Item>
        <Form.Item name="type" label="科目类型" rules={[{ required: true }]}><Select options={subjectTypes.options} disabled={Boolean(parentSubject || editing)} /></Form.Item>
        <Form.Item name="category" label="科目类别" rules={[{ required: true, message: '请选择科目类别' }]}><Select options={categoryOptions} disabled={Boolean(parentSubject || usage.childCount > 0)} /></Form.Item>
        <Form.Item name="balanceDirection" label="余额方向" rules={[{ required: true }]}><Select options={directions.options} disabled={usage.used} /></Form.Item>
        <Form.Item name="auxiliaryTypeIds" label="辅助核算"><Select mode="multiple" options={auxiliaryTypes.map(type => ({ value: type.id, label: type.name }))} disabled={auxiliaryDisabled} /></Form.Item>
        {auxiliaryMigrationRequired && <Alert type="warning" showIcon message="请选择历史凭证要迁入的辅助核算项目，该操作不可撤销" style={{ marginBottom: 12 }} />}
        {auxiliaryMigrationRequired && watchedAuxiliaryTypeIds.map(typeId => <Form.Item key={typeId} name={`mapping_${typeId}`} label={auxiliaryTypes.find(type => type.id === typeId)?.name || '迁移项目'} rules={[{ required: true, message: '请选择迁移项目' }]}><Select options={(migrationOptions[typeId] || []).map(item => ({ value: item.id, label: `${item.code} ${item.name}` }))} /></Form.Item>)}
        <Form.Item name="currencyIds" label="外币核算"><Select mode="multiple" options={currencies.filter(currency => !currency.standard).map(currency => ({ value: currency.id, label: `${currency.code} ${currency.name}` }))} disabled={parentUsed} /></Form.Item>
        <Form.Item name="quantityAccounting" valuePropName="checked"><Checkbox disabled={parentUsed || usage.quantityDataCount > 0}>启用数量核算</Checkbox></Form.Item>
        {watchedQuantity && <Form.Item name="quantityUnit" label="数量单位" rules={[{ required: true, message: '请输入数量单位' }]}><Input maxLength={255} disabled={parentUsed} /></Form.Item>}
        <Form.Item name="cash" valuePropName="checked"><Checkbox disabled={Boolean(parentSubject?.cash)}>现金及现金等价物</Checkbox></Form.Item>
      </Form>
    </Modal>
    {accountSetId && <FmsImportModal open={importOpen} onClose={() => setImportOpen(false)} title="导入科目" onGetTemplate={() => fmsConfig.subject.getImportTemplate()} onUpload={file => fmsConfig.subject.import(accountSetId, file)} onSuccess={() => void getList()} />}
  </section>
}

function suggestChildCode(parent: FmsSubjectVO, rule: string): string {
  const segmentLength = rule.split('-').map(Number)[parent.level || 1] || 2
  const used = new Set((parent.children || []).map(child => child.code.slice(parent.code.length)))
  const max = 10 ** segmentLength - 1
  for (let value = 1; value <= max; value++) {
    const suffix = String(value).padStart(segmentLength, '0')
    if (!used.has(suffix)) return `${parent.code}${suffix}`
  }
  return `${parent.code}${String(max).padStart(segmentLength, '0')}`
}

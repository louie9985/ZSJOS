import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import dayjs from 'dayjs'
import { Alert, Button, Form, InputNumber, Modal, Select, Space, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { DeleteOutlined, DownloadOutlined, PlusOutlined, SaveOutlined, UploadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { DICT_TYPE } from '../../constants'
import FmsImportModal from '../../components/fms/FmsImportModal'
import { saveBlob } from '../../services/download'
import { fmsConfig } from '../../services/fms'
import { FMS_DEBIT_CREDIT_DIRECTION, FMS_SUBJECT_TYPE } from '../../services/fms/constants'
import { formatAmount, formatQuantity } from '../../services/fms/format'
import type {
  FmsAuxiliaryItemOptionVO,
  FmsInitialBalance,
  FmsInitialBalanceAmounts,
  FmsInitialBalanceAssist,
  FmsInitialBalanceAuxiliaryItem,
  FmsInitialBalanceUpdate,
  FmsTrialBalance
} from '../../services/fms/types'
import { useDict } from '../../services/useDict'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'

type AmountField = keyof FmsInitialBalanceAmounts
type ViewRow = FmsInitialBalance & {
  rowKey: string
  isAssist?: boolean
  isLeaf: boolean
  level: number
  auxiliaryItemIds?: number[]
  auxiliaries?: FmsInitialBalanceAuxiliaryItem[]
}

const AMOUNT_FIELDS: AmountField[] = [
  'openingAmount', 'openingQuantity', 'yearDebitAmount', 'yearDebitQuantity', 'yearCreditAmount',
  'yearCreditQuantity', 'yearOpeningAmount', 'yearOpeningQuantity', 'profitLossAmount', 'profitLossQuantity'
]
const DIRECT_SUM_FIELDS = new Set<AmountField>(['yearDebitAmount', 'yearDebitQuantity', 'yearCreditAmount', 'yearCreditQuantity'])

function pickAmounts(row: FmsInitialBalanceAmounts): FmsInitialBalanceAmounts {
  return Object.fromEntries(AMOUNT_FIELDS.map(field => [field, Number(row[field] || 0)])) as unknown as FmsInitialBalanceAmounts
}

function zeroAmounts(): FmsInitialBalanceAmounts {
  return pickAmounts({} as FmsInitialBalanceAmounts)
}

function buildAssistRow(subject: ViewRow, assist: FmsInitialBalanceAssist, key: string | number): ViewRow {
  return {
    ...subject,
    ...assist,
    rowKey: `assist-${subject.subjectId}-${assist.assistCombinationId || key}`,
    isAssist: true,
    isLeaf: true,
    level: subject.level + 1,
    auxiliaryItemIds: assist.auxiliaries.map(item => item.itemId),
    auxiliaries: assist.auxiliaries,
    assistBalances: []
  }
}

export function buildInitialBalanceViewRows(list: FmsInitialBalance[]): ViewRow[] {
  const rows: ViewRow[] = []
  const levels = new Map<number, number>()
  const parentIds = new Set(list.map(item => item.parentId).filter((id): id is number => Boolean(id)))
  list.forEach(item => {
    const level = (item.parentId ? levels.get(item.parentId) || 0 : 0) + 1
    levels.set(item.subjectId, level)
    const subject: ViewRow = { ...item, rowKey: `subject-${item.subjectId}`, isLeaf: !parentIds.has(item.subjectId), level }
    rows.push(subject)
    item.assistBalances.forEach((assist, index) => rows.push(buildAssistRow(subject, assist, index)))
  })
  return rows
}

export function serializeInitialBalanceRows(rows: ViewRow[]): FmsInitialBalanceUpdate[] {
  const assistRows = rows.filter(row => row.isAssist)
  return rows.filter(row => !row.isAssist && row.isLeaf).map(row => ({
    subjectId: row.subjectId,
    ...pickAmounts(row),
    assistBalances: assistRows.filter(assist => assist.subjectId === row.subjectId).map(assist => ({
      auxiliaryItemIds: assist.auxiliaryItemIds || [],
      ...pickAmounts(assist)
    }))
  }))
}

function aggregateRows(source: ViewRow[]): ViewRow[] {
  const rows = source.map(row => ({ ...row }))
  const subjects = new Map<number, ViewRow>()
  rows.forEach(row => {
    if (row.isAssist) return
    subjects.set(row.subjectId, row)
    if (!row.isLeaf || row.auxiliaryAccounting) AMOUNT_FIELDS.forEach(field => { row[field] = 0 })
  })
  for (let index = rows.length - 1; index >= 0; index--) {
    const row = rows[index]
    const parent = row.isAssist ? subjects.get(row.subjectId) : subjects.get(row.parentId || 0)
    if (!parent) continue
    AMOUNT_FIELDS.forEach(field => {
      const amount = Number(row[field] || 0)
      parent[field] = Number(parent[field] || 0) + (
        DIRECT_SUM_FIELDS.has(field) || row.balanceDirection === parent.balanceDirection ? amount : -amount
      )
    })
  }
  return rows
}

function cartesian(groups: FmsAuxiliaryItemOptionVO[][]): FmsAuxiliaryItemOptionVO[][] {
  return groups.reduce<FmsAuxiliaryItemOptionVO[][]>((combinations, items) =>
    combinations.flatMap(combination => items.map(item => [...combination, item])), [[]])
}

export default function FmsConfigInitialBalancePage({ permissions }: { permissions: string[] }) {
  const { accountSet, currentMonth, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const subjectTypes = useDict(DICT_TYPE.FMS_SUBJECT_TYPE)
  const [subjectType, setSubjectType] = useState<number>(FMS_SUBJECT_TYPE.ASSET)
  const [loadedSubjectType, setLoadedSubjectType] = useState<number>(FMS_SUBJECT_TYPE.ASSET)
  const [rows, setRows] = useState<ViewRow[]>([])
  const [accountStartTime, setAccountStartTime] = useState<number>()
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)
  const [edited, setEdited] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const [trialOpen, setTrialOpen] = useState(false)
  const [trial, setTrial] = useState<FmsTrialBalance>()
  const [importOpen, setImportOpen] = useState(false)
  const [assistSubject, setAssistSubject] = useState<ViewRow>()
  const [assistOptions, setAssistOptions] = useState<Record<number, FmsAuxiliaryItemOptionVO[]>>({})
  const [assistLoading, setAssistLoading] = useState(false)
  const [assistForm] = Form.useForm<Record<string, number[]>>()
  const version = useRef(0)

  const isJanuary = Boolean(accountStartTime && dayjs(accountStartTime).month() === 0)
  const periodEditable = Boolean(accountStartTime && currentMonth && currentMonth === dayjs(accountStartTime).format('YYYY-MM'))
  const canUpdate = writable && periodEditable && permissions.includes('fms:config:initial-balance:update')
  const canQuery = permissions.includes('fms:config:initial-balance:query')
  const canExport = permissions.includes('fms:config:initial-balance:export')
  const canImport = writable && periodEditable && permissions.includes('fms:config:initial-balance:import')

  const loadPage = useCallback(async (type = subjectType) => {
    if (!accountSetId) { setRows([]); return }
    const currentVersion = ++version.current
    setLoading(true)
    setLoadError('')
    try {
      const [accountSetDetail, balances] = await Promise.all([
        fmsConfig.accountSet.get(accountSetId),
        fmsConfig.initialBalance.list(accountSetId, type)
      ])
      if (version.current !== currentVersion) return
      setAccountStartTime(accountSetDetail.startTime)
      setRows(buildInitialBalanceViewRows(balances))
      setLoadedSubjectType(type)
      setEdited(false)
    } catch (e) {
      if (version.current !== currentVersion) return
      setLoadError(e instanceof Error ? e.message : '初始余额加载失败')
    } finally {
      if (version.current === currentVersion) setLoading(false)
    }
  }, [accountSetId, subjectType])

  useEffect(() => {
    if (accountSetId) void loadPage(subjectType)
    else { setRows([]); setAccountStartTime(undefined) }
  }, [accountSetId, loadPage, subjectType])

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!edited) return
      event.preventDefault()
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [edited])

  const changeSubjectType = (nextType: number) => {
    const apply = () => {
      if (isJanuary && nextType === FMS_SUBJECT_TYPE.PROFIT_LOSS) {
        message.warning('年初启用的账套不需要录入损益初始余额')
        return
      }
      setSubjectType(nextType)
    }
    if (!edited) { apply(); return }
    Modal.confirm({
      title: '放弃未保存修改？',
      content: '当前修改尚未保存，切换科目类别后不会保留。',
      onOk: apply,
      onCancel: () => setSubjectType(loadedSubjectType)
    })
  }

  const updateAmount = (rowKey: string, field: AmountField, value: number) => {
    setRows(current => aggregateRows(current.map(row => {
      if (row.rowKey !== rowKey) return row
      const next = { ...row, [field]: value }
      if (!isJanuary && ['openingAmount', 'yearDebitAmount', 'yearCreditAmount'].includes(field)) {
        next.yearOpeningAmount = next.balanceDirection === FMS_DEBIT_CREDIT_DIRECTION.DEBIT
          ? next.openingAmount - next.yearDebitAmount + next.yearCreditAmount
          : next.openingAmount + next.yearDebitAmount - next.yearCreditAmount
      }
      if (!isJanuary && ['openingQuantity', 'yearDebitQuantity', 'yearCreditQuantity'].includes(field)) {
        next.yearOpeningQuantity = next.balanceDirection === FMS_DEBIT_CREDIT_DIRECTION.DEBIT
          ? next.openingQuantity - next.yearDebitQuantity + next.yearCreditQuantity
          : next.openingQuantity + next.yearDebitQuantity - next.yearCreditQuantity
      }
      return next
    })))
    setEdited(true)
  }

  const openAssist = async (row: ViewRow) => {
    if (!accountSetId) return
    setAssistSubject(row)
    assistForm.resetFields()
    setAssistLoading(true)
    try {
      const entries = await Promise.all(row.auxiliaryConfigs.map(async config => [
        config.auxiliaryTypeId,
        await fmsConfig.auxiliaryItem.list(accountSetId, config.auxiliaryTypeId)
      ] as const))
      setAssistOptions(Object.fromEntries(entries))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '辅助核算项目加载失败')
      setAssistSubject(undefined)
    } finally {
      setAssistLoading(false)
    }
  }

  const addAssist = async () => {
    if (!assistSubject) return
    const values = await assistForm.validateFields()
    const combinations = cartesian(assistSubject.auxiliaryConfigs.map(config =>
      (values[String(config.auxiliaryTypeId)] || []).map(id => assistOptions[config.auxiliaryTypeId]?.find(item => item.id === id)).filter((item): item is FmsAuxiliaryItemOptionVO => Boolean(item))))
    const existing = rows.filter(row => row.isAssist && row.subjectId === assistSubject.subjectId)
    const additions = combinations.filter(items => !existing.some(row =>
      row.auxiliaryItemIds?.length === items.length && items.every(item => row.auxiliaryItemIds?.includes(item.id))))
    if (!additions.length) { message.warning('所选辅助核算明细均已存在'); return }

    const subjectIndex = rows.findIndex(row => row.rowKey === assistSubject.rowKey)
    let insertIndex = subjectIndex + 1
    while (rows[insertIndex]?.isAssist && rows[insertIndex].subjectId === assistSubject.subjectId) insertIndex++
    const addedRows = additions.map((items, index) => buildAssistRow(assistSubject, {
      ...zeroAmounts(),
      auxiliaries: assistSubject.auxiliaryConfigs.map((config, configIndex) => ({
        type: config.type,
        typeId: config.auxiliaryTypeId,
        itemId: items[configIndex].id,
        name: items[configIndex].name
      }))
    }, Date.now() + index))
    setRows(current => aggregateRows([...current.slice(0, insertIndex), ...addedRows, ...current.slice(insertIndex)]))
    setEdited(true)
    setAssistSubject(undefined)
  }

  const removeAssist = (rowKey: string) => {
    setRows(current => aggregateRows(current.filter(row => row.rowKey !== rowKey)))
    setEdited(true)
  }

  const handleSave = async () => {
    if (!accountSetId || !canUpdate) return
    const balances = serializeInitialBalanceRows(rows)
    if (subjectType === FMS_SUBJECT_TYPE.PROFIT_LOSS && balances.some(item => Math.abs(item.yearOpeningAmount) >= 0.005)) {
      message.warning('损益类科目的年初余额必须为 0')
      return
    }
    setSaving(true)
    try {
      await fmsConfig.initialBalance.save(accountSetId, balances)
      message.success('初始余额保存成功')
      await loadPage(subjectType)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const openTrialBalance = async () => {
    if (!accountSetId) return
    try {
      setTrial(await fmsConfig.initialBalance.trialBalance(accountSetId))
      setTrialOpen(true)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '试算失败')
    }
  }

  const handleExport = async () => {
    if (!accountSetId) return
    setExportLoading(true)
    try {
      saveBlob(await fmsConfig.initialBalance.exportExcel(accountSetId), '财务初始余额.xlsx')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }

  const canEditRow = useCallback((row: ViewRow) => canUpdate && (row.isAssist || (row.isLeaf && !row.auxiliaryAccounting)), [canUpdate])
  const amountColumn = useCallback((title: string, field: AmountField, quantity = false): ColumnsType<ViewRow>[number] => ({
    title,
    width: quantity ? 135 : 145,
    align: 'right',
    render: (_, row) => canEditRow(row) && (!quantity || row.quantityAccounting)
      ? <InputNumber value={Number(row[field] || 0)} min={0} precision={quantity ? 4 : 2} controls={false} style={{ width: '100%' }} onChange={value => updateAmount(row.rowKey, field, Number(value || 0))} />
      : quantity ? formatQuantity(Number(row[field] || 0), row.quantityAccounting) : formatAmount(Number(row[field] || 0))
  }), [canEditRow, isJanuary])

  const columns = useMemo<ColumnsType<ViewRow>>(() => {
    const result: ColumnsType<ViewRow> = [
      { title: '科目编码', dataIndex: 'subjectCode', width: 140, fixed: 'left' },
      {
        title: '科目名称', width: 300, fixed: 'left',
        render: (_, row) => <Space size={4} style={{ paddingLeft: (row.level - 1) * 14 }}>
          <span>{row.isAssist ? `${row.subjectName}_${row.auxiliaries?.map(item => item.name).join('_')}` : row.subjectName}</span>
          {canUpdate && !row.isAssist && row.isLeaf && row.auxiliaryAccounting && <Button type="link" size="small" icon={<PlusOutlined />} onClick={() => void openAssist(row)}>添加明细</Button>}
          {canUpdate && row.isAssist && <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label="删除辅助明细" onClick={() => removeAssist(row.rowKey)} />}
        </Space>
      },
      { title: '方向', width: 72, align: 'center', render: (_, row) => row.balanceDirection === FMS_DEBIT_CREDIT_DIRECTION.DEBIT ? '借' : '贷' },
      { title: '期初余额', children: [amountColumn('数量', 'openingQuantity', true), amountColumn('金额', 'openingAmount')] }
    ]
    if (!isJanuary) {
      result.push(
        { title: '本年累计借方', children: [amountColumn('数量', 'yearDebitQuantity', true), amountColumn('金额', 'yearDebitAmount')] },
        { title: '本年累计贷方', children: [amountColumn('数量', 'yearCreditQuantity', true), amountColumn('金额', 'yearCreditAmount')] },
        { title: '年初余额', children: [amountColumn('数量', 'yearOpeningQuantity', true), amountColumn('金额', 'yearOpeningAmount')] }
      )
      if (subjectType === FMS_SUBJECT_TYPE.PROFIT_LOSS) {
        result.push({ title: '实际损益发生额', children: [amountColumn('数量', 'profitLossQuantity', true), amountColumn('金额', 'profitLossAmount')] })
      }
    }
    return result
  }, [amountColumn, canUpdate, isJanuary, subjectType])

  return <section className="workspace-page fms-page">
    <div className="fms-search-area">
      <Space wrap>
        <Select value={subjectType} onChange={changeSubjectType} options={subjectTypes.options} loading={subjectTypes.loading} style={{ width: 180 }} />
        {canUpdate && <Button type="primary" icon={<SaveOutlined />} loading={saving} disabled={!edited} onClick={() => void handleSave()}>保存</Button>}
        {canQuery && <Button onClick={() => void openTrialBalance()}>试算平衡</Button>}
        {canImport && <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>导入</Button>}
        {canExport && <Button icon={<DownloadOutlined />} loading={exportLoading} onClick={() => void handleExport()}>导出</Button>}
      </Space>
    </div>

    {subjectTypes.error && <Alert type="error" showIcon message="科目类别加载失败" description={subjectTypes.error} action={<Button size="small" onClick={() => void subjectTypes.reload()}>重试</Button>} style={{ marginBottom: 12 }} />}
    {isJanuary && <Alert type="info" showIcon message="账套从一月启用，只需录入期初余额" style={{ marginBottom: 12 }} />}
    {accountStartTime && !periodEditable && <Alert type="warning" showIcon message="账套已结账，初始余额不可修改" style={{ marginBottom: 12 }} />}
    {edited && <Alert type="warning" showIcon message="当前修改尚未保存，切换科目类别或离开页面前请先保存" style={{ marginBottom: 12 }} />}
    {loadError && <Alert type="error" showIcon message="初始余额加载失败" description={loadError} action={<Button size="small" onClick={() => void loadPage(subjectType)}>重试</Button>} style={{ marginBottom: 12 }} />}

    <div className="fms-table-area">
      <FmsProTable rowKey="rowKey" columns={columns} dataSource={rows} loading={loading} pagination={false} bordered size="small" scroll={{ x: isJanuary ? 800 : 1700, y: 'calc(100vh - 330px)' }} />
    </div>

    <Modal open={trialOpen} title="试算平衡" onCancel={() => setTrialOpen(false)} footer={null} width={760} destroyOnHidden>
      {trial && <Space direction="vertical" style={{ width: '100%' }}>
        <Alert message={trial.balanced ? '期初余额试算平衡' : '期初余额试算不平衡'} type={trial.balanced ? 'success' : 'error'} showIcon />
        <div>期初借方：{formatAmount(trial.openingDebitAmount)}</div>
        <div>期初贷方：{formatAmount(trial.openingCreditAmount)}</div>
        <div>期初差额：{formatAmount(trial.openingDifferenceAmount)}</div>
        <div>本年借方：{formatAmount(trial.yearDebitAmount)}</div>
        <div>本年贷方：{formatAmount(trial.yearCreditAmount)}</div>
        <div>本年差额：{formatAmount(trial.yearDifferenceAmount)}</div>
      </Space>}
    </Modal>

    <Modal title={`添加辅助明细 - ${assistSubject?.subjectName || ''}`} open={Boolean(assistSubject)} onCancel={() => setAssistSubject(undefined)} onOk={() => void addAssist()} confirmLoading={assistLoading} width={820} destroyOnHidden>
      <Form form={assistForm} layout="vertical">
        {assistSubject?.auxiliaryConfigs.map(config => <Form.Item key={config.auxiliaryTypeId} name={String(config.auxiliaryTypeId)} label={config.name} rules={[{ required: true, message: `请选择${config.name}` }]}>
          <Select mode="multiple" showSearch optionFilterProp="label" loading={assistLoading} options={(assistOptions[config.auxiliaryTypeId] || []).map(item => ({ value: item.id, label: `${item.code} ${item.name}` }))} />
        </Form.Item>)}
      </Form>
    </Modal>

    {accountSetId && <FmsImportModal
      open={importOpen}
      onClose={() => setImportOpen(false)}
      title="导入初始余额"
      onGetTemplate={() => fmsConfig.initialBalance.getImportTemplate(accountSetId)}
      onUpload={file => fmsConfig.initialBalance.import(accountSetId, file)}
      onSuccess={() => void loadPage(subjectType)}
    />}
  </section>
}

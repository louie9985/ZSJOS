import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Checkbox, DatePicker, Dropdown, Form, Input, InputNumber, message, Modal, Popover, Select, Space, Tag } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { ArrowLeftOutlined, ArrowRightOutlined, CopyOutlined, DeleteOutlined, MinusCircleOutlined, PlusOutlined, PrinterOutlined, SaveOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsVoucher, type FmsVoucher, type FmsVoucherEntry } from '../../services/fms/voucher'
import { fmsConfig } from '../../services/fms'
import type { FmsVoucherWordVO, FmsSubjectVO, FmsVoucherTemplateCategoryVO, FmsVoucherTemplateVO } from '../../services/fms/types'
import { formatMoney, formatAmount } from '../../services/fms/format'
import { FMS_VOUCHER_STATUS, FMS_SUBJECT_STATUS, FMS_DEBIT_CREDIT_DIRECTION } from '../../services/fms/constants'
import { useCellFocusGrid } from '../../components/fms/useCellFocusGrid'
import FmsVoucherPrintSettings from '../../components/fms/FmsVoucherPrintSettings'
import { APP_ROUTES } from '../../constants'

interface EntryForm {
  rowKey: number
  digest: string
  subjectId?: number
  quantity?: number
  unitPrice?: number
  debitAmount?: number
  creditAmount?: number
  auxiliaries: Array<{ typeId: number; itemId: number }>
}

let nextRowKey = 1
const createEmptyEntry = (): EntryForm => ({
  rowKey: nextRowKey++, digest: '', auxiliaries: []
})

export function canWriteVoucher(id: number | undefined, writable: boolean, permissions: readonly string[]): boolean {
  return writable && permissions.includes(id ? 'fms:voucher:update' : 'fms:voucher:create')
}

export default function FmsVoucherCreatePage({ permissions }: { permissions: string[] }) {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { accountSet, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const companyName = accountSet?.companyName || ''

  const [voucherWords, setVoucherWords] = useState<FmsVoucherWordVO[]>([])
  const [subjects, setSubjects] = useState<FmsSubjectVO[]>([])
  const [detail, setDetail] = useState<FmsVoucher>()
  const [formData, setFormData] = useState<{ id?: number; voucherWordId?: number; voucherNumber?: number; voucherTime: number; attachmentCount: number }>({
    voucherTime: dayjs().startOf('day').valueOf(), attachmentCount: 0
  })
  const [entries, setEntries] = useState<EntryForm[]>(() => Array.from({ length: 4 }, createEmptyEntry))
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [prevId, setPrevId] = useState<number>()
  const [nextId, setNextId] = useState<number>()
  const [printOpen, setPrintOpen] = useState(false)
  const [templates, setTemplates] = useState<FmsVoucherTemplateVO[]>([])
  const [templateCategories, setTemplateCategories] = useState<FmsVoucherTemplateCategoryVO[]>([])
  const [templateSelectOpen, setTemplateSelectOpen] = useState(false)
  const [templateSaveOpen, setTemplateSaveOpen] = useState(false)
  const [templateLoading, setTemplateLoading] = useState(false)
  const [templateSaving, setTemplateSaving] = useState(false)
  const [templateForm] = Form.useForm<{ categoryId: number; name: string; saveMoney: boolean }>()
  const [subjectBalances, setSubjectBalances] = useState<Record<number, { balance: number; balanceDirection?: string }>>({})
  const [auxiliaryOptions, setAuxiliaryOptions] = useState<Record<number, Array<{ id: number; code: string; name: string }>>>({})
  const version = useRef(0)

  // 分录单元格键盘导航
  const focusGrid = useCellFocusGrid(['digest', 'subjectId', 'debitAmount', 'creditAmount'])

  const subjectById = useMemo(() => {
    const map = new Map<number, FmsSubjectVO>()
    const walk = (nodes: FmsSubjectVO[]) => { for (const n of nodes) { map.set(n.id, n); if (n.children) walk(n.children) } }
    walk(subjects)
    return map
  }, [subjects])

  const queryId = Number(params.get('id')) || undefined
  const queryCopyFrom = Number(params.get('copyFrom')) || undefined
  const canCreate = writable && permissions.includes('fms:voucher:create')
  const canUpdate = writable && permissions.includes('fms:voucher:update')
  const canReview = writable && permissions.includes('fms:voucher:review')
  const canDelete = writable && permissions.includes('fms:voucher:delete')
  const canPrint = permissions.includes('fms:voucher:print')
  const canCreateTemplate = writable && permissions.includes('fms:config:voucher-template:create')

  const loadTemplates = useCallback(async () => {
    if (!accountSetId) return
    setTemplateLoading(true)
    try {
      const [templateList, categoryList] = await Promise.all([
        fmsConfig.voucherTemplate.list(accountSetId),
        fmsConfig.voucherTemplateCategory.list(accountSetId)
      ])
      setTemplates(templateList)
      setTemplateCategories(categoryList)
      return { templateList, categoryList }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '凭证模板加载失败')
    } finally {
      setTemplateLoading(false)
    }
  }, [accountSetId])

  const openTemplateSelect = useCallback(async () => {
    setTemplateSelectOpen(true)
    await loadTemplates()
  }, [loadTemplates])

  const applyTemplate = useCallback(async (template: FmsVoucherTemplateVO) => {
    const unavailable = template.entries.some(entry => {
      const subject = subjectById.get(entry.subjectId)
      return !subject || subject.status !== FMS_SUBJECT_STATUS.ENABLED || Boolean(subject.children?.length)
    })
    if (unavailable) {
      message.error('模板包含当前账套不可用的会计科目，暂不能套用')
      return
    }
    const nextEntries: EntryForm[] = template.entries.map(entry => ({
      rowKey: nextRowKey++, digest: entry.digest, subjectId: entry.subjectId,
      quantity: entry.quantity, unitPrice: entry.unitPrice,
      debitAmount: entry.debitAmount, creditAmount: entry.creditAmount,
      auxiliaries: entry.auxiliaries.map(item => ({ typeId: item.typeId, itemId: item.itemId }))
    }))
    while (nextEntries.length < 4) nextEntries.push(createEmptyEntry())
    setEntries(nextEntries)
    const typeIds = [...new Set(nextEntries.flatMap(entry => entry.auxiliaries.map(item => item.typeId)))]
    const optionEntries = await Promise.all(typeIds.map(async typeId => [typeId, await fmsConfig.auxiliaryItem.list(accountSetId!, typeId)] as const))
    setAuxiliaryOptions(current => ({ ...current, ...Object.fromEntries(optionEntries) }))
    setTemplateSelectOpen(false)
    message.success(`已套用凭证模板“${template.name}”`)
  }, [accountSetId, subjectById])

  const openTemplateSave = useCallback(async () => {
    const validEntries = entries.filter(entry => entry.subjectId)
    if (!validEntries.length) { message.warning('请至少填写一条分录'); return }
    if (validEntries.some(entry => !entry.digest)) { message.warning('请先补全分录摘要'); return }
    const result = await loadTemplates()
    if (!result?.categoryList.length) {
      message.warning('请先在凭证模板管理中新增模板分类')
      return
    }
    templateForm.setFieldsValue({ categoryId: result.categoryList[0].id, name: '', saveMoney: false })
    setTemplateSaveOpen(true)
  }, [entries, loadTemplates, templateForm])

  const saveTemplate = useCallback(async () => {
    if (!accountSetId) return
    const values = await templateForm.validateFields()
    const sourceEntries = entries.filter(entry => entry.subjectId)
    setTemplateSaving(true)
    try {
      await fmsConfig.voucherTemplate.create({
        accountSetId,
        categoryId: values.categoryId,
        name: values.name,
        entries: sourceEntries.map(entry => ({
          digest: entry.digest,
          subjectId: entry.subjectId!,
          quantity: values.saveMoney ? entry.quantity : undefined,
          unitPrice: values.saveMoney ? entry.unitPrice : undefined,
          debitAmount: values.saveMoney ? entry.debitAmount : undefined,
          creditAmount: values.saveMoney ? entry.creditAmount : undefined,
          auxiliaries: entry.auxiliaries.filter(item => item.itemId > 0).map(item => ({ typeId: item.typeId, itemId: item.itemId }))
        }))
      })
      message.success('凭证模板保存成功')
      setTemplateSaveOpen(false)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '模板保存失败')
    } finally {
      setTemplateSaving(false)
    }
  }, [accountSetId, entries, templateForm])

  // 初始化
  useEffect(() => {
    if (!accountSetId) return
    const v = ++version.current
    setLoading(true)
    Promise.all([
      fmsConfig.voucherWord.list(accountSetId),
      fmsConfig.subject.simpleList(accountSetId)
    ]).then(([words, subj]) => {
      if (v !== version.current) return
      setVoucherWords(words)
      setSubjects(subj)
    }).finally(() => { if (v === version.current) setLoading(false) })
  }, [accountSetId])

  const defaultVoucherWordId = voucherWords.find(w => w.defaultStatus)?.id || voucherWords[0]?.id

  const loadDetail = useCallback(async (id: number, copy = false) => {
    if (!accountSetId) return
    setLoading(true)
    try {
      const voucher = await fmsVoucher.get(accountSetId, id)
      setDetail(voucher)
      setFormData({
        id: copy ? undefined : voucher.id,
        voucherWordId: voucher.voucherWordId,
        voucherNumber: copy ? undefined : voucher.voucherNumber,
        voucherTime: voucher.voucherTime, attachmentCount: voucher.attachmentCount
      })
      setEntries(voucher.entries.map(e => ({
        rowKey: nextRowKey++, digest: e.digest, subjectId: e.subjectId,
        quantity: e.quantity, unitPrice: e.unitPrice,
        debitAmount: e.debitAmount, creditAmount: e.creditAmount,
        auxiliaries: e.auxiliaries?.map(a => ({ typeId: a.typeId, itemId: a.itemId })) || []
      })))
      // 相邻凭证
      const list = await fmsVoucher.page({ accountSetId, pageNo: 1, pageSize: 500 })
      const ids = list.list.map(x => x.id)
      const idx = ids.indexOf(id)
      setPrevId(idx > 0 ? ids[idx - 1] : undefined)
      setNextId(idx >= 0 && idx < ids.length - 1 ? ids[idx + 1] : undefined)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载凭证失败')
    } finally {
      setLoading(false)
    }
  }, [accountSetId])

  const refreshVoucherNumber = useCallback(async () => {
    if (!accountSetId || formData.id) return
    if (!formData.voucherWordId || !formData.voucherTime) return
    try {
      const n = await fmsVoucher.nextNumber(accountSetId, formData.voucherWordId, dayjs(formData.voucherTime).format('YYYY-MM'))
      setFormData(prev => ({ ...prev, voucherNumber: n }))
    } catch { /* 序号获取失败不阻塞 */ }
  }, [accountSetId, formData.id, formData.voucherWordId, formData.voucherTime])

  // 加载分录科目余额（当前期间）
  const loadSubjectBalances = useCallback(async () => {
    if (!accountSetId || !formData.voucherTime) return
    try {
      const month = dayjs(formData.voucherTime).format('YYYY-MM')
      const list = await fmsVoucher.subjectBalanceList(accountSetId, month)
      const map: Record<number, { balance: number; balanceDirection?: string }> = {}
      for (const item of list) map[item.subjectId] = { balance: item.balance, balanceDirection: item.balanceDirection }
      setSubjectBalances(map)
    } catch { /* 余额加载失败不阻塞录入 */ }
  }, [accountSetId, formData.voucherTime])

  // 新建凭证（copyFrom 或新增）
  useEffect(() => {
    if (!accountSetId) return
    if (queryCopyFrom) { void loadDetail(queryCopyFrom, true) }
    else if (queryId) { void loadDetail(queryId) }
    else {
      setDetail(undefined)
      setFormData({
        id: undefined, voucherWordId: defaultVoucherWordId,
        voucherNumber: undefined, voucherTime: dayjs().startOf('day').valueOf(), attachmentCount: 0
      })
      setEntries(Array.from({ length: 4 }, createEmptyEntry))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountSetId, queryId, queryCopyFrom, defaultVoucherWordId])

  // 获取下一凭证号
  useEffect(() => {
    if (!formData.id && formData.voucherWordId && formData.voucherTime) {
      void refreshVoucherNumber()
    }
  }, [formData.id, formData.voucherWordId, formData.voucherTime, refreshVoucherNumber])

  // 科目余额随账套/期间变化刷新
  useEffect(() => {
    void loadSubjectBalances()
  }, [loadSubjectBalances, formData.voucherTime, accountSetId])

  const isApproved = detail?.status === FMS_VOUCHER_STATUS.APPROVED
  const isClosingGenerated = detail?.closingGenerated
  const canSave = canWriteVoucher(formData.id, writable, permissions)
  const readOnly = !canSave || (Boolean(formData.id) && (isApproved || isClosingGenerated))

  const totalDebit = entries.reduce((s, e) => s + Number(e.debitAmount || 0), 0)
  const totalCredit = entries.reduce((s, e) => s + Number(e.creditAmount || 0), 0)
  const balanced = Math.abs(totalDebit - totalCredit) < 0.01

  const addEntry = useCallback((index?: number) => {
    setEntries(prev => {
      const entry = createEmptyEntry()
      const copy = [...prev]
      if (index === undefined) copy.push(entry)
      else copy.splice(index + 1, 0, entry)
      return copy
    })
  }, [])

  const deleteEntry = useCallback((index: number) => {
    setEntries(prev => prev.length <= 2 ? prev : prev.filter((_, i) => i !== index))
  }, [])

  const updateEntry = useCallback((index: number, patch: Partial<EntryForm>) => {
    setEntries(prev => prev.map((e, i) => i === index ? { ...e, ...patch } : e))
  }, [])

  // 加载某辅助核算类型的项目选项（带缓存）
  const loadAuxiliaryOptions = useCallback(async (auxiliaryTypeId: number) => {
    if (!accountSetId) return
    setAuxiliaryOptions(prev => {
      if (prev[auxiliaryTypeId]) return prev
      return prev
    })
    try {
      const items = await fmsConfig.auxiliaryItem.list(accountSetId, auxiliaryTypeId)
      setAuxiliaryOptions(prev => prev[auxiliaryTypeId] ? prev : { ...prev, [auxiliaryTypeId]: items })
    } catch { /* 辅助项目加载失败不阻塞 */ }
  }, [accountSetId])

  // 选择科目后，为配置了辅助核算的科目建辅助项占位并加载选项
  const handleSelectSubject = useCallback((index: number, subjectId: number) => {
    const subject = subjectById.get(subjectId)
    const typeIds = subject?.auxiliaryTypeIds || []
    setEntries(prev => prev.map((e, i) => i === index
      ? { ...e, subjectId, auxiliaries: typeIds.map(typeId => ({ typeId, itemId: 0 })) }
      : e))
    typeIds.forEach(id => void loadAuxiliaryOptions(id))
  }, [subjectById, loadAuxiliaryOptions])

  // 更新某分录的某辅助核算项目
  const updateAuxiliaryItem = useCallback((index: number, typeId: number, itemId: number) => {
    setEntries(prev => prev.map((e, i) => i === index
      ? { ...e, auxiliaries: e.auxiliaries.map(a => a.typeId === typeId ? { ...a, itemId } : a) }
      : e))
  }, [])

  const handleAmountChange = useCallback((index: number, direction: 'debit' | 'credit', value?: number) => {
    setEntries(prev => prev.map((e, i) => {
      if (i !== index) return e
      const amt = Number(value || 0)
      return direction === 'debit'
        ? { ...e, debitAmount: amt, creditAmount: undefined }
        : { ...e, creditAmount: amt, debitAmount: undefined }
    }))
  }, [])

  const buildPayload = useCallback(() => {
    if (!accountSetId) return undefined
    const validEntries = entries.filter(e => e.subjectId)
    if (validEntries.length === 0) { message.warning('请至少填写一条分录'); return undefined }
    for (const e of validEntries) {
      if (!e.digest) { message.warning('请输入摘要'); return undefined }
    }
    const payloadEntries: FmsVoucherEntry[] = validEntries.map(e => ({
      digest: e.digest, subjectId: e.subjectId!, quantity: e.quantity, unitPrice: e.unitPrice,
      debitAmount: e.debitAmount, creditAmount: e.creditAmount,
      auxiliaries: e.auxiliaries.filter(a => a.itemId > 0).map(a => ({ typeId: a.typeId, itemId: a.itemId, type: undefined }))
    }))
    return {
      id: formData.id,
      accountSetId,
      voucherWordId: formData.voucherWordId!,
      voucherNumber: formData.voucherNumber || 1,
      voucherTime: formData.voucherTime,
      attachmentCount: formData.attachmentCount,
      entries: payloadEntries
    }
  }, [accountSetId, entries, formData])

  const submit = useCallback(async (saveAndCreate: boolean) => {
    const payload = buildPayload()
    if (!payload) return
    if (payload.id ? !canUpdate : !canCreate) return
    setSaving(true)
    try {
      const id = payload.id ? (await fmsVoucher.update(payload), payload.id) : await fmsVoucher.create(payload)
      message.success('保存成功')
      if (saveAndCreate) {
        setDetail(undefined)
        setFormData({ id: undefined, voucherWordId: defaultVoucherWordId, voucherNumber: undefined, voucherTime: dayjs().startOf('day').valueOf(), attachmentCount: 0 })
        setEntries(Array.from({ length: 4 }, createEmptyEntry))
      } else {
        navigate(`${APP_ROUTES.FMS_VOUCHER_CREATE}?id=${id}`, { replace: true })
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }, [buildPayload, navigate, defaultVoucherWordId, canCreate, canUpdate])

  const handleReview = useCallback(async (status: number) => {
    if (!accountSetId || !formData.id) return
    Modal.confirm({
      title: status === FMS_VOUCHER_STATUS.APPROVED ? '确认审核该凭证吗？' : '确认反审核该凭证吗？',
      onOk: async () => {
        try {
          await fmsVoucher.updateReviewStatus(accountSetId, [formData.id!], status)
          message.success('操作成功')
          await loadDetail(formData.id!)
        } catch (e) { message.error(e instanceof Error ? e.message : '操作失败') }
      }
    })
  }, [accountSetId, formData.id, loadDetail])

  const handleDelete = useCallback(async () => {
    if (!accountSetId || !formData.id) return
    Modal.confirm({
      title: '删除凭证',
      content: '确认删除该凭证吗？删除后会产生凭证断号',
      onOk: async () => {
        try {
          await fmsVoucher.deleteList(accountSetId, [formData.id!])
          message.success('删除成功')
          navigate(APP_ROUTES.FMS_VOUCHER_CREATE, { replace: true })
        } catch (e) { message.error(e instanceof Error ? e.message : '删除失败') }
      }
    })
  }, [accountSetId, formData.id, navigate])

  const copyVoucher = useCallback(() => {
    if (!detail) return
    navigate(`${APP_ROUTES.FMS_VOUCHER_CREATE}?copyFrom=${detail.id}`, { replace: true })
  }, [detail, navigate])

  // 打开打印设置弹窗
  const openPrint = useCallback(() => {
    if (!detail) return
    setPrintOpen(true)
  }, [detail])

  // `=` 键：把当前焦点行的金额填成使借贷平衡的值
  const handleBalance = useCallback(() => {
    const row = focusGrid.currentRow.current
    const col = focusGrid.currentCol.current
    const field = ['digest', 'subjectId', 'debitAmount', 'creditAmount'][col]
    if (field !== 'debitAmount' && field !== 'creditAmount') return
    if (!entries[row]) return
    const otherTotal = entries.reduce((s, e, i) => {
      if (i === row) return s
      return s + Number(field === 'debitAmount' ? (e.debitAmount || 0) : (e.creditAmount || 0))
    }, 0)
    const balanceAmount = otherTotal >= 0 ? otherTotal : 0
    if (!Number.isFinite(balanceAmount) || balanceAmount < 0) return
    setEntries(prev => prev.map((e, i) => i === row
      ? field === 'debitAmount'
        ? { ...e, debitAmount: balanceAmount, creditAmount: undefined }
        : { ...e, creditAmount: balanceAmount, debitAmount: undefined }
      : e))
  }, [focusGrid, entries])

  // 表格键盘导航（方向键/Enter/Tab/`=`/F12）
  const handleTableKeyDown = useCallback((e: React.KeyboardEvent) => {
    const result = focusGrid.handleKeyDown(e, entries.length)
    if (!result.handled) return
    if (result.action === 'save') {
      void submit(false)
    } else if (result.action === 'balance') {
      handleBalance()
    }
  }, [focusGrid, entries.length, submit, handleBalance])

  const subjectOptions = useMemo(() => {
    const opts: { value: number; label: string; disabled: boolean }[] = []
    const walk = (nodes: FmsSubjectVO[], depth = 0) => {
      for (const n of nodes) {
        opts.push({
          value: n.id,
          label: `${'　'.repeat(depth)}${n.code} ${n.name}`,
          disabled: n.status !== FMS_SUBJECT_STATUS.ENABLED || Boolean(n.children?.length)
        })
        if (n.children) walk(n.children, depth + 1)
      }
    }
    walk(subjects)
    return opts
  }, [subjects])

  const columns = useMemo(() => [
    {
      title: '', width: 48, align: 'center' as const,
      render: (_: unknown, _row: EntryForm, index: number) => readOnly
        ? null
        : <Space size={0} direction="vertical">
            <Button type="text" size="small" icon={<PlusOutlined/>} onClick={() => addEntry(index)}/>
            <Button type="text" size="small" danger icon={<MinusCircleOutlined/>} disabled={entries.length <= 2} onClick={() => deleteEntry(index)}/>
          </Space>
    },
    {
      title: '摘要', width: 220,
      render: (_: unknown, row: EntryForm, index: number) => readOnly
        ? <span>{row.digest}</span>
        : <div ref={focusGrid.reg(index, 'digest')}><Input value={row.digest} maxLength={500} onChange={e => updateEntry(index, { digest: e.target.value })} placeholder="请输入摘要"/></div>
    },
    {
      title: '会计科目', width: 260,
      render: (_: unknown, row: EntryForm, index: number) => {
        if (readOnly) {
          const s = row.subjectId ? subjectById.get(row.subjectId) : undefined
          return <span>{s ? `${s.code} ${s.name}` : ''}</span>
        }
        const bal = row.subjectId ? subjectBalances[row.subjectId] : undefined
        const subject = row.subjectId ? subjectById.get(row.subjectId) : undefined
        return <div ref={focusGrid.reg(index, 'subjectId')}>
          <Select
          value={row.subjectId}
          showSearch
          placeholder="请选择科目"
          options={subjectOptions}
          onChange={(v: number) => handleSelectSubject(index, v)}
          style={{ width: '100%' }}
        />
        {(() => {
          const typeIds = subject?.auxiliaryTypeIds || []
          return typeIds.map(typeId => (
            <Select
              key={typeId}
              value={row.auxiliaries.find(a => a.typeId === typeId)?.itemId || undefined}
              placeholder={subject?.auxiliaryTypeNames?.[typeIds.indexOf(typeId)] || '选择辅助项目'}
              options={(auxiliaryOptions[typeId] || []).map(i => ({ value: i.id, label: `${i.code} ${i.name}` }))}
              onChange={(v: number) => updateAuxiliaryItem(index, typeId, v)}
              style={{ width: '100%', marginTop: 4 }}
              size="small"
            />
          ))
        })()}
        {bal && (
          <div style={{ fontSize: 12, color: 'var(--crm-text-secondary)', marginTop: 2 }}>
            余额：{bal.balanceDirection || ''}{formatMoney(bal.balance)}
          </div>
        )}
        </div>
      }
    },
    {
      title: '借方金额', width: 180, align: 'right' as const,
      render: (_: unknown, row: EntryForm, index: number) => readOnly
        ? <span>{row.debitAmount ? formatMoney(row.debitAmount) : ''}</span>
        : <div ref={focusGrid.reg(index, 'debitAmount')}><InputNumber value={row.debitAmount} precision={2} min={0} controls={false} disabled={row.creditAmount !== undefined} onChange={v => handleAmountChange(index, 'debit', v ?? undefined)} style={{ width: '100%' }}/></div>
    },
    {
      title: '贷方金额', width: 180, align: 'right' as const,
      render: (_: unknown, row: EntryForm, index: number) => readOnly
        ? <span>{row.creditAmount ? formatMoney(row.creditAmount) : ''}</span>
        : <div ref={focusGrid.reg(index, 'creditAmount')}><InputNumber value={row.creditAmount} precision={2} min={0} controls={false} disabled={row.debitAmount !== undefined} onChange={v => handleAmountChange(index, 'credit', v ?? undefined)} style={{ width: '100%' }}/></div>
    }
  ], [readOnly, subjectOptions, subjectById, auxiliaryOptions, subjectBalances, entries.length, addEntry, deleteEntry, updateEntry, handleAmountChange, handleSelectSubject, updateAuxiliaryItem, focusGrid])

  return (
    <section className="workspace-page fms-page">
      {/* 操作栏 */}
      <div className="fms-search-area" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Space wrap>
          {formData.id && canCreate && <Button type="primary" icon={<PlusOutlined/>} onClick={() => navigate(APP_ROUTES.FMS_VOUCHER_CREATE, { replace: true })}>新增</Button>}
          {formData.id && canCreate && <Button icon={<CopyOutlined/>} onClick={copyVoucher}>复制</Button>}
          {!formData.id && canSave && <Button type="primary" icon={<SaveOutlined/>} loading={saving} onClick={() => submit(true)}>保存并新增</Button>}
          {canSave && <Button icon={<SaveOutlined/>} loading={saving} onClick={() => submit(false)}>保存</Button>}
          {!formData.id && canCreate && <Dropdown menu={{ items: [
            ...(canCreateTemplate ? [{ key: 'save', label: '保存为模板' }] : []),
            { key: 'apply', label: '使用模板' }
          ], onClick: ({ key }) => key === 'save' ? void openTemplateSave() : void openTemplateSelect() }}><Button>模板</Button></Dropdown>}
          {formData.id && canReview && !isApproved && !isClosingGenerated && <Button onClick={() => handleReview(FMS_VOUCHER_STATUS.APPROVED)}>审核</Button>}
          {formData.id && canReview && isApproved && !isClosingGenerated && <Button onClick={() => handleReview(FMS_VOUCHER_STATUS.PENDING_REVIEW)}>反审核</Button>}
          {formData.id && canDelete && !isApproved && !isClosingGenerated && <Button danger icon={<DeleteOutlined/>} onClick={handleDelete}>删除</Button>}
          {formData.id && canPrint && <Button icon={<PrinterOutlined/>} onClick={openPrint}>打印</Button>}
        </Space>
        <Space>
          <Button shape="circle" size="small" icon={<ArrowLeftOutlined/>} disabled={!prevId} onClick={() => prevId && navigate(`${APP_ROUTES.FMS_VOUCHER_CREATE}?id=${prevId}`, { replace: true })}/>
          <Button shape="circle" size="small" icon={<ArrowRightOutlined/>} disabled={!nextId} onClick={() => nextId && navigate(`${APP_ROUTES.FMS_VOUCHER_CREATE}?id=${nextId}`, { replace: true })}/>
        </Space>
      </div>

      {/* 凭证表单 */}
      <div className="fms-table-area" style={{ borderTop: '3px solid var(--crm-cta-bg)' }}>
        <div style={{ textAlign: 'center', fontSize: 22, fontWeight: 600, fontFamily: 'STKaiti, KaiTi, serif', letterSpacing: 5, marginBlockEnd: 12 }}>
          记账凭证
        </div>
        <div style={{ fontSize: 13, color: 'var(--crm-text-secondary)', textAlign: 'right', marginBottom: 8 }}>
          {dayjs(formData.voucherTime).format('YYYY年MM月')}
        </div>
        <Space wrap style={{ marginBlockEnd: 16 }}>
          <span style={{ fontWeight: 500 }}>凭证字</span>
          <Select value={formData.voucherWordId} disabled={readOnly} onChange={(v) => setFormData(prev => ({ ...prev, voucherWordId: v }))} options={voucherWords.map(w => ({ value: w.id, label: w.name }))} style={{ width: 100 }}/>
          <span>号</span>
          <InputNumber value={formData.voucherNumber} min={1} controls={false} disabled={readOnly || !!formData.id} onChange={v => setFormData(prev => ({ ...prev, voucherNumber: v ?? undefined }))} style={{ width: 100 }}/>
          <DatePicker value={dayjs(formData.voucherTime)} disabled={readOnly} onChange={d => d && setFormData(prev => ({ ...prev, voucherTime: d.startOf('day').valueOf() }))}/>
          <span style={{ fontWeight: 500 }}>附单据</span>
          <InputNumber value={formData.attachmentCount} min={0} controls={false} disabled={readOnly} onChange={v => setFormData(prev => ({ ...prev, attachmentCount: v ?? 0 }))} style={{ width: 80 }}/>
          <span>张</span>
        </Space>

        <div onKeyDown={handleTableKeyDown}>
        <FmsProTable<EntryForm>
          rowKey="rowKey"
          columns={columns}
          dataSource={entries}
          pagination={false}
          bordered
          size="small"
          loading={loading}
          footer={() => (
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <span>合计：</span>
              <span style={{ width: 180, textAlign: 'right', color: 'var(--crm-cta-bg)', fontWeight: 600 }}>{formatAmount(totalDebit)}</span>
              <span style={{ width: 180, textAlign: 'right', color: 'var(--crm-cta-bg)', fontWeight: 600 }}>{formatAmount(totalCredit)}</span>
            </Space>
          )}
        />
        </div>

        {!readOnly && !balanced && totalDebit + totalCredit > 0 && (
          <Alert message="借贷不平衡，请检查金额" type="warning" showIcon style={{ marginTop: 12 }}/>
        )}
        {isApproved && <Alert message="该凭证已审核，不可修改" type="info" showIcon style={{ marginTop: 12 }}/>}
        {isClosingGenerated && <Alert message="该凭证为结账生成，不可修改" type="info" showIcon style={{ marginTop: 12 }}/>}
      </div>

      {/* 打印设置弹窗 */}
      {detail && (
        <FmsVoucherPrintSettings
          open={printOpen}
          vouchers={[detail]}
          companyName={companyName}
          onClose={() => setPrintOpen(false)}
        />
      )}
      <Modal title="使用凭证模板" open={templateSelectOpen} onCancel={() => setTemplateSelectOpen(false)} footer={null} width={760} destroyOnHidden>
        <FmsProTable<FmsVoucherTemplateVO>
          rowKey="id"
          loading={templateLoading}
          dataSource={templates}
          pagination={false}
          size="small"
          columns={[
            { title: '模板名称', dataIndex: 'name' },
            { title: '分类', dataIndex: 'categoryName', width: 160, render: (value, row) => value || templateCategories.find(category => category.id === row.categoryId)?.name || '-' },
            { title: '分录数', width: 90, render: (_, row) => row.entries.length },
            { title: '操作', width: 90, render: (_, row) => <Button type="link" onClick={() => void applyTemplate(row)}>使用</Button> }
          ]}
        />
      </Modal>
      <Modal title="保存为凭证模板" open={templateSaveOpen} onCancel={() => setTemplateSaveOpen(false)} onOk={() => void saveTemplate()} confirmLoading={templateSaving} width={720} destroyOnHidden>
        <Form form={templateForm} layout="vertical">
          <Form.Item name="categoryId" label="模板分类" rules={[{ required: true, message: '请选择模板分类' }]}><Select options={templateCategories.map(category => ({ value: category.id, label: category.name }))} /></Form.Item>
          <Form.Item name="name" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}><Input maxLength={255} /></Form.Item>
          <Form.Item name="saveMoney" valuePropName="checked"><Checkbox>保留数量、单价和借贷金额</Checkbox></Form.Item>
        </Form>
      </Modal>
    </section>
  )
}

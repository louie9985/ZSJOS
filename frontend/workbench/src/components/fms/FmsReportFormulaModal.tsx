import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Modal, Select, Space, Typography, message } from 'antd'
import FmsProTable from './FmsProTable'
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsConfig } from '../../services/fms'
import { fmsReport } from '../../services/fms/report'
import type { FmsReportFormulaItemUpdate, FmsSubjectVO } from '../../services/fms/types'
import { FMS_BALANCE_FORMULA_RULE_OPTIONS, FMS_INCOME_FORMULA_RULE_OPTIONS, FMS_SUBJECT_STATUS } from '../../services/fms/constants'

export type FmsReportFormulaType = 'balance' | 'income' | 'cash-flow'

export interface FmsReportFormulaTarget {
  id?: number
  name: string
  formula?: string
}

interface FmsReportFormulaModalProps {
  open: boolean
  onClose: () => void
  /** 当前编辑的报表项目 */
  item?: FmsReportFormulaTarget
  /** 报表类型 */
  type: FmsReportFormulaType
}

interface FormulaRow extends FmsReportFormulaItemUpdate {
  rowKey: number
}

let nextFormulaRowKey = 1

/**
 * 报表公式配置弹窗。
 * 展示当前报表项目的公式项列表（科目 + 运算符 + 取数规则），可增删行，
 * 保存时按报表类型调用对应 update / saveFormula 接口。
 */
export default function FmsReportFormulaModal({ open, onClose, item, type }: FmsReportFormulaModalProps) {
  const { accountSet } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const [rows, setRows] = useState<FormulaRow[]>([])
  const [subjects, setSubjects] = useState<FmsSubjectVO[]>([])
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(false)
  const version = useRef(0)

  const typeLabel = type === 'balance' ? '资产负债表' : type === 'income' ? '利润表' : '现金流量表'

  // 打开时加载科目树
  useEffect(() => {
    if (!open || !accountSetId) return
    const v = ++version.current
    setLoading(true)
    fmsConfig.subject.simpleList(accountSetId).then(subj => {
      if (v !== version.current) return
      setSubjects(subj)
    }).finally(() => { if (v === version.current) setLoading(false) })
    // 重置行，从空开始编辑（每行默认 '-' 运算符 + 余额取数规则）
    setRows([])
  }, [open, accountSetId])

  // 构建科目 option（可搜索树）
  const subjectOptions = useCallback(() => {
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

  const ruleOptions = type === 'balance'
    ? FMS_BALANCE_FORMULA_RULE_OPTIONS
    : FMS_INCOME_FORMULA_RULE_OPTIONS

  const addRow = useCallback(() => {
    setRows(prev => [...prev, { rowKey: nextFormulaRowKey++, subjectId: 0, operator: '+', rules: ruleOptions[0].value }])
  }, [ruleOptions])

  const removeRow = useCallback((rowKey: number) => {
    setRows(prev => prev.filter(r => r.rowKey !== rowKey))
  }, [])

  const updateRow = useCallback((rowKey: number, patch: Partial<FormulaRow>) => {
    setRows(prev => prev.map(r => r.rowKey === rowKey ? { ...r, ...patch } : r))
  }, [])

  const handleSave = useCallback(async () => {
    if (!item || !item.id || !accountSetId) return
    const formulas = rows
      .filter(r => Number(r.subjectId) > 0)
      .map(r => ({ subjectId: Number(r.subjectId), operator: r.operator, rules: Number(r.rules) }))
    if (formulas.length === 0) {
      message.warning('请至少添加一条公式')
      return
    }
    setSaving(true)
    try {
      const payload = { accountSetId, id: item.id, formulas }
      if (type === 'balance') await fmsReport.balanceSheet.update(payload)
      else if (type === 'income') await fmsReport.incomeStatement.update(payload)
      else await fmsReport.cashFlowStatement.saveFormula(payload)
      message.success('公式保存成功')
      onClose()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }, [item, accountSetId, rows, type, onClose])

  const columns: ColumnsType<FormulaRow> = [
    {
      title: '科目', key: 'subjectId', width: 360,
      render: (_: unknown, row: FormulaRow) => (
        <Select
          value={row.subjectId || undefined}
          showSearch
          placeholder="请选择科目"
          options={subjectOptions()}
          onChange={(v: number) => updateRow(row.rowKey, { subjectId: v })}
          style={{ width: '100%' }}
        />
      )
    },
    {
      title: '运算符', key: 'operator', width: 90, align: 'center',
      render: (_: unknown, row: FormulaRow) => (
        <Select
          value={row.operator}
          onChange={(v: '+' | '-') => updateRow(row.rowKey, { operator: v })}
          options={[{ value: '+', label: '+' }, { value: '-', label: '-' }]}
          style={{ width: 72 }}
        />
      )
    },
    {
      title: '取数规则', key: 'rules', width: 220,
      render: (_: unknown, row: FormulaRow) => (
        <Select
          value={row.rules}
          onChange={(v: number) => updateRow(row.rowKey, { rules: v })}
          options={ruleOptions.map(o => ({ value: o.value, label: o.label }))}
          style={{ width: '100%' }}
        />
      )
    },
    {
      title: '', key: 'actions', width: 48, align: 'center',
      render: (_: unknown, row: FormulaRow) => (
        <Button type="text" size="small" danger icon={<MinusCircleOutlined/>} onClick={() => removeRow(row.rowKey)}/>
      )
    }
  ]

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={`${typeLabel} · 公式配置`}
      confirmLoading={saving}
      onOk={handleSave}
      okButtonProps={{ disabled: !item?.id }}
      okText="保存"
      cancelText="关闭"
      destroyOnClose
      width={760}
    >
      <Typography.Paragraph style={{ marginBlockEnd: 12 }}>
        <Typography.Text strong>项目：</Typography.Text>{item?.name || '-'}
      </Typography.Paragraph>
      <Space style={{ marginBlockEnd: 12 }}>
        <Button size="small" icon={<PlusOutlined/>} onClick={addRow}>添加公式项</Button>
      </Space>
      <FmsProTable<FormulaRow>
        rowKey="rowKey"
        columns={columns}
        dataSource={rows}
        pagination={false}
        size="small"
        bordered
        loading={loading}
        locale={{ emptyText: '暂无公式项，请添加' }}
      />
    </Modal>
  )
}

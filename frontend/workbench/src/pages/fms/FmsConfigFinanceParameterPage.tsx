import { useCallback, useMemo, useRef, useState } from 'react'
import { Alert, Button, Checkbox, Divider, Empty, Form, InputNumber, message, Select, Spin } from 'antd'
import dayjs from 'dayjs'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsConfig } from '../../services/fms'
import {
  FMS_ACCOUNTING_STANDARD_OPTIONS,
  FMS_DEFAULT_SUBJECT_CODE_RULE,
  FMS_DEFAULT_SUBJECT_LEVEL,
  FMS_LEDGER_BALANCE_MODE,
  FMS_SUBJECT_CODE_LENGTH_MAX,
  FMS_SUBJECT_CODE_LENGTH_MIN,
  FMS_SUBJECT_LEVEL_MAX
} from '../../services/fms/constants'

interface FinanceParameterFormData {
  standard: number
  level: number
  subjectCodeRules: number[]
  ledgerBalanceMode: number
  voucherReviewRequired: boolean
}

function parseSubjectCodeRules(rule: string) { return rule.split('-').map(Number) }

function createEmptyFormData(): FinanceParameterFormData {
  return {
    standard: FMS_ACCOUNTING_STANDARD_OPTIONS[0].value,
    level: FMS_DEFAULT_SUBJECT_LEVEL,
    subjectCodeRules: parseSubjectCodeRules(FMS_DEFAULT_SUBJECT_CODE_RULE),
    ledgerBalanceMode: FMS_LEDGER_BALANCE_MODE.SAME_AS_SUBJECT,
    voucherReviewRequired: true
  }
}

export default function FmsConfigFinanceParameterPage({ permissions }: { permissions: string[] }) {
  const { accountSet, writable } = useFmsAccountSet()
  const accountSetId = accountSet?.id

  const [loading, setLoading] = useState(false)
  const [submitLoading, setSubmitLoading] = useState(false)
  const [accountSetData, setAccountSetData] = useState<{ companyName?: string; startTime?: number; currencyId?: number; standard?: number; initialized?: boolean }>()
  const [currencyLabel, setCurrencyLabel] = useState('-')
  const [financeParameter, setFinanceParameter] = useState<{ level: number; subjectCodeRule: string; ledgerBalanceMode: number; voucherReviewRequired: boolean }>()
  const [formData, setFormData] = useState<FinanceParameterFormData>(createEmptyFormData())
  const [originalLevel, setOriginalLevel] = useState(FMS_DEFAULT_SUBJECT_LEVEL)
  const [originalRules, setOriginalRules] = useState(parseSubjectCodeRules(FMS_DEFAULT_SUBJECT_CODE_RULE))
  const version = useRef(0)

  const getParameterData = useCallback(async () => {
    const currentId = accountSetId
    if (!currentId) {
      setAccountSetData(undefined); setCurrencyLabel('-'); setFinanceParameter(undefined)
      setFormData(createEmptyFormData()); return
    }
    const v = ++version.current
    setLoading(true)
    try {
      const [accountSetRes, paramRes, currencyList] = await Promise.all([
        fmsConfig.accountSet.get(currentId),
        fmsConfig.financeParameter.get(currentId),
        fmsConfig.currency.list(currentId)
      ])
      if (v !== version.current) return
      setAccountSetData(accountSetRes)
      const baseCurrency = (currencyList as Array<{ id?: number; code?: string; name?: string }> | undefined)?.find(c => c.id === (accountSetRes as { currencyId?: number }).currencyId)
      setCurrencyLabel(baseCurrency ? `${baseCurrency.code} ${baseCurrency.name}` : '-')
      setFinanceParameter(paramRes || undefined)
      if (!paramRes) { setFormData(createEmptyFormData()); return }
      setOriginalLevel(paramRes.level)
      const rules = parseSubjectCodeRules(paramRes.subjectCodeRule)
      setOriginalRules(rules)
      setFormData({
        standard: accountSetRes.standard ?? FMS_ACCOUNTING_STANDARD_OPTIONS[0].value,
        level: paramRes.level,
        subjectCodeRules: [...rules],
        ledgerBalanceMode: paramRes.ledgerBalanceMode,
        voucherReviewRequired: paramRes.voucherReviewRequired
      })
    } catch (e) {
      if (v !== version.current) return
      message.error(e instanceof Error ? e.message : '加载财务参数失败')
    } finally {
      if (v === version.current) setLoading(false)
    }
  }, [accountSetId])

  const lastAccountSetId = useRef<number | undefined>(undefined)
  if (accountSetId !== lastAccountSetId.current) {
    lastAccountSetId.current = accountSetId
    setTimeout(() => getParameterData(), 0)
  }

  const levelOptions = useMemo(() =>
    Array.from({ length: FMS_SUBJECT_LEVEL_MAX - originalLevel + 1 }, (_, i) => originalLevel + i),
  [originalLevel])

  const handleLevelChange = (level: number) => {
    setFormData(prev => {
      const rules = [...prev.subjectCodeRules]
      while (rules.length < level) rules.push(FMS_SUBJECT_CODE_LENGTH_MIN)
      return { ...prev, level, subjectCodeRules: rules.slice(0, level) }
    })
  }

  const getRuleMinimum = (index: number) => originalRules[index] || FMS_SUBJECT_CODE_LENGTH_MIN

  const submit = useCallback(async () => {
    if (!accountSetId) return
    setSubmitLoading(true)
    try {
      await fmsConfig.financeParameter.update({
        accountSetId,
        standard: formData.standard,
        level: formData.level,
        subjectCodeRule: formData.subjectCodeRules.join('-'),
        ledgerBalanceMode: formData.ledgerBalanceMode,
        voucherReviewRequired: formData.voucherReviewRequired
      })
      message.success('财务参数保存成功')
      await getParameterData()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      setSubmitLoading(false)
    }
  }, [accountSetId, formData, getParameterData])

  const canUpdate = permissions.includes('fms:config:finance-parameter:update') && writable

  if (!accountSetId) return <section className="workspace-page fms-page"><Empty description="请选择账套"/></section>

  const showAlert = accountSetData && !financeParameter
  return (
    <section className="workspace-page fms-page">
      <div className="page-heading">
        <h4>财务参数</h4>
      </div>
      <Spin spinning={loading}>
        <div className="fms-table-area" style={{ maxWidth: 960 }}>
          <Form layout="vertical" disabled={!writable} style={{ maxWidth: 480 }}>
            <Divider>基础参数</Divider>
            <Form.Item label="公司名称">
              <span>{accountSetData?.companyName}</span>
            </Form.Item>
            <Form.Item label="本位币">
              <span>{currencyLabel}</span>
            </Form.Item>
            <Form.Item label="启用期间">
              <span>{accountSetData?.startTime ? dayjs(`${accountSetData.startTime}`).format('YYYY-MM') : '-'}</span>
            </Form.Item>
            <Form.Item label="会计制度">
              <Select
                value={formData.standard}
                onChange={(standard: number) => setFormData(prev => ({ ...prev, standard }))}
                options={FMS_ACCOUNTING_STANDARD_OPTIONS.map(o => ({ label: o.label, value: o.value }))}
                style={{ width: 320 }}
              />
            </Form.Item>
            {financeParameter && (
              <>
                <Divider>科目参数</Divider>
                <Form.Item label="科目级次" required>
                  <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>
                    <Select
                      value={formData.level}
                      onChange={handleLevelChange}
                      options={levelOptions.map(l => ({ label: `${l} 级`, value: l }))}
                      style={{ width: 160 }}
                    />
                    <span style={{ color: 'var(--crm-text-warning)' }}>科目级次和编码长度调大后不能再调小，请谨慎操作</span>
                  </div>
                </Form.Item>
                <Form.Item label="编码长度" required>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {formData.subjectCodeRules.map((v, index) => (
                      <span key={index} style={{ display: 'inline-flex', alignItems: 'center', gap: 8, whiteSpace: 'nowrap' }}>
                        <InputNumber
                          min={getRuleMinimum(index)}
                          max={FMS_SUBJECT_CODE_LENGTH_MAX}
                          value={v}
                          onChange={val => setFormData(prev => {
                            const rules = [...prev.subjectCodeRules]
                            rules[index] = Number(val ?? FMS_SUBJECT_CODE_LENGTH_MIN)
                            return { ...prev, subjectCodeRules: rules }
                          })}
                          style={{ width: 72 }}
                          controls={false}
                        />
                        {index < formData.subjectCodeRules.length - 1 && <span>-</span>}
                      </span>
                    ))}
                  </div>
                </Form.Item>
                <Divider>账簿</Divider>
                <Form.Item label="账簿余额方向">
                  <Checkbox
                    checked={formData.ledgerBalanceMode === FMS_LEDGER_BALANCE_MODE.SAME_AS_SUBJECT}
                    onChange={e => setFormData(prev => ({
                      ...prev,
                      ledgerBalanceMode: e.target.checked ? FMS_LEDGER_BALANCE_MODE.SAME_AS_SUBJECT : FMS_LEDGER_BALANCE_MODE.OPPOSITE_TO_SUBJECT
                    }))}
                  >与科目方向相同</Checkbox>
                </Form.Item>
                <Form.Item label="结账条件">
                  <Checkbox
                    checked={formData.voucherReviewRequired}
                    onChange={e => setFormData(prev => ({ ...prev, voucherReviewRequired: e.target.checked }))}
                  >凭证审核后才允许结账</Checkbox>
                </Form.Item>
                {canUpdate && (
                  <Button type="primary" loading={submitLoading} onClick={submit}>保存</Button>
                )}
              </>
            )}
            {showAlert && (
              <Alert
                message={accountSetData.initialized ? '当前账套缺少财务参数，请检查初始化数据' : '当前账套尚未初始化，请先完成账套初始化'}
                type="info" showIcon style={{ marginTop: 16 }}
              />
            )}
          </Form>
        </div>
      </Spin>
    </section>
  )
}

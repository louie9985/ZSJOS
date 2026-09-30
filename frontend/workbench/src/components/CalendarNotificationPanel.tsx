import { LinkedText } from './ResourceLink'
import { Alert, Button, Checkbox, Descriptions, Modal, Radio, TreeSelect, Space, Typography } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import { api, type CalendarNotifyInput, type CalendarNotifyPreview, type CalendarNotifyResult, type CalendarNotifyUser, type SimpleDept } from '../services/api'
import { createIdempotencyKey } from '../services/idempotency'
import { buildCalendarRecipientTree, loadCalendarRecipients } from '../services/calendarRecipientTree'

type Props = { calendarType: 'EXAM' | 'COURSE'; calendarId: number; permissions: string[]; onClose: () => void }

/** Mount once per arrangement so selections and request identity never leak into another calendar. */
export default function CalendarNotificationPanel({ calendarType, calendarId, permissions, onClose }: Props) {
  const prefix = calendarType === 'EXAM' ? 'zsjos:exam-calendar' : 'zsjos:course-calendar'
  const allowed = (permission: string) => permissions.includes(permission) || permissions.includes('*:*:*')
  const canNotify = allowed(`${prefix}:notify`)
  const canAll = canNotify && allowed(`${prefix}:notify-all`)
  const client = calendarType === 'EXAM' ? api.examCalendar : api.courseCalendar
  const [scope, setScope] = useState<'SPECIFIED' | 'ALL'>('SPECIFIED')
  const [selected, setSelected] = useState<CalendarNotifyUser[]>([])
  const [resend, setResend] = useState(false)
  const [departments, setDepartments] = useState<SimpleDept[]>([])
  const [retry, setRetry] = useState(0)
  const [users, setUsers] = useState<CalendarNotifyUser[]>([])
  const [userLoading, setUserLoading] = useState(true)
  const [userError, setUserError] = useState('')
  const [preview, setPreview] = useState<CalendarNotifyPreview>()
  const [confirmed, setConfirmed] = useState(false)
  const [busy, setBusy] = useState<'preview' | 'send'>()
  const [error, setError] = useState('')
  const [result, setResult] = useState<CalendarNotifyResult>()
  const guard = useRef(false)
  const generation = useRef(0)
  const requestKey = useRef(createIdempotencyKey())
  const lastHash = useRef<string | undefined>(undefined)

  useEffect(() => {
    let current = true
    if (!canNotify || scope !== 'SPECIFIED') return
    setUserLoading(true)
    setUserError('')
    // Enable department checks only after the complete eligible roster is available.
    void Promise.all([api.simpleDepartments(), loadCalendarRecipients(client.notifyUsers, () => !current)]).then(([depts, employees]) => {
      if (!current) return
      setDepartments(depts)
      setUsers(employees)
    }).catch(cause => {
      if (current) { setUsers([]); setDepartments([]); setUserError(cause instanceof Error ? cause.message : '员工加载失败') }
    }).finally(() => { if (current) setUserLoading(false) })
    return () => { current = false }
  }, [client, canNotify, scope, retry])

  useEffect(() => () => { ++generation.current }, [])
  useEffect(() => {
    if (!canNotify || (scope === 'ALL' && !canAll)) {
      ++generation.current
      setPreview(undefined)
      setConfirmed(false)
    }
  }, [canNotify, canAll, scope])

  const invalidate = () => {
    ++generation.current
    setPreview(undefined)
    setConfirmed(false)
    setError('')
    requestKey.current = createIdempotencyKey()
    lastHash.current = undefined
  }
  const input = (): CalendarNotifyInput => ({ calendarType, calendarId, scope, resend,
    userIds: scope === 'SPECIFIED' ? selected.map(user => user.id) : undefined })
  const authorized = canNotify && (scope !== 'ALL' || canAll)
  const treeData = useMemo(() => buildCalendarRecipientTree(departments, users), [departments, users])
  const usersByKey = useMemo(() => new Map(users.map(user => ['user:' + user.id, user])), [users])
  const staleSelection = selected.some(user => !usersByKey.has('user:' + user.id))
  const recipientsReady = scope === 'ALL' || (!userLoading && !userError && !staleSelection && selected.length > 0)
  const makePreview = async () => {
    if (guard.current || !authorized || !recipientsReady) return
    guard.current = true
    const version = ++generation.current
    setBusy('preview'); setError(''); setConfirmed(false); setPreview(undefined)
    try {
      const data = await client.previewNotify(input())
      if (version !== generation.current) return
      // Refreshing an expired credential keeps the retry key unless its business content changed.
      if (lastHash.current && lastHash.current !== data.contentHash) requestKey.current = createIdempotencyKey()
      lastHash.current = data.contentHash
      setPreview(data)
    } catch (cause) {
      if (version === generation.current) setError(cause instanceof Error ? cause.message : '通知预览失败')
    } finally { guard.current = false; setBusy(undefined) }
  }
  const send = async () => {
    if (guard.current || !authorized || !recipientsReady || !preview || (scope === 'ALL' && !confirmed)) return
    guard.current = true
    const version = generation.current
    setBusy('send'); setError('')
    try {
      const data = await client.notify({ ...input(), calendarVersion: preview.calendarVersion,
        previewToken: preview.previewToken, idempotencyKey: requestKey.current })
      if (version === generation.current) setResult(data)
    } catch (cause) {
      if (version !== generation.current) return
      setError(cause instanceof Error ? cause.message : '通知提交失败，请重试')
      const code = cause && typeof cause === 'object' && 'code' in cause ? Number(cause.code) : undefined
      if ([1900092008, 1900092009, 1900092010].includes(code ?? 0)) {
        setPreview(undefined); setConfirmed(false)
      }
    } finally { guard.current = false; setBusy(undefined) }
  }
  return <Modal open title="发送日历通知" width="min(640px, calc(100vw - 32px))"
    onCancel={() => { if (!guard.current) onClose() }} closable={!busy} maskClosable={!busy}
    footer={result ? <Button onClick={onClose}>关闭</Button> : <Space wrap>
      <Button disabled={Boolean(busy)} onClick={onClose}>取消</Button>
      <Button loading={busy === 'preview'} disabled={!authorized || Boolean(busy) || !recipientsReady} onClick={() => void makePreview()}>预览通知</Button>
      <Button type="primary" loading={busy === 'send'} disabled={!authorized || Boolean(busy) || !recipientsReady || !preview || (scope === 'ALL' && !confirmed)} onClick={() => void send()}>提交发送</Button>
    </Space>}>
    <Space orientation="vertical" style={{ width: '100%' }}>
      {!authorized && <Alert type="warning" showIcon title="没有当前范围的通知权限，请联系管理员授权" />}
      {error && <Alert type="error" showIcon title={error} />}
      {result ? <Alert type="success" showIcon title={result.acceptedCount ? '已提交发送' : '本次无需重复发送'}
        description={`批次 ${result.batchId}：受理 ${result.acceptedCount} 人，跳过 ${result.skippedCount} 人。渠道送达结果以通知记录为准。`} /> : <>
        <Radio.Group value={scope} disabled={Boolean(busy) || !canNotify} onChange={event => { invalidate(); setScope(event.target.value) }}>
          <Radio value="SPECIFIED">指定员工</Radio>{canAll && <Radio value="ALL">全员通知</Radio>}
        </Radio.Group>
        {scope === 'SPECIFIED' && <>
          <TreeSelect<{ value: string; label: string }[]> aria-label="接收员工" placeholder="搜索部门或员工，可多选" style={{ width: '100%' }}
            treeCheckable multiple allowClear showSearch labelInValue treeDefaultExpandAll
            showCheckedStrategy={TreeSelect.SHOW_CHILD} maxTagCount="responsive"
            value={selected.map(user => ({ value: 'user:' + user.id, label: user.nickname }))}
            treeData={treeData} treeNodeFilterProp="searchText"
            loading={userLoading} disabled={Boolean(busy) || !canNotify || userLoading || Boolean(userError)}
            onChange={values => {
              invalidate()
              setSelected(values.flatMap(value => { const user = usersByKey.get(value.value); return user ? [user] : [] }))
            }}
            notFoundContent={userLoading ? '加载中…' : userError || '没有匹配的部门或员工'}
            styles={{ popup: { root: { maxWidth: 'calc(100vw - 48px)' } } }} />
          {userError && <Alert type="error" title={userError} action={<Button onClick={() => { invalidate(); setRetry(value => value + 1) }}>重试</Button>} />}
          {!userError && <Typography.Text type="secondary">{userLoading ? '正在加载部门和员工…' : '已选 ' + selected.length + ' 人，共 ' + users.length + ' 名可选员工；勾选部门可选择其下全部员工。'}</Typography.Text>}
          {!userLoading && !userError && staleSelection && <Alert type="warning" title="部分已选员工不再可选，请移除后重新预览" />}
        </>}
        <Checkbox checked={resend} disabled={Boolean(busy) || !authorized} onChange={event => { invalidate(); setResend(event.target.checked) }}>再次提醒已通知人员</Checkbox>
        {!preview && <Typography.Text type="secondary">选择接收人后预览；修改选择需要重新预览。</Typography.Text>}
        {preview && <>
          <Descriptions column={1} size="small" items={[
            { key: 'title', label: '通知内容', children: preview.title },
            { key: 'time', label: '时间', children: preview.time || '—' },
            { key: 'remark', label: '备注', children: <LinkedText text={preview.remark || '—'} mode="remark" /> },
            { key: 'count', label: '接收人数', children: `${preview.recipientCount} 人，已受理 ${preview.notifiedCount} 人，新增 ${preview.newRecipientCount} 人` },
          ]} />
          <Typography.Text type="secondary">{resend ? '本次会再次提醒所选的已通知人员。' : '默认跳过当前版本已经受理通知的人员。'}</Typography.Text>
          {scope === 'ALL' && <Checkbox checked={confirmed} disabled={Boolean(busy)} onChange={event => setConfirmed(event.target.checked)}>确认向当前租户 {preview.recipientCount} 名启用员工提交{resend ? '提醒' : '通知（已受理人员将跳过）'}</Checkbox>}
        </>}
      </>}
    </Space>
  </Modal>
}

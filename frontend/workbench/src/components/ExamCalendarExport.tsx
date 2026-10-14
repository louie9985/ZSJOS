import { useRef, useState } from 'react'
import { Alert, Button, DatePicker, Form, Modal, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import type { Dayjs } from 'dayjs'
import { api, type ExamSchedule } from '../services/api'
import { APP_ROUTES } from '../constants'

export default function ExamCalendarExport({ anchor, recordStatus, permissions }: {
  anchor: Dayjs; recordStatus?: ExamSchedule['recordStatus']; permissions: string[]
}) {
  const [open, setOpen] = useState(false)
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [created, setCreated] = useState(false)
  const lock = useRef(false)
  const allowed = (permission: string) => permissions.includes('*:*:*') || permissions.includes(permission)
  if (!['zsjos:exam-calendar:query', 'zsjos:exam-calendar:export', 'zsjos:export:query'].every(allowed)) return null
  const submit = async () => {
    if (!range || lock.current || created) return
    lock.current = true; setBusy(true); setError('')
    try {
      await api.createExportTask('exam_calendar', {
        rangeStart: range[0].format('YYYY-MM-DD'), rangeEnd: range[1].format('YYYY-MM-DD'), recordStatus
      })
      setCreated(true)
    } catch (e) { setError(e instanceof Error ? e.message : '导出任务创建失败，请重试') }
    finally { lock.current = false; setBusy(false) }
  }
  return <>
    <Button icon={<DownloadOutlined />} onClick={() => {
      setRange([anchor.startOf('month'), anchor.endOf('month')]); setCreated(false); setError(''); setOpen(true)
    }}>下载 Excel</Button>
    <Modal title="下载考期明细" open={open} onCancel={() => { if (!lock.current) setOpen(false) }}
      closable={!busy} maskClosable={!busy} keyboard={!busy} confirmLoading={busy}
      okText={created ? '完成' : '生成 Excel'} cancelText="取消"
      okButtonProps={{ disabled: !range }} cancelButtonProps={{ disabled: busy }}
      onOk={() => created ? setOpen(false) : void submit()}>
      {created ? <Alert type="success" showIcon title="导出任务已创建"
        description={<span>文件生成后，请到<Typography.Link href={APP_ROUTES.EXPORT_TASKS}>导出任务</Typography.Link>下载；生成失败时可查看原因。</span>} /> : <>
        <Form layout="vertical"><Form.Item label="时间区间" required>
          <DatePicker.RangePicker value={range} disabled={busy} style={{ width: '100%' }}
            onChange={value => setRange(value?.[0] && value[1] ? [value[0], value[1]] : null)} />
        </Form.Item></Form>
        <Typography.Paragraph type="secondary">包含首尾日期，导出当前发布状态筛选下可见的单日及多日考期。跨区间的多日考期保留完整起止日期，每条考期一行。</Typography.Paragraph>
        {error && <Alert type="error" showIcon title={error} />}
      </>}
    </Modal>
  </>
}

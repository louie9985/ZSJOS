import { Alert, Button, Calendar, Empty, Modal, Space, Spin, Typography } from 'antd'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { api, type DeliveryClassExamOption } from '../services/api'

export function examOptionDates(row: DeliveryClassExamOption) {
  const start = row.scheduleType === 'EXACT' ? row.exactDate : row.startDate
  const end = row.scheduleType === 'EXACT' ? row.exactDate : row.endDate
  return start && end ? { start, end } : undefined
}

export default function DeliveryClassExamCalendar({ open, initialDate, onClose, onSelect }: {
  open: boolean; initialDate?: string; onClose: () => void
  onSelect: (date: string) => void
}) {
  const [date, setDate] = useState(dayjs())
  const [rows, setRows] = useState<DeliveryClassExamOption[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (!open) return
    let active = true
    setLoading(true); setError(''); setRows([])
    api.deliveryClasses.exams().then(value => { if (active) setRows(value) })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : '考期加载失败') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [open, attempt])
  useEffect(() => { if (open) setDate(initialDate ? dayjs(initialDate) : dayjs()) }, [open, initialDate])
  const matches = (key: string) => rows.filter(row => {
    const range = examOptionDates(row)
    return range && range.start <= key && key <= range.end
  })
  const daily = matches(date.format('YYYY-MM-DD'))
  return <Modal open={open} title="选择已有考期" footer={null} onCancel={onClose} width={720} destroyOnHidden>
    <Typography.Paragraph type="secondary">选择日期查看当天考期，点击考期填入；填入后仍可修改日期。</Typography.Paragraph>
    {error ? <Alert type="error" showIcon title={error} action={<Button onClick={() => setAttempt(value => value + 1)}>重试</Button>} /> :
      <Spin spinning={loading}>
        <Calendar fullscreen={false} value={date} onSelect={value => setDate(value)}
          cellRender={(value, info) => info.type === 'date' && matches(value.format('YYYY-MM-DD')).length
            ? <span aria-label={value.format('YYYY-MM-DD') + '有考期'}>●</span> : null} />
        {!loading && !rows.length ? <Empty description="暂无已发布且未结束的考期，可返回手动选择日期" /> :
          <Space orientation="vertical" style={{ width: '100%' }}>
            <Typography.Text strong>{date.format('YYYY年M月D日')} 考期</Typography.Text>
            {!loading && !daily.length && <Empty description="当天暂无考期，请选择有标记的日期" />}
            {daily.map(row => <Button key={row.id} block style={{ height: 'auto', whiteSpace: 'normal' }} onClick={() => {
              const range = examOptionDates(row)
              if (range) onSelect(date.format('YYYY-MM-DD'))
            }}>{row.displayName}</Button>)}
          </Space>}
      </Spin>}
  </Modal>
}

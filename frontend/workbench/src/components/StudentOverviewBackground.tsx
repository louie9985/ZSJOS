import { useEffect, useState } from 'react'
import { Alert, Button, Drawer, Empty, Skeleton, Typography } from 'antd'
import { studentInfoApi, studentInfoError, type StudentInfoDetail } from '../services/studentInfo'
import StudentInfoPanel from './StudentInfoPanel'

const overviewKeys = new Set(['employer', 'job', 'education_level', 'school', 'study_purpose'])

export default function StudentOverviewBackground({ leadId, allowed }: { leadId?: number; allowed: boolean }) {
  const [data, setData] = useState<StudentInfoDetail>(), [error, setError] = useState('')
  const [loading, setLoading] = useState(true), [retry, setRetry] = useState(0), [open, setOpen] = useState(false)
  useEffect(() => {
    let active = true
    setData(undefined); setError(''); setOpen(false); setLoading(allowed && !!leadId)
    if (allowed && leadId) void studentInfoApi.detail(leadId).then(value => { if (active) setData(value) })
      .catch(cause => { if (active) setError(studentInfoError(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [leadId, allowed, retry])
  return <section className="student-overview-background">
    <div className="student-overview-subheading"><Typography.Text strong>个人背景</Typography.Text>
      {allowed && leadId && data?.status === 'SUBMITTED' && <Button type="link" size="small" onClick={() => setOpen(true)}>完整资料</Button>}
    </div>
    {!allowed ? <Typography.Text type="secondary">暂无学员资料查看权限</Typography.Text>
      : !leadId ? <Typography.Text type="secondary">当前服务未关联学员信息表</Typography.Text>
      : loading ? <Skeleton active paragraph={{ rows: 3 }} title={false} />
      : error ? <Alert type="error" message={error} action={<Button size="small" onClick={() => setRetry(value => value + 1)}>重试</Button>} />
      : data?.status !== 'SUBMITTED' ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未提交学员信息" />
      : <><dl className="student-overview-fields">{data.fields.filter(field => field.enabled && overviewKeys.has(field.key)).sort((a, b) => a.sort - b.sort).map(field => <div key={field.key} className={field.type === 'textarea' ? 'is-long' : undefined}>
        <dt>{field.label}</dt><dd>{field.type === 'textarea' ? <Typography.Paragraph ellipsis={{ rows: 4, expandable: 'collapsible', symbol: expanded => expanded ? '收起' : '展开' }}>{data.values[field.key] || '未填写'}</Typography.Paragraph> : data.values[field.key] || '未填写'}</dd>
      </div>)}</dl><Typography.Text type="secondary">来源：已提交学员信息表</Typography.Text></>}
    <Drawer title="完整学员资料" width="min(720px, 100vw)" open={open && allowed} onClose={() => setOpen(false)} destroyOnHidden>
      {open && allowed && leadId && <StudentInfoPanel key={leadId} leadId={leadId} />}
    </Drawer>
  </section>
}

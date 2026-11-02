import { useEffect, useState } from 'react'
import { Button, Modal, Space, Tag, Typography } from 'antd'
import { Link } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import type { RegistrationCase } from '../services/api'
import { hasPermission } from '../services/managementAccess'
import { studentInfoStatus } from '../services/studentInfo'
import StudentInfoPanel from './StudentInfoPanel'

export default function RegistrationStudentInfo({ item, permissions }: { item: RegistrationCase; permissions: string[]; disabled: boolean; onChanged: () => void; onBusy?: (busy: boolean) => void }) {
  const [detailCaseId,setDetailCaseId]=useState<number>()
  const canRead=hasPermission(permissions,'zsjos:student-info-form:read')
  useEffect(()=>setDetailCaseId(undefined),[item.id,canRead])
  return <section aria-label="报名资料收集状态">
    <Typography.Text strong>报名资料收集</Typography.Text>
    <Typography.Paragraph type="secondary">资料由订单收集任务统一处理，此处同步展示当前状态。</Typography.Paragraph>
    <Space wrap>
      <Tag>{studentInfoStatus[item.informationSubmissionStatus||'NONE']||'未知状态'}</Tag>
      {item.informationSent&&<Tag>已确认发送</Tag>}
      {hasPermission(permissions,'zsjos:student-info-task:query')&&<Link to={`${APP_ROUTES.STUDENT_INFO_TASKS}?registrationCaseId=${item.id}`}>前往报名资料收集</Link>}
      {canRead&&<Button onClick={()=>setDetailCaseId(item.id)}>查看学员资料</Button>}
    </Space>
    {canRead && detailCaseId === item.id && <Modal open title="学员报名资料" width={1080} className="student-info-dialog"
      style={{ top: 16, maxWidth: 'calc(100vw - 32px)', paddingBottom: 0 }}
      styles={{ container: { maxHeight: 'calc(100dvh - 32px)', display: 'flex', flexDirection: 'column' }, body: { minHeight: 0, overflowY: 'auto' }, footer: { flexShrink: 0 } }}
      footer={<Button onClick={() => setDetailCaseId(undefined)}>关闭</Button>}
      onCancel={() => setDetailCaseId(undefined)} destroyOnHidden><StudentInfoPanel caseId={item.id}/></Modal>}
  </section>
}

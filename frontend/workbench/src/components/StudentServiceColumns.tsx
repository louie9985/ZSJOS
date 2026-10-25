import type { ReactNode } from 'react'
import type { ProColumns } from '@ant-design/pro-components'
import { Space, Tag, Typography } from 'antd'
import type { MyStudent, StudentServiceSummary } from '../services/api'
import type { BusinessSort } from '../services/businessListSort'
import DateTimeText from './DateTimeText'
import ProductSpecs from './ProductSpecs'
import DetailFieldGrid from './DetailFieldGrid'
import TaskIndicator from './LeadTaskIndicator'

export type ServiceStudent = MyStudent & { serviceRelationId: number; summary?: StudentServiceSummary; pendingTasks?: import('../services/api').StudentServiceSubject['pendingTasks'] }
type Column = ProColumns<ServiceStudent> & { display?: (row: ServiceStudent) => ReactNode }
const svc = (r: ServiceStudent) => r.services[0]
const history = (s?: string) => s || '历史未记录'
export const assignedName = (id?: number, name?: string) => name || (id ? '人员姓名未提供' : '未分配')
export const acceptanceLabel = (value?: string) => value === 'accepted' ? '已接收' : value === 'pending' ? '待接收' : '历史未记录'
const statusLabel = (s?: string) => ({ active: '服务中', paused: '已暂停', completed: '已结业', closed: '已关闭', terminated: '已终止' }[s || ''] || '未知状态')
const taskLabel = (s?: string) => ({ student_first_contact: '首联', student_study_plan: '制定学习计划', student_contact: '普通联系', student_delivery_stage: '交付阶段' }[s || ''] || '未知类型')
const orderType = (s?: string) => ({ first_purchase: '首购', repurchase: '复购' }[s || ''] || '历史未记录')
const orderStatus = (s?: string) => ({ pending_approval: '审批中', revision_required: '待修改', effective: '已生效', superseded: '已被接续', terminated: '已终止' }[s || ''] || '未知状态')
const infoStatus = (s?: string) => ({ NONE: '未创建', DRAFT: '待提交', SUBMITTED: '已提交', EXPIRED: '已失效' }[s || ''] || '历史未记录')
const accessText = (access?: string) => access === 'denied' ? '无权查看' : access === 'absent' ? '不适用' : '摘要未提供'
const time = (value?: Parameters<typeof DateTimeText>[0]['value'], emptyText = '历史未记录') => <DateTimeText value={value} emptyText={emptyText} />
const text = (value?: string) => <Typography.Text ellipsis={{ tooltip: value }}>{history(value)}</Typography.Text>
const column = (key: string, title: string, render: (row: ServiceStudent) => ReactNode, width = 160, sorter = false): Column => ({ key, title, width, sorter, display: render, render: (_, row) => render(row) })
const order = (key: keyof NonNullable<StudentServiceSummary['orderHandoff']>, format: (value: string | undefined) => ReactNode = history) => (r: ServiceStudent) => {
  const h = r.summary?.orderHandoff
  return h?.access === 'readable' ? format(h[key] as string | undefined) : accessText(h?.access)
}

export function studentServiceColumnDefinitions(): Column[] {
  return [
    { ...column('name', '学员姓名', r => <Space size={6}><TaskIndicator lead={r} compact /><Typography.Text strong>{r.name || '未填写姓名'}</Typography.Text></Space>, 180, true), fixed: 'left' },
    column('personNo', '学员编号', r => history(r.personNo), 160, true),
    column('mobile', '手机号', r => r.mobile ? <Typography.Text copyable onClick={e => e.stopPropagation()}>{r.mobile}</Typography.Text> : '未填写', 160, true),
    column('wechatId', '微信号', r => r.wechatId ? <Typography.Text copyable onClick={e => e.stopPropagation()}>{r.wechatId}</Typography.Text> : '未填写', 170, true),
    { ...column('courses', '课程服务', r => <Space orientation="vertical" size={2}><strong>{svc(r)?.courseName || svc(r)?.skuName || '历史课程信息缺失'}</strong><span>{svc(r)?.skuName}</span><ProductSpecs product={svc(r) || {}} /></Space>, 280, true), minWidth: 280, ellipsis: false },
    column('className', '班级', r => r.summary?.classKind === 'PENDING' || (r.summary?.systemClass && !r.summary.classKind) ? '待分班' : svc(r)?.className || '未分班', 180, true),
    column('ownerUserName', '学习规划师（班主任）', r => assignedName(svc(r)?.ownerUserId, svc(r)?.ownerUserName), 190),
    column('acceptanceStatus', '接收状态', r => <Tag color={svc(r)?.acceptanceStatus === 'accepted' ? 'green' : 'orange'}>{acceptanceLabel(svc(r)?.acceptanceStatus)}</Tag>, 120),
    column('serviceStatus', '服务状态', r => statusLabel(svc(r)?.status), 120, true),
    column('deliveryStage', '交付阶段', r => r.summary?.deliveryStageLabel || '摘要未提供', 170),
    column('currentTask', '当前联系任务', r => r.summary?.currentTask ? taskLabel(r.summary.currentTask.type) : r.summary ? '暂无任务' : '摘要未提供', 160),
    column('taskState', '任务状态', r => r.summary?.currentTask ? <Tag color={r.summary.currentTask.overdue ? 'red' : 'blue'}>{r.summary.currentTask.overdue ? '已逾期' : '待处理'}</Tag> : r.summary ? '暂无任务' : '摘要未提供', 120),
    column('latestContactAt', '最近联系时间', r => time(r.summary?.latestContact?.submittedAt, '暂无联系记录'), 180, true),
    column('taskDueAt', '当前任务截止时间', r => time(r.summary?.currentTask?.dueAt, r.summary?.currentTask ? '未设置截止时间' : '暂无任务'), 180, true),
    column('examDate', '考试日期', r => r.summary?.examDate || '未安排考试', 140, true),
    column('orderNos', '订单号', r => svc(r)?.orderNo || (svc(r)?.orderId ? '历史未记录' : '独立服务'), 190, true),
    column('leadNo', '客资编号', r => r.leadNo || '未关联客资', 160, true),
    column('categoryPath', '课程分类', r => svc(r)?.categoryPath?.join(' / ') || '历史未记录', 200),
    column('skuName', '具体方案', r => history(svc(r)?.skuName), 180),
    column('specs', '成交规格', r => svc(r)?.specs?.length ? <ProductSpecs product={svc(r)} /> : '历史未记录', 220),
    column('contentDirector', '编导', r => assignedName(svc(r)?.contentDirectorUserId, svc(r)?.contentDirectorUserName)),
    column('careerPlanner', '职业规划师', r => assignedName(svc(r)?.careerPlannerUserId, svc(r)?.careerPlannerUserName)),
    column('operator', '运营负责人', r => assignedName(svc(r)?.operatorUserId, svc(r)?.operatorUserName)),
    column('directorStage', '编导阶段', r => ({ precheck: '前置确认', interview: '定位访谈', positioning_ready: '定位就绪' }[svc(r)?.directorStage || ''] || '历史未记录')),
    column('directorInterviewAt', '访谈预约时间', r => time(svc(r)?.directorInterviewAt)),
    column('classNo', '班级编号', r => history(r.summary?.classNo)),
    column('classDepartment', '班级所属部门', r => history(r.summary?.classDepartment)),
    column('classExamSchedule', '班级考期', r => history(r.summary?.classExamSchedule), 200),
    column('contactType', '最近联系类型', r => r.summary?.latestContact ? taskLabel(r.summary.latestContact.contactType) : '暂无联系记录'),
    column('contactResult', '最近联系结果', r => r.summary?.latestContact ? r.summary.latestContact.successful === true ? '联系成功' : r.summary.latestContact.successful === false ? '联系未成功' : '未记录结果' : '暂无联系记录'),
    column('contactFailure', '未成功原因', r => history(r.summary?.latestContact?.unsuccessfulReasonLabel)),
    column('contactRemark', '最近联系摘要', r => text(r.summary?.latestContact?.remark), 260),
    column('contactOperator', '最近联系操作人', r => assignedName(r.summary?.latestContact?.operatorUserId, r.summary?.latestContact?.operatorUserName)),
    column('nextContactAt', '最近记录约定下次联系', r => time(r.summary?.latestContact?.nextContactAt), 190),
    column('activatedAt', '激活时间', r => time(svc(r)?.activatedAt), 180, true),
    column('acceptedAt', '接收时间', r => time(svc(r)?.acceptedAt), 180, true),
    column('pausedAt', '暂停时间', r => time(r.summary?.pausedAt), 180, true),
    column('pauseReason', '暂停原因', r => text(r.summary?.pauseReason), 220),
    column('completedAt', '完成时间', r => time(r.summary?.completedAt), 180, true),
    column('terminatedAt', '终止时间', r => time(r.summary?.terminatedAt), 180, true),
    column('terminationReason', '终止原因', r => text(r.summary?.terminationReason), 220),
    column('orderType', '订单类型', order('orderType', orderType)),
    column('orderStatus', '订单审核', r => svc(r)?.orderStatusLabel || (svc(r)?.orderId ? '历史未记录' : '独立服务'), 130),
    column('handoffOrderStatus', '订单状态', order('status', orderStatus)),
    column('formalSalesUserName', '成交负责人', order('formalSalesUserName')),
    column('submitterUserName', '订单提交人', order('submitterUserName')),
    column('formalOwnerIdentity', '成交归属身份', order('formalOwnerIdentity', v => ({ sales: '销售', education: '教务' }[v || ''] || '历史未记录'))),
    column('orderSubmittedAt', '订单提交时间', order('submittedAt', v => time(v)), 180),
    column('orderEffectiveAt', '订单生效时间', order('effectiveAt', v => time(v)), 180),
    column('studentNatureLabel', '学员性质', order('studentNatureLabel')),
    column('studentSourceLabel', '学生来源', order('studentSourceLabel')),
    column('classType', '开通班种', order('classType')),
    column('servicePeriodLabel', '约定服务周期', order('servicePeriodLabel')),
    column('agreedExamTime', '商定考试时间', order('agreedExamTime')),
    column('studentSpecialRequirements', '学员特殊要求', order('studentSpecialRequirements', text), 260),
    column('orderRemark', '订单备注', order('remark', text), 260),
    column('materialDeliveryContact', '教材邮递联系', order('materialDeliveryContact', text), 240),
    column('infoStatus', '学员信息表状态', r => r.summary?.infoSubmission?.access === 'readable' ? infoStatus(r.summary.infoSubmission.status) : accessText(r.summary?.infoSubmission?.access)),
    column('infoSubmittedAt', '学员信息表提交时间', r => r.summary?.infoSubmission?.access === 'readable' ? time(r.summary.infoSubmission.submittedAt) : accessText(r.summary?.infoSubmission?.access), 180),
    column('inServicePeriod', '学员在服务期（人工标记）', r => r.inServicePeriod === true ? '是' : r.inServicePeriod === false ? '否' : '历史未记录', 210),
  ]
}
export const studentColumnDefaults = Object.fromEntries(studentServiceColumnDefinitions().map((c, i) => [String(c.key), { show: i < 16, order: i }]))
export function studentServiceColumns(sort: BusinessSort): Column[] {
  return studentServiceColumnDefinitions().map(c => ({ ...c, sortOrder: c.sorter && sort.sortField === c.key ? sort.sortOrder : null }))
}
export function StudentServiceSummaryDetail({ student }: { student: ServiceStudent }) {
  const definitions = studentServiceColumnDefinitions().filter(c => !['name', 'mobile', 'wechatId', 'specs', 'skuName'].includes(String(c.key)))
  return <section className="registration-summary-card"><Typography.Title level={5}>当前课程服务与交接信息</Typography.Title><DetailFieldGrid items={definitions.map(c => ({ key: String(c.key), label: c.title as string, value: c.display?.(student) }))} /></section>
}

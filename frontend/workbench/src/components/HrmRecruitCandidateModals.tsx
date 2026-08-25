import { useEffect, useMemo, useState } from 'react'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import {
  Alert, Button, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Upload, message
} from 'antd'
import { DeleteOutlined, UploadOutlined } from '@ant-design/icons'
import type { UploadFile, UploadProps } from 'antd'
import {
  api, type HrmEmployeeSave, type HrmRecruitCandidate, type HrmRecruitInterview
} from '../services/api'
import { HRM_DICT } from '../services/hrm'
import { useDict } from '../services/useDict'
import DeptTreeSelect from './DeptTreeSelect'
import HrmEmployeePicker from './HrmEmployeePicker'

type Option = { value: number; label: string }

export function RecruitCandidateFormModal({ open, candidate, posts, channels, onClose, onSaved }: {
  open: boolean
  candidate?: HrmRecruitCandidate
  posts: Option[]
  channels: Option[]
  onClose: () => void
  onSaved: () => void
}) {
  const [form] = Form.useForm<HrmRecruitCandidate>()
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const education = useDict(HRM_DICT.RECRUIT_CANDIDATE_EDUCATION)
  const sex = useDict(HRM_DICT.SYSTEM_USER_SEX)
  const resumeUrls = Form.useWatch('resumeUrls', form) || []

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(candidate ? { ...candidate, resumeUrls: candidate.resumeUrls || [] } : { name: '', sex: 1, resumeUrls: [] })
  }, [open, candidate, form])

  const uploadFileList: UploadFile[] = resumeUrls.map((url, index) => ({
    uid: `${index}-${url}`, name: decodeURIComponent(url.split('/').pop() || `简历 ${index + 1}`), status: 'done', url
  }))

  const customRequest: UploadProps['customRequest'] = async options => {
    setUploading(true)
    try {
      const url = await api.hrm.recruit.uploadResume(options.file as File)
      form.setFieldValue('resumeUrls', [...resumeUrls, url])
      options.onSuccess?.(url)
      message.success('简历上传成功')
    } catch (e) {
      options.onError?.(e instanceof Error ? e : new Error('上传失败'))
      message.error(e instanceof Error ? e.message : '简历上传失败')
    } finally {
      setUploading(false)
    }
  }

  const beforeUpload: UploadProps['beforeUpload'] = file => {
    const suffix = file.name.split('.').pop()?.toLowerCase()
    if (!suffix || !['doc', 'docx', 'pdf'].includes(suffix)) {
      message.error('仅支持 DOC、DOCX、PDF 简历')
      return Upload.LIST_IGNORE
    }
    if (file.size > 20 * 1024 * 1024) {
      message.error('单个简历不能超过 20MB')
      return Upload.LIST_IGNORE
    }
    if (resumeUrls.length >= 5) {
      message.error('简历附件最多 5 个')
      return Upload.LIST_IGNORE
    }
    return true
  }

  const save = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      if (candidate) await api.hrm.recruit.candidate.update({ ...values, id: candidate.id })
      else await api.hrm.recruit.candidate.create(values)
      message.success(candidate ? '已保存' : '已创建')
      onClose(); onSaved()
    } catch (e) {
      if (e instanceof Error) message.error(e.message)
    } finally {
      setSaving(false)
    }
  }

  return <Modal title={candidate ? '编辑候选人' : '新增候选人'} open={open} onCancel={onClose} onOk={() => void save()} confirmLoading={saving} width="min(960px, 96vw)" destroyOnClose>
    {(education.error || sex.error) && <Alert type="error" showIcon message="候选人字典加载失败"/>}
    <Form form={form} layout="vertical" className="hrm-edit-form">
      <div className="hrm-edit-grid">
        <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }, { max: 255 }]}><Input/></Form.Item>
        <Form.Item name="mobile" label="手机号" rules={[{ required: true, message: '请输入手机号' }, { pattern: /^(\+?0?\d{2,4}-?)?\d{6,11}$/, message: '手机号格式不正确' }]}><Input maxLength={18}/></Form.Item>
        <Form.Item name="sex" label="性别" rules={[{ required: true, message: '请选择性别' }]}><Select loading={sex.loading} options={sex.options.filter(item => item.value !== 0)}/></Form.Item>
        <Form.Item name="age" label="年龄"><InputNumber min={0} max={99} style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '邮箱格式不正确' }]}><Input maxLength={255}/></Form.Item>
        <Form.Item name="postId" label="应聘职位" rules={[{ required: true, message: '请选择应聘职位' }]}><Select showSearch optionFilterProp="label" options={posts}/></Form.Item>
        <Form.Item name="workTime" label="工作年限"><InputNumber min={0} max={60} style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="education" label="学历" rules={[{ required: true, message: '请选择学历' }]}><Select loading={education.loading} options={education.options}/></Form.Item>
        <Form.Item name="graduateSchool" label="毕业院校"><Input maxLength={255}/></Form.Item>
        <Form.Item name="latestWorkPlace" label="最近工作单位"><Input maxLength={255}/></Form.Item>
        <Form.Item name="channelId" label="招聘渠道"><Select allowClear showSearch optionFilterProp="label" options={channels}/></Form.Item>
      </div>
      <Form.Item label="简历附件">
        <Upload
          fileList={uploadFileList}
          customRequest={customRequest}
          beforeUpload={beforeUpload}
          onRemove={file => { form.setFieldValue('resumeUrls', resumeUrls.filter(url => url !== file.url)); return true }}
          maxCount={5}
        >
          <Button icon={<UploadOutlined/>} loading={uploading} disabled={resumeUrls.length >= 5}>上传简历</Button>
        </Upload>
      </Form.Item>
      <Form.Item name="remark" label="备注"><Input.TextArea rows={3} maxLength={255} showCount/></Form.Item>
    </Form>
  </Modal>
}

type InterviewFormValues = Omit<HrmRecruitInterview, 'interviewTime'> & { interviewTime?: Dayjs }

export function RecruitInterviewFormModal({ open, candidate, interview, onClose, onSaved }: {
  open: boolean
  candidate?: HrmRecruitCandidate
  interview?: HrmRecruitInterview
  onClose: () => void
  onSaved: () => void
}) {
  const [form] = Form.useForm<InterviewFormValues>()
  const [saving, setSaving] = useState(false)
  const interviewTypes = useDict(HRM_DICT.RECRUIT_INTERVIEW_TYPE)

  useEffect(() => {
    if (!open || !candidate?.id) return
    form.resetFields()
    form.setFieldsValue(interview ? { ...interview, interviewTime: dayjs(interview.interviewTime) } : {
      candidateId: candidate.id, type: 3, interviewEmployeeId: undefined, otherInterviewEmployeeIds: [], interviewTime: dayjs().add(1, 'day').startOf('hour'), address: '', remark: ''
    })
  }, [open, candidate, interview, form])

  const save = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      const payload: HrmRecruitInterview = { ...values, candidateId: candidate!.id!, interviewTime: values.interviewTime!.valueOf() }
      if (interview) await api.hrm.recruit.interview.update({ ...payload, id: interview.id })
      else await api.hrm.recruit.interview.create(payload)
      message.success(interview ? '面试安排已更新' : '面试已安排')
      onClose(); onSaved()
    } catch (e) {
      if (e instanceof Error) message.error(e.message)
    } finally {
      setSaving(false)
    }
  }

  return <Modal title={interview ? '更改面试安排' : '安排面试'} open={open} onCancel={onClose} onOk={() => void save()} confirmLoading={saving} width="min(960px, 96vw)" destroyOnClose>
    {interviewTypes.error && <Alert type="error" showIcon message={interviewTypes.error}/>}
    <Form form={form} layout="vertical">
      <div className="hrm-edit-grid">
        <Form.Item name="type" label="面试方式" rules={[{ required: true, message: '请选择面试方式' }]}><Select loading={interviewTypes.loading} options={interviewTypes.options}/></Form.Item>
        <Form.Item name="interviewTime" label="面试时间" rules={[{ required: true, message: '请选择面试时间' }]}><DatePicker showTime style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="interviewEmployeeId" label="主面试官" rules={[{ required: true, message: '请选择主面试官' }]}><HrmEmployeePicker/></Form.Item>
        <Form.Item name="otherInterviewEmployeeIds" label="其他面试官"><HrmEmployeePicker mode="multiple"/></Form.Item>
      </div>
      <Form.Item name="address" label="面试地址"><Input maxLength={255}/></Form.Item>
      <Form.Item name="remark" label="备注"><Input.TextArea rows={3} maxLength={255} showCount/></Form.Item>
    </Form>
  </Modal>
}

export function RecruitInterviewResultModal({ open, interview, cancelMode = false, onClose, onSaved }: {
  open: boolean
  interview?: HrmRecruitInterview
  cancelMode?: boolean
  onClose: () => void
  onSaved: () => void
}) {
  const [form] = Form.useForm<{ result: number; evaluate?: string; cancelReason?: string }>()
  const [saving, setSaving] = useState(false)
  const results = useDict(HRM_DICT.RECRUIT_INTERVIEW_RESULT)
  const result = Form.useWatch('result', form)

  useEffect(() => {
    if (!open || !interview) return
    form.resetFields()
    form.setFieldsValue({ result: cancelMode ? 4 : 2, evaluate: interview.evaluate, cancelReason: interview.cancelReason })
  }, [open, interview, cancelMode, form])

  const save = async () => {
    if (!interview?.id) return
    try {
      const values = await form.validateFields()
      setSaving(true)
      await api.hrm.recruit.interview.updateResult({
        id: interview.id, result: values.result,
        evaluate: values.result === 4 ? undefined : values.evaluate,
        cancelReason: values.result === 4 ? values.cancelReason : undefined
      })
      message.success(cancelMode ? '面试已取消' : '面试结果已登记')
      onClose(); onSaved()
    } catch (e) {
      if (e instanceof Error) message.error(e.message)
    } finally {
      setSaving(false)
    }
  }

  return <Modal title={cancelMode ? '取消面试' : '登记面试结果'} open={open} onCancel={onClose} onOk={() => void save()} confirmLoading={saving} width="min(840px, 96vw)" destroyOnClose>
    <Form form={form} layout="vertical">
      {!cancelMode && <Form.Item name="result" label="面试结果" rules={[{ required: true, message: '请选择面试结果' }]}>
        <Select loading={results.loading} options={results.options.filter(item => item.value === 2 || item.value === 3)}/>
      </Form.Item>}
      {result === 4
        ? <Form.Item name="cancelReason" label="取消原因" rules={[{ required: true, message: '请输入取消原因' }]}><Input.TextArea rows={3} maxLength={255}/></Form.Item>
        : <Form.Item name="evaluate" label="面试评价"><Input.TextArea rows={4} maxLength={255} showCount/></Form.Item>}
    </Form>
  </Modal>
}

type ConvertFormValues = Omit<HrmEmployeeSave, 'entryTime' | 'companyAgeStartTime'> & {
  entryTime?: Dayjs
  companyAgeStartTime?: Dayjs
}

const CANDIDATE_EDUCATION_TO_EMPLOYEE: Record<number, number> = { 1: 1, 2: 2, 3: 6, 4: 7, 5: 8, 6: 9, 7: 10 }

export function RecruitCandidateConvertModal({ open, candidate, onClose, onSaved }: {
  open: boolean
  candidate?: HrmRecruitCandidate
  onClose: () => void
  onSaved: () => void
}) {
  const [form] = Form.useForm<ConvertFormValues>()
  const [saving, setSaving] = useState(false)
  const employeeTypes = useDict(HRM_DICT.EMPLOYEE_TYPE)
  const employeeStatuses = useDict(HRM_DICT.EMPLOYEE_STATUS)
  const entryStatuses = useDict(HRM_DICT.EMPLOYEE_ENTRY_STATUS)
  const education = useDict(HRM_DICT.EMPLOYEE_EDUCATION)
  const type = Form.useWatch('type', form)
  const entryStatus = Form.useWatch('entryStatus', form)
  const statusOptions = useMemo(() => employeeStatuses.options.filter(item => type === 2 ? item.value >= 3 : item.value <= 2), [employeeStatuses.options, type])

  useEffect(() => {
    if (!open || !candidate) return
    const entryTime = candidate.entryTime ? dayjs(candidate.entryTime) : dayjs()
    form.resetFields()
    form.setFieldsValue({
      candidateId: candidate.id, name: candidate.name, mobile: candidate.mobile, sex: candidate.sex,
      age: candidate.age, email: candidate.email,
      highestEducation: candidate.education == null ? undefined : CANDIDATE_EDUCATION_TO_EMPLOYEE[candidate.education],
      deptId: candidate.deptId, postName: candidate.postName, channelId: candidate.channelId,
      entryStatus: 2, status: 2, type: 1, entryTime, companyAgeStartTime: entryTime, probation: 3, remark: candidate.remark
    })
  }, [open, candidate, form])

  const save = async () => {
    if (!candidate?.id) return
    try {
      const values = await form.validateFields()
      setSaving(true)
      await api.hrm.recruit.candidate.convertEmployee({
        ...values, candidateId: candidate.id,
        entryTime: values.entryTime!.valueOf(),
        companyAgeStartTime: values.companyAgeStartTime?.valueOf()
      })
      message.success('已转为待入职员工')
      onClose(); onSaved()
    } catch (e) {
      if (e instanceof Error) message.error(e.message)
    } finally {
      setSaving(false)
    }
  }

  return <Modal title="转为员工档案" open={open} onCancel={onClose} onOk={() => void save()} confirmLoading={saving} width="min(960px, 96vw)" destroyOnClose>
    <Form form={form} layout="vertical" className="hrm-edit-form">
      <div className="hrm-edit-grid">
        <Form.Item name="name" label="姓名" rules={[{ required: true }]}><Input/></Form.Item>
        <Form.Item name="mobile" label="手机号" rules={[{ required: true }]}><Input/></Form.Item>
        <Form.Item name="jobNumber" label="工号" rules={entryStatus === 1 ? [{ required: true, message: '在职员工必须填写工号' }] : undefined}><Input/></Form.Item>
        <Form.Item name="deptId" label="部门"><DeptTreeSelect/></Form.Item>
        <Form.Item name="postName" label="职位"><Input/></Form.Item>
        <Form.Item name="leaderEmployeeId" label="直属上级"><HrmEmployeePicker/></Form.Item>
        <Form.Item name="entryStatus" label="入职状态" rules={[{ required: true }]}><Select loading={entryStatuses.loading} options={entryStatuses.options.filter(item => item.value === 1 || item.value === 2)}/></Form.Item>
        <Form.Item name="entryTime" label="入职时间" rules={[{ required: true }]}><DatePicker style={{ width: '100%' }}/></Form.Item>
        <Form.Item name="type" label="聘用形式" rules={[{ required: true }]}><Select loading={employeeTypes.loading} options={employeeTypes.options}/></Form.Item>
        <Form.Item name="status" label="员工状态" rules={type === 2 ? [{ required: true, message: '请选择员工状态' }] : undefined}><Select loading={employeeStatuses.loading} options={statusOptions}/></Form.Item>
        {type === 1 && <Form.Item name="probation" label="试用期（月）" rules={[{ required: true }]}><InputNumber min={0} max={6} style={{ width: '100%' }}/></Form.Item>}
        <Form.Item name="highestEducation" label="最高学历"><Select allowClear loading={education.loading} options={education.options}/></Form.Item>
        <Form.Item name="workCity" label="工作城市"><Input/></Form.Item>
        <Form.Item name="workAddress" label="工作地点"><Input/></Form.Item>
      </div>
      <Form.Item name="remark" label="备注"><Input.TextArea rows={2} maxLength={500}/></Form.Item>
    </Form>
  </Modal>
}

export function InterviewDeleteButton({ interview, onDeleted }: { interview: HrmRecruitInterview; onDeleted: () => void }) {
  return <Button type="text" danger icon={<DeleteOutlined/>} title="删除面试" onClick={() => Modal.confirm({
    title: '删除面试记录', content: '确定删除这条面试记录吗？', okType: 'danger',
    onOk: async () => { await api.hrm.recruit.interview.delete(interview.id!); message.success('已删除'); onDeleted() }
  })}/>
}

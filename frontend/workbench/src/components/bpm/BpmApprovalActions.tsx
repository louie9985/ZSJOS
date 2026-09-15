import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Alert, App, Button, Form, Input, Modal, Select, Space, Typography } from 'antd'
import {
  CheckOutlined,
  CloseOutlined,
  CommentOutlined,
  RollbackOutlined,
  SendOutlined,
  SwapOutlined,
  UserAddOutlined,
  UserDeleteOutlined,
  UserSwitchOutlined
} from '@ant-design/icons'
import {
  api,
  ApiError,
  AuthenticationError,
  BPM_OPERATION_BUTTON,
  type BpmOperationButtonType,
  type BpmTask,
  type SimpleUser
} from '../../services/api'
import BpmSignaturePad from './BpmSignaturePad'

/** 任务状态 1=审批中，只有该状态的任务可被处理。与后端 BpmTaskStatusEnum 一致。 */
const TASK_STATUS_RUNNING = 1

/** 按钮默认名称；后端 buttonsSetting.displayName 优先。 */
const DEFAULT_BUTTON_NAMES: Record<BpmOperationButtonType, string> = {
  [BPM_OPERATION_BUTTON.APPROVE]: '通过',
  [BPM_OPERATION_BUTTON.REJECT]: '拒绝',
  [BPM_OPERATION_BUTTON.TRANSFER]: '转办',
  [BPM_OPERATION_BUTTON.DELEGATE]: '委派',
  [BPM_OPERATION_BUTTON.ADD_SIGN]: '加签',
  [BPM_OPERATION_BUTTON.RETURN]: '退回',
  [BPM_OPERATION_BUTTON.COPY]: '抄送'
}

type ActionKind =
  | 'approve'
  | 'reject'
  | 'transfer'
  | 'delegate'
  | 'addSign'
  | 'deleteSign'
  | 'return'
  | 'copy'
  | 'comment'

/**
 * 判断按钮是否展示。后端未下发该按钮配置时默认展示，
 * 与 Admin 的 isShowButton 保持一致语义。
 */
export function isBpmButtonVisible(task: BpmTask | undefined, type: BpmOperationButtonType) {
  const setting = task?.buttonsSetting?.[type]
  return setting ? setting.enable !== false : true
}

export function bpmButtonName(task: BpmTask | undefined, type: BpmOperationButtonType) {
  return task?.buttonsSetting?.[type]?.displayName || DEFAULT_BUTTON_NAMES[type]
}

/** 任务是否可处理：仅审批中的任务可执行动作。 */
export function isBpmTaskActionable(task?: BpmTask) {
  return task?.status === TASK_STATUS_RUNNING
}

type ActionFormValues = {
  reason?: string
  assigneeUserId?: number
  delegateUserId?: number
  userIds?: number[]
  copyUserIds?: number[]
  targetTaskDefinitionKey?: string
  /** 减签目标是子任务的任务编号，不是节点 Key。 */
  deleteSignTaskId?: string
  signType?: 'before' | 'after'
}

const ACTION_TITLES: Record<ActionKind, string> = {
  approve: '通过',
  reject: '拒绝',
  transfer: '转办',
  delegate: '委派',
  addSign: '加签',
  deleteSign: '减签',
  return: '退回',
  copy: '抄送',
  comment: '评论'
}

/**
 * BPM 通用审批动作栏。
 *
 * 按钮显隐、显示名、意见必填、是否需要签名全部由后端 todoTask 下发，
 * 因此新增审批流程只要在 BPM 中配置节点即可获得全部动作，无需修改本组件。
 */
export default function BpmApprovalActions({
  task,
  canUpdate,
  users,
  allowDecision = true,
  decisionHint,
  extraActions,
  extraHint,
  onSuccess
}: {
  /** 当前待办任务；来自审批详情的 todoTask。 */
  task?: BpmTask
  canUpdate: boolean
  users: SimpleUser[]
  /**
   * 是否允许在此处做通过/拒绝。
   * 结论在业务页逐条产生的流程（如生产内容批审）必须传 false，
   * 否则直接推进 BPM 会绕过业务结论，导致业务数据与流程状态不一致。
   */
  allowDecision?: boolean
  /** allowDecision=false 时展示的引导文案。 */
  decisionHint?: string
  /** 业务侧推进流程的动作，排在流程类动作之前。 */
  extraActions?: ReactNode
  /** 业务侧动作的补充说明，例如逐条结论的完成进度。 */
  extraHint?: ReactNode
  onSuccess: () => void
}) {
  const { message } = App.useApp()
  const [form] = Form.useForm<ActionFormValues>()
  const [action, setAction] = useState<ActionKind>()
  const [submitting, setSubmitting] = useState(false)
  const [signPicUrl, setSignPicUrl] = useState<string>()
  const [signOpen, setSignOpen] = useState(false)
  const [returnNodes, setReturnNodes] = useState<Array<{ key: string; name: string }>>([])
  const [returnNodesLoading, setReturnNodesLoading] = useState(false)

  const actionable = isBpmTaskActionable(task) && canUpdate
  const reasonRequired = task?.reasonRequire === true
  const signRequired = task?.signEnable === true

  const userOptions = useMemo(
    () => users.map(user => ({
      value: user.id,
      label: user.deptName ? `${user.nickname} · ${user.deptName}` : user.nickname
    })),
    [users]
  )

  // 减签候选就是加签产生的子任务，随任务一起下发，无需额外请求。
  const childrenTasks = useMemo(() => task?.children ?? [], [task])

  useEffect(() => {
    // 切换任务时清空上一条任务残留的签名与候选数据。
    setSignPicUrl(undefined)
    setReturnNodes([])
  }, [task?.id])

  const openAction = useCallback(async (kind: ActionKind) => {
    if (!task) return
    form.resetFields()
    setAction(kind)

    if (kind === 'return') {
      setReturnNodesLoading(true)
      try {
        const list = await api.bpmReturnTaskList(task.id)
        setReturnNodes(list
          .map(item => ({
            key: item.taskDefinitionKey || item.definitionKey || item.id || '',
            name: item.name
          }))
          .filter(item => item.key))
      } catch {
        message.error('可退回节点加载失败，请重试')
        setReturnNodes([])
      } finally {
        setReturnNodesLoading(false)
      }
    }

  }, [form, message, task])

  const closeAction = () => {
    setAction(undefined)
    form.resetFields()
  }

  const submit = async () => {
    if (!task || !action) return
    let values: ActionFormValues
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    if (action === 'approve' && signRequired && !signPicUrl) {
      message.warning('该节点要求签名，请先完成签名')
      return
    }

    setSubmitting(true)
    try {
      const reason = values.reason?.trim() ?? ''
      switch (action) {
        case 'approve':
          await api.approveBpmTask({ id: task.id, reason, signPicUrl })
          break
        case 'reject':
          await api.rejectBpmTask({ id: task.id, reason })
          break
        case 'transfer':
          await api.transferBpmTask({ id: task.id, assigneeUserId: values.assigneeUserId!, reason })
          break
        case 'delegate':
          await api.delegateBpmTask({ id: task.id, delegateUserId: values.delegateUserId!, reason })
          break
        case 'addSign':
          await api.createBpmTaskSign({
            id: task.id,
            userIds: values.userIds!,
            type: values.signType ?? 'after',
            reason
          })
          break
        case 'deleteSign':
          // 减签的 id 是被移除的子任务编号，不是当前任务。
          await api.deleteBpmTaskSign({ id: values.deleteSignTaskId!, reason })
          break
        case 'return':
          await api.returnBpmTask({
            id: task.id,
            targetTaskDefinitionKey: values.targetTaskDefinitionKey!,
            reason
          })
          break
        case 'copy':
          await api.copyBpmTask({ id: task.id, copyUserIds: values.copyUserIds!, reason })
          break
        case 'comment':
          await api.createBpmComment({ taskId: task.id, message: reason })
          break
      }
      message.success(`${ACTION_TITLES[action]}成功`)
      closeAction()
      setSignPicUrl(undefined)
      onSuccess()
    } catch (error) {
      if (error instanceof AuthenticationError) {
        message.error('登录已失效，请重新登录')
      } else if (error instanceof ApiError) {
        // 后端已返回明确的业务原因（如任务已被处理、无权限），直接透出。
        message.error(error.message)
      } else {
        message.error(`${ACTION_TITLES[action]}失败，请重试`)
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (!task) return null

  // 业务侧推进动作由业务权限（如 zsjos:content-review:director-review）授权，
  // 与 bpm:task:update 无关，因此即使没有 BPM 处理权限也要保留它。
  const businessOnly = (hint: string) => <div className="bpm-approval-actions">
    {extraHint && <Typography.Text type="secondary">{extraHint}</Typography.Text>}
    {extraActions && <Space wrap>{extraActions}</Space>}
    <Typography.Text type="secondary">{hint}</Typography.Text>
  </div>

  if (!canUpdate) {
    return businessOnly('当前账号没有 bpm:task:update 权限，无法使用加签、转办等流程动作。')
  }

  if (!isBpmTaskActionable(task)) {
    return businessOnly('该任务已处理完成，流程动作仅可查看。')
  }

  const reasonLabel = action === 'comment' ? '评论内容' : '审批意见'
  // 评论内容与转办/委派/加签/减签/退回原因均为后端必填；通过与拒绝按节点配置。
  const reasonMandatory = action === 'comment'
    || (action !== undefined && ['transfer', 'delegate', 'addSign', 'deleteSign', 'return'].includes(action))
    || reasonRequired

  return <div className="bpm-approval-actions">
    {!allowDecision && decisionHint && <Alert
      type="info"
      showIcon
      message={decisionHint}
      className="bpm-approval-decision-hint"
    />}

    {extraHint && <Typography.Text type="secondary">{extraHint}</Typography.Text>}

    <Space wrap>
      {extraActions}

      {allowDecision && isBpmButtonVisible(task, BPM_OPERATION_BUTTON.APPROVE) && <Button
        type="primary"
        icon={<CheckOutlined/>}
        disabled={!actionable}
        onClick={() => void openAction('approve')}
      >{bpmButtonName(task, BPM_OPERATION_BUTTON.APPROVE)}</Button>}

      {allowDecision && isBpmButtonVisible(task, BPM_OPERATION_BUTTON.REJECT) && <Button
        danger
        icon={<CloseOutlined/>}
        disabled={!actionable}
        onClick={() => void openAction('reject')}
      >{bpmButtonName(task, BPM_OPERATION_BUTTON.REJECT)}</Button>}

      {isBpmButtonVisible(task, BPM_OPERATION_BUTTON.TRANSFER) && <Button
        icon={<SwapOutlined/>}
        disabled={!actionable}
        onClick={() => void openAction('transfer')}
      >{bpmButtonName(task, BPM_OPERATION_BUTTON.TRANSFER)}</Button>}

      {isBpmButtonVisible(task, BPM_OPERATION_BUTTON.DELEGATE) && <Button
        icon={<UserSwitchOutlined/>}
        disabled={!actionable}
        onClick={() => void openAction('delegate')}
      >{bpmButtonName(task, BPM_OPERATION_BUTTON.DELEGATE)}</Button>}

      {isBpmButtonVisible(task, BPM_OPERATION_BUTTON.ADD_SIGN) && <Button
        icon={<UserAddOutlined/>}
        disabled={!actionable}
        onClick={() => void openAction('addSign')}
      >{bpmButtonName(task, BPM_OPERATION_BUTTON.ADD_SIGN)}</Button>}

      {/* 减签只针对加签产生的子任务，与 Admin 一致：没有子任务时不展示。 */}
      {childrenTasks.length > 0 && <Button
        icon={<UserDeleteOutlined/>}
        disabled={!actionable}
        onClick={() => void openAction('deleteSign')}
      >减签</Button>}

      {/* 退回同样会推进流程状态，与通过/拒绝一并受 allowDecision 约束。 */}
      {allowDecision && isBpmButtonVisible(task, BPM_OPERATION_BUTTON.RETURN) && <Button
        icon={<RollbackOutlined/>}
        disabled={!actionable}
        onClick={() => void openAction('return')}
      >{bpmButtonName(task, BPM_OPERATION_BUTTON.RETURN)}</Button>}

      {isBpmButtonVisible(task, BPM_OPERATION_BUTTON.COPY) && <Button
        icon={<SendOutlined/>}
        disabled={!actionable}
        onClick={() => void openAction('copy')}
      >{bpmButtonName(task, BPM_OPERATION_BUTTON.COPY)}</Button>}

      <Button
        icon={<CommentOutlined/>}
        disabled={!actionable}
        onClick={() => void openAction('comment')}
      >评论</Button>
    </Space>

    <Modal
      open={action !== undefined}
      title={ACTION_TITLES[action ?? 'approve']}
      onCancel={closeAction}
      onOk={() => void submit()}
      confirmLoading={submitting}
      okText="提交"
      cancelText="取消"
      destroyOnHidden
    >
      <Form form={form} layout="vertical" initialValues={{ signType: 'after' }}>
        {action === 'approve' && signRequired && <Form.Item label="签名" required>
          <Space>
            <Button onClick={() => setSignOpen(true)}>{signPicUrl ? '重新签名' : '点击签名'}</Button>
            {signPicUrl && <img src={signPicUrl} alt="签名" className="bpm-signature-preview"/>}
          </Space>
        </Form.Item>}

        {action === 'transfer' && <Form.Item
          name="assigneeUserId"
          label="新审批人"
          rules={[{ required: true, message: '请选择新审批人' }]}
        >
          <Select showSearch optionFilterProp="label" options={userOptions} placeholder="选择新审批人"/>
        </Form.Item>}

        {action === 'delegate' && <Form.Item
          name="delegateUserId"
          label="被委派人"
          rules={[{ required: true, message: '请选择被委派人' }]}
        >
          <Select showSearch optionFilterProp="label" options={userOptions} placeholder="选择被委派人"/>
        </Form.Item>}

        {action === 'addSign' && <>
          <Form.Item name="signType" label="加签方式" rules={[{ required: true }]}>
            <Select options={[
              { value: 'before', label: '向前加签（先由加签人审批）' },
              { value: 'after', label: '向后加签（本人通过后再由加签人审批）' }
            ]}/>
          </Form.Item>
          <Form.Item
            name="userIds"
            label="加签人"
            rules={[{ required: true, message: '请选择加签人' }]}
          >
            <Select mode="multiple" showSearch optionFilterProp="label" options={userOptions} placeholder="选择加签人"/>
          </Form.Item>
        </>}

        {action === 'deleteSign' && <Form.Item
          name="deleteSignTaskId"
          label="减签人员"
          rules={[{ required: true, message: '请选择要减签的任务' }]}
        >
          <Select
            placeholder="选择要减签的任务"
            options={childrenTasks.map(item => ({
              value: item.id,
              label: `${item.name}${item.assigneeUser?.nickname ? ` · ${item.assigneeUser.nickname}` : ''}`
            }))}
          />
        </Form.Item>}

        {action === 'return' && <Form.Item
          name="targetTaskDefinitionKey"
          label="退回节点"
          rules={[{ required: true, message: '请选择退回节点' }]}
          extra={!returnNodesLoading && returnNodes.length === 0 ? '当前任务没有可退回的节点。' : undefined}
        >
          <Select
            loading={returnNodesLoading}
            placeholder="选择退回节点"
            options={returnNodes.map(item => ({ value: item.key, label: item.name }))}
          />
        </Form.Item>}

        {action === 'copy' && <Form.Item
          name="copyUserIds"
          label="抄送人"
          rules={[{ required: true, message: '请选择抄送人' }]}
        >
          <Select mode="multiple" showSearch optionFilterProp="label" options={userOptions} placeholder="选择抄送人"/>
        </Form.Item>}

        <Form.Item
          name="reason"
          label={reasonLabel}
          rules={reasonMandatory ? [{ required: true, message: `请输入${reasonLabel}` }] : undefined}
        >
          <Input.TextArea rows={3} maxLength={500} showCount placeholder={`请输入${reasonLabel}`}/>
        </Form.Item>
      </Form>
    </Modal>

    <BpmSignaturePad
      open={signOpen}
      onClose={() => setSignOpen(false)}
      onConfirm={url => setSignPicUrl(url)}
    />
  </div>
}

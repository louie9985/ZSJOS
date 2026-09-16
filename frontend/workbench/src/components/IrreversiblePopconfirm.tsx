import { Popconfirm } from 'antd'
import type { ReactElement } from 'react'
import {
  irreversibleConfirmTitle,
  IRREVERSIBLE_CONFIRM_DESCRIPTION
} from '../services/irreversibleConfirm'

export default function IrreversiblePopconfirm({
  action,
  danger = false,
  children,
  onConfirm,
  open,
  onOpenChange,
  disabled = false,
  description = IRREVERSIBLE_CONFIRM_DESCRIPTION
}: {
  action: string
  danger?: boolean
  children: ReactElement
  onConfirm: () => void | Promise<void>
  open?: boolean
  onOpenChange?: (open: boolean) => void
  disabled?: boolean
  /** 覆盖默认的「该操作无法撤回」说明，用于可恢复的不可逆动作（如取消支付链接后可重新生成）。 */
  description?: string
}) {
  return <Popconfirm
    title={irreversibleConfirmTitle(action)}
    description={description}
    okText="确认执行"
    cancelText="取消"
    okButtonProps={{ danger }}
    disabled={disabled}
    open={open}
    onOpenChange={nextOpen => {
      if (open === undefined || !nextOpen) onOpenChange?.(nextOpen)
    }}
    onConfirm={onConfirm}
  >
    {children}
  </Popconfirm>
}

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
  disabled = false
}: {
  action: string
  danger?: boolean
  children: ReactElement
  onConfirm: () => void | Promise<void>
  open?: boolean
  onOpenChange?: (open: boolean) => void
  disabled?: boolean
}) {
  return <Popconfirm
    title={irreversibleConfirmTitle(action)}
    description={IRREVERSIBLE_CONFIRM_DESCRIPTION}
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

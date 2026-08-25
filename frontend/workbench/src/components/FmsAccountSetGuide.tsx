import { Modal } from 'antd'
import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { useFmsAccountSet } from '../services/useFmsAccountSet'

/**
 * FMS 账套开通引导弹窗。
 * 在 listLoaded 后若仍无可用账套且不在开通页面时自动展示。
 */
export default function FmsAccountSetGuide() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { accountSet, listLoaded } = useFmsAccountSet()

  // 仅在 /fms 路径下检查
  const isFmsPath = pathname.startsWith('/fms')
  // 已在开通配置页则不弹
  const isAccountSetConfigPage = pathname === APP_ROUTES.FMS_CONFIG_ACCOUNT_SET

  const shouldShow = isFmsPath && listLoaded && !accountSet && !isAccountSetConfigPage

  const handleOk = useCallback(() => {
    navigate(APP_ROUTES.FMS_CONFIG_ACCOUNT_SET)
  }, [navigate])

  if (!shouldShow) return null

  return (
    <Modal
      title="请先开通账套"
      open
      closable={false}
      maskClosable={false}
      cancelButtonProps={{ style: { display: 'none' } }}
      okText="前往开通"
      onOk={handleOk}
    >
      <p>当前没有可用的账套，请先前往账套管理页面开通或加入一个账套后再使用财务功能。</p>
    </Modal>
  )
}

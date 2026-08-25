import { useState } from 'react'
import { Modal, Select, Tag, message } from 'antd'
import { useLocation } from 'react-router-dom'
import { useFmsAccountSet } from '../services/useFmsAccountSet'

/**
 * FMS 账套切换器，放在 header-actions 区域。
 * 仅在 /fms 路径下显示。
 */
export default function FmsAccountSetSwitch() {
  const { pathname } = useLocation()
  const { accountSet, accountSetList, currentMonth, loading, selectAccountSet } = useFmsAccountSet()
  const [switching, setSwitching] = useState(false)

  // 仅在 FMS 模块路径下显示
  if (!pathname.startsWith('/fms')) return null

  return (
    <span className="fms-account-set-switch">
      <Select
        size="small"
        style={{ width: 160 }}
        value={accountSet?.id}
        loading={loading || switching}
        disabled={switching}
        placeholder="选择账套"
        onChange={id => {
          if (id === accountSet?.id) return
          const target = accountSetList.find(item => item.id === id)
          if (!target) return
          Modal.confirm({
            title: '切换账套',
            content: `切换账套后，财务页面中未保存的内容不会保留。确认切换至“${target.companyName}”吗？`,
            okText: '确认切换',
            cancelText: '取消',
            onOk: async () => {
              setSwitching(true)
              try {
                await selectAccountSet(id)
                message.success(`已切换至账套“${target.companyName}”`)
              } catch (e) {
                message.error(e instanceof Error ? e.message : '账套切换失败')
                throw e
              } finally {
                setSwitching(false)
              }
            }
          })
        }}
        options={accountSetList
          .filter(item => item.initialized)
          .map(item => ({ value: item.id!, label: item.companyName }))}
      />
      {currentMonth && (
        <Tag className="fms-account-set-period" color="blue" style={{ marginLeft: 4 }}>
          {currentMonth}
        </Tag>
      )}
    </span>
  )
}

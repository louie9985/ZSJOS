import { useCallback, useEffect, useRef, useState } from 'react'
import { Select, Spin } from 'antd'
import { api, type EamAssetListItem } from '../services/api'

/**
 * 资产远程搜索选择器。与 admin 端 el-select[remote] 行为一致：
 * 打开即拉首页，输入时按名称远程搜索。
 */
export default function AssetSelect({ value, onChange, placeholder = '输入资产编号或名称搜索', disabled, statusLabels }: {
  value?: number
  onChange?: (value: number | undefined) => void
  placeholder?: string
  disabled?: boolean
  statusLabels?: Record<string, string>
}) {
  const [options, setOptions] = useState<EamAssetListItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const searchVersion = useRef(0)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const search = useCallback(async (keyword: string) => {
    const version = ++searchVersion.current
    setLoading(true); setError('')
    try {
      const result = await api.eam.asset.page({ pageNo: 1, pageSize: 20, name: keyword || undefined })
      if (version === searchVersion.current) setOptions(result.list)
    } catch (e) {
      if (version === searchVersion.current) setError(e instanceof Error ? e.message : '资产搜索失败')
    } finally {
      if (version === searchVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void search('') }, [search])
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current) }, [])

  return <Select
    showSearch filterOption={false} allowClear disabled={disabled}
    value={value} onChange={onChange} placeholder={placeholder} style={{ width: '100%' }}
    loading={loading}
    notFoundContent={loading ? <Spin size="small"/> : error || '无匹配资产'}
    onSearch={keyword => {
      if (timer.current) clearTimeout(timer.current)
      timer.current = setTimeout(() => void search(keyword), 300)
    }}
    options={options.map(item => ({
      value: item.id,
      label: `${item.assetCode ?? ''} ${item.name}${item.status != null && statusLabels?.[String(item.status)] ? ` · ${statusLabels[String(item.status)]}` : ''}`.trim()
    }))}
  />
}

import { useCallback, useEffect, useState } from 'react'
import { api, type NotifyMessageCategoryOption } from './api'

/**
 * 消息分类目录由服务端 `NotifyMessageCategory` 统一提供，客户端不做任何分类判断。
 *
 * 这样 SQL 过滤与展示分类只可能有一处口径：任何「某条消息属于哪个分类」的改动
 * 只需改服务端，前端自动跟随，不会再出现前后端判定顺序不一致导致同一条消息
 * 在两个分类下重复出现的问题。
 */
export function useNotifyMessageCategories() {
  const [categories, setCategories] = useState<NotifyMessageCategoryOption[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setCategories(await api.myNotifyMessageCategories())
      setError('')
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '消息分类加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  return { categories, loading, error, reload: load }
}

/**
 * 分类编码 → 名称。未在目录中的编码回退为服务端的兜底分类名称，
 * 使新增分类在后端先上线时不会显示为空白。
 */
export const notifyMessageCategoryLabelOf = (
  categories: NotifyMessageCategoryOption[],
  key?: string | null
) => categories.find(item => item.key === key)?.label || '系统'

/** Vue Admin 在 Workbench 中以纯内容页运行时的判定。 */
export const isWorkbenchEmbed = () => {
  if (typeof window === 'undefined') return false
  return (
    import.meta.env.VITE_BASE_PATH?.startsWith('/admin-embed') === true ||
    new URLSearchParams(window.location.search).get('embed') === 'workbench'
  )
}

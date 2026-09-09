type BootstrapLocation = Pick<Location, 'pathname' | 'search' | 'hash'>

/** 构造认证初始化失败后的登录回跳地址，保留用户原本打开的页面。 */
export const resolveBootstrapLoginUrl = (location: BootstrapLocation): string => {
  const target = `${location.pathname}${location.search}${location.hash}` || '/'
  return `/login?redirect=${encodeURIComponent(target)}`
}

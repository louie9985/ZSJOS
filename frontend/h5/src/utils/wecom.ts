/**
 * 企业微信环境检测和 SDK 工具
 */

/** 判断是否在企业微信环境中 */
export function isInWecom(): boolean {
  const ua = navigator.userAgent.toLowerCase()
  return ua.includes('wxwork')
}

/** 判断是否在微信环境中（含企微） */
export function isInWechat(): boolean {
  const ua = navigator.userAgent.toLowerCase()
  return ua.includes('micromessenger')
}

/**
 * 企微 OAuth 登录
 * 拼接授权 URL 并跳转，授权后企微会回调到 redirectUri 带上 code
 */
export function redirectToWecomOAuth(corpId: string, agentId: string, redirectUri: string) {
  const url = `https://open.weixin.qq.com/connect/oauth2/authorize?appid=${corpId}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=snsapi_base&agentid=${agentId}#wechat_redirect`
  window.location.href = url
}

/**
 * 从 URL 中提取 OAuth code
 */
export function getWecomCodeFromUrl(): string | null {
  const params = new URLSearchParams(window.location.search)
  return params.get('code')
}

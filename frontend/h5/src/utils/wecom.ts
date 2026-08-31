/**
 * 企业微信环境检测工具
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
 * 企微登录与绑定由后端返回授权地址，这里只保留环境识别。
 */

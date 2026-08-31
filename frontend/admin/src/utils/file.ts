/** 从 URL 中提取文件名 */
export const getFileNameFromUrl = (url: string): string => {
  try {
    const urlObj = new URL(url)
    const pathname = urlObj.pathname
    const fileName = pathname.split('/').pop() || 'unknown'
    return decodeURIComponent(fileName)
  } catch {
    // 如果 URL 解析失败，尝试从字符串中提取
    const cleanUrl = url.split(/[?#]/)[0]
    const parts = cleanUrl.split('/')
    const fileName = parts[parts.length - 1] || 'unknown'
    try {
      return decodeURIComponent(fileName)
    } catch {
      return fileName
    }
  }
}

/** 获取文件扩展名 */
export const getFileExtension = (filename: string): string => {
  const cleanName = filename.split(/[?#]/)[0]
  return cleanName.split('.').pop()?.toLowerCase() || ''
}

const imageExtensions = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg', 'heic', 'heif']
const audioExtensions = ['mp3', 'wav', 'm4a', 'aac', 'flac', 'ogg']
const videoExtensions = ['mp4', 'avi', 'mov', 'wmv', 'mkv', 'webm', 'flv']
const archiveExtensions = ['zip', 'rar', '7z', 'tar', 'gz', 'bz2']
const textExtensions = ['txt', 'md', 'log']
const excelExtensions = ['xls', 'xlsx', 'xlsm', 'xlsb']
const wordExtensions = ['doc', 'docx', 'docm']
const powerpointExtensions = ['ppt', 'pptx', 'pptm']

const mimeTypeMatches = (mimeType: string | undefined, prefix: string): boolean => {
  return Boolean(mimeType?.toLowerCase().startsWith(prefix))
}

/** 判断是否为图片 */
export const isImage = (filename: string): boolean => {
  const ext = getFileExtension(filename)
  return imageExtensions.includes(ext)
}

/** 格式化文件大小 */
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/** 获取文件图标 */
export const getFileIcon = (filename: string, mimeType?: string): string => {
  const ext = getFileExtension(filename)
  if (imageExtensions.includes(ext) || mimeTypeMatches(mimeType, 'image/')) return 'fa-solid:file-image'
  if (
    excelExtensions.includes(ext) ||
    mimeTypeMatches(mimeType, 'application/vnd.ms-excel') ||
    mimeTypeMatches(mimeType, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
  ) return 'fa-solid:file-excel'
  if (
    wordExtensions.includes(ext) ||
    mimeTypeMatches(mimeType, 'application/msword') ||
    mimeTypeMatches(mimeType, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')
  ) return 'fa-solid:file-word'
  if (
    powerpointExtensions.includes(ext) ||
    mimeTypeMatches(mimeType, 'application/vnd.ms-powerpoint') ||
    mimeTypeMatches(mimeType, 'application/vnd.openxmlformats-officedocument.presentationml.presentation')
  ) return 'fa-solid:file-powerpoint'
  if (ext === 'pdf' || mimeTypeMatches(mimeType, 'application/pdf')) return 'fa-solid:file-pdf'
  if (
    archiveExtensions.includes(ext) ||
    mimeTypeMatches(mimeType, 'application/zip') ||
    mimeTypeMatches(mimeType, 'application/x-zip') ||
    mimeTypeMatches(mimeType, 'application/x-rar') ||
    mimeTypeMatches(mimeType, 'application/x-7z')
  ) return 'fa-solid:file-archive'
  if (audioExtensions.includes(ext) || mimeTypeMatches(mimeType, 'audio/')) return 'fa-solid:file-audio'
  if (videoExtensions.includes(ext) || mimeTypeMatches(mimeType, 'video/')) return 'fa-solid:file-video'
  if (ext === 'csv' || mimeTypeMatches(mimeType, 'text/csv')) return 'fa-solid:file-csv'
  if (textExtensions.includes(ext) || mimeTypeMatches(mimeType, 'text/')) return 'fa-solid:file-alt'
  if (ext === 'code' || ext === 'json' || ext === 'xml' || ext === 'yml' || ext === 'yaml') return 'fa-solid:file-code'
  return 'fa-solid:file'
}

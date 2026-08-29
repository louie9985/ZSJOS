import {
  FileExcelOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  FilePptOutlined,
  FileTextOutlined,
  FileUnknownOutlined,
  FileWordOutlined,
  FileZipOutlined
} from '@ant-design/icons'

export type AnnouncementAttachmentKind =
  | 'image'
  | 'excel'
  | 'word'
  | 'powerpoint'
  | 'pdf'
  | 'archive'
  | 'text'
  | 'unknown'

const extension = (name: string) => {
  const separator = name.lastIndexOf('.')
  return separator > 0 && separator < name.length - 1 ? name.slice(separator + 1).toLowerCase() : ''
}

const mimeTypeMatches = (mimeType: string | undefined, prefix: string) =>
  Boolean(mimeType?.toLowerCase().startsWith(prefix))

export const getAnnouncementAttachmentKind = (name: string, mimeType?: string): AnnouncementAttachmentKind => {
  const ext = extension(name)
  if (mimeTypeMatches(mimeType, 'image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'heic', 'heif'].includes(ext)) return 'image'
  if (ext === 'pdf' || mimeTypeMatches(mimeType, 'application/pdf')) return 'pdf'
  if (
    ['xls', 'xlsx', 'xlsm', 'xlsb'].includes(ext) ||
    mimeTypeMatches(mimeType, 'application/vnd.ms-excel') ||
    mimeTypeMatches(mimeType, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
  ) return 'excel'
  if (
    ['doc', 'docx', 'docm'].includes(ext) ||
    mimeTypeMatches(mimeType, 'application/msword') ||
    mimeTypeMatches(mimeType, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')
  ) return 'word'
  if (
    ['ppt', 'pptx', 'pptm'].includes(ext) ||
    mimeTypeMatches(mimeType, 'application/vnd.ms-powerpoint') ||
    mimeTypeMatches(mimeType, 'application/vnd.openxmlformats-officedocument.presentationml.presentation')
  ) return 'powerpoint'
  if (
    ['zip', 'rar', '7z', 'tar', 'gz', 'bz2'].includes(ext) ||
    mimeTypeMatches(mimeType, 'application/zip') ||
    mimeTypeMatches(mimeType, 'application/x-zip') ||
    mimeTypeMatches(mimeType, 'application/x-rar') ||
    mimeTypeMatches(mimeType, 'application/x-7z')
  ) return 'archive'
  if (['txt', 'md', 'log', 'csv', 'json', 'xml', 'yml', 'yaml'].includes(ext) || mimeTypeMatches(mimeType, 'text/')) return 'text'
  return 'unknown'
}

const iconMap: Record<AnnouncementAttachmentKind, typeof FileUnknownOutlined> = {
  image: FileImageOutlined,
  excel: FileExcelOutlined,
  word: FileWordOutlined,
  powerpoint: FilePptOutlined,
  pdf: FilePdfOutlined,
  archive: FileZipOutlined,
  text: FileTextOutlined,
  unknown: FileUnknownOutlined
}

export default function AnnouncementAttachmentIcon({
  name,
  mimeType,
  className
}: {
  name: string
  mimeType?: string
  className?: string
}) {
  const IconComponent = iconMap[getAnnouncementAttachmentKind(name, mimeType)]
  return <IconComponent className={className} aria-hidden />
}

import type { UploadRawFile } from 'element-plus'

const message = useMessage() // 消息

enum UploadType {
  Image = 'image',
  Voice = 'voice',
  Video = 'video'
}

const useBeforeUpload = (type: UploadType, _maxSizeMB?: number) => {
  const fn = (rawFile: UploadRawFile): boolean => {
    let allowTypes: string[] = []
    let name = ''

    switch (type) {
      case UploadType.Image:
        allowTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/bmp', 'image/jpg']
        name = '图片'
        break
      case UploadType.Voice:
        allowTypes = ['audio/mp3', 'audio/mpeg', 'audio/wma', 'audio/wav', 'audio/amr']
        name = '语音'
        break
      case UploadType.Video:
        allowTypes = ['video/mp4']
        name = '视频'
        break
    }
    // 格式不正确
    if (!allowTypes.includes(rawFile.type)) {
      message.error(`上传${name}格式不对!`)
      return false
    }
    return true
  }

  return fn
}

export { UploadType, useBeforeUpload }

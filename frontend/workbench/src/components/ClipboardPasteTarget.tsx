import { PictureOutlined } from '@ant-design/icons'
import { Button, message } from 'antd'
import { useCallback, useRef, useState, type HTMLAttributes, type ReactNode } from 'react'
import { ClipboardImageReadError, readClipboardImageFiles } from '../services/clipboardImage'

type Options = {
  disabled?: boolean
  canPaste?: () => boolean
  onFiles: (files: File[]) => void
}

export function ClipboardUploadButtons({ disabled = false, canPaste = () => true, onFiles, children }: Options & { children: ReactNode }) {
  const { targetRef, targetProps, pasteButtonProps } = useClipboardPasteTarget({ disabled, canPaste, onFiles })
  return <div ref={targetRef} {...targetProps}>
    <div className="attachment-upload-actions">
      {children}
      <Button {...pasteButtonProps} icon={<PictureOutlined />}>
        上传剪贴板截图
      </Button>
    </div>
  </div>
}

export function useClipboardPasteTarget({ disabled = false, canPaste = () => true, onFiles }: Options) {
  const targetRef = useRef<HTMLDivElement>(null)
  const readingRef = useRef(false)
  const [reading, setReading] = useState(false)
  const unavailable = disabled || !canPaste()

  const readClipboard = useCallback(async () => {
    if (readingRef.current || disabled || !canPaste()) return
    readingRef.current = true
    setReading(true)
    try {
      const files = await readClipboardImageFiles()
      if (!files.length) {
        message.warning('剪贴板中没有图片')
        return
      }
      onFiles(files)
    } catch (cause) {
      message.error(cause instanceof ClipboardImageReadError ? cause.message : '当前浏览器无法读取剪贴板，请使用文件上传')
    } finally {
      readingRef.current = false
      setReading(false)
    }
  }, [canPaste, disabled, onFiles])

  const targetProps: HTMLAttributes<HTMLDivElement> = {
    'aria-busy': reading,
  }
  const pasteButtonProps = {
    disabled: unavailable || reading,
    loading: reading,
    'aria-label': '上传剪贴板截图',
    'aria-busy': reading,
    onClick: () => { void readClipboard() },
  } as const

  return { reading, targetRef, targetProps, pasteButtonProps }
}

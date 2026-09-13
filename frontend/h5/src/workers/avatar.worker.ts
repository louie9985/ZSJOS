/// <reference lib="webworker" />

import { createAvatar, type AvatarOptions } from '@/vendor/avatar-kit/index.mjs'

interface AvatarWorkerRequest {
  id: number
  options: AvatarOptions
}

self.addEventListener('message', (event: MessageEvent<AvatarWorkerRequest>) => {
  const { id, options } = event.data
  try {
    self.postMessage({ id, dataUri: createAvatar(options).dataUri })
  } catch (cause) {
    self.postMessage({ id, error: cause instanceof Error ? cause.message : '头像生成失败' })
  }
})

export {}

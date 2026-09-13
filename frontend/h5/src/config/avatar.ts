import { getTenantId } from '@/utils/storage'
import type { AvatarTheme, AvatarVariant } from '@/vendor/avatar-kit/index.mjs'

export const avatarConfig: Readonly<{
  variant: AvatarVariant
  theme: AvatarTheme
  namespace: string
}> = Object.freeze({
  variant: 'geometric',
  theme: 'light',
  namespace: 'zsjos:partner-h5'
})

export const ANONYMOUS_AVATAR_SEED = 'anonymous'

export function avatarNamespace() {
  return `${avatarConfig.namespace}:tenant:${getTenantId()}`
}

export function partnerAvatarSeed(id?: number | string | null) {
  return id == null || String(id).trim() === '' ? ANONYMOUS_AVATAR_SEED : `partner:${id}`
}

export function leadAvatarSeed(id?: number | string | null) {
  return id == null || String(id).trim() === '' ? ANONYMOUS_AVATAR_SEED : `lead:${id}`
}

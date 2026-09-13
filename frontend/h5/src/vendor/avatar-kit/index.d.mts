export type AvatarVariant = 'geometric' | 'abstract' | 'character' | 'collection'
export type AvatarTheme = 'light' | 'dark'

export interface AvatarOptions {
  seed: string | number
  variant?: AvatarVariant
  namespace?: string
  theme?: AvatarTheme
  size?: number
}

export interface GeneratedAvatar {
  readonly version: 'v1'
  readonly seed: string
  readonly variant: AvatarVariant
  readonly size: number
  readonly theme: AvatarTheme
  readonly palette: string
  readonly collectionName: string | null
  readonly svg: string
  readonly dataUri: string
}

export const VERSION: 'v1'
export const VARIANTS: readonly AvatarVariant[]
export function createAvatar(options: AvatarOptions): GeneratedAvatar

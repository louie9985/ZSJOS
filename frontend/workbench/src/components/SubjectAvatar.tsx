import { Avatar, type AvatarProps } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ANONYMOUS_AVATAR_SEED,
  DEFAULT_AVATAR_VERSION,
  DEFAULT_AVATAR_NAMESPACE_PREFIX,
  selectAvatarVariant,
} from '@zsjos/avatar-kit/config'
import { createAvatar, VARIANTS, type AvatarVariant, type AvatarVersion } from '@zsjos/avatar-kit'
import { useTheme } from './Theme/ThemeContext'

export type SubjectAvatarProps = Omit<AvatarProps, 'src' | 'size' | 'shape' | 'children' | 'alt'> & {
  seed?: string | number
  src?: string
  variant?: AvatarVariant
  version?: AvatarVersion
  size?: number
  shape?: 'circle' | 'rounded'
  theme?: 'light' | 'dark'
  label?: string
  namespace?: string
  subjectType?: 'lead' | 'student' | 'subject'
}

export function normalizeSubjectSeed(seed?: string | number): string {
  if (typeof seed === 'number' && Number.isFinite(seed)) return String(seed)
  if (typeof seed === 'string' && seed.trim()) return seed
  return ANONYMOUS_AVATAR_SEED
}

export function normalizeSubjectSize(size?: number): number {
  return Number.isInteger(size) && size! >= 1 && size! <= 2048 ? size! : 40
}

export function resolveSubjectNamespace(namespace?: string): string {
  if (namespace?.trim()) return namespace
  return DEFAULT_AVATAR_NAMESPACE_PREFIX
}

export default function SubjectAvatar({
  seed,
  src,
  variant,
  version = DEFAULT_AVATAR_VERSION,
  size,
  shape = 'circle',
  theme,
  label,
  namespace,
  subjectType = 'subject',
  style,
  ...props
}: SubjectAvatarProps) {
  const { isDark } = useTheme()
  const normalizedSize = normalizeSubjectSize(size)
  const normalizedSeed = normalizeSubjectSeed(seed)
  const selectedVariant = variant && VARIANTS.includes(variant)
    ? variant
    : selectAvatarVariant(normalizedSeed)
  const selectedTheme = theme || (isDark ? 'dark' : 'light')
  const selectedNamespace = resolveSubjectNamespace(namespace)
  const generated = useMemo(
    () => createAvatar({
      seed: normalizedSeed,
      version,
      namespace: selectedNamespace,
      variant: selectedVariant,
      theme: selectedTheme,
      size: normalizedSize,
    }),
    [normalizedSeed, normalizedSize, selectedNamespace, selectedTheme, selectedVariant, version],
  )
  const realSource = src?.trim() || undefined
  const [displaySource, setDisplaySource] = useState(realSource || generated.dataUri)
  const renderToken = useRef(0)

  useEffect(() => {
    renderToken.current += 1
    setDisplaySource(realSource || generated.dataUri)
  }, [generated.dataUri, realSource])

  const handleError = useCallback(() => {
    if (!realSource) return false
    const token = renderToken.current
    setDisplaySource(current => {
      if (token !== renderToken.current || current !== realSource) return current
      return generated.dataUri
    })
    return false
  }, [generated.dataUri, realSource])

  return (
    <Avatar
      {...props}
      src={displaySource}
      size={normalizedSize}
      shape="circle"
      alt={label}
      onError={handleError}
      style={{ ...style, borderRadius: shape === 'rounded' ? '25%' : '50%' }}
    />
  )
}

import { describe, expect, it } from 'vitest'
import { ANONYMOUS_AVATAR_SEED, selectAvatarVariant } from '@zsjos/avatar-kit/config'
import { createAvatar } from '@zsjos/avatar-kit'
import { normalizeSubjectSeed, normalizeSubjectSize, resolveSubjectNamespace } from './SubjectAvatar'

describe('SubjectAvatar identity normalization', () => {
  it('uses the stable ID and keeps names out of the generated identity', () => {
    expect(normalizeSubjectSeed(42)).toBe('42')
    expect(selectAvatarVariant(42)).toBe(selectAvatarVariant(42))
    expect(createAvatar({ seed: normalizeSubjectSeed(42), variant: selectAvatarVariant(42) }).svg)
      .toBe(createAvatar({ seed: normalizeSubjectSeed(42), variant: selectAvatarVariant(42) }).svg)
  })

  it('uses one anonymous identity for missing or blank IDs', () => {
    expect(normalizeSubjectSeed()).toBe(ANONYMOUS_AVATAR_SEED)
    expect(normalizeSubjectSeed('  ')).toBe(ANONYMOUS_AVATAR_SEED)
  })

  it('keeps the same business number identical across subject types and pages', () => {
    const seed = normalizeSubjectSeed('KZ202609090001')
    const namespace = resolveSubjectNamespace()
    const variant = selectAvatarVariant(seed)
    const lead = createAvatar({ seed, namespace, variant, theme: 'light', size: 24 }).svg
    const student = createAvatar({ seed, namespace, variant: selectAvatarVariant(seed, 'student'), theme: 'light', size: 56 }).svg
    expect(lead.replace(/width="24" height="24"/g, 'WIDTH HEIGHT')).toBe(student.replace(/width="56" height="56"/g, 'WIDTH HEIGHT'))
    expect(selectAvatarVariant(seed, 'lead')).toBe(selectAvatarVariant(seed, 'student'))
  })

  it('uses the fixed namespace for all default subject avatars', () => {
    expect(resolveSubjectNamespace()).toBe('zsjos:subject')
    expect(resolveSubjectNamespace('  ')).toBe('zsjos:subject')
  })

  it('keeps v1 as the default while allowing explicit v2 generation', () => {
    const options = { seed: 'KZ202609090001', namespace: 'zsjos:subject', variant: 'geometric' as const }
    expect(createAvatar(options).version).toBe('v1')
    expect(createAvatar({ ...options, version: 'v2' }).version).toBe('v2')
  })

  it('accepts stable display sizes and falls back for invalid values', () => {
    for (const size of [24, 32, 40, 56]) expect(normalizeSubjectSize(size)).toBe(size)
    expect(normalizeSubjectSize(0)).toBe(40)
    expect(normalizeSubjectSize(2.5)).toBe(40)
  })
})

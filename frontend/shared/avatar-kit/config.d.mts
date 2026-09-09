import type { AvatarVariant, AvatarVersion } from './index.d.mts';

export const DEFAULT_AVATAR_VARIANT: AvatarVariant;
export const DEFAULT_AVATAR_VERSION: AvatarVersion;
export const DEFAULT_AVATAR_NAMESPACE_PREFIX: 'zsjos:subject';
export const ANONYMOUS_AVATAR_SEED: 'anonymous';
export const AVATAR_STYLE_VARIANTS: readonly AvatarVariant[];
export function selectAvatarVariant(seed: string | number, subjectType?: string): AvatarVariant;

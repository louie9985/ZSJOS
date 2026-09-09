export const DEFAULT_AVATAR_VARIANT = 'geometric';
// Keep v1 as the compatibility default; consumers can opt into the expanded v2 set.
export const DEFAULT_AVATAR_VERSION = 'v1';
export const DEFAULT_AVATAR_NAMESPACE_PREFIX = 'zsjos:subject';
export const ANONYMOUS_AVATAR_SEED = 'anonymous';
export const AVATAR_STYLE_VARIANTS = Object.freeze(['geometric', 'abstract', 'character', 'collection']);

function hash(value) {
  let result = 2166136261;
  for (const byte of new TextEncoder().encode(value)) {
    result ^= byte;
    result = Math.imul(result, 16777619);
  }
  return result >>> 0;
}

export function selectAvatarVariant(seed, _subjectType) {
  // Keep the optional second argument for v1 callers, but never let the page type
  // change the identity of a subject avatar.
  const identity = JSON.stringify(['avatar-style-v1', DEFAULT_AVATAR_NAMESPACE_PREFIX, String(seed)]);
  return AVATAR_STYLE_VARIANTS[hash(identity) % AVATAR_STYLE_VARIANTS.length];
}

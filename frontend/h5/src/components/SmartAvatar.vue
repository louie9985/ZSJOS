<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ANONYMOUS_AVATAR_SEED, avatarConfig, avatarNamespace } from '@/config/avatar'
import { loadGeneratedAvatar } from '@/services/avatar'
import type { AvatarTheme, AvatarVariant } from '@/vendor/avatar-kit/index.mjs'

const props = withDefaults(defineProps<{
  seed?: string | number | null
  src?: string | null
  variant?: AvatarVariant
  size?: number
  shape?: 'circle' | 'rounded'
  theme?: AvatarTheme
  label?: string
  loading?: 'eager' | 'lazy'
}>(), {
  seed: ANONYMOUS_AVATAR_SEED,
  src: '',
  variant: avatarConfig.variant,
  size: 40,
  shape: 'circle',
  theme: avatarConfig.theme,
  label: '',
  loading: 'lazy'
})

const generatedSrc = ref('')
const failedRealSrc = ref('')
let generationVersion = 0

const normalizedSeed = computed(() => {
  if ((typeof props.seed === 'string' || typeof props.seed === 'number') && String(props.seed).trim()) {
    return props.seed
  }
  return ANONYMOUS_AVATAR_SEED
})
const normalizedSrc = computed(() => props.src?.trim() || '')
const realSrcVisible = computed(() => !!normalizedSrc.value && failedRealSrc.value !== normalizedSrc.value)
const displayedSrc = computed(() => realSrcVisible.value ? normalizedSrc.value : generatedSrc.value)
const radius = computed(() => props.shape === 'rounded' ? '25%' : '50%')
const avatarStyle = computed(() => ({
  '--smart-avatar-size': `${props.size}px`,
  '--smart-avatar-radius': radius.value
}))

watch(normalizedSrc, () => {
  failedRealSrc.value = ''
})

watch(
  () => [normalizedSeed.value, props.variant, props.size, props.theme] as const,
  async ([seed, variant, size, theme]) => {
    const version = ++generationVersion
    generatedSrc.value = ''
    try {
      const dataUri = await loadGeneratedAvatar({
        seed,
        namespace: avatarNamespace(),
        variant,
        size,
        theme
      })
      if (version === generationVersion) generatedSrc.value = dataUri
    } catch {
      // 固定尺寸占位保持可用；生成失败不影响页面其余内容。
    }
  },
  { immediate: true }
)

function handleImageError(event: Event) {
  const attemptedSrc = (event.currentTarget as HTMLImageElement | null)?.getAttribute('src') || ''
  if (attemptedSrc && normalizedSrc.value === attemptedSrc) failedRealSrc.value = attemptedSrc
}
</script>

<template>
  <span
    class="smart-avatar"
    :class="`smart-avatar--${shape}`"
    :style="avatarStyle"
    :role="label && !displayedSrc ? 'img' : undefined"
    :aria-label="label && !displayedSrc ? label : undefined"
    :aria-hidden="!label ? 'true' : undefined"
  >
    <img
      v-if="displayedSrc"
      :key="displayedSrc"
      :src="displayedSrc"
      :alt="label"
      :width="size"
      :height="size"
      :loading="loading"
      decoding="async"
      @error="handleImageError"
    />
  </span>
</template>

<style scoped>
.smart-avatar {
  display: inline-flex;
  flex: 0 0 auto;
  width: var(--smart-avatar-size);
  height: var(--smart-avatar-size);
  overflow: hidden;
  align-items: center;
  justify-content: center;
  border-radius: var(--smart-avatar-radius);
  background: color-mix(in srgb, var(--h5-primary-light) 68%, var(--h5-card-bg));
  vertical-align: middle;
}

.smart-avatar img {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}
</style>

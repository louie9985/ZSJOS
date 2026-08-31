<script setup lang="ts">
import { computed } from 'vue'

export interface LiquidSegmentItem {
  key: string
  label: string
}

const props = withDefaults(defineProps<{
  modelValue: string
  items: LiquidSegmentItem[]
  ariaLabel: string
  compact?: boolean
}>(), {
  compact: false
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const activeIndex = computed(() => {
  const index = props.items.findIndex((item) => item.key === props.modelValue)
  return index >= 0 ? index : 0
})

const indicatorStyle = computed(() => ({
  width: `${100 / Math.max(props.items.length, 1)}%`,
  transform: `translateX(${activeIndex.value * 100}%)`
}))

function select(key: string) {
  if (key === props.modelValue) return
  emit('update:modelValue', key)
  emit('change', key)
}
</script>

<template>
  <div
    class="liquid-segmented"
    :class="{ 'liquid-segmented--compact': props.compact }"
    role="tablist"
    :aria-label="props.ariaLabel"
  >
    <span class="liquid-segmented__indicator" :style="indicatorStyle" aria-hidden="true" />
    <button
      v-for="item in props.items"
      :key="item.key"
      type="button"
      role="tab"
      class="liquid-segmented__item"
      :class="{ 'is-active': item.key === props.modelValue }"
      :aria-selected="item.key === props.modelValue"
      @click="select(item.key)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<style scoped>
.liquid-segmented {
  position: relative;
  display: grid;
  grid-template-columns: repeat(v-bind('props.items.length'), minmax(0, 1fr));
  min-width: 0;
  padding: 3px;
  overflow: hidden;
  border: 1px solid var(--h5-border);
  border-radius: 999px;
  background: var(--h5-bg);
  isolation: isolate;
}

.liquid-segmented__indicator {
  position: absolute;
  top: 3px;
  bottom: 3px;
  left: 3px;
  z-index: 0;
  border-radius: 999px;
  background: color-mix(in srgb, var(--h5-primary) 15%, var(--h5-card-bg));
  box-shadow: 0 3px 9px rgba(31, 35, 48, 0.08);
  transform-origin: center;
  transition: transform 0.38s cubic-bezier(0.22, 1.35, 0.36, 1), width 0.2s ease;
  will-change: transform;
}

.liquid-segmented__item {
  position: relative;
  z-index: 1;
  min-width: 0;
  height: 34px;
  padding: 0 8px;
  overflow: hidden;
  border: 0;
  background: transparent;
  color: var(--h5-text-secondary);
  font: inherit;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.22s ease, transform 0.3s cubic-bezier(0.22, 1.35, 0.36, 1);
}

.liquid-segmented__item.is-active {
  color: var(--h5-primary-dark);
  font-weight: 700;
  transform: scale(1.03);
}

.liquid-segmented--compact .liquid-segmented__item {
  height: 30px;
  padding: 0 2px;
}

@media (prefers-reduced-motion: reduce) {
  .liquid-segmented__indicator,
  .liquid-segmented__item {
    transition: none;
  }
}
</style>

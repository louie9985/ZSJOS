<script setup lang="ts">
import { ref } from 'vue'
import type { PopoverPlacement } from 'vant'

const props = withDefaults(defineProps<{
  text: string
  placement?: PopoverPlacement
  ariaLabel?: string
  icon?: string
  iconSize?: number | string
  nowrap?: boolean
  themeTint?: boolean
}>(), {
  placement: 'bottom-start',
  ariaLabel: '查看说明',
  icon: 'question-o',
  iconSize: 16,
  nowrap: false,
  themeTint: false
})

const show = ref(false)
</script>

<template>
  <van-popover
    v-model:show="show"
    :placement="props.placement"
    theme="light"
    teleport="body"
    class="help-popover"
    :class="{ 'help-popover--theme': props.themeTint }"
  >
    <div
      class="help-popover__content"
      :class="{
        'help-popover__content--nowrap': props.nowrap,
        'help-popover__content--theme': props.themeTint
      }"
    >
      {{ props.text }}
    </div>
    <template #reference>
      <button
        type="button"
        class="help-popover__button"
        :aria-label="props.ariaLabel"
        :aria-expanded="show"
      >
        <van-icon :name="props.icon" :size="props.iconSize" />
      </button>
    </template>
  </van-popover>
</template>

<style scoped>
.help-popover__button {
  display: inline-flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  margin: -8px -8px -8px -4px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--h5-text-secondary);
  cursor: pointer;
}

.help-popover__button:active {
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
}

.help-popover__content {
  max-width: 220px;
  padding: 10px 12px;
  color: var(--h5-text-primary);
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-line;
}

.help-popover__content--nowrap {
  max-width: none;
  white-space: nowrap;
}

.help-popover__content--theme {
  max-width: min(280px, calc(100vw - 32px));
}

:global(.help-popover.van-popover) {
  border: 1px solid var(--h5-glass-border);
  border-radius: 12px;
  background: var(--h5-glass-surface-strong);
  box-shadow: var(--h5-glass-shadow-floating);
  backdrop-filter: saturate(160%) blur(var(--h5-glass-blur-strong));
  -webkit-backdrop-filter: saturate(160%) blur(var(--h5-glass-blur-strong));
}

:global(.help-popover.van-popover--light .van-popover__arrow) {
  color: var(--h5-glass-surface-strong);
}

:global(.help-popover--theme.van-popover) {
  --help-popover-theme-background: color-mix(
    in srgb,
    var(--h5-primary) 12%,
    var(--h5-glass-surface-strong)
  );
  background: var(--help-popover-theme-background);
}

:global(.help-popover--theme.van-popover--light .van-popover__arrow) {
  color: var(--help-popover-theme-background);
}
</style>

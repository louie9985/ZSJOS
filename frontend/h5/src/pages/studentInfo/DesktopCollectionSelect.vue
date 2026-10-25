<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  id: string
  label: string
  modelValue: string
  options: { value: string; label: string }[]
  disabled: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()
const open = ref(false)
const search = ref('')
const trigger = ref<HTMLButtonElement>()
const panel = ref<HTMLElement>()
const searchInput = ref<HTMLInputElement>()
const list = ref<HTMLElement>()
const width = ref(240)
const placement = ref<'bottom-start' | 'top-start'>('bottom-start')
const active = ref(-1)
const longList = computed(() => props.options.length > 10)
const display = computed(() => props.options.find(option => option.value === props.modelValue)?.label || '')
const filtered = computed(() => props.options.filter(option => option.label.includes(search.value.trim())))
const listId = computed(() => `${props.id}-desktop-options`)

function close(restoreFocus = false) {
  open.value = false
  if (restoreFocus) trigger.value?.focus({ preventScroll: true })
}

function choose(value: string) {
  if (props.disabled) return
  emit('update:modelValue', value)
  close(true)
}

function scrollActive() {
  // Scroll only the options; scrolling an ancestor can move the form away from its field.
  void nextTick(() => {
    const container = list.value
    const option = container?.querySelector<HTMLElement>(`[data-index="${active.value}"]`)
    if (!container || !option) return
    const bounds = container.getBoundingClientRect()
    const item = option.getBoundingClientRect()
    if (item.top < bounds.top) container.scrollTop += item.top - bounds.top
    else if (item.bottom > bounds.bottom) container.scrollTop += item.bottom - bounds.bottom
  })
}

function keydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    close(true)
  } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!filtered.value.length) return
    active.value = active.value < 0 ? (event.key === 'ArrowDown' ? 0 : filtered.value.length - 1)
      : (active.value + (event.key === 'ArrowDown' ? 1 : -1) + filtered.value.length) % filtered.value.length
    scrollActive()
  } else if (event.key === 'Enter' && filtered.value[active.value]) {
    event.preventDefault()
    choose(filtered.value[active.value].value)
  } else if (event.key === 'Tab') {
    // The panel is teleported; resume tab order from the original field.
    close(true)
  }
}

function outside(event: Event) {
  const target = event.target as Node
  if (!panel.value?.contains(target) && !trigger.value?.contains(target)) close()
}

watch(open, async value => {
  if (!value) return
  if (props.disabled) { close(); return }
  search.value = ''
  const bounds = trigger.value?.getBoundingClientRect()
  width.value = bounds?.width || 240
  if (bounds) {
    const below = window.innerHeight - bounds.bottom
    const expectedHeight = Math.min(props.options.length * 36, 280, window.innerHeight * 0.45) + (longList.value ? 54 : 12)
    placement.value = below < expectedHeight + 4 && bounds.top > below ? 'top-start' : 'bottom-start'
  }
  active.value = props.options.findIndex(option => option.value === props.modelValue)
  await nextTick()
  if (!open.value) return
  ;(longList.value ? searchInput.value : list.value)?.focus({ preventScroll: true })
  scrollActive()
})
watch(search, () => { active.value = -1 }, { flush: 'sync' })
watch(() => props.disabled, value => { if (value) close() })
onMounted(() => {
  document.addEventListener('pointerdown', outside)
  document.addEventListener('focusin', outside)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', outside)
  document.removeEventListener('focusin', outside)
})
</script>

<template>
  <div class="desktop-select norem">
    <van-popover v-model:show="open" :placement="placement" :show-arrow="false" :offset="[0, 4]"
      :overlay="false" class="desktop-select-popover norem" :style="{ width: width + 'px' }">
      <template #reference>
        <button :id="id" ref="trigger" type="button" class="desktop-select-trigger norem"
          :class="{ placeholder: !display }" :disabled="disabled" :aria-label="label"
          aria-haspopup="dialog" :aria-expanded="open" :aria-controls="id + '-desktop-panel'"
          @keydown.down.prevent="open = true" @keydown.up.prevent="open = true" @keydown.esc="close(true)">
          <span>{{ display || '请选择' }}</span><van-icon name="arrow-down" />
        </button>
      </template>
      <div :id="id + '-desktop-panel'" ref="panel" role="dialog" :aria-label="'选择' + label"
        class="desktop-select-panel norem" @keydown="keydown">
        <input v-if="longList" ref="searchInput" v-model="search" type="search" class="desktop-select-search norem"
          :aria-label="'搜索' + label" :placeholder="'搜索' + label" :aria-controls="listId"
          :aria-activedescendant="active >= 0 ? listId + '-' + active : undefined" autocomplete="off" />
        <div :id="listId" ref="list" role="listbox" :aria-label="label" tabindex="0"
          :aria-activedescendant="active >= 0 ? listId + '-' + active : undefined" class="desktop-select-options norem">
          <button v-for="(option, index) in filtered" :id="listId + '-' + index" :key="option.value"
            type="button" role="option" tabindex="-1" :aria-label="option.label" :aria-selected="modelValue === option.value" :data-index="index"
            class="desktop-select-option norem" :class="{ active: active === index }" @click="choose(option.value)">
            <span>{{ option.label }}</span><van-icon v-if="modelValue === option.value" name="success" />
          </button>
          <p v-if="!filtered.length" class="desktop-select-empty norem">{{ options.length ? '没有匹配选项' : '暂无可选项' }}</p>
        </div>
      </div>
    </van-popover>
  </div>
</template>

<style scoped>
.desktop-select.norem { flex: 1; min-width: 0; }
.desktop-select.norem :deep(.van-popover__wrapper) { display: flex; width: 100%; }
.desktop-select-trigger.norem { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; min-height: 44px; padding: 8px 0; border: 0; border-bottom: 1px solid var(--h5-border); background: transparent; color: var(--h5-text-primary); font: inherit; text-align: left; cursor: pointer; }
.desktop-select-trigger.norem span { overflow-wrap: anywhere; }
.desktop-select-trigger.placeholder.norem { color: var(--h5-text-placeholder); }
.desktop-select-trigger.norem:disabled { color: var(--h5-text-secondary); cursor: default; }
.desktop-select-popover.norem { max-width: calc(100vw - 32px); border: 1px solid var(--h5-border); border-radius: 8px; box-shadow: 0 8px 24px #0000001f; }
.desktop-select-panel.norem { padding: 6px; color: var(--h5-text-primary); background: var(--h5-content-surface-strong-fallback); font-size: 14px; }
.desktop-select-search.norem { box-sizing: border-box; width: 100%; height: 36px; padding: 6px 10px; margin-bottom: 6px; border: 1px solid var(--h5-border); border-radius: 6px; background: transparent; color: inherit; font: inherit; }
.desktop-select-options.norem { max-height: min(280px, 45vh); overflow-y: auto; overscroll-behavior: contain; }
.desktop-select-option.norem { display: flex; align-items: center; justify-content: space-between; gap: 8px; width: 100%; min-height: 36px; padding: 8px 10px; border: 0; border-radius: 4px; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; overflow-wrap: anywhere; }
.desktop-select-option.norem:hover, .desktop-select-option.active.norem { background: var(--h5-primary-opacity); }
.desktop-select-option.norem[aria-selected=true] { color: var(--h5-primary); }
.desktop-select-trigger.norem:focus-visible, .desktop-select-search.norem:focus-visible, .desktop-select-options.norem:focus-visible { outline: 2px solid var(--h5-primary); outline-offset: 2px; }
.desktop-select-empty.norem { margin: 0; padding: 20px 10px; text-align: center; color: var(--h5-text-secondary); }
</style>

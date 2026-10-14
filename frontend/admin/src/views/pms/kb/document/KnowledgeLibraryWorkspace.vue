<template>
  <div
    ref="workspaceRef"
    class="knowledge-library-workspace"
    :class="{ 'is-stacked': stacked, 'is-resizing': dragging }"
    :style="{ '--knowledge-sidebar-width': `${sidebarWidth}px` }"
  >
    <div class="knowledge-workspace-sidebar"><slot name="sidebar"></slot></div>
    <div v-if="!stacked" class="knowledge-workspace-divider">
      <div
        class="knowledge-workspace-resizer"
        role="separator"
        tabindex="0"
        aria-label="调整知识库目录宽度"
        aria-orientation="vertical"
        :aria-valuemin="MIN_WIDTH"
        :aria-valuemax="maxWidth"
        :aria-valuenow="sidebarWidth"
        title="拖动调整目录宽度，双击恢复默认"
        @pointerdown="startDrag"
        @pointermove="moveDrag"
        @pointerup="finishDrag(true)"
        @pointercancel="finishDrag(false)"
        @lostpointercapture="finishDrag(false)"
        @dblclick="resetWidth"
        @keydown="adjustWithKeyboard"
      ></div>
    </div>
    <div class="knowledge-workspace-content"><slot></slot></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'
import { useEventListener, useMediaQuery, useResizeObserver } from '@vueuse/core'

defineOptions({ name: 'PmsKnowledgeLibraryWorkspace' })

const DEFAULT_WIDTH = 320
const MIN_WIDTH = 240
const MAX_WIDTH = 560
const CONTENT_MIN_WIDTH = 480
const GAP = 16
const STORAGE_KEY = 'pms:knowledge-library:sidebar-width'
const workspaceRef = ref<HTMLElement>()
const workspaceWidth = ref(0)
const narrowViewport = useMediaQuery('(max-width: 900px)')
const preferredWidth = ref(readWidth())
const dragging = ref(false)
const stacked = computed(
  () => narrowViewport.value || workspaceWidth.value < MIN_WIDTH + CONTENT_MIN_WIDTH + GAP
)
const maxWidth = computed(() =>
  Math.max(
    MIN_WIDTH,
    Math.min(MAX_WIDTH, Math.floor(workspaceWidth.value - CONTENT_MIN_WIDTH - GAP))
  )
)
// Temporary container constraints must not overwrite the user's saved desktop preference.
const sidebarWidth = computed(() =>
  Math.min(maxWidth.value, Math.max(MIN_WIDTH, preferredWidth.value))
)

useResizeObserver(workspaceRef, ([entry]) => {
  workspaceWidth.value = entry.contentRect.width
})

function readWidth() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    const value = stored === null ? NaN : Number(stored)
    return Number.isFinite(value) && value >= MIN_WIDTH && value <= MAX_WIDTH
      ? value
      : DEFAULT_WIDTH
  } catch {
    return DEFAULT_WIDTH
  }
}

function saveWidth() {
  try {
    localStorage.setItem(STORAGE_KEY, String(preferredWidth.value))
  } catch {
    // Browser storage may be disabled; resizing remains available for this visit.
  }
}

let pointerId: number | undefined
let captureTarget: HTMLElement | undefined
let startX = 0
let startWidth = DEFAULT_WIDTH
let originalPreference = DEFAULT_WIDTH
let previousUserSelect = ''
let previousUserSelectPriority = ''
let moved = false

function startDrag(event: PointerEvent) {
  if (event.button !== 0 || !event.isPrimary || stacked.value || dragging.value) return
  event.preventDefault()
  captureTarget = event.currentTarget as HTMLElement
  captureTarget.setPointerCapture(event.pointerId)
  pointerId = event.pointerId
  startX = event.clientX
  startWidth = sidebarWidth.value
  originalPreference = preferredWidth.value
  moved = false
  previousUserSelect = document.body.style.getPropertyValue('user-select')
  previousUserSelectPriority = document.body.style.getPropertyPriority('user-select')
  document.body.style.setProperty('user-select', 'none')
  dragging.value = true
  captureTarget.focus()
}

function moveDrag(event: PointerEvent) {
  if (!dragging.value || event.pointerId !== pointerId) return
  const delta = event.clientX - startX
  if (delta !== 0) moved = true
  preferredWidth.value = Math.round(
    Math.min(maxWidth.value, Math.max(MIN_WIDTH, startWidth + delta))
  )
}

function finishDrag(save: boolean) {
  if (!dragging.value) return
  dragging.value = false
  document.body.style.setProperty('user-select', previousUserSelect, previousUserSelectPriority)
  if (save && moved) saveWidth()
  else preferredWidth.value = originalPreference
  const target = captureTarget
  const capturedPointer = pointerId
  pointerId = undefined
  captureTarget = undefined
  if (capturedPointer !== undefined && target?.hasPointerCapture(capturedPointer)) {
    target.releasePointerCapture(capturedPointer)
  }
}

function resetWidth() {
  finishDrag(false)
  preferredWidth.value = DEFAULT_WIDTH
  saveWidth()
}

function adjustWithKeyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home'].includes(event.key)) return
  event.preventDefault()
  if (event.key === 'Home') return resetWidth()
  finishDrag(false)
  preferredWidth.value = Math.min(
    maxWidth.value,
    Math.max(MIN_WIDTH, sidebarWidth.value + (event.key === 'ArrowRight' ? 16 : -16))
  )
  saveWidth()
}

watch(stacked, (value) => {
  if (value) finishDrag(false)
})
useEventListener(window, 'blur', () => finishDrag(false))
onBeforeUnmount(() => finishDrag(false))
onDeactivated(() => finishDrag(false))
</script>

<style scoped lang="scss">
.knowledge-library-workspace {
  display: grid;
  grid-template-columns: var(--knowledge-sidebar-width) 16px minmax(0, 1fr);
  min-height: calc(100vh - 120px);

  &.is-stacked {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }
}

.knowledge-workspace-sidebar,
.knowledge-workspace-content {
  min-width: 0;
}

.knowledge-workspace-divider {
  display: flex;
  justify-content: center;
}

.knowledge-workspace-resizer {
  position: relative;
  width: 8px;
  cursor: col-resize;
  touch-action: none;

  &::after {
    position: absolute;
    inset: 0 3px;
    background: transparent;
    content: '';
  }

  &:hover::after,
  &:focus-visible::after,
  .is-resizing &::after {
    background: var(--el-color-primary);
  }
}
</style>

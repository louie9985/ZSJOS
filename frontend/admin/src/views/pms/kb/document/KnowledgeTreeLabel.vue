<template>
  <el-tooltip
    :visible="overflow && (hovered || focused)"
    :content="label"
    placement="right"
    :fallback-placements="['left', 'bottom', 'top']"
    :popper-options="{
      modifiers: [
        { name: 'flip', options: { rootBoundary: 'viewport', padding: 16 } },
        {
          name: 'preventOverflow',
          options: { rootBoundary: 'viewport', padding: 16, altAxis: true }
        }
      ]
    }"
    popper-class="pms-knowledge-tree-name-tooltip"
    :popper-style="{
      maxWidth: 'min(480px, calc(100vw - 32px))',
      overflowWrap: 'anywhere',
      boxSizing: 'border-box',
      pointerEvents: 'none'
    }"
    :enterable="false"
    :show-after="300"
  >
    <span
      ref="labelRef"
      class="knowledge-tree-label"
      @mouseenter="showHover"
      @mouseleave="hideHover"
      @pointerdown="hideHover"
      >{{ label }}</span
    >
  </el-tooltip>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useEventListener, useResizeObserver } from '@vueuse/core'
import { ElTooltip } from 'element-plus'

defineOptions({ name: 'PmsKnowledgeTreeLabel' })
const props = defineProps<{ label: string }>()
const labelRef = ref<HTMLElement>()
const treeNode = ref<HTMLElement>()
const overflow = ref(false)
const hovered = ref(false)
const focused = ref(false)
let hoverTimer: ReturnType<typeof setTimeout> | undefined

function measure() {
  const label = labelRef.value
  overflow.value = Boolean(label && label.clientWidth > 0 && label.scrollWidth > label.clientWidth)
}

function hideHover() {
  clearTimeout(hoverTimer)
  hovered.value = false
}

function showHover() {
  hideHover()
  measure()
  // Controlled visibility also supports focus on Element Plus's containing treeitem.
  hoverTimer = setTimeout(() => {
    hovered.value = true
  }, 300)
}

useResizeObserver(labelRef, measure)
watch(
  () => props.label,
  async () => {
    await nextTick()
    measure()
  }
)
onMounted(() => {
  treeNode.value = labelRef.value?.closest<HTMLElement>('[role="treeitem"]') ?? undefined
  measure()
})
useEventListener(
  treeNode,
  'focus',
  (event: FocusEvent) => {
    focused.value = event.target === treeNode.value
    measure()
  },
  true
)
useEventListener(
  treeNode,
  'blur',
  () => {
    focused.value = false
  },
  true
)
onBeforeUnmount(hideHover)
</script>

<style scoped>
.knowledge-tree-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

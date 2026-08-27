<template>
  <div class="unarranged-tree">
    <div
      v-for="node in nodes"
      :key="node.key"
      class="unarranged-item"
      draggable="true"
      @dragstart.stop="startDrag($event, node.key)"
    >
      <div class="unarranged-row">
        <Icon class="unarranged-handle" icon="ic:round-drag-indicator" />
        <Icon :icon="node.icon || (node.type === 'GROUP' ? 'ep:folder' : 'ep:document')" />
        <div class="unarranged-main">
          <div class="unarranged-name">{{ node.name }}</div>
          <div class="unarranged-path">
            {{ node.type === 'PAGE' ? candidateMap[node.sourceMenuId!]?.path || `菜单 #${node.sourceMenuId}` : '目录及其全部内容' }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { Icon } from '@/components/Icon'
import type { WorkbenchLayoutNode } from '@/api/system/workbenchLayout'

defineOptions({ name: 'WorkbenchUnarrangedTree' })

defineProps<{
  nodes: WorkbenchLayoutNode[]
  candidateMap: Record<number, { path: string }>
}>()

const emit = defineEmits<{
  (e: 'drag-start', key: string): void
}>()

const startDrag = (event: DragEvent, key: string) => {
  event.dataTransfer?.setData('text/workbench-layout-key', key)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
  emit('drag-start', key)
}
</script>

<style scoped>
.unarranged-tree {
  display: grid;
  gap: 8px;
}

.unarranged-row {
  display: flex;
  min-height: 46px;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
}

.unarranged-handle {
  flex: none;
  cursor: grab;
  color: var(--el-text-color-secondary);
}

.unarranged-main {
  min-width: 0;
  flex: 1;
}

.unarranged-name,
.unarranged-path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unarranged-name {
  font-size: 13px;
}

.unarranged-path {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

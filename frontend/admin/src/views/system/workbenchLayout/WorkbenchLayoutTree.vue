<template>
  <VueDraggable
    :list="nodes"
    :animation="180"
    :data-depth="depth"
    :data-unclassified-list="unclassifiedList"
    :disabled="!editable"
    :group="{ name: 'workbench-layout' }"
    :move="allowMove"
    class="layout-tree-list"
    handle=".layout-drag-handle"
    item-key="key"
    @change="emit('change')"
    @start="startDrag"
  >
    <template #item="{ element }">
      <div v-show="!element.hidden" class="layout-tree-item" :data-node-key="element.key">
        <div class="layout-node-row" :class="{ 'is-hidden': element.hidden }">
          <el-tooltip content="拖拽调整层级和顺序">
            <Icon class="layout-drag-handle" icon="ic:round-drag-indicator" />
          </el-tooltip>
          <Icon :icon="element.icon || (element.type === 'GROUP' ? 'ep:folder' : 'ep:document')" />
          <div class="layout-node-main">
            <div class="layout-node-name">{{ element.name }}</div>
            <div v-if="element.type === 'PAGE'" class="layout-node-path">
              {{ candidateMap[element.sourceMenuId]?.path || `菜单 #${element.sourceMenuId}` }}
            </div>
            <div v-else class="layout-node-path">分组 · 第 {{ depth + 1 }} 级</div>
          </div>
          <el-tag v-if="element.key === UNCLASSIFIED_KEY" size="small" type="info">固定</el-tag>
          <el-switch
            v-model="element.hidden"
            active-text="隐藏"
            :disabled="!editable || (scopeType === 'ROLE' && isGlobalGroup(element))"
            inactive-text="显示"
            inline-prompt
            @change="emit('change')"
          />
          <template v-if="element.type === 'GROUP'">
            <el-popover
              v-if="editable && canEditGroup(element)"
              placement="bottom-end"
              trigger="click"
              :width="300"
            >
              <template #reference>
                <el-button circle text title="编辑分组">
                  <Icon icon="ep:edit" />
                </el-button>
              </template>
              <el-form label-width="48px">
                <el-form-item label="名称">
                  <el-input v-model="element.name" maxlength="50" @change="emit('change')" />
                </el-form-item>
                <el-form-item label="图标">
                  <IconSelect v-model="element.icon" clearable @change="emit('change')" />
                </el-form-item>
              </el-form>
            </el-popover>
            <el-button
              v-if="editable && element.key !== UNCLASSIFIED_KEY && depth < 2"
              circle
              text
              title="新增下级分组"
              @click="emit('add-child', element)"
            >
              <Icon icon="ep:folder-add" />
            </el-button>
            <el-button
              v-if="editable && canDeleteGroup(element)"
              circle
              text
              title="删除分组"
              type="danger"
              @click="emit('delete-group', element.key)"
            >
              <Icon icon="ep:delete" />
            </el-button>
          </template>
        </div>
        <WorkbenchLayoutTree
          v-if="element.type === 'GROUP'"
          :candidate-map="candidateMap"
          :depth="depth + 1"
          :editable="editable"
          :nodes="element.children"
          :scope-type="scopeType"
          :unclassified-list="element.key === UNCLASSIFIED_KEY"
          @add-child="emit('add-child', $event)"
          @change="emit('change')"
          @delete-group="emit('delete-group', $event)"
          @external-drag-start="emit('external-drag-start', $event)"
        />
      </div>
    </template>
  </VueDraggable>
</template>

<script lang="ts" setup>
import VueDraggable from 'vuedraggable'
import { Icon, IconSelect } from '@/components/Icon'
import type { WorkbenchLayoutNode, WorkbenchLayoutScopeType } from '@/api/system/workbenchLayout'

defineOptions({ name: 'WorkbenchLayoutTree' })

const UNCLASSIFIED_KEY = '__unclassified__'

const props = withDefaults(
  defineProps<{
    nodes: WorkbenchLayoutNode[]
    depth: number
    scopeType: WorkbenchLayoutScopeType
    editable: boolean
    unclassifiedList?: boolean
    candidateMap: Record<number, { path: string }>
  }>(),
  { unclassifiedList: false }
)

const emit = defineEmits<{
  (e: 'change'): void
  (e: 'add-child', node: WorkbenchLayoutNode): void
  (e: 'delete-group', key: string): void
  (e: 'external-drag-start', key: string): void
}>()

const canEditGroup = (node: WorkbenchLayoutNode) =>
  node.key !== UNCLASSIFIED_KEY &&
  (props.scopeType === 'GLOBAL' || node.key.startsWith('role-group-'))

const canDeleteGroup = (node: WorkbenchLayoutNode) =>
  node.key !== UNCLASSIFIED_KEY &&
  (props.scopeType === 'GLOBAL' || node.key.startsWith('role-group-'))

const isGlobalGroup = (node: WorkbenchLayoutNode) =>
  node.type === 'GROUP' && !node.key.startsWith('role-group-')

const groupLevels = (node: WorkbenchLayoutNode): number => {
  if (node.type !== 'GROUP') return 0
  const childLevels = node.children.map(groupLevels)
  return 1 + (childLevels.length ? Math.max(...childLevels) : 0)
}

const startDrag = (event: { item?: HTMLElement }) => {
  const key = event.item?.dataset.nodeKey
  if (props.scopeType === 'ROLE' && key && key !== UNCLASSIFIED_KEY) {
    emit('external-drag-start', key)
  }
}

const allowMove = (event: any) => {
  if (!props.editable) return false
  const node = event.draggedContext.element as WorkbenchLayoutNode
  if (props.scopeType === 'ROLE' && isGlobalGroup(node)) return false
  const targetDepth = Number(event.to?.dataset?.depth ?? props.depth)
  const targetIsUnclassified = event.to?.dataset?.unclassifiedList === 'true'
  if (node.key === UNCLASSIFIED_KEY && targetDepth !== 0) return false
  if (targetIsUnclassified && node.type === 'GROUP') return false
  return node.type !== 'GROUP' || targetDepth + groupLevels(node) <= 3
}
</script>

<style scoped>
.layout-tree-list {
  min-height: 14px;
}

.layout-tree-list:empty {
  min-height: 42px;
  border: 1px dashed var(--el-border-color);
}

.layout-tree-item {
  margin-top: 8px;
}

.layout-node-row {
  display: flex;
  min-height: 48px;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
}

.layout-node-row.is-hidden {
  opacity: 0.62;
}

.layout-drag-handle {
  flex: none;
  cursor: grab;
  color: var(--el-text-color-secondary);
}

.layout-node-main {
  min-width: 0;
  flex: 1;
}

.layout-node-name,
.layout-node-path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.layout-node-name {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.layout-node-path {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.layout-tree-item > .layout-tree-list {
  margin-left: 26px;
}
</style>

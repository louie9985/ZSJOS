<template>
  <div class="px-4px pb-16px pl-16px pt-8px text-14px">
    <!-- 主页入口 -->
    <div
      class="h-40px flex cursor-pointer items-center gap-6px rounded-[var(--el-border-radius-base)] px-8px hover:bg-[var(--el-fill-color-light)]"
      :class="{
        'bg-[var(--el-fill-color-light)] text-[var(--el-color-primary)]': activeView === 'home'
      }"
      @click="emit('home')"
    >
      <Icon icon="ep:home-filled" />
      <span>主页</span>
    </div>

    <!-- 知识库目录 -->
    <div class="h-40px flex items-center justify-between px-8px">
      <div class="flex min-w-0 items-center gap-6px font-600">
        <span>目录</span>
      </div>
      <div>
        <el-dropdown
          v-if="canCreateFolder || canCreateDocument"
          v-hasPermi="['pms:kb:library:update']"
          trigger="click"
          @command="emit('create', $event)"
        >
          <el-button link><Icon icon="ep:plus" /></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="canCreateDocument" command="document">
                创建文档
              </el-dropdown-item>
              <el-dropdown-item v-if="canCreateFolder" command="folder">
                创建文件夹
              </el-dropdown-item>
              <el-dropdown-item v-if="canCreateDocument" command="upload">
                上传文件
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    <el-tree
      v-if="treeData.length"
      ref="treeRef"
      :current-node-key="currentNodeKey"
      :data="treeData"
      :default-expanded-keys="defaultExpandedNodeKeys"
      class="knowledge-sidebar-tree"
      highlight-current
      node-key="key"
      @node-click="emit('node-click', $event)"
    >
      <template #default="{ data }">
        <div class="knowledge-tree-row group flex min-w-0 flex-1 items-center gap-6px">
          <Icon class="shrink-0" :icon="getKnowledgeTreeNodeIcon(data)" />
          <KnowledgeTreeLabel :label="data.label" />
          <el-dropdown
            v-if="
              canEditKnowledgeContent(data.currentUserLevel) ||
              canDeleteKnowledgeContent(data.currentUserLevel)
            "
            trigger="click"
            class="knowledge-tree-actions shrink-0"
            @command="emit('node-action', data, $event)"
          >
            <el-button
              class="knowledge-tree-action !h-24px !w-24px"
              aria-label="内容操作"
              link
              @click.stop
              ><Icon icon="ep:more-filled"
            /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-if="data.kind === 'folder' && canEditKnowledgeContent(data.currentUserLevel)"
                  v-hasPermi="['pms:kb:library:update']"
                  command="create-document"
                >
                  新建文档
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="data.kind === 'folder' && canEditKnowledgeContent(data.currentUserLevel)"
                  v-hasPermi="['pms:kb:library:update']"
                  command="create-folder"
                >
                  新建文件夹
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="data.kind === 'folder' && canEditKnowledgeContent(data.currentUserLevel)"
                  v-hasPermi="['pms:kb:library:update']"
                  command="upload"
                  >上传文件</el-dropdown-item
                >
                <el-dropdown-item
                  v-if="canEditKnowledgeContent(data.currentUserLevel)"
                  v-hasPermi="['pms:kb:library:update']"
                  command="rename"
                >
                  重命名
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="canManageKnowledgeContent(data.currentUserLevel)"
                  v-hasPermi="['pms:kb:library:update']"
                  command="move"
                >
                  移动
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="canDeleteKnowledgeContent(data.currentUserLevel)"
                  v-hasPermi="['pms:kb:library:delete']"
                  command="delete"
                  divided
                >
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
    </el-tree>
    <el-empty v-else :image-size="72" description="暂无目录或文档" />

    <!-- 最近删除入口 -->
    <div v-if="writeStatus">
      <div
        v-hasPermi="['pms:kb:library:delete']"
        class="h-40px flex cursor-pointer items-center gap-6px rounded-[var(--el-border-radius-base)] px-8px hover:bg-[var(--el-fill-color-light)]"
        :class="{
          'bg-[var(--el-fill-color-light)] text-[var(--el-color-primary)]': activeView === 'recycle'
        }"
        @click="emit('recycle')"
      >
        <Icon icon="ep:delete" />
        最近删除
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, ref, watch } from 'vue'
import type { ElTree } from 'element-plus'
import KnowledgeTreeLabel from './KnowledgeTreeLabel.vue'
import {
  getKnowledgeTreeNodeIcon,
  type KnowledgeContentView,
  type KnowledgeTreeNode
} from './types'
import {
  canDeleteKnowledgeContent,
  canEditKnowledgeContent,
  canManageKnowledgeContent
} from '@/views/pms/kb/utils/permission'

defineOptions({ name: 'PmsKnowledgeLibrarySidebar' })

const props = defineProps<{
  treeData: KnowledgeTreeNode[]
  currentNodeKey?: string
  activeView: KnowledgeContentView
  writeStatus: boolean
  canCreateFolder: boolean
  canCreateDocument: boolean
}>() // 组件参数
const emit = defineEmits(['home', 'create', 'node-click', 'node-action', 'recycle']) // 组件事件
const treeRef = ref<InstanceType<typeof ElTree>>() // 目录树 Ref

const defaultExpandedNodeKeys = computed(() => (props.currentNodeKey ? [props.currentNodeKey] : [])) // 默认展开当前内容的目录链路

/** 同步目录树的当前节点，并自动展开其全部父级目录 */
async function setCurrentNode() {
  await nextTick()
  treeRef.value?.setCurrentKey(props.currentNodeKey, true)
}

watch([() => props.currentNodeKey, () => props.treeData], () => {
  setCurrentNode()
})
</script>

<style lang="scss" scoped>
.knowledge-sidebar-tree {
  padding-right: 8px;
  background-color: transparent;
}

.knowledge-tree-row {
  overflow: hidden;
}

.knowledge-tree-actions {
  width: 24px;
}

.knowledge-tree-action {
  opacity: 0;
}

.knowledge-tree-row:hover .knowledge-tree-action,
.knowledge-tree-row:focus-within .knowledge-tree-action,
:deep(.el-tree-node:focus > .el-tree-node__content .knowledge-tree-action) {
  opacity: 1;
}

:deep(.el-tree-node__content) {
  height: 40px;
  padding-right: 4px;
  border-radius: var(--el-border-radius-base);
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  color: var(--el-color-primary);
  background-color: var(--el-fill-color-light);
}
</style>

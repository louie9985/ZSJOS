<template>
  <ContentWrap>
    <div
      ref="splitRef"
      class="category-split"
      :style="{ '--category-left-width': `${splitPercent}%` }"
    >
      <!-- 左侧分类树 -->
      <section class="category-pane category-tree-pane">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <span class="font-medium">资产分类</span>
          <div class="flex gap-2">
            <el-button v-hasPermi="['eam:category:import']" plain size="small" @click="openImport">
              <Icon icon="ep:upload" class="mr-5px" /> 导入配置
            </el-button>
            <el-button
              v-hasPermi="['eam:category:create']"
              type="primary"
              plain
              size="small"
              @click="openForm('create')"
            >
              <Icon icon="ep:plus" class="mr-5px" /> 新增
            </el-button>
          </div>
        </div>
        <div class="mb-3 grid gap-2 sm:grid-cols-2">
          <el-input v-model="keyword" clearable placeholder="搜索分类名称或编码">
            <template #prefix><Icon icon="ep:search" /></template>
          </el-input>
          <el-select v-model="rootFilter" clearable placeholder="全部顶级分类">
            <el-option
              v-for="item in rootOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </div>
        <el-tree
          ref="categoryTreeRef"
          v-loading="loading"
          :data="filteredTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <div class="category-tree-node">
              <div class="category-node-main">
                <el-tooltip :content="data.name" placement="top" :show-after="500">
                  <span class="category-node-name">{{ data.name }}</span>
                </el-tooltip>
                <el-tag size="small" class="ml-1">{{ data.code }}</el-tag>
                <el-tag v-if="data.status === 1" size="small" type="info" class="ml-1">
                  已关闭
                </el-tag>
                <el-tag size="small" type="info" class="ml-1">
                  {{ data.managementMode === 2 ? '批量' : '单件' }} / {{ data.unit || '个' }}
                </el-tag>
                <el-tag v-if="data.effectiveDeliveryMode" size="small" type="success" class="ml-1">
                  {{ data.effectiveDeliveryMode === 1 ? '实物' : '数字' }} /
                  {{ data.effectiveCustodyMode === 1 ? '消耗' : '归还' }}
                </el-tag>
                <el-tag v-else size="small" type="danger" class="ml-1">待确认采购属性</el-tag>
              </div>
              <span class="category-node-actions">
                <el-button
                  v-hasPermi="['eam:category:create']"
                  link
                  type="primary"
                  size="small"
                  @click.stop="openForm('create', undefined, data.id)"
                >
                  子类
                </el-button>
                <el-button
                  v-hasPermi="['eam:category:update']"
                  link
                  type="primary"
                  size="small"
                  @click.stop="openForm('update', data.id)"
                >
                  编辑
                </el-button>
                <el-button
                  v-hasPermi="['eam:category:delete']"
                  link
                  type="danger"
                  size="small"
                  @click.stop="handleDelete(data.id)"
                >
                  删除
                </el-button>
              </span>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!loading && filteredTree.length === 0" description="没有匹配的资产分类" />
      </section>

      <div
        class="category-splitter"
        role="separator"
        aria-label="调整资产分类与字段配置宽度"
        aria-orientation="vertical"
        :aria-valuenow="Math.round(splitPercent)"
        :aria-valuemin="Math.ceil(splitBounds.min)"
        :aria-valuemax="Math.floor(splitBounds.max)"
        tabindex="0"
        @pointerdown="startResize"
        @keydown="handleSplitterKeydown"
      >
        <span class="category-splitter-handle"></span>
      </div>

      <!-- 右侧：所选分类的自定义字段 -->
      <section class="category-pane category-field-pane">
        <FieldConfig :category-id="currentCategoryId" :category-name="currentCategoryName" />
      </section>
    </div>
  </ContentWrap>

  <CategoryForm ref="formRef" :category-list="categoryList" @success="handleFormSuccess" />
  <CategoryImportForm ref="importRef" @success="handleImportSuccess" />
</template>

<script setup lang="ts">
import { handleTree } from '@/utils/tree'
import * as CategoryApi from '@/api/eam/category'
import CategoryForm from './CategoryForm.vue'
import CategoryImportForm from './CategoryImportForm.vue'
import FieldConfig from './FieldConfig.vue'

defineOptions({ name: 'EamCategory' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(false)
const categoryList = ref<CategoryApi.CategoryVO[]>([])
const categoryTree = ref<any[]>([])
const keyword = ref('')
const rootFilter = ref<number>()
const currentCategoryId = ref<number | undefined>()
const currentCategoryName = ref<string>('')
const categoryTreeRef = ref()
const splitRef = ref<HTMLElement>()
const SPLIT_STORAGE_KEY = 'eam-category-split-percent'
const DEFAULT_SPLIT_PERCENT = 42
const splitPercent = ref(DEFAULT_SPLIT_PERCENT)
const splitBounds = reactive({ min: 30, max: 60 })
let resizingPointerId: number | undefined

const rootOptions = computed(() =>
  categoryList.value.filter(
    (item): item is CategoryApi.CategoryVO & { id: number } =>
      item.parentId === 0 && item.id != null
  )
)
const filteredTree = computed(() => {
  const source = rootFilter.value
    ? categoryTree.value.filter((item) => item.id === rootFilter.value)
    : categoryTree.value
  const search = keyword.value.trim().toLowerCase()
  if (!search) return source
  const filterNodes = (nodes: any[]): any[] =>
    nodes.flatMap((node) => {
      const children = filterNodes(node.children || [])
      const matched = `${node.name} ${node.code}`.toLowerCase().includes(search)
      return matched || children.length ? [{ ...node, children }] : []
    })
  return filterNodes(source)
})

const getList = async () => {
  loading.value = true
  try {
    categoryList.value = await CategoryApi.getCategoryList()
    categoryTree.value = handleTree(categoryList.value as any, 'id', 'parentId')
  } finally {
    loading.value = false
  }
}

const findCategoryRootId = (id: number) => {
  const byId = new Map(categoryList.value.map((item) => [item.id, item]))
  let current = byId.get(id)
  const visited = new Set<number>()
  while (current?.id != null && current.parentId !== 0 && !visited.has(current.id)) {
    visited.add(current.id)
    current = byId.get(current.parentId)
  }
  return current?.id
}

const handleFormSuccess = async (payload: { id?: number; parentId: number; name: string }) => {
  await getList()
  if (payload.id == null) return
  const saved = categoryList.value.find((item) => item.id === payload.id)
  if (!saved) {
    currentCategoryId.value = undefined
    currentCategoryName.value = ''
    return
  }
  if (rootFilter.value && findCategoryRootId(saved.id!) !== rootFilter.value) {
    rootFilter.value = undefined
  }
  const search = keyword.value.trim().toLowerCase()
  if (search && !`${saved.name} ${saved.code}`.toLowerCase().includes(search)) {
    keyword.value = ''
  }
  currentCategoryId.value = saved.id
  currentCategoryName.value = saved.name
  await nextTick()
  categoryTreeRef.value?.setCurrentKey(saved.id)
}

const handleNodeClick = (data: CategoryApi.CategoryVO) => {
  currentCategoryId.value = data.id
  currentCategoryName.value = data.name
}

const formRef = ref()
const openForm = (type: string, id?: number, parentId?: number) => {
  formRef.value.open(type, id, parentId)
}

const updateSplitBounds = () => {
  const width = splitRef.value?.clientWidth || 0
  if (!width) return
  splitBounds.min = Math.max(30, (360 / width) * 100)
  splitBounds.max = Math.min(60, ((width - 520 - 12) / width) * 100)
  if (splitBounds.max < splitBounds.min) splitBounds.max = splitBounds.min
  splitPercent.value = Math.min(splitBounds.max, Math.max(splitBounds.min, splitPercent.value))
}

const saveSplitPercent = () => {
  localStorage.setItem(SPLIT_STORAGE_KEY, splitPercent.value.toFixed(2))
}

const resizeFromClientX = (clientX: number) => {
  const rect = splitRef.value?.getBoundingClientRect()
  if (!rect?.width) return
  const next = ((clientX - rect.left) / rect.width) * 100
  splitPercent.value = Math.min(splitBounds.max, Math.max(splitBounds.min, next))
}

const stopResize = (event: PointerEvent) => {
  if (resizingPointerId !== event.pointerId) return
  resizingPointerId = undefined
  document.body.classList.remove('category-split-resizing')
  saveSplitPercent()
  window.removeEventListener('pointermove', handleResize)
  window.removeEventListener('pointerup', stopResize)
  window.removeEventListener('pointercancel', stopResize)
}

const handleResize = (event: PointerEvent) => {
  if (resizingPointerId === event.pointerId) resizeFromClientX(event.clientX)
}

const startResize = (event: PointerEvent) => {
  if (window.matchMedia('(max-width: 991px)').matches) return
  resizingPointerId = event.pointerId
  document.body.classList.add('category-split-resizing')
  resizeFromClientX(event.clientX)
  window.addEventListener('pointermove', handleResize)
  window.addEventListener('pointerup', stopResize)
  window.addEventListener('pointercancel', stopResize)
}

const handleSplitterKeydown = (event: KeyboardEvent) => {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  if (event.key === 'Home') splitPercent.value = splitBounds.min
  else if (event.key === 'End') splitPercent.value = splitBounds.max
  else {
    const delta = event.key === 'ArrowLeft' ? -2 : 2
    splitPercent.value = Math.min(
      splitBounds.max,
      Math.max(splitBounds.min, splitPercent.value + delta)
    )
  }
  saveSplitPercent()
}

const importRef = ref()
const openImport = () => importRef.value.open()
const handleImportSuccess = async () => {
  await getList()
  currentCategoryId.value = undefined
  currentCategoryName.value = ''
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await CategoryApi.deleteCategory(id)
    message.success(t('common.delSuccess'))
    if (currentCategoryId.value === id) {
      currentCategoryId.value = undefined
      currentCategoryName.value = ''
    }
    await getList()
  } catch {}
}

onMounted(() => {
  const stored = Number(localStorage.getItem(SPLIT_STORAGE_KEY))
  if (Number.isFinite(stored)) splitPercent.value = stored
  nextTick(updateSplitBounds)
  window.addEventListener('resize', updateSplitBounds)
  getList()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateSplitBounds)
  window.removeEventListener('pointermove', handleResize)
  window.removeEventListener('pointerup', stopResize)
  window.removeEventListener('pointercancel', stopResize)
  document.body.classList.remove('category-split-resizing')
})
</script>

<style scoped>
.category-split {
  display: grid;
  grid-template-columns: minmax(360px, var(--category-left-width)) 12px minmax(520px, 1fr);
  align-items: start;
}

.category-pane {
  min-width: 0;
}

.category-tree-node {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
}

.category-node-main {
  display: flex;
  min-width: 0;
  align-items: center;
  overflow: hidden;
  white-space: nowrap;
}

.category-node-name {
  min-width: 48px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-node-actions {
  display: inline-flex;
  flex: none;
  margin-left: 8px;
}

.category-splitter {
  display: flex;
  min-height: 420px;
  cursor: col-resize;
  align-items: stretch;
  justify-content: center;
  outline: none;
  touch-action: none;
}

.category-splitter-handle {
  width: 2px;
  border-radius: 2px;
  background: var(--el-border-color);
  transition:
    width 0.15s ease,
    background-color 0.15s ease;
}

.category-splitter:hover .category-splitter-handle,
.category-splitter:focus-visible .category-splitter-handle {
  width: 4px;
  background: var(--el-color-primary);
}

@media (max-width: 991px) {
  .category-split {
    display: flex;
    flex-direction: column;
    gap: 24px;
  }

  .category-pane {
    width: 100%;
  }

  .category-splitter {
    display: none;
  }
}
</style>

<style>
body.category-split-resizing {
  cursor: col-resize;
  user-select: none;
}
</style>

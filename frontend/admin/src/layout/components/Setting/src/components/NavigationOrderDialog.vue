<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { usePermissionStore } from '@/store/modules/permission'
import { useUserStore } from '@/store/modules/user'
import { getNavigationPreference, saveNavigationPreference } from '@/api/zsjos/navigationPreference'
import {
  applyNavigationOrder,
  visibleNavigation,
  findNavigationLevel,
  navigationKey,
  setSiblingOrder,
  setNavigationVisible,
  isNavigationVisible,
  NAVIGATION_QUERY,
  NAVIGATION_UPDATE,
  type NavigationOrder
} from '@/utils/navigationOrder'

const open = defineModel<boolean>({ default: false })
const permission = usePermissionStore()
const user = useUserStore()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const draft = ref<NavigationOrder>({ groups: [] })
const original = ref('')
const path = ref<string[]>([])
const dragged = ref<string>()
const canRead = computed(
  () => user.getPermissions.has(NAVIGATION_QUERY) || user.getPermissions.has('*:*:*')
)
const canSave = computed(
  () =>
    canRead.value &&
    (user.getPermissions.has(NAVIGATION_UPDATE) || user.getPermissions.has('*:*:*'))
)
const tree = computed(() =>
  applyNavigationOrder(visibleNavigation(permission.navigationSource), draft.value, 'root', true)
)
const rows = computed(() => findNavigationLevel(tree.value, path.value))
const parent = computed(() => path.value.join('/') || 'root')
const dirty = computed(() => JSON.stringify(draft.value) !== original.value)
async function load() {
  if (!canRead.value) return
  loading.value = true
  original.value = ''
  error.value = ''
  path.value = []
  try {
    draft.value = await getNavigationPreference()
    original.value = JSON.stringify(draft.value)
  } catch {
    error.value = '导航设置读取失败，请重试。'
  } finally {
    loading.value = false
  }
}
watch(open, (value) => {
  if (value) void load()
})
function move(from: number, to: number) {
  if (!canSave.value || saving.value || to < 0 || to >= rows.value.length || from < 0) return
  const keys = rows.value.map(navigationKey)
  keys.splice(to, 0, keys.splice(from, 1)[0])
  draft.value = setSiblingOrder(draft.value, parent.value, keys)
}
function drop(index: number) {
  if (dragged.value)
    move(
      rows.value.findIndex((row) => navigationKey(row) === dragged.value),
      index
    )
  dragged.value = undefined
}
async function save() {
  if (!canSave.value || !dirty.value || saving.value) return
  saving.value = true
  error.value = ''
  try {
    await saveNavigationPreference(draft.value)
    open.value = false
    window.location.reload()
  } catch {
    error.value = '保存失败，调整内容已保留，请重试。'
    ElMessage.error(error.value)
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <ElDialog
    v-model="open"
    title="导航栏调整"
    width="min(620px, 94vw)"
    :z-index="4100"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    :before-close="
      (done) => {
        if (!saving) done()
      }
    "
  >
    <ElAlert v-if="!canRead" title="暂无导航设置权限" type="warning" :closable="false" />
    <div v-else v-loading="loading">
      <ElAlert v-if="error" :title="error" type="error" :closable="false" class="mb-12px" />
      <ElButton v-if="error && !original" @click="load">重新加载</ElButton>
      <template v-if="!loading && original">
        <div class="navigation-order-heading"
          ><span>{{ path.length ? '子菜单顺序' : '一级菜单顺序' }}</span
          ><ElButton v-if="path.length" link @click="path.pop()">返回上一级</ElButton></div
        >
        <p class="navigation-order-hint">拖动或使用箭头排序，关闭开关可隐藏菜单；仅对我生效。</p>
        <ElEmpty v-if="!rows.length" description="暂无可调整的导航菜单" />
        <div
          v-for="(row, index) in rows"
          :key="navigationKey(row)"
          class="navigation-order-row"
          :draggable="canSave && !saving"
          @dragstart="dragged = navigationKey(row)"
          @dragend="dragged = undefined"
          @dragover.prevent
          @drop.prevent="drop(index)"
        >
          <ElSwitch :aria-label="'显示' + row.name" :disabled="!canSave || saving"
            :model-value="isNavigationVisible(draft, parent, navigationKey(row))"
            @change="value => draft = setNavigationVisible(draft, parent, navigationKey(row), Boolean(value))" />
          <span class="navigation-order-grip" aria-hidden="true">⠿</span
          ><span class="navigation-order-name">{{ row.name }}</span>
          <ElButton
            v-if="row.children?.length"
            link
            type="primary"
            :disabled="saving"
            @click="path.push(navigationKey(row))"
            >子菜单</ElButton
          >
          <ElButton
            link
            :aria-label="'上移' + row.name"
            :disabled="!canSave || saving || index === 0"
            @click="move(index, index - 1)"
            >↑</ElButton
          >
          <ElButton
            link
            :aria-label="'下移' + row.name"
            :disabled="!canSave || saving || index === rows.length - 1"
            @click="move(index, index + 1)"
            >↓</ElButton
          >
        </div>
        <p class="navigation-order-hint">保存成功后关闭弹窗，并自动刷新当前页面。</p>
      </template>
    </div>
    <template #footer
      ><div class="navigation-order-footer"
        ><ElButton
          :disabled="!canSave || loading || saving || !original"
          @click="draft = { groups: [] }"
          >恢复默认</ElButton
        ><div
          ><ElButton :disabled="saving" @click="open = false">取消</ElButton
          ><ElButton
            type="primary"
            :loading="saving"
            :disabled="!canSave || loading || !original || !dirty"
            @click="save"
            >保存并关闭</ElButton
          ></div
        ></div
      ></template
    >
  </ElDialog>
</template>
<style scoped>
.navigation-order-heading,
.navigation-order-row,
.navigation-order-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.navigation-order-row {
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  margin-bottom: 8px;
}
.navigation-order-name {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.navigation-order-grip {
  cursor: grab;
  color: var(--el-text-color-secondary);
}
.navigation-order-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.navigation-order-footer {
  flex-wrap: wrap;
}
.navigation-order-row .el-button + .el-button {
  margin-left: 0;
}
</style>

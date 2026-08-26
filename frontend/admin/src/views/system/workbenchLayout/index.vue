<template>
  <ContentWrap>
    <div class="layout-toolbar">
      <el-radio-group :model-value="scopeType" @change="changeScopeType">
        <el-radio-button value="GLOBAL">全局布局</el-radio-button>
        <el-radio-button value="ROLE">角色覆盖</el-radio-button>
      </el-radio-group>
      <el-select
        v-if="scopeType === 'ROLE'"
        v-model="scopeId"
        class="!w-260px"
        filterable
        placeholder="选择租户角色"
        @change="loadDraft"
      >
        <el-option
          v-for="role in candidates?.roles || []"
          :key="role.id"
          :label="role.name"
          :value="role.id"
        >
          <span>{{ role.name }}</span>
          <el-tag v-if="role.status !== 0" class="ml-8px" size="small" type="info">已停用</el-tag>
          <el-tag v-if="role.publishedVersionNo" class="ml-8px" size="small">
            v{{ role.publishedVersionNo }}
          </el-tag>
        </el-option>
      </el-select>
      <el-tag v-if="draft?.publishedVersionNo" type="success">
        已发布 v{{ draft.publishedVersionNo }}
      </el-tag>
      <el-tag v-else type="info">未发布</el-tag>
      <el-tag v-if="dirty" type="warning">草稿未保存</el-tag>
      <div class="layout-toolbar-spacer"></div>
      <el-select
        v-model="previewUserId"
        class="!w-220px"
        filterable
        placeholder="选择预览员工"
      >
        <el-option
          v-for="user in users"
          :key="user.id"
          :label="user.nickname"
          :value="user.id"
        />
      </el-select>
      <el-button :disabled="!draft || !previewUserId" :loading="previewLoading" @click="previewLayout">
        <Icon icon="ep:view" />
        员工预览
      </el-button>
      <el-button :disabled="!draft" @click="openVersions">
        <Icon icon="ep:clock" />
        发布历史
      </el-button>
      <el-button
        v-hasPermi="['system:workbench-layout:update']"
        :disabled="!draft"
        :loading="saving"
        type="primary"
        @click="saveCurrentDraft(true)"
      >
        <Icon icon="ep:document-checked" />
        保存草稿
      </el-button>
      <el-button
        v-hasPermi="['system:workbench-layout:publish']"
        :disabled="!draft"
        :loading="publishing"
        type="success"
        @click="publishCurrentDraft"
      >
        <Icon icon="ep:promotion" />
        发布
      </el-button>
    </div>
  </ContentWrap>

  <ContentWrap v-if="loadError">
    <el-alert :closable="false" :description="loadError" show-icon title="布局加载失败" type="error">
      <template #default>
        <el-button class="mt-8px" type="primary" @click="loadPage">重试</el-button>
      </template>
    </el-alert>
  </ContentWrap>

  <ContentWrap v-else v-loading="loading">
    <div v-if="draft" class="layout-workspace">
      <aside class="candidate-pane">
        <div class="pane-heading">
          <span>候选页面</span>
          <el-tag size="small">{{ filteredCandidates.length }}</el-tag>
        </div>
        <el-input v-model="candidateKeyword" clearable placeholder="搜索名称或路径">
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
        <el-scrollbar class="candidate-scroll">
          <div v-if="filteredCandidates.length === 0" class="pane-empty">暂无匹配页面</div>
          <div v-for="page in filteredCandidates" :key="page.sourceMenuId" class="candidate-row">
            <Icon :icon="page.icon || 'ep:document'" />
            <div class="candidate-main">
              <div class="candidate-name">{{ page.name }}</div>
              <div class="candidate-path">{{ page.path }}</div>
            </div>
            <el-tag v-if="arrangedPageIds.has(page.sourceMenuId)" size="small" type="info">已编排</el-tag>
            <el-button
              v-else-if="canUpdate"
              circle
              text
              title="加入未分类"
              type="primary"
              @click="addCandidate(page)"
            >
              <Icon icon="ep:plus" />
            </el-button>
          </div>
        </el-scrollbar>
      </aside>

      <section class="editor-pane">
        <div class="pane-heading">
          <span>{{ scopeType === 'GLOBAL' ? '全局导航树' : '角色最终导航树' }}</span>
          <el-button
            v-hasPermi="['system:workbench-layout:update']"
            plain
            size="small"
            type="primary"
            @click="addRootGroup"
          >
            <Icon icon="ep:folder-add" />
            新增一级分组
          </el-button>
        </div>
        <div v-if="scopeType === 'ROLE'" class="role-settings">
          <el-switch
            v-model="draft.snapshot.enabled"
            active-text="启用角色覆盖"
            :disabled="!canUpdate"
            inactive-text="停用角色覆盖"
            @change="markDirty"
          />
          <el-input-number
            v-if="draft.snapshot.enabled"
            v-model="draft.snapshot.priority"
            :disabled="!canUpdate"
            :min="1"
            controls-position="right"
            @change="markDirty"
          />
          <span v-if="draft.snapshot.enabled" class="setting-label">优先级，数字越小越优先</span>
        </div>
        <WorkbenchLayoutTree
          :candidate-map="candidateMap"
          :depth="0"
          :editable="canUpdate"
          :nodes="draft.snapshot.nodes"
          :scope-type="scopeType"
          @add-child="addChildGroup"
          @change="markDirty"
          @delete-group="deleteGroup"
        />
      </section>
    </div>
  </ContentWrap>

  <Dialog v-model="previewVisible" title="员工导航预览" width="860px">
    <div v-if="previewResult" class="preview-content">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="员工">{{ previewResult.userName }}</el-descriptions-item>
        <el-descriptions-item label="全局版本">
          {{ previewResult.meta.globalVersionNo ? `v${previewResult.meta.globalVersionNo}` : '当前草稿' }}
        </el-descriptions-item>
        <el-descriptions-item label="胜出角色">
          {{ winningRoleName(previewResult.meta.winningRoleId) }}
        </el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">最终导航</el-divider>
      <el-tree
        :data="previewResult.finalTree"
        :default-expand-all="true"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
      >
        <template #default="{ data }">
          <span class="preview-node">
            <Icon :icon="data.icon || 'ep:document'" />
            <span>{{ data.name }}</span>
            <span v-if="data.sourceMenuId" class="candidate-path">{{ data.path }}</span>
          </span>
        </template>
      </el-tree>
      <template v-if="previewResult.filteredItems.length">
        <el-divider content-position="left">过滤结果</el-divider>
        <el-table :data="previewResult.filteredItems" max-height="220">
          <el-table-column label="页面" prop="name" />
          <el-table-column label="原因" prop="reason" width="220" />
        </el-table>
      </template>
    </div>
  </Dialog>

  <Dialog v-model="impactVisible" title="发布影响" width="720px">
    <el-alert
      :closable="false"
      :title="impact?.publishable ? '校验通过' : '发布已阻止'"
      :type="impact?.publishable ? 'success' : 'error'"
      show-icon
    />
    <el-table v-if="impact?.issues.length" :data="impact.issues" class="mt-12px">
      <el-table-column label="角色" min-width="160">
        <template #default="scope">{{ scope.row.roleName || scope.row.roleId || '全局布局' }}</template>
      </el-table-column>
      <el-table-column label="问题" min-width="360" prop="message" />
    </el-table>
  </Dialog>

  <el-drawer v-model="versionsVisible" size="560px" title="发布历史">
    <el-table v-loading="versionsLoading" :data="versions">
      <el-table-column label="版本" prop="versionNo" width="80">
        <template #default="scope">v{{ scope.row.versionNo }}</template>
      </el-table-column>
      <el-table-column label="发布说明" min-width="180" prop="publishRemark" show-overflow-tooltip />
      <el-table-column label="发布人" prop="publisherUserId" width="90" />
      <el-table-column label="发布时间" min-width="170" prop="publishTime" />
      <el-table-column label="操作" width="90">
        <template #default="scope">
          <el-button
            v-hasPermi="['system:workbench-layout:update']"
            link
            type="primary"
            @click="restoreVersion(scope.row)"
          >
            恢复草稿
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>

<script lang="ts" setup>
import { cloneDeep } from 'lodash-es'
import { Icon } from '@/components/Icon'
import * as UserApi from '@/api/system/user'
import * as LayoutApi from '@/api/system/workbenchLayout'
import { checkPermi } from '@/utils/permission'
import type {
  WorkbenchLayoutCandidateRespVO,
  WorkbenchLayoutDraftRespVO,
  WorkbenchLayoutImpactRespVO,
  WorkbenchLayoutNode,
  WorkbenchLayoutPreviewRespVO,
  WorkbenchLayoutScopeType,
  WorkbenchLayoutVersionRespVO
} from '@/api/system/workbenchLayout'
import WorkbenchLayoutTree from './WorkbenchLayoutTree.vue'

defineOptions({ name: 'SystemWorkbenchLayout' })

const UNCLASSIFIED_KEY = '__unclassified__'
const message = useMessage()
const canUpdate = checkPermi(['system:workbench-layout:update'])

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const loadError = ref('')
const dirty = ref(false)
const candidates = ref<WorkbenchLayoutCandidateRespVO>()
const users = ref<UserApi.UserSimpleVO[]>([])
const scopeType = ref<WorkbenchLayoutScopeType>('GLOBAL')
const scopeId = ref(0)
const draft = ref<WorkbenchLayoutDraftRespVO>()
const candidateKeyword = ref('')
const previewUserId = ref<number>()

const candidateMap = computed(() =>
  Object.fromEntries((candidates.value?.pages || []).map((page) => [page.sourceMenuId, page]))
)

const collectPageIds = (nodes: WorkbenchLayoutNode[], result = new Set<number>()) => {
  for (const node of nodes) {
    if (node.type === 'PAGE' && node.sourceMenuId) result.add(node.sourceMenuId)
    collectPageIds(node.children, result)
  }
  return result
}

const arrangedPageIds = computed(() => collectPageIds(draft.value?.snapshot.nodes || []))
const filteredCandidates = computed(() => {
  const keyword = candidateKeyword.value.trim().toLowerCase()
  if (!keyword) return candidates.value?.pages || []
  return (candidates.value?.pages || []).filter(
    (page) => page.name.toLowerCase().includes(keyword) || page.path.toLowerCase().includes(keyword)
  )
})

const markDirty = () => {
  dirty.value = true
}

const loadDraft = async () => {
  if (scopeType.value === 'ROLE' && !scopeId.value) {
    draft.value = undefined
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    draft.value = await LayoutApi.getDraft(scopeType.value, scopeId.value)
    dirty.value = false
  } catch (error: any) {
    draft.value = undefined
    loadError.value = error?.msg || error?.message || '无法读取布局草稿'
  } finally {
    loading.value = false
  }
}

const changeScopeType = async (value: string | number | boolean | undefined) => {
  const target = value as WorkbenchLayoutScopeType
  if (target === scopeType.value) return
  if (dirty.value) {
    try {
      await message.confirm('当前草稿尚未保存，切换后将丢失本地修改。')
    } catch {
      return
    }
  }
  scopeType.value = target
  scopeId.value = target === 'GLOBAL' ? 0 : candidates.value?.roles[0]?.id || 0
  await loadDraft()
}

const newGroup = (): WorkbenchLayoutNode => ({
  key: `${scopeType.value === 'ROLE' ? 'role' : 'global'}-group-${crypto.randomUUID()}`,
  type: 'GROUP',
  name: '新分组',
  icon: 'ep:folder',
  hidden: false,
  children: []
})

const addRootGroup = () => {
  draft.value?.snapshot.nodes.push(newGroup())
  markDirty()
}

const addChildGroup = (parent: WorkbenchLayoutNode) => {
  parent.children.push(newGroup())
  markDirty()
}

const unwrapGroup = (nodes: WorkbenchLayoutNode[], key: string): boolean => {
  const index = nodes.findIndex((node) => node.key === key)
  if (index >= 0) {
    const [removed] = nodes.splice(index, 1)
    nodes.splice(index, 0, ...removed.children)
    return true
  }
  return nodes.some((node) => unwrapGroup(node.children, key))
}

const deleteGroup = (key: string) => {
  if (!draft.value || key === UNCLASSIFIED_KEY) return
  unwrapGroup(draft.value.snapshot.nodes, key)
  markDirty()
}

const findUnclassified = (nodes: WorkbenchLayoutNode[]): WorkbenchLayoutNode | undefined => {
  for (const node of nodes) {
    if (node.key === UNCLASSIFIED_KEY) return node
    const child = findUnclassified(node.children)
    if (child) return child
  }
}

const addCandidate = (page: WorkbenchLayoutCandidateRespVO['pages'][number]) => {
  if (!draft.value || arrangedPageIds.value.has(page.sourceMenuId)) return
  findUnclassified(draft.value.snapshot.nodes)?.children.push({
    key: `menu-${page.sourceMenuId}`,
    type: 'PAGE',
    sourceMenuId: page.sourceMenuId,
    name: page.name,
    icon: page.icon,
    hidden: false,
    children: []
  })
  markDirty()
}

const saveCurrentDraft = async (showSuccess: boolean) => {
  if (!draft.value) return
  if (!canUpdate) {
    message.warning('当前账号没有更新 Workbench 布局草稿的权限')
    return
  }
  saving.value = true
  try {
    draft.value.draftRevision = await LayoutApi.saveDraft({
      scopeType: scopeType.value,
      scopeId: scopeId.value,
      draftRevision: draft.value.draftRevision,
      snapshot: cloneDeep(draft.value.snapshot)
    })
    dirty.value = false
    if (showSuccess) message.success('草稿已保存')
  } catch (error: any) {
    if (error?.code === 1002031002) await loadDraft()
    throw error
  } finally {
    saving.value = false
  }
}

const impactVisible = ref(false)
const impact = ref<WorkbenchLayoutImpactRespVO>()

const publishCurrentDraft = async () => {
  if (!draft.value) return
  publishing.value = true
  try {
    if (dirty.value) {
      if (!canUpdate) {
        message.warning('请由有更新权限的管理员先保存当前草稿')
        return
      }
      await saveCurrentDraft(false)
    }
    impact.value = await LayoutApi.getPublishImpact(scopeType.value, scopeId.value)
    if (!impact.value.publishable) {
      impactVisible.value = true
      return
    }
    const result = await message.prompt('请输入本次发布说明', '发布 Workbench 布局')
    const remark = result.value?.trim()
    if (!remark) {
      message.warning('发布说明不能为空')
      return
    }
    await LayoutApi.publish({
      scopeType: scopeType.value,
      scopeId: scopeId.value,
      draftRevision: draft.value.draftRevision,
      publishRemark: remark
    })
    message.success('布局已发布，将在员工下次刷新或登录时生效')
    await loadDraft()
    if (candidates.value) candidates.value = await LayoutApi.getCandidates()
  } finally {
    publishing.value = false
  }
}

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewResult = ref<WorkbenchLayoutPreviewRespVO>()

const previewLayout = async () => {
  if (!draft.value || !previewUserId.value) return
  previewLoading.value = true
  try {
    previewResult.value = await LayoutApi.preview({
      userId: previewUserId.value,
      scopeType: scopeType.value,
      scopeId: scopeId.value,
      snapshot: cloneDeep(draft.value.snapshot)
    })
    previewVisible.value = true
  } finally {
    previewLoading.value = false
  }
}

const winningRoleName = (roleId?: number) =>
  roleId ? candidates.value?.roles.find((role) => role.id === roleId)?.name || `角色 #${roleId}` : '全局布局'

const versionsVisible = ref(false)
const versionsLoading = ref(false)
const versions = ref<WorkbenchLayoutVersionRespVO[]>([])

const openVersions = async () => {
  versionsVisible.value = true
  versionsLoading.value = true
  try {
    versions.value = await LayoutApi.getVersions(scopeType.value, scopeId.value)
  } finally {
    versionsLoading.value = false
  }
}

const restoreVersion = async (version: WorkbenchLayoutVersionRespVO) => {
  if (!draft.value) return
  await message.confirm(`将 v${version.versionNo} 恢复为新草稿？当前线上版本不会立即变化。`)
  draft.value.draftRevision = await LayoutApi.restoreDraft({
    scopeType: scopeType.value,
    scopeId: scopeId.value,
    versionId: version.id,
    draftRevision: draft.value.draftRevision
  })
  versionsVisible.value = false
  await loadDraft()
  message.success('历史版本已恢复为草稿')
}

const loadPage = async () => {
  loading.value = true
  loadError.value = ''
  try {
    ;[candidates.value, users.value] = await Promise.all([
      LayoutApi.getCandidates(),
      UserApi.getSimpleUserOptions()
    ])
    await loadDraft()
  } catch (error: any) {
    loadError.value = error?.msg || error?.message || '无法加载 Workbench 菜单编排'
  } finally {
    loading.value = false
  }
}

onMounted(loadPage)
</script>

<style scoped>
.layout-toolbar {
  display: flex;
  min-height: 32px;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.layout-toolbar-spacer {
  flex: 1;
}

.layout-workspace {
  display: grid;
  min-height: 620px;
  grid-template-columns: minmax(240px, 300px) minmax(480px, 1fr);
}

.candidate-pane {
  min-width: 0;
  padding-right: 16px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.editor-pane {
  min-width: 0;
  padding-left: 18px;
}

.pane-heading {
  display: flex;
  min-height: 32px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.candidate-scroll {
  height: 550px;
  margin-top: 10px;
}

.candidate-row {
  display: flex;
  min-height: 48px;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.candidate-main {
  min-width: 0;
  flex: 1;
}

.candidate-name,
.candidate-path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.candidate-name {
  font-size: 13px;
}

.candidate-path,
.setting-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.pane-empty {
  padding: 32px 0;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.role-settings {
  display: flex;
  min-height: 42px;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  padding: 6px 10px;
  background: var(--el-fill-color-light);
}

.preview-content {
  min-height: 240px;
}

.preview-node {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

@media (max-width: 900px) {
  .layout-workspace {
    grid-template-columns: 1fr;
  }

  .candidate-pane {
    padding-right: 0;
    padding-bottom: 16px;
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .editor-pane {
    padding-top: 16px;
    padding-left: 0;
  }

  .candidate-scroll {
    height: 260px;
  }
}
</style>

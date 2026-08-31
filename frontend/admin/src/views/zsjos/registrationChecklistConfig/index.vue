<template>
  <ContentWrap>
    <div class="toolbar"
      ><div><h3>履约清单配置</h3><span>发布后仅新建报名任务使用新版本</span></div
      ><div
        ><el-button :disabled="!!config?.draft" @click="copy">复制已发布版本</el-button
        ><el-button type="primary" :disabled="!draft" :loading="saving" @click="save"
          >保存草稿</el-button
        ><el-button :disabled="!draft" @click="publish">发布</el-button></div
      ></div
    >
  </ContentWrap>
  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon
      ><template #default
        ><el-button link type="primary" @click="load">重试</el-button></template
      ></el-alert
    >
    <el-empty v-else-if="!draft" description="暂无草稿，请先复制已发布版本" />
    <template v-else>
      <div class="section-heading"
        ><h4>任务清单</h4
        ><el-button type="primary" plain @click="addItem"
          ><Icon icon="ep:plus" class="mr-5px" />新增清单项</el-button
        ></div
      >
      <el-table :data="draft.items" row-key="id">
        <el-table-column label="顺序" width="120"
          ><template #default="{ $index }"
            ><el-button link :disabled="$index === 0" @click="move(draft.items, $index, -1)"
              >上移</el-button
            ><el-button
              link
              :disabled="$index === draft.items.length - 1"
              @click="move(draft.items, $index, 1)"
              >下移</el-button
            ></template
          ></el-table-column
        >
        <el-table-column label="事项" min-width="280"
          ><template #default="{ row }"
            ><el-input v-model="row.title" :disabled="row.systemRequired" /></template
        ></el-table-column>
        <el-table-column label="类型" width="150"
          ><template #default="{ row }"
            ><el-select
              v-model="row.itemType"
              :disabled="row.systemRequired"
              @change="
                row.attachmentRequired = row.itemType === 'attachment' && row.attachmentRequired
              "
              ><el-option label="人工确认" value="checkbox" /><el-option
                label="上传附件"
                value="attachment" /><el-option
                v-if="row.systemRequired"
                label="系统固定项"
                value="study_planner" /></el-select></template
        ></el-table-column>
        <el-table-column label="必传" width="90"
          ><template #default="{ row }"
            ><el-switch
              v-if="row.itemType === 'attachment'"
              v-model="row.attachmentRequired"
            /><span v-else>-</span></template
          ></el-table-column
        >
        <el-table-column label="启用" width="90"
          ><template #default="{ row }"
            ><el-switch v-model="row.enabled" :disabled="row.systemRequired" /></template
        ></el-table-column>
        <el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button
              link
              type="danger"
              :disabled="row.systemRequired"
              @click="removeItem(row.id)"
              >删除</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <div class="section-heading"
        ><h4>学员流转部门</h4
        ><el-button type="primary" plain @click="addRoute"
          ><Icon icon="ep:plus" class="mr-5px" />新增流转部门</el-button
        ></div
      >
      <el-table :data="draft.routeOptions" row-key="id">
        <el-table-column label="顺序" width="120"
          ><template #default="{ $index }"
            ><el-button link :disabled="$index === 0" @click="move(draft.routeOptions, $index, -1)"
              >上移</el-button
            ><el-button
              link
              :disabled="$index === draft.routeOptions.length - 1"
              @click="move(draft.routeOptions, $index, 1)"
              >下移</el-button
            ></template
          ></el-table-column
        >
        <el-table-column label="部门" min-width="260"
          ><template #default="{ row }"
            ><el-select v-model="row.departmentId" filterable class="w-100%"
              ><el-option
                v-for="dept in departments"
                :key="dept.id"
                :label="dept.name"
                :value="dept.id" /></el-select></template
        ></el-table-column>
        <el-table-column label="负责人类型" width="180"
          ><template #default="{ row }"
            ><el-select v-model="row.assigneeType"
              ><el-option label="学习规划师" value="study_planner" /><el-option
                label="编导"
                value="content_director" /></el-select></template
        ></el-table-column>
        <el-table-column label="启用" width="90"
          ><template #default="{ row }"><el-switch v-model="row.enabled" /></template
        ></el-table-column>
        <el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button link type="danger" @click="removeRoute(row.id)">删除</el-button></template
          ></el-table-column
        >
      </el-table>
    </template>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as Api from '@/api/zsjos/registrationChecklistConfig'
import * as DeptApi from '@/api/system/dept'
defineOptions({ name: 'ZsjosRegistrationChecklistConfig' })
const message = useMessage()
const loading = ref(false),
  saving = ref(false),
  error = ref('')
const config = ref<Api.ChecklistConfig>(),
  departments = ref<DeptApi.DeptVO[]>([])
const draft = computed(() => config.value?.draft)
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    ;[config.value, departments.value] = await Promise.all([
      Api.getRegistrationChecklistConfig(),
      DeptApi.getSimpleDeptList()
    ])
  } catch (e: any) {
    error.value = e?.msg || e?.message || '配置加载失败'
  } finally {
    loading.value = false
  }
}
const move = <T,>(rows: T[], index: number, offset: number) => {
  const target = index + offset
  if (target < 0 || target >= rows.length) return
  ;[rows[index], rows[target]] = [rows[target], rows[index]]
}
const addItem = () =>
  draft.value?.items.push({
    id: -Date.now(),
    itemKey: `custom_${crypto.randomUUID().replaceAll('-', '')}`,
    itemType: 'checkbox',
    title: '新清单项',
    sort: 0,
    enabled: true,
    systemRequired: false,
    attachmentRequired: false
  })
const addRoute = () => {
  const dept = departments.value[0]
  if (!draft.value || !dept) return message.warning('系统暂无可用部门')
  draft.value.routeOptions.push({
    id: -Date.now(),
    optionKey: `custom_${crypto.randomUUID().replaceAll('-', '')}`,
    departmentId: dept.id,
    departmentName: dept.name,
    assigneeType: 'study_planner',
    assigneeTypeLabel: '学习规划师',
    sort: 0,
    enabled: true,
    systemRequired: false
  })
}
const removeItem = (id: number) => {
  if (draft.value) draft.value.items = draft.value.items.filter((item) => item.id !== id)
}
const removeRoute = (id: number) => {
  if (draft.value)
    draft.value.routeOptions = draft.value.routeOptions.filter((item) => item.id !== id)
}
const copy = async () => {
  if (!config.value) return
  try {
    await Api.copyRegistrationChecklistDraft(config.value.templateVersion)
    message.success('已创建草稿')
    await load()
  } catch (e: any) {
    message.error(e?.msg || '复制失败')
  }
}
const save = async () => {
  if (!config.value?.draft) return
  saving.value = true
  try {
    const items = config.value.draft.items.map((item, index) => ({
      ...item,
      sort: (index + 1) * 10
    }))
    const routeOptions = config.value.draft.routeOptions.map((route, index) => ({
      ...route,
      sort: (index + 1) * 10
    }))
    await Api.saveRegistrationChecklistDraft({
      templateVersion: config.value.templateVersion,
      items,
      routeOptions
    })
    message.success('草稿已保存')
    await load()
  } catch (e: any) {
    message.error(e?.msg || '保存失败')
  } finally {
    saving.value = false
  }
}
const publish = async () => {
  if (!config.value) return
  try {
    await Api.publishRegistrationChecklist(config.value.templateVersion)
    message.success('模板已发布')
    await load()
  } catch (e: any) {
    message.error(e?.msg || '发布失败')
  }
}
onMounted(load)
</script>

<style scoped>
.toolbar,
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar h3,
.section-heading h4 {
  margin: 0;
}

.toolbar span {
  color: var(--el-text-color-secondary);
}

.section-heading {
  margin: 24px 0 12px;
}
</style>

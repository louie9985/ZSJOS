<template>
  <ContentWrap>
    <div class="config-toolbar">
      <div>
        <h3>学员信息收集表配置</h3>
        <span>{{
          config?.published ? `当前生效版本 V${config.published.versionNo}` : '尚未发布'
        }}</span>
      </div>
      <div class="config-actions">
        <el-button :disabled="busy" @click="load">刷新</el-button>
        <el-button
          v-hasPermi="['zsjos:student-info-form:config:update']"
          :disabled="busy || !!error"
          :loading="saving"
          @click="save"
          >保存草稿</el-button
        >
        <el-button
          v-hasPermi="['zsjos:student-info-form:config:publish']"
          type="primary"
          :disabled="busy || dirty || !config?.draft"
          @click="publish"
          >发布草稿</el-button
        >
      </div>
    </div>
  </ContentWrap>
  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <el-button link @click="load">重试</el-button>
    </el-alert>
    <el-table v-else :data="fields" row-key="key">
      <el-table-column prop="label" label="字段" min-width="170" fixed />
      <el-table-column label="类型" width="110"
        ><template #default="{ row }">{{ types[row.type] }}</template></el-table-column
      >
      <el-table-column label="数据来源" min-width="220">
        <template #default="{ row }">
          <el-select
            v-if="row.type === 'dict'"
            v-model="row.dictType"
            filterable
            :disabled="!editable || busy || row.key === 'gender'"
            placeholder="选择系统字典"
          >
            <el-option
              v-for="item in dictTypes"
              :key="item.type"
              :label="item.name"
              :value="item.type"
            />
          </el-select>
          <span v-else>{{ row.type === 'area' ? '系统地区管理' : '文本' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80"
        ><template #default="{ row }"
          ><el-switch v-model="row.enabled" :disabled="!editable || busy" /></template
      ></el-table-column>
      <el-table-column label="必填" width="80"
        ><template #default="{ row }"
          ><el-switch
            v-model="row.required"
            :disabled="!editable || busy || !row.enabled" /></template
      ></el-table-column>
      <el-table-column label="排序" width="145"
        ><template #default="{ row }"
          ><el-input-number
            v-model="row.sort"
            :min="0"
            :max="10000"
            :precision="0"
            controls-position="right"
            :disabled="!editable || busy"
            class="sort-input" /></template
      ></el-table-column>
      <el-table-column label="备注" min-width="250"
        ><template #default="{ row }"
          ><el-input
            v-model="row.note"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            :disabled="!editable || busy" /></template
      ></el-table-column>
    </el-table>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as Api from '@/api/zsjos/studentInfoFormConfig'
import * as DictTypeApi from '@/api/system/dict/dict.type'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'ZsjosStudentInfoFormConfig' })
const message = useMessage()
const config = ref<Api.Config>()
const fields = ref<Api.Field[]>([])
const dictTypes = ref<DictTypeApi.DictTypeVO[]>([])
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const error = ref('')
const saved = ref('')
const busy = computed(() => loading.value || saving.value || publishing.value)
const dirty = computed(() => JSON.stringify(fields.value) !== saved.value)
const editable = computed(() => checkPermi(['zsjos:student-info-form:config:update']))
const types: Record<string, string> = {
  text: '单行文本',
  textarea: '多行文本',
  dict: '下拉选择',
  area: '地区树'
}
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const [result, dictionaries] = await Promise.all([
      Api.getConfig(),
      DictTypeApi.getSimpleDictTypeList()
    ])
    config.value = result
    dictTypes.value = dictionaries
    fields.value = structuredClone(
      result.draft?.fields ?? result.published?.fields ?? result.presets
    )
    saved.value = JSON.stringify(fields.value)
  } catch (e: any) {
    error.value = e?.message || '配置加载失败'
  } finally {
    loading.value = false
  }
}
const save = async () => {
  if (busy.value) return
  saving.value = true
  try {
    const draft = await Api.saveDraft({
      id: config.value?.draft?.id,
      revision: config.value?.draft?.revision ?? 0,
      fields: fields.value
    })
    if (config.value) config.value.draft = draft
    fields.value = structuredClone(draft.fields)
    saved.value = JSON.stringify(fields.value)
    message.success('草稿已保存')
  } finally {
    saving.value = false
  }
}
const publish = async () => {
  if (!config.value?.draft || dirty.value || busy.value) return
  try {
    await message.confirm('发布后，新生成的收集表使用此版本。确认发布？')
  } catch {
    return
  }
  publishing.value = true
  try {
    await Api.publish({ id: config.value.draft.id, revision: config.value.draft.revision })
    message.success('配置已发布')
    await load()
  } finally {
    publishing.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.config-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}
.config-toolbar h3 {
  margin: 0 0 8px;
  font-size: 18px;
}
.config-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.config-actions .el-button {
  margin-left: 0;
}
.sort-input {
  width: 120px;
}
</style>

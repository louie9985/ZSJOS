<template>
  <ContentWrap>
    <div class="feedback-settings-heading">
      <div>
        <h3>反馈设置</h3>
        <p>配置动态表单、标题字段和分派负责人。未配置可用负责人的类型不会向员工开放。</p>
      </div>
      <el-button :loading="loading" @click="load">
        <Icon icon="ep:refresh" class="mr-5px" />刷新
      </el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default
        ><el-button link type="primary" @click="load">重新加载</el-button></template
      >
    </el-alert>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="configs" row-key="feedbackType">
      <el-table-column label="类型" min-width="130">
        <template #default="{ row }"
          ><strong>{{ typeLabel(row.feedbackType) }}</strong></template
        >
      </el-table-column>
      <el-table-column label="动态表单" min-width="220">
        <template #default="{ row }">
          <span>{{ row.formName || `表单 ${row.formId}` }}</span>
          <el-tag v-if="row.incompatibleFields?.length" class="ml-8px" type="danger" size="small">
            不兼容
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标题字段" prop="titleFieldKey" min-width="130" />
      <el-table-column label="分派负责人" min-width="200">
        <template #default="{ row }">
          <span v-if="row.feedbackType === 'SURVEY'">不适用</span>
          <el-tag v-else-if="!row.dispatcherUserIds?.length" type="warning">暂未开放</el-tag>
          <span v-else>{{ row.dispatcherUserIds.length }} 人</span>
        </template>
      </el-table-column>
      <el-table-column label="需求审批" min-width="120">
        <template #default="{ row }">
          <el-tag
            v-if="row.feedbackType === 'REQUIREMENT'"
            :type="row.approvalEnabled ? 'success' : 'info'"
          >
            {{ row.approvalEnabled ? '已开启' : '已关闭' }}
          </el-tag>
          <span v-else>不适用</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-hasPermi="['zsjos:feedback:settings:save']"
            @click="openEditor(row)"
          >
            编辑
          </el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无反馈设置" /></template>
    </el-table>
  </ContentWrap>

  <el-dialog v-model="editorOpen" :title="`编辑${typeLabel(editor.feedbackType)}`" width="620px">
    <el-form label-position="top">
      <el-form-item label="动态表单" required>
        <el-select
          v-model="editor.formId"
          class="w-full"
          filterable
          placeholder="请选择兼容的 BPM 动态表单"
          @change="formChanged"
        >
          <el-option
            v-for="item in formOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
            :disabled="Boolean(item.incompatibleFields?.length)"
          >
            <span>{{ item.name }}</span>
            <span v-if="item.incompatibleFields?.length" class="feedback-form-warning">
              {{ item.incompatibleFields.join('、') }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-alert
        v-if="selectedForm?.incompatibleFields?.length"
        :title="`不能绑定：${selectedForm.incompatibleFields.join('、')}`"
        type="error"
        show-icon
        :closable="false"
        class="mb-16px"
      />
      <el-form-item
        :label="editor.feedbackType === 'SURVEY' ? '总体满意度字段' : '标题字段'"
        required
      >
        <el-select v-model="editor.titleFieldKey" class="w-full" placeholder="请选择必填字段">
          <el-option v-for="key in titleFieldKeys" :key="key" :label="key" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="editor.feedbackType !== 'SURVEY'" label="分派负责人">
        <el-select
          v-model="editor.dispatcherUserIds"
          class="w-full"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="留空时员工端暂不开放"
        >
          <el-option
            v-for="item in candidates"
            :key="item.id"
            :label="item.nickname"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <template v-if="editor.feedbackType === 'REQUIREMENT'">
        <el-form-item label="审批流程">
          <el-switch v-model="editor.approvalEnabled" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item v-if="editor.approvalEnabled" label="已发布流程" required>
          <el-select
            v-model="editor.bpmProcessDefinitionKey"
            class="w-full"
            placeholder="请先导入并发布需求审批流程"
          >
            <el-option
              v-for="item in processOptions"
              :key="item.id"
              :label="`${item.name} · v${item.version}`"
              :value="item.key"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="editor.approvalEnabled && !processOptions.length"
          title="当前没有可用的需求审批流程，暂时不能保存开启状态"
          type="warning"
          show-icon
          :closable="false"
        />
      </template>
    </el-form>
    <template #footer>
      <el-button @click="editorOpen = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import * as FeedbackApi from '@/api/zsjos/feedback'
import { useMessage } from '@/hooks/web/useMessage'

defineOptions({ name: 'ZsjosFeedbackSettings' })

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const configs = ref<FeedbackApi.FeedbackConfig[]>([])
const formOptions = ref<FeedbackApi.FeedbackFormOption[]>([])
const processOptions = ref<FeedbackApi.FeedbackProcessOption[]>([])
const candidates = ref<FeedbackApi.FeedbackUserOption[]>([])
const editorOpen = ref(false)
const editor = reactive<FeedbackApi.FeedbackConfig>({
  feedbackType: 'REQUIREMENT',
  formId: 0,
  titleFieldKey: '',
  dispatcherUserIds: [],
  approvalEnabled: false,
  version: 0,
  incompatibleFields: []
})

const typeLabels: Record<FeedbackApi.FeedbackType, string> = {
  REQUIREMENT: '需求反馈',
  BUG: 'BUG 反馈',
  SUPPORT: '技术支持',
  SURVEY: '满意度'
}
const typeLabel = (type: FeedbackApi.FeedbackType) => typeLabels[type]
const selectedForm = computed(() => formOptions.value.find((item) => item.id === editor.formId))
const titleFieldKeys = computed(() =>
  editor.feedbackType === 'SURVEY'
    ? selectedForm.value?.requiredRatingFieldKeys || []
    : selectedForm.value?.requiredTextFieldKeys || []
)
const selectedProcess = computed(() =>
  processOptions.value.find((item) => item.key === editor.bpmProcessDefinitionKey)
)
const canSave = computed(
  () =>
    Boolean(editor.formId && editor.titleFieldKey) &&
    !selectedForm.value?.incompatibleFields?.length &&
    (!editor.approvalEnabled || Boolean(editor.bpmProcessDefinitionKey && selectedProcess.value))
)

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    ;[configs.value, formOptions.value, processOptions.value] = await Promise.all([
      FeedbackApi.getFeedbackConfigs(),
      FeedbackApi.getFeedbackFormOptions(),
      FeedbackApi.getFeedbackProcessOptions()
    ])
  } catch (cause: any) {
    error.value = cause?.message || '反馈设置加载失败'
  } finally {
    loading.value = false
  }
}

const openEditor = async (row: FeedbackApi.FeedbackConfig) => {
  Object.assign(editor, row, { dispatcherUserIds: [...(row.dispatcherUserIds || [])] })
  candidates.value =
    row.feedbackType === 'SURVEY' ? [] : await FeedbackApi.getFeedbackCandidates(row.feedbackType)
  editorOpen.value = true
}

const formChanged = () => {
  if (!titleFieldKeys.value.includes(editor.titleFieldKey)) {
    editor.titleFieldKey = titleFieldKeys.value[0] || ''
  }
}

const save = async () => {
  if (!canSave.value) return
  saving.value = true
  try {
    await FeedbackApi.saveFeedbackConfig(editor)
    message.success('反馈设置已保存')
    editorOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.feedback-settings-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.feedback-settings-heading h3 {
  margin: 0 0 6px;
  font-size: 18px;
}

.feedback-settings-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.feedback-form-warning {
  float: right;
  max-width: 320px;
  margin-left: 16px;
  overflow: hidden;
  color: var(--el-color-danger);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (width <= 640px) {
  .feedback-settings-heading {
    flex-direction: column;
  }
}
</style>

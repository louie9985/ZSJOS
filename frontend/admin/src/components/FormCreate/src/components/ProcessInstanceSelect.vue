<template>
  <div class="process-instance-select">
    <el-skeleton v-if="loading" :rows="2" animated />
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false">
      <el-button link type="primary" @click="loadRelations">重试</el-button>
    </el-alert>
    <el-empty
      v-else-if="readonly && !selectedItems.length"
      description="暂无关联审批单"
      :image-size="72"
    />
    <div v-else class="relation-list">
      <div v-for="item in selectedItems" :key="item.targetProcessInstanceId" class="relation-row">
        <div class="relation-main" @click="readonly && item.id && openDetail(item)">
          <div class="relation-title">
            <span v-if="item.displayNo" class="relation-no">{{ item.displayNo }}</span>
            {{ item.name || '未命名审批' }}
          </div>
          <div class="relation-meta">
            {{ item.processDefinitionName || '未知流程' }} · {{ formatDate(item.startTime) }}
          </div>
          <el-tag v-if="item.detailAvailable === false" type="info">审批记录不可用</el-tag>
        </div>
        <el-button
          v-if="!readonly"
          link
          type="danger"
          :icon="Delete"
          aria-label="移除关联审批"
          @click="remove(item.targetProcessInstanceId)"
        />
      </div>
    </div>
    <el-button
      v-if="!readonly"
      :icon="Link"
      :disabled="selectedItems.length >= 20"
      @click="openSelector"
    >
      选择审批单（{{ selectedItems.length }}/20）
    </el-button>

    <el-dialog
      v-model="selectorVisible"
      title="选择关联审批单"
      width="min(920px, 94vw)"
      destroy-on-close
    >
      <el-form :inline="true" :model="query">
        <el-form-item label="关键词"
          ><el-input v-model="query.keyword" clearable @keyup.enter="search"
        /></el-form-item>
        <el-form-item label="流程标识"
          ><el-input v-model="query.processDefinitionKey" clearable
        /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable class="w-140px">
            <el-option
              v-for="item in getIntDictOptions(DICT_TYPE.BPM_PROCESS_INSTANCE_STATUS)"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item><el-button :icon="Search" @click="search">查询</el-button></el-form-item>
      </el-form>
      <el-alert
        v-if="candidateError"
        :title="candidateError"
        type="error"
        show-icon
        :closable="false"
      >
        <el-button link type="primary" @click="loadCandidates">重试</el-button>
      </el-alert>
      <el-table
        v-else
        v-loading="candidateLoading"
        :data="candidates"
        row-key="targetProcessInstanceId"
        @selection-change="selection = $event"
      >
        <el-table-column type="selection" width="46" :selectable="selectable" reserve-selection />
        <el-table-column label="审批" min-width="240">
          <template #default="scope"
            ><b>{{ scope.row.name }}</b
            ><div class="relation-meta">{{ scope.row.displayNo || '无可读编号' }}</div></template
          >
        </el-table-column>
        <el-table-column prop="processDefinitionName" label="流程类型" min-width="150" />
        <el-table-column label="状态" width="100"
          ><template #default="scope"
            ><dict-tag
              :type="DICT_TYPE.BPM_PROCESS_INSTANCE_STATUS"
              :value="scope.row.status" /></template
        ></el-table-column>
        <el-table-column label="发起时间" width="170"
          ><template #default="scope">{{
            formatDate(scope.row.startTime)
          }}</template></el-table-column
        >
        <template #empty><el-empty description="暂无可关联审批" /></template>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="candidateTotal"
        layout="total, prev, pager, next"
        @current-change="loadCandidates"
      />
      <template #footer
        ><el-button @click="selectorVisible = false">取消</el-button
        ><el-button type="primary" :disabled="mergedSelection.length > 20" @click="confirmSelection"
          >确认（{{ mergedSelection.length }}/20）</el-button
        ></template
      >
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      title="关联审批详情"
      width="min(1040px, 96vw)"
      destroy-on-close
    >
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <el-alert
        v-else-if="detailError"
        :title="detailError"
        type="error"
        show-icon
        :closable="false"
        ><el-button link type="primary" @click="loadDetail">重试</el-button></el-alert
      >
      <el-result
        v-else-if="detail?.relation?.detailAvailable === false"
        icon="info"
        title="审批记录不可用"
        sub-title="目标历史已清理，仅保留发起时冻结摘要"
      />
      <template v-else-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题">{{ detail.relation.name }}</el-descriptions-item>
          <el-descriptions-item label="流程">{{
            detail.relation.processDefinitionName
          }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{
            detail.relation.startUserName
          }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{
            formatDate(detail.relation.startTime)
          }}</el-descriptions-item>
        </el-descriptions>
        <el-tabs class="mt-16px">
          <el-tab-pane label="表单">
            <form-create
              v-if="detailForm.rule.length"
              v-model="detailForm.value"
              v-model:api="detailFormApi"
              :option="detailForm.option"
              :rule="detailForm.rule"
            />
          </el-tab-pane>
          <el-tab-pane label="审批轨迹"
            ><ProcessInstanceTimeline :activity-nodes="detail.approvalDetail?.activityNodes || []"
          /></el-tab-pane>
          <el-tab-pane label="流程图">
            <ProcessInstanceSimpleViewer
              v-if="detail.approvalDetail?.processDefinition?.modelType === BpmModelType.SIMPLE"
              :loading="false"
              :model-view="detail.modelView"
            />
            <ProcessInstanceBpmnViewer v-else :loading="false" :model-view="detail.modelView" />
          </el-tab-pane>
        </el-tabs>
      </template>
      <template #footer
        ><el-button @click="detailVisible = false">关闭</el-button
        ><el-button v-if="detail?.relation?.detailAvailable" :icon="Printer" @click="loadPrint"
          >打印数据</el-button
        ></template
      >
    </el-dialog>
    <PrintDialog ref="printRef" />
  </div>
</template>

<script lang="ts" setup>
import { Delete, Link, Printer, Search } from '@element-plus/icons-vue'
import type { Api as FormCreateApi } from '@form-create/element-ui'
import * as ProcessInstanceApi from '@/api/bpm/processInstance'
import type { ProcessInstanceRelationVO } from '@/api/bpm/processInstance'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { BpmModelType } from '@/utils/constants'
import { formatDate } from '@/utils/formatTime'
import { setConfAndFields2 } from '@/utils/formCreate'
import ProcessInstanceTimeline from '@/views/bpm/processInstance/detail/ProcessInstanceTimeline.vue'
import ProcessInstanceBpmnViewer from '@/views/bpm/processInstance/detail/ProcessInstanceBpmnViewer.vue'
import ProcessInstanceSimpleViewer from '@/views/bpm/processInstance/detail/ProcessInstanceSimpleViewer.vue'
import PrintDialog from '@/views/bpm/processInstance/detail/PrintDialog.vue'

defineOptions({ name: 'ProcessInstanceSelect' })
const props = withDefaults(
  defineProps<{
    modelValue?: string[]
    disabled?: boolean
    formField?: string
    processInstanceId?: string
    mode?: 'create' | 'detail'
  }>(),
  { modelValue: () => [], disabled: false, formField: '', mode: 'create' }
)
const emit = defineEmits<{ (event: 'update:modelValue', value: string[]): void }>()
const readonly = computed(
  () => props.disabled || props.mode === 'detail' || Boolean(props.processInstanceId)
)
const selectedItems = ref<ProcessInstanceRelationVO[]>([])
const loading = ref(false)
const error = ref('')
const selectorVisible = ref(false)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  processDefinitionKey: '',
  status: undefined as number | undefined
})
const candidates = ref<ProcessInstanceRelationVO[]>([])
const candidateTotal = ref(0)
const candidateLoading = ref(false)
const candidateError = ref('')
const selection = ref<ProcessInstanceRelationVO[]>([])
const mergedSelection = computed(() => {
  const map = new Map(selectedItems.value.map((item) => [item.targetProcessInstanceId, item]))
  selection.value.forEach((item) => map.set(item.targetProcessInstanceId, item))
  return [...map.values()]
})
const selectable = (row: ProcessInstanceRelationVO) =>
  selectedItems.value.some(
    (item) => item.targetProcessInstanceId === row.targetProcessInstanceId
  ) || mergedSelection.value.length < 20

const loadRelations = async () => {
  if (!readonly.value || !props.processInstanceId || !props.formField) return
  loading.value = true
  error.value = ''
  try {
    const groups = (await ProcessInstanceApi.getRelationList(props.processInstanceId)) as Record<
      string,
      ProcessInstanceRelationVO[]
    >
    selectedItems.value = [...(groups[props.formField] || [])] as any
  } catch (cause: any) {
    error.value = cause?.message || '关联审批加载失败'
  } finally {
    loading.value = false
  }
}
const hydrateCreateValues = async () => {
  const ids = (props.modelValue || []) as string[]
  if (!ids.length) {
    selectedItems.value = []
    return
  }
  try {
    const page = await ProcessInstanceApi.getRelationCandidatePage({ pageNo: 1, pageSize: 100 })
    const map = new Map<string, ProcessInstanceRelationVO>(
      page.list.map(
        (item: ProcessInstanceRelationVO) =>
          [item.targetProcessInstanceId, item] as [string, ProcessInstanceRelationVO]
      )
    )
    selectedItems.value = ids.map(
      (id) =>
        map.get(id) ||
        ({
          targetProcessInstanceId: id,
          name: '已失效审批',
          detailAvailable: false
        } as ProcessInstanceRelationVO)
    )
  } catch {
    selectedItems.value = ids.map(
      (id) =>
        ({
          targetProcessInstanceId: id,
          name: '已选择审批',
          detailAvailable: false
        }) as ProcessInstanceRelationVO
    )
  }
}
const loadCandidates = async () => {
  candidateLoading.value = true
  candidateError.value = ''
  try {
    const page = await ProcessInstanceApi.getRelationCandidatePage(query)
    candidates.value = page.list
    candidateTotal.value = page.total
  } catch (cause: any) {
    candidateError.value = cause?.message || '候选审批加载失败'
  } finally {
    candidateLoading.value = false
  }
}
const openSelector = () => {
  selectorVisible.value = true
  selection.value = []
  loadCandidates()
}
const search = () => {
  query.pageNo = 1
  loadCandidates()
}
const confirmSelection = () => {
  selectedItems.value = mergedSelection.value.slice(0, 20)
  emit(
    'update:modelValue',
    selectedItems.value.map((item) => item.targetProcessInstanceId)
  )
  selectorVisible.value = false
}
const remove = (id: string) => {
  selectedItems.value = selectedItems.value.filter((item) => item.targetProcessInstanceId !== id)
  emit(
    'update:modelValue',
    selectedItems.value.map((item) => item.targetProcessInstanceId)
  )
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<any>()
const activeRelation = ref<ProcessInstanceRelationVO>()
const detailForm = ref<any>({ rule: [], option: {}, value: {} })
const detailFormApi = ref<FormCreateApi>()
const openDetail = (item: ProcessInstanceRelationVO) => {
  activeRelation.value = item
  detailVisible.value = true
  loadDetail()
}
const loadDetail = async () => {
  if (!activeRelation.value?.id) return
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await ProcessInstanceApi.getRelationDetail(activeRelation.value.id)
    const approval = detail.value?.approvalDetail
    if (approval?.processDefinition?.formFields) {
      setConfAndFields2(
        detailForm,
        approval.processDefinition.formConf,
        approval.processDefinition.formFields,
        approval.processInstance.formVariables,
        { mode: 'detail', processInstanceId: activeRelation.value.targetProcessInstanceId }
      )
      nextTick(() => {
        detailFormApi.value?.btn.show(false)
        detailFormApi.value?.resetBtn.show(false)
        detailFormApi.value?.disabled(true)
      })
    }
  } catch (cause: any) {
    detailError.value = cause?.message || '关联审批详情加载失败'
  } finally {
    detailLoading.value = false
  }
}
const printRef = ref()
const loadPrint = async () => {
  if (activeRelation.value?.id) await printRef.value?.openRelation(activeRelation.value.id)
}

watch(() => [props.processInstanceId, props.formField, readonly.value], loadRelations, {
  immediate: true
})
watch(() => props.modelValue, hydrateCreateValues, { immediate: true, deep: true })
</script>

<style scoped>
.process-instance-select {
  display: grid;
  gap: 12px;
  width: 100%;
}
.relation-list {
  display: grid;
  gap: 8px;
}
.relation-row {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}
.relation-main {
  min-width: 0;
  flex: 1;
  cursor: pointer;
}
.relation-title {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.relation-no {
  margin-right: 8px;
  color: var(--el-color-primary);
}
.relation-meta {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

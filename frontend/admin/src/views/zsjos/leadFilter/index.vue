<template>
  <ContentWrap>
    <div class="filter-heading">
      <div>
        <h3>客资筛选方案</h3>
        <p>分别维护提交人、负责人和审批人的收件箱分组。只有发布后的版本会影响员工工作台。</p>
      </div>
      <div class="heading-actions">
        <el-tag v-if="config" type="success" effect="plain"
          >已发布 V{{ config.publishedVersion }}</el-tag
        >
        <el-button :icon="Clock" @click="openHistory">版本记录</el-button>
      </div>
    </div>
  </ContentWrap>

  <ContentWrap>
    <div class="audience-toolbar">
      <el-segmented v-model="audience" :options="audienceOptions" @change="loadAll" />
      <span class="toolbar-note">{{
        audience === 'submitter' ? '员工查看自己提交的客资' : audience === 'owner' ? '销售查看自己负责的客资' : '审批人查看自己待办或已办的成交订单'
      }}</span>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default
        ><el-button link type="primary" @click="loadAll">重新加载</el-button></template
      >
    </el-alert>

    <div v-else v-loading="loading" class="filter-editor">
      <el-empty v-if="!loading && groups.length === 0" description="暂无筛选分组" />
      <el-collapse v-else v-model="activeGroups">
        <el-collapse-item v-for="(group, groupIndex) in groups" :key="group.key" :name="group.key">
          <template #title>
            <div class="group-title">
              <el-switch v-model="group.enabled" @click.stop />
              <strong>{{ group.label || '未命名分组' }}</strong>
              <el-tag size="small" effect="plain">{{ group.key }}</el-tag>
              <span>{{ conditionSummary(group.conditions) }}</span>
            </div>
          </template>

          <div class="group-editor">
            <div class="field-grid">
              <el-form-item label="分组名称">
                <el-input v-model="group.label" maxlength="20" show-word-limit />
              </el-form-item>
              <el-form-item label="稳定编码">
                <el-input v-model="group.key" :disabled="group.key === 'all'" maxlength="64" />
              </el-form-item>
              <el-form-item label="二级标题">
                <el-input
                  v-model="group.sectionLabel"
                  placeholder="无二级项时可留空"
                  maxlength="20"
                />
              </el-form-item>
            </div>

            <div class="editor-section">
              <div class="section-heading">
                <strong>分组条件</strong>
                <el-button
                  text
                  type="primary"
                  :disabled="group.key === 'all' || group.conditions.length >= 2"
                  @click="addCondition(group.conditions)"
                >
                  <Icon icon="ep:plus" /> 添加条件
                </el-button>
              </div>
              <div v-if="group.conditions.length" class="condition-list">
                <div
                  v-for="(condition, conditionIndex) in group.conditions"
                  :key="conditionIndex"
                  class="condition-row"
                >
                  <el-select
                    v-model="condition.field"
                    placeholder="选择字段"
                    @change="condition.values = []"
                  >
                    <el-option
                      v-for="capability in capabilities"
                      :key="capability.field"
                      :label="capability.label"
                      :value="capability.field"
                    />
                  </el-select>
                  <el-select
                    v-model="condition.values"
                    multiple
                    collapse-tags
                    collapse-tags-tooltip
                    placeholder="选择允许值"
                  >
                    <el-option
                      v-for="value in capabilityValues(condition.field)"
                      :key="value.value"
                      :label="value.label"
                      :value="value.value"
                    />
                  </el-select>
                  <el-button
                    :icon="Delete"
                    circle
                    text
                    type="danger"
                    @click="group.conditions.splice(conditionIndex, 1)"
                  />
                </div>
              </div>
              <el-text v-else type="info">不限制条件，将匹配该视角下的全部客资。</el-text>
            </div>

            <div class="editor-section">
              <div class="section-heading">
                <strong>二级筛选项</strong>
                <el-button
                  text
                  type="primary"
                  :disabled="group.options.length >= 20"
                  @click="addOption(group)"
                  ><Icon icon="ep:plus" /> 添加筛选项</el-button
                >
              </div>
              <el-table :data="group.options" row-key="key" empty-text="该分组不显示二级筛选">
                <el-table-column label="显示" width="72"
                  ><template #default="scope"><el-switch v-model="scope.row.enabled" /></template
                ></el-table-column>
                <el-table-column label="名称" min-width="150"
                  ><template #default="scope"
                    ><el-input v-model="scope.row.label" maxlength="20" /></template
                ></el-table-column>
                <el-table-column label="编码" min-width="160"
                  ><template #default="scope"
                    ><el-input
                      v-model="scope.row.key"
                      :disabled="scope.row.key === 'all'"
                      maxlength="64" /></template
                ></el-table-column>
                <el-table-column label="条件" min-width="360">
                  <template #default="scope">
                    <div class="option-conditions">
                      <div
                        v-for="(condition, conditionIndex) in scope.row.conditions"
                        :key="conditionIndex"
                        class="condition-row compact"
                      >
                        <el-select v-model="condition.field" @change="condition.values = []">
                          <el-option
                            v-for="capability in capabilities"
                            :key="capability.field"
                            :label="capability.label"
                            :value="capability.field"
                          />
                        </el-select>
                        <el-select v-model="condition.values" multiple collapse-tags>
                          <el-option
                            v-for="value in capabilityValues(condition.field)"
                            :key="value.value"
                            :label="value.label"
                            :value="value.value"
                          />
                        </el-select>
                        <el-button
                          :icon="Delete"
                          circle
                          text
                          type="danger"
                          @click="scope.row.conditions.splice(conditionIndex, 1)"
                        />
                      </div>
                      <el-button
                        v-if="scope.row.key !== 'all' && scope.row.conditions.length < 2"
                        link
                        type="primary"
                        @click="addCondition(scope.row.conditions)"
                        >添加条件</el-button
                      >
                      <el-text v-if="scope.row.key === 'all'" type="info">继承分组条件</el-text>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="150" fixed="right">
                  <template #default="scope">
                    <el-button-group>
                      <el-button
                        :icon="ArrowUp"
                        :disabled="scope.$index === 0"
                        @click="move(group.options, scope.$index, -1)"
                      />
                      <el-button
                        :icon="ArrowDown"
                        :disabled="scope.$index === group.options.length - 1"
                        @click="move(group.options, scope.$index, 1)"
                      />
                      <el-button
                        :icon="Delete"
                        type="danger"
                        :disabled="scope.row.key === 'all'"
                        @click="group.options.splice(scope.$index, 1)"
                      />
                    </el-button-group>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <div class="group-actions">
              <el-button
                :icon="ArrowUp"
                :disabled="groupIndex === 0"
                @click="move(groups, groupIndex, -1)"
                >上移</el-button
              >
              <el-button
                :icon="ArrowDown"
                :disabled="groupIndex === groups.length - 1"
                @click="move(groups, groupIndex, 1)"
                >下移</el-button
              >
              <el-button
                :icon="Delete"
                type="danger"
                plain
                :disabled="group.key === 'all'"
                @click="groups.splice(groupIndex, 1)"
                >删除分组</el-button
              >
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>

      <el-button
        class="add-group"
        :icon="Plus"
        plain
        :disabled="groups.length >= 20"
        @click="addGroup"
        >新增一级分组</el-button
      >
      <div class="footer-actions">
        <el-button v-hasPermi="['zsjos:lead-filter:update']" :loading="saving" @click="save"
          >保存草稿</el-button
        >
        <el-button
          v-hasPermi="['zsjos:lead-filter:publish']"
          type="primary"
          :loading="publishing"
          @click="publishConfig"
          >发布到工作台</el-button
        >
      </div>
    </div>
  </ContentWrap>

  <Dialog v-model="historyVisible" title="版本记录" width="680px">
    <el-table v-loading="historyLoading" :data="versions" empty-text="暂无发布记录">
      <el-table-column label="版本" prop="versionNo" width="100"
        ><template #default="scope">V{{ scope.row.versionNo }}</template></el-table-column
      >
      <el-table-column label="发布时间" prop="publishedAt" min-width="190" :formatter="zsjosDateFormatter" />
      <el-table-column label="发布人" prop="publishedBy" width="120"
        ><template #default="scope">用户 #{{ scope.row.publishedBy }}</template></el-table-column
      >
      <el-table-column label="操作" width="110">
        <template #default="scope">
          <el-button
            v-hasPermi="['zsjos:lead-filter:publish']"
            link
            type="primary"
            :disabled="scope.row.versionNo === config?.publishedVersion"
            @click="rollbackVersion(scope.row.versionNo)"
            >回滚</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </Dialog>
</template>

<script lang="ts" setup>
import { ArrowDown, ArrowUp, Clock, Delete, Plus } from '@element-plus/icons-vue'
import * as LeadFilterApi from '@/api/zsjos/leadFilter'
import { zsjosDateFormatter } from '@/utils/zsjosTime'

defineOptions({ name: 'ZsjosLeadFilter' })

const message = useMessage()
const audience = ref<LeadFilterApi.LeadFilterAudience>('submitter')
const audienceOptions = [
  { label: '提交人视角', value: 'submitter' },
  { label: '负责人视角', value: 'owner' },
  { label: '审批人视角', value: 'reviewer' }
]
const config = ref<LeadFilterApi.LeadFilterAdminVO>()
const groups = ref<LeadFilterApi.LeadFilterGroupVO[]>([])
const capabilities = ref<LeadFilterApi.LeadFilterCapabilityVO[]>([])
const versions = ref<LeadFilterApi.LeadFilterVersionVO[]>([])
const activeGroups = ref<string[]>([])
const loading = ref(true)
const saving = ref(false)
const publishing = ref(false)
const error = ref('')
const historyVisible = ref(false)
const historyLoading = ref(false)
const keyPattern = /^[a-z][a-z0-9_]{1,63}$/

const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
const capabilityValues = (field: string) =>
  capabilities.value.find((item) => item.field === field)?.values || []
const conditionSummary = (conditions: LeadFilterApi.LeadFilterConditionVO[]) => {
  if (!conditions.length) return '全部匹配'
  return conditions
    .map((condition) => {
      const capability = capabilities.value.find((item) => item.field === condition.field)
      const labels = condition.values.map(
        (value) => capability?.values.find((item) => item.value === value)?.label || value
      )
      return `${capability?.label || condition.field}：${labels.join('、')}`
    })
    .join('；')
}

const loadConfig = async () => {
  loading.value = true
  error.value = ''
  try {
    config.value = await LeadFilterApi.getConfig(audience.value)
    groups.value = clone(config.value.draftGroups || [])
    activeGroups.value = groups.value.slice(0, 2).map((item) => item.key)
  } catch (loadError: any) {
    error.value = loadError?.msg || loadError?.message || '筛选方案加载失败'
  } finally {
    loading.value = false
  }
}
const loadAll = async () => {
  try {
    capabilities.value = await LeadFilterApi.getCapabilities(audience.value)
    await loadConfig()
  } catch (loadError: any) {
    error.value = loadError?.msg || loadError?.message || '筛选能力加载失败'
    loading.value = false
  }
}

const addCondition = (conditions: LeadFilterApi.LeadFilterConditionVO[]) => {
  const first = capabilities.value[0]
  conditions.push({ field: first?.field || '', values: [] })
}
const addGroup = () => {
  const key = `group_${Date.now()}`
  groups.value.push({
    key,
    label: '新分组',
    sort: groups.value.length * 10,
    enabled: true,
    sectionLabel: '当前环节',
    conditions: [],
    options: []
  })
  activeGroups.value.push(key)
}
const addOption = (group: LeadFilterApi.LeadFilterGroupVO) => {
  if (!group.options.length) {
    group.options.push({ key: 'all', label: '全部', sort: 0, enabled: true, conditions: [] })
  }
  group.options.push({
    key: `option_${Date.now()}`,
    label: '新筛选项',
    sort: group.options.length * 10,
    enabled: true,
    conditions: []
  })
}
const move = <T,>(items: T[], index: number, offset: number) => {
  const target = index + offset
  if (target < 0 || target >= items.length) return
  const [item] = items.splice(index, 1)
  items.splice(target, 0, item)
}
const normalizeSort = () => {
  groups.value.forEach((group, groupIndex) => {
    group.sort = groupIndex * 10
    group.options.forEach((option, optionIndex) => {
      option.sort = optionIndex * 10
    })
  })
}
const validate = () => {
  if (
    audience.value !== 'reviewer' &&
    !groups.value.some(
      (group) => group.key === 'all' && group.enabled && group.conditions.length === 0
    )
  )
    throw new Error('必须保留启用且无条件的 all 分组')
  if (groups.value.length > 20) throw new Error('一级分组不能超过 20 个')
  const groupKeys = new Set<string>()
  for (const group of groups.value) {
    if (!group.key || !group.label) throw new Error('分组名称和编码不能为空')
    if (!keyPattern.test(group.key))
      throw new Error(`分组“${group.label}”的编码只能使用小写字母、数字和下划线`)
    if (groupKeys.has(group.key)) throw new Error(`分组编码“${group.key}”不能重复`)
    groupKeys.add(group.key)
    if (group.conditions.some((condition) => !condition.field || !condition.values.length))
      throw new Error(`分组“${group.label}”存在未完成的条件`)
    if (group.options.length > 20) throw new Error(`分组“${group.label}”的筛选项不能超过 20 个`)
    if (
      group.options.length &&
      !group.options.some(
        (option) => option.key === 'all' && option.enabled && option.conditions.length === 0
      )
    )
      throw new Error(`分组“${group.label}”必须保留无条件的“全部”筛选项`)
    const optionKeys = new Set<string>()
    for (const option of group.options) {
      if (!option.key || !option.label) throw new Error(`分组“${group.label}”存在未命名筛选项`)
      if (!keyPattern.test(option.key))
        throw new Error(`筛选项“${option.label}”的编码只能使用小写字母、数字和下划线`)
      if (optionKeys.has(option.key))
        throw new Error(`分组“${group.label}”中的筛选项编码“${option.key}”不能重复`)
      optionKeys.add(option.key)
      if (option.conditions.some((condition) => !condition.field || !condition.values.length))
        throw new Error(`筛选项“${option.label}”存在未完成的条件`)
    }
  }
}
const save = async () => {
  try {
    validate()
  } catch (validationError: any) {
    message.error(validationError.message)
    return
  }
  normalizeSort()
  saving.value = true
  try {
    await LeadFilterApi.saveDraft(audience.value, groups.value)
    message.success('草稿已保存，发布前不会影响员工工作台')
    await loadConfig()
  } finally {
    saving.value = false
  }
}
const publishConfig = async () => {
  try {
    validate()
  } catch (validationError: any) {
    message.error(validationError.message)
    return
  }
  await ElMessageBox.confirm('将先保存当前草稿，再发布到员工工作台。确认继续？', '发布筛选方案', {
    type: 'warning'
  })
  publishing.value = true
  try {
    normalizeSort()
    await LeadFilterApi.saveDraft(audience.value, groups.value)
    const version = await LeadFilterApi.publish(audience.value)
    message.success(`已发布 V${version}`)
    await loadConfig()
  } finally {
    publishing.value = false
  }
}
const openHistory = async () => {
  historyVisible.value = true
  historyLoading.value = true
  try {
    versions.value = await LeadFilterApi.getVersions(audience.value)
  } finally {
    historyLoading.value = false
  }
}
const rollbackVersion = async (versionNo: number) => {
  await ElMessageBox.confirm(
    `将 V${versionNo} 复制为新的发布版本，历史记录不会删除。`,
    '回滚筛选方案',
    { type: 'warning' }
  )
  const newVersion = await LeadFilterApi.rollback(audience.value, versionNo)
  message.success(`已回滚并发布为 V${newVersion}`)
  historyVisible.value = false
  await loadConfig()
}

onMounted(loadAll)
</script>

<style scoped>
.filter-heading,
.audience-toolbar,
.section-heading,
.group-actions,
.footer-actions,
.heading-actions {
  display: flex;
  align-items: center;
}

.filter-heading {
  justify-content: space-between;
  gap: 16px;
}

.filter-heading h3 {
  margin: 0 0 8px;
  font-size: 18px;
}

.filter-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.heading-actions {
  gap: 10px;
}

.audience-toolbar {
  gap: 16px;
  margin-bottom: 18px;
}

.toolbar-note {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.filter-editor {
  min-height: 240px;
}

.group-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.group-title > span:last-child {
  overflow: hidden;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-editor {
  padding: 6px 12px 18px;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.editor-section {
  padding: 14px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.section-heading {
  justify-content: space-between;
  margin-bottom: 10px;
}

.condition-list,
.option-conditions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.condition-row {
  display: grid;
  grid-template-columns: minmax(150px, 220px) minmax(260px, 1fr) 36px;
  gap: 8px;
}

.condition-row.compact {
  grid-template-columns: 130px minmax(180px, 1fr) 32px;
}

.group-actions {
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.add-group {
  width: 100%;
  margin-top: 16px;
}

.footer-actions {
  position: sticky;
  bottom: 0;
  z-index: 2;
  padding: 16px 0 4px;
  background: var(--el-bg-color);
  justify-content: flex-end;
  gap: 8px;
}

@media (width <= 768px) {
  .filter-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .audience-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .field-grid {
    grid-template-columns: 1fr;
  }

  .condition-row,
  .condition-row.compact {
    grid-template-columns: 1fr 1fr 32px;
  }

  .group-title > span:last-child {
    display: none;
  }

  .footer-actions {
    position: static;
  }
}
</style>

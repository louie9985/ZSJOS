<template>
  <el-dialog
    v-model="visible"
    :title="educationSelfSourced ? '教务录入客资' : selfSourced ? '销售录入客资' : '提交客资'"
    width="min(760px, 96vw)"
    destroy-on-close
  >
    <el-alert v-if="optionError" :title="optionError" type="error" show-icon class="mb-12px"
      ><template #default><el-button link @click="loadOptions">重试</el-button></template></el-alert
    >
    <el-form
      ref="formRef"
      v-loading="optionLoading"
      :model="form"
      :rules="rules"
      label-width="110px"
    >
      <el-row :gutter="16"
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="客户姓名" prop="name"
            ><el-input v-model="form.name" /></el-form-item></el-col
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="手机号" prop="mobile" :required="!form.wechatId.trim()"
            ><el-input v-model="form.mobile" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="微信号" :required="!form.mobile.trim()"><el-input v-model="form.wechatId" /></el-form-item></el-col
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="客户地区" prop="region"
            ><el-cascader
              v-model="form.region"
              :options="areaOptions"
              :props="{ value: 'selectionCode', label: 'name', children: 'children' }"
              class="w-100%" /></el-form-item></el-col
      ></el-row>
      <div v-if="selfSourced" class="archive-lookup">
        <el-button :loading="archiveLoading" @click="queryArchive">查找档案</el-button>
        <el-alert v-if="archiveError" :title="archiveError" type="error" show-icon :closable="false"><el-button link @click="queryArchive">重试</el-button></el-alert>
        <el-alert v-if="archiveCandidates" :closable="false" type="info" show-icon
          :title="archiveCandidates.length ? '该客户已在系统中存在，请确认客户档案与已有信息一致，本次创建将关联至此档案' : '系统中未查询到重复档案，本次将新建档案、客资'"
          :description="archiveCandidates.length > 1 ? `查询到 ${archiveCandidates.length} 份档案，请选择本次关联的客户档案。` : archiveCandidates.length === 1 ? '已默认选中下方档案，请核对。姓名和联系方式以本次填写为准。' : undefined" />
        <el-radio-group v-if="archiveCandidates?.length" class="archive-options" :model-value="selectedArchive?.personId" @change="selectArchiveId">
          <el-radio v-for="item in archiveCandidates" :key="item.personId" :value="item.personId" class="archive-option">
            <div class="archive-heading"><strong>{{ item.name || '姓名未填写' }}</strong><span>{{ item.personNo || '客户档案' }}</span></div>
            <dl class="archive-fields"><div><dt>手机号</dt><dd>{{ item.mobile || '未填写' }}</dd></div><div><dt>微信号</dt><dd>{{ item.wechatId || '未填写' }}</dd></div></dl>
          </el-radio>
        </el-radio-group>
      </div>
      <el-row :gutter="16"
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="来源渠道" prop="sourceChannel"
            ><el-select v-model="form.sourceChannel" class="w-100%"
              ><el-option
                v-for="item in sourceOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value" /></el-select></el-form-item></el-col
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="客资分类" prop="leadCategory"
            ><el-select v-model="form.leadCategory" class="w-100%"
              ><el-option
                v-for="item in categoryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value" /></el-select></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="意向课程" prop="spuRef"
            ><el-select v-model="form.spuRef" filterable class="w-100%" @change="form.skuRef = ''"
              ><el-option
                v-for="item in catalog.spus"
                :key="item.spuRef"
                :label="item.spuName"
                :value="item.spuRef" /></el-select></el-form-item></el-col
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="具体方案" prop="skuRef"
            ><el-select v-model="form.skuRef" filterable class="w-100%"
              ><el-option
                v-for="item in skuOptions"
                :key="item.skuRef"
                :label="skuLabel(item)"
                :value="item.skuRef" /></el-select></el-form-item></el-col
      ></el-row>
      <el-alert v-if="selfSourced" :closable="false" type="info"
        :title="form.newMediaProviderUserId ? '业务来源：新媒体引流，由本人代录' : '业务来源：本人自拓，无新媒体引流'"
        description="由新媒体引流、本人先收到信息时，请选择对应新媒体人员。录入人和客资提供方分别记录。" />
      <el-form-item v-if="selfSourced" label="新媒体提供方">
        <el-select v-model="form.newMediaProviderUserId" clearable filterable placeholder="本人自拓不选；新媒体引流请选择人员">
          <el-option v-for="item in providers" :key="item.id" :label="item.nickname" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="!selfSourced" label="派单方式" prop="dispatchMode"
        ><el-radio-group v-model="form.dispatchMode"
          ><el-radio value="auto">自动分配</el-radio
          ><el-radio v-if="canSpecifySales" value="specified">指定销售</el-radio></el-radio-group
        ></el-form-item
      >
      <el-form-item
        v-if="!selfSourced && canSpecifySales && form.dispatchMode === 'specified'"
        label="指定销售"
        prop="specifiedSalesUserId"
        ><el-select v-model="form.specifiedSalesUserId" filterable class="w-100%"
          ><el-option
            v-for="item in sales"
            :key="item.id"
            :label="item.nickname"
            :value="item.id" /></el-select
      ></el-form-item>
      <el-alert v-if="automatic" type="info" show-icon :closable="false" class="mb-12px"
        title="提交后将自动生成首跟记录并判定有效，请确认已联系客户且有意向。"
        description="本次新建客资将归属本人，已有档案可复用。" />
      <el-form-item label="备注" prop="remark" :required="automatic"
        ><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit
      /></el-form-item>
      <el-form-item v-if="automatic" label="下次跟进" prop="selfSourcedNextFollowUpAt" :required="automatic">
        <el-date-picker v-model="form.selfSourcedNextFollowUpAt" type="datetime" value-format="x" placeholder="请选择下次跟进时间" class="w-100%" />
      </el-form-item>
      <el-alert v-if="automatic" type="info" :closable="false"
        :title="'提交摘要：新建后归属本人、自动首跟、判有效、生成商机。'"
        :description="form.selfSourcedNextFollowUpAt ? '后续提醒：' + formatZsjosTimestamp(Number(form.selfSourcedNextFollowUpAt)) : '请选择下次跟进时间'" />
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="saving" :disabled="!!optionError" @click="submit"
        >确认提交</el-button
      ></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import { formatZsjosTimestamp } from '@/utils/zsjosTime'
import { catalogSpecs, specText } from '@/utils/productSpecs'
import type { FormInstance, FormRules } from 'element-plus'
import * as MenuApi from '@/api/zsjos/workbenchMenus'
import * as AreaApi from '@/api/system/area'
import { getSimpleDictDataList, type DictDataVO } from '@/api/system/dict/dict.data'
import { useUserStoreWithOut } from '@/store/modules/user'
const props = defineProps<{ selfSourced?: boolean; educationSelfSourced?: boolean }>()
const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const userStore = useUserStoreWithOut()
const visible = ref(false)
const saving = ref(false)
const idempotencyKey = ref<string>()
const optionLoading = ref(false)
const optionError = ref('')
const formRef = ref<FormInstance>()
const areaOptions = ref<any[]>([])
const sourceOptions = ref<DictDataVO[]>([])
const categoryOptions = ref<DictDataVO[]>([])
const providers = ref<Array<{ id: number; nickname: string }>>([])
const sales = ref<Array<{ id: number; nickname: string }>>([])
const catalog = reactive<{ spus: any[]; skus: any[] }>({ spus: [], skus: [] })
const skuLabel = (sku: { spuRef: string; skuName: string; attrValues: Record<string, string>; specs?: import('@/utils/productSpecs').ProductSpec[] }) =>
  [sku.skuName, (sku.specs ?? catalogSpecs(sku.attrValues, catalog.spus.find(spu => spu.spuRef === sku.spuRef)?.attrs)).map(specText).join(' · ')].filter(Boolean).join(' · ')
const canSpecifySales = computed(() => userStore.getPermissions.has('zsjos:lead:submit:specify'))
const emptyForm = () => ({
  name: '',
  newMediaProviderUserId: undefined as number | undefined,
  mobile: '',
  wechatId: '',
  region: [] as string[],
  sourceChannel: '',
  leadCategory: '',
  spuRef: '',
  skuRef: '',
  dispatchMode: 'auto',
  specifiedSalesUserId: undefined as number | undefined,
  selfSourcedNextFollowUpAt: undefined as string | undefined,
  remark: ''
})
const form = reactive(emptyForm())
const archiveMobile = computed(() => form.mobile)
const archiveWechat = computed(() => form.wechatId)
const archiveCandidates = ref<MenuApi.LeadArchiveCandidate[]>()
const selectedArchive = ref<MenuApi.LeadArchiveCandidate>()
const archiveLoading = ref(false)
const archiveError = ref('')
let archiveSequence = 0
const invalidateArchive = () => {
  archiveSequence++
  archiveCandidates.value = undefined; selectedArchive.value = undefined; archiveError.value = ''; archiveLoading.value = false
}
const selectArchiveId = (id: string | number | boolean | undefined) => {
  const selected = archiveCandidates.value?.find(item => item.personId === id)
  selectedArchive.value = selected
}
watch(() => [form.mobile.trim(), form.wechatId.trim().toLowerCase()], invalidateArchive, { flush: 'sync' })
const queryArchive = async () => {
  const sequence = ++archiveSequence
  archiveLoading.value = true; archiveError.value = ''; archiveCandidates.value = undefined; selectedArchive.value = undefined
  try {
    const candidates = await MenuApi.querySelfSourcedArchive(!!props.educationSelfSourced, {
      mobile: archiveMobile.value.trim() || undefined, wechatId: archiveWechat.value.trim() || undefined, idempotencyKey: crypto.randomUUID()
    })
    if (sequence !== archiveSequence) return
    archiveCandidates.value = candidates
    if (candidates.length === 1) selectArchiveId(candidates[0].personId)
  } catch (error: unknown) {
    if (sequence === archiveSequence) archiveError.value = error instanceof Error ? error.message : '查询失败，请重试'
  } finally { if (sequence === archiveSequence) archiveLoading.value = false }
}
const automatic = computed(() => !!props.selfSourced && form.newMediaProviderUserId == null)
watch(automatic, enabled => {
  if (!enabled) form.selfSourcedNextFollowUpAt = undefined
  formRef.value?.clearValidate(['remark', 'selfSourcedNextFollowUpAt'])
})
const rules: FormRules = {
  remark: [{ validator: (_r, value, cb) => !automatic.value || value?.trim() ? cb() : cb(new Error('请填写已联系客户及意向情况，作为首跟内容和判有效依据')) }],
  selfSourcedNextFollowUpAt: [{ validator: (_r, value, cb) => !automatic.value ? cb() : !value ? cb(new Error('请选择下次跟进时间')) : Number(value) > Date.now() ? cb() : cb(new Error('下次跟进时间必须晚于当前时间')) }],
  name: [{ required: true, message: '请输入客户姓名' }],
  mobile: [
    {
      validator: (_r, v, cb) =>
        v || form.wechatId ? cb() : cb(new Error('手机号和微信号至少填写一个'))
    }
  ],
  region: [{ required: true, message: '请选择客户地区' }],
  sourceChannel: [{ required: true }],
  leadCategory: [{ required: true }],
  spuRef: [{ required: true, message: '请选择意向课程' }],
  skuRef: [{ required: true, message: '请选择具体方案' }],
  dispatchMode: [{ required: true }],
  specifiedSalesUserId: [
    {
      validator: (_r, v, cb) =>
        form.dispatchMode !== 'specified' || v ? cb() : cb(new Error('请选择指定销售'))
    }
  ]
}
const skuOptions = computed(() => catalog.skus.filter((item) => item.spuRef === form.spuRef))
const loadOptions = async () => {
  optionLoading.value = true
  optionError.value = ''
  try {
    const [areas, dicts, products] = await Promise.all([
      AreaApi.getAreaTree(),
      getSimpleDictDataList(),
      MenuApi.leadCatalog()
    ])
    areaOptions.value = areas
    const all = dicts as DictDataVO[]
    sourceOptions.value = all.filter(
      (i) => i.dictType === 'zsjos_lead_source_channel' && i.status === 0
    )
    categoryOptions.value = all.filter(
      (i) => i.dictType === 'zsjos_lead_category' && i.status === 0
    )
    catalog.spus = products.spus || []
    catalog.skus = products.skus || []
    if (props.selfSourced) providers.value = await MenuApi.leadNewMediaProviders()
    if (!props.selfSourced && canSpecifySales.value) sales.value = await MenuApi.leadSalesCandidates()
  } catch (e: any) {
    optionError.value = e?.msg || e?.message || '表单配置加载失败'
  } finally {
    optionLoading.value = false
  }
}
const open = () => {
  invalidateArchive()
  idempotencyKey.value = undefined
  Object.assign(form, emptyForm())
  if (!canSpecifySales.value) form.dispatchMode = 'auto'
  visible.value = true
  void loadOptions()
}
const submit = async () => {
  if (saving.value) return
  if (props.selfSourced) {
    if (archiveLoading.value || !archiveCandidates.value) { message.warning('请先查询档案'); return }
    if (archiveCandidates.value.length && !selectedArchive.value) { message.warning('请先选择本次创建客资使用的档案'); return }
  }
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    const [provinceCode, cityCode] = form.region
    const result = await MenuApi.createLead(
      {
        ...(props.selfSourced ? { archiveQueryConfirmed: true, archiveQueryMobile: archiveMobile.value.trim() || undefined,
          archiveQueryWechatId: archiveWechat.value.trim() || undefined, selectedPersonId: selectedArchive.value?.personId,
          archiveSnapshotKey: selectedArchive.value?.snapshotKey } : {}),
        name: form.name.trim(),
        mobile: form.mobile.trim() || undefined,
        wechatId: form.wechatId.trim() || undefined,
        provinceCode,
        cityCode,
        intendedProducts: [
          {
            spuRef: form.spuRef,
            skuRef: form.skuRef,
            spuUnknown: false,
            skuUnknown: false,
            primary: true
          }
        ],
        sourceChannel: form.sourceChannel,
        leadCategory: form.leadCategory,
        remark: form.remark.trim() || undefined,
        attachments: [],
        dispatchMode: props.selfSourced ? 'auto' : form.dispatchMode,
        newMediaProviderUserId: props.selfSourced ? form.newMediaProviderUserId : undefined,
        specifiedSalesUserId: props.selfSourced ? undefined : form.specifiedSalesUserId,
        selfSourcedNextFollowUpAt: automatic.value && form.selfSourcedNextFollowUpAt ? Number(form.selfSourcedNextFollowUpAt) : undefined,
        idempotencyKey: idempotencyKey.value ?? (idempotencyKey.value = crypto.randomUUID())
      },
      !!props.selfSourced, !!props.educationSelfSourced
    )
    if (result?.outcome === 'activated') message.success('客资已存在，已激活提醒')
    else if (result?.outcome === 'review_pending') message.warning('疑似重复，待复核；尚未新建或判有效')
    else if (result?.outcome === 'created') message.success(result.automaticQualificationApplied && result.qualificationStatus === 'valid' ? '客资已判有效，已生成首跟记录和商机' : '客资已提交')
    else message.warning('查重未通过，未新建客资')
    visible.value = false
    emit('success')
  } catch (e: any) {
    if (e) message.error(e?.msg || e?.message || '提交失败')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.archive-lookup { display: grid; gap: 12px; margin-bottom: 16px; }
.archive-lookup > .el-button { justify-self: start; }
.archive-options { display: grid; gap: 8px; }
.archive-option { height: auto; margin: 0; padding: 12px; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); align-items: flex-start; }
.archive-option.is-checked { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.archive-option :deep(.el-radio__label) { flex: 1; min-width: 0; white-space: normal; }
.archive-heading { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; overflow-wrap: anywhere; }
.archive-heading span, .archive-fields dt { color: var(--el-text-color-secondary); font-size: 12px; }
.archive-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 12px 0 0; }
.archive-fields dd { margin: 4px 0 0; color: var(--el-text-color-primary); overflow-wrap: anywhere; }
@media(max-width: 480px) { .archive-fields { grid-template-columns: 1fr; } }
</style>

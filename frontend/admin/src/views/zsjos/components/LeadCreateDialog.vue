<template>
  <el-dialog
    v-model="visible"
    :title="selfSourced ? '新增销售自拓客资' : '提交客资'"
    width="760px"
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
        ><el-col :span="12"
          ><el-form-item label="客户姓名" prop="name"
            ><el-input v-model="form.name" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="手机号" prop="mobile"
            ><el-input v-model="form.mobile" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="微信号"><el-input v-model="form.wechatId" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="客户地区" prop="region"
            ><el-cascader
              v-model="form.region"
              :options="areaOptions"
              :props="{ value: 'selectionCode', label: 'name', children: 'children' }"
              class="w-100%" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="来源渠道" prop="sourceChannel"
            ><el-select v-model="form.sourceChannel" class="w-100%"
              ><el-option
                v-for="item in sourceOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value" /></el-select></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="客资分类" prop="leadCategory"
            ><el-select v-model="form.leadCategory" class="w-100%"
              ><el-option
                v-for="item in categoryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value" /></el-select></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="意向课程" prop="spuRef"
            ><el-select v-model="form.spuRef" filterable class="w-100%" @change="form.skuRef = ''"
              ><el-option
                v-for="item in catalog.spus"
                :key="item.spuRef"
                :label="item.spuName"
                :value="item.spuRef" /></el-select></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="具体方案" prop="skuRef"
            ><el-select v-model="form.skuRef" filterable class="w-100%"
              ><el-option
                v-for="item in skuOptions"
                :key="item.skuRef"
                :label="item.skuName"
                :value="item.skuRef" /></el-select></el-form-item></el-col
      ></el-row>
      <el-form-item v-if="!selfSourced" label="派单方式" prop="dispatchMode"
        ><el-radio-group v-model="form.dispatchMode"
          ><el-radio value="auto">自动分配</el-radio
          ><el-radio value="specified">指定销售</el-radio></el-radio-group
        ></el-form-item
      >
      <el-form-item
        v-if="!selfSourced && form.dispatchMode === 'specified'"
        label="指定销售"
        prop="specifiedSalesUserId"
        ><el-select v-model="form.specifiedSalesUserId" filterable class="w-100%"
          ><el-option
            v-for="item in sales"
            :key="item.id"
            :label="item.nickname"
            :value="item.id" /></el-select
      ></el-form-item>
      <el-form-item label="备注"
        ><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit
      /></el-form-item>
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
import type { FormInstance, FormRules } from 'element-plus'
import * as MenuApi from '@/api/zsjos/workbenchMenus'
import * as AreaApi from '@/api/system/area'
import { getSimpleDictDataList, type DictDataVO } from '@/api/system/dict/dict.data'
const props = defineProps<{ selfSourced?: boolean }>()
const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const visible = ref(false)
const saving = ref(false)
const optionLoading = ref(false)
const optionError = ref('')
const formRef = ref<FormInstance>()
const areaOptions = ref<any[]>([])
const sourceOptions = ref<DictDataVO[]>([])
const categoryOptions = ref<DictDataVO[]>([])
const sales = ref<Array<{ id: number; nickname: string }>>([])
const catalog = reactive<{ spus: any[]; skus: any[] }>({ spus: [], skus: [] })
const emptyForm = () => ({
  name: '',
  mobile: '',
  wechatId: '',
  region: [] as string[],
  sourceChannel: '',
  leadCategory: '',
  spuRef: '',
  skuRef: '',
  dispatchMode: 'auto',
  specifiedSalesUserId: undefined as number | undefined,
  remark: ''
})
const form = reactive(emptyForm())
const rules: FormRules = {
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
    if (!props.selfSourced) sales.value = await MenuApi.leadSalesCandidates()
  } catch (e: any) {
    optionError.value = e?.msg || e?.message || '表单配置加载失败'
  } finally {
    optionLoading.value = false
  }
}
const open = () => {
  Object.assign(form, emptyForm())
  visible.value = true
  void loadOptions()
}
const submit = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const [provinceCode, cityCode] = form.region
    await MenuApi.createLead(
      {
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
        specifiedSalesUserId: props.selfSourced ? undefined : form.specifiedSalesUserId,
        idempotencyKey: crypto.randomUUID()
      },
      !!props.selfSourced
    )
    message.success('客资已提交')
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

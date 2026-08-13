<template>
  <el-dialog v-model="visible" title="新增历史客户复购" width="820px" destroy-on-close>
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
          ><el-form-item label="客户姓名" prop="customerName"
            ><el-input v-model="form.customerName" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="手机号" prop="customerMobile"
            ><el-input v-model="form.customerMobile" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="微信号"
            ><el-input v-model="form.customerWechatId" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="学员姓名" prop="studentName"
            ><el-input v-model="form.studentName" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="学员性质" prop="studentNature"
            ><el-select v-model="form.studentNature" class="w-100%"
              ><el-option
                v-for="i in option('zsjos_order_student_nature')"
                :key="i.value"
                :label="i.label"
                :value="i.value" /></el-select></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="客户地区" prop="region"
            ><el-cascader
              v-model="form.region"
              :options="areas"
              :props="{ value: 'selectionCode', label: 'name', children: 'children' }"
              class="w-100%" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="课程" prop="spuRef"
            ><el-select v-model="form.spuRef" filterable class="w-100%" @change="form.skuRef = ''"
              ><el-option
                v-for="i in catalog.spus"
                :key="i.spuRef"
                :label="i.spuName"
                :value="i.spuRef" /></el-select></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="具体方案" prop="skuRef"
            ><el-select v-model="form.skuRef" filterable class="w-100%"
              ><el-option
                v-for="i in skuOptions"
                :key="i.skuRef"
                :label="i.skuName"
                :value="i.skuRef" /></el-select></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="实收金额" prop="actualAmount"
            ><el-input-number
              v-model="form.actualAmount"
              :min="0"
              :precision="2"
              class="w-100%" /></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="缴费时间" prop="customerPaidAt"
            ><el-date-picker
              v-model="form.customerPaidAt"
              type="datetime"
              class="w-100%" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="服务周期" prop="servicePeriod"
            ><el-select v-model="form.servicePeriod" class="w-100%"
              ><el-option
                v-for="i in option('zsjos_order_service_period')"
                :key="i.value"
                :label="i.label"
                :value="i.value" /></el-select></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="学员来源" prop="studentSource"
            ><el-select v-model="form.studentSource" class="w-100%"
              ><el-option
                v-for="i in option('zsjos_order_student_source')"
                :key="i.value"
                :label="i.label"
                :value="i.value" /></el-select></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :span="12"
          ><el-form-item label="缴费方式" prop="feeMode"
            ><el-select v-model="form.feeMode" class="w-100%"
              ><el-option
                v-for="i in option('zsjos_order_fee_mode')"
                :key="i.value"
                :label="i.label"
                :value="i.value" /></el-select></el-form-item></el-col
        ><el-col :span="12"
          ><el-form-item label="支付方式" prop="paymentMethod"
            ><el-select v-model="form.paymentMethod" class="w-100%"
              ><el-option
                v-for="i in option('zsjos_order_payment_method')"
                :key="i.value"
                :label="i.label"
                :value="i.value" /></el-select></el-form-item></el-col
      ></el-row>
      <el-form-item label="复购原因" prop="repurchaseReason"
        ><el-input
          v-model="form.repurchaseReason"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="saving" :disabled="!!optionError" @click="submit"
        >提交复购</el-button
      ></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import * as MenuApi from '@/api/zsjos/workbenchMenus'
import * as AreaApi from '@/api/system/area'
import { getSimpleDictDataList, type DictDataVO } from '@/api/system/dict/dict.data'
const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const visible = ref(false)
const saving = ref(false)
const optionLoading = ref(false)
const optionError = ref('')
const formRef = ref<FormInstance>()
const areas = ref<any[]>([])
const dicts = ref<DictDataVO[]>([])
const catalog = reactive<{ spus: any[]; skus: any[] }>({ spus: [], skus: [] })
const empty = () => ({
  customerName: '',
  customerMobile: '',
  customerWechatId: '',
  studentName: '',
  studentNature: '',
  region: [] as string[],
  spuRef: '',
  skuRef: '',
  actualAmount: 0,
  customerPaidAt: new Date(),
  servicePeriod: '',
  studentSource: '',
  feeMode: '',
  paymentMethod: '',
  repurchaseReason: ''
})
const form = reactive(empty())
const rules: FormRules = {
  customerName: [{ required: true }],
  customerMobile: [
    {
      validator: (_r, v, cb) =>
        v || form.customerWechatId ? cb() : cb(new Error('手机号和微信号至少填写一个'))
    }
  ],
  studentName: [{ required: true }],
  studentNature: [{ required: true }],
  region: [{ required: true }],
  spuRef: [{ required: true }],
  skuRef: [{ required: true }],
  actualAmount: [{ required: true }],
  customerPaidAt: [{ required: true }],
  servicePeriod: [{ required: true }],
  studentSource: [{ required: true }],
  feeMode: [{ required: true }],
  paymentMethod: [{ required: true }],
  repurchaseReason: [{ required: true }]
}
const option = (type: string) => dicts.value.filter((i) => i.dictType === type && i.status === 0)
const skuOptions = computed(() => catalog.skus.filter((i) => i.spuRef === form.spuRef))
const loadOptions = async () => {
  optionLoading.value = true
  optionError.value = ''
  try {
    const [a, d, c] = await Promise.all([
      AreaApi.getAreaTree(),
      getSimpleDictDataList(),
      MenuApi.salesOrderCatalog()
    ])
    areas.value = a
    dicts.value = d
    catalog.spus = c.spus || []
    catalog.skus = c.skus || []
  } catch (e: any) {
    optionError.value = e?.msg || e?.message || '订单配置加载失败'
  } finally {
    optionLoading.value = false
  }
}
const open = () => {
  Object.assign(form, empty())
  visible.value = true
  void loadOptions()
}
const submit = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const [provinceCode, cityCode] = form.region
    const province = areas.value.find((i) => i.selectionCode === provinceCode)
    const city = province?.children?.find((i: any) => i.selectionCode === cityCode)
    await MenuApi.createExternalRepurchase({
      customerName: form.customerName.trim(),
      customerMobile: form.customerMobile.trim() || undefined,
      customerWechatId: form.customerWechatId.trim() || undefined,
      repurchaseReason: form.repurchaseReason.trim(),
      order: {
        buyerName: form.customerName.trim(),
        studentName: form.studentName.trim(),
        studentNature: form.studentNature,
        studentMobile: form.customerMobile.trim() || undefined,
        studentWechatId: form.customerWechatId.trim() || undefined,
        provinceCode,
        provinceName: province?.name || '',
        cityCode,
        cityName: city?.name || '',
        servicePeriod: form.servicePeriod,
        studentSource: form.studentSource,
        customerPaidAt: form.customerPaidAt.getTime(),
        feeMode: form.feeMode,
        paymentMethod: form.paymentMethod,
        items: [{ spuRef: form.spuRef, skuRef: form.skuRef, actualAmount: form.actualAmount }],
        paymentVouchers: [],
        idempotencyKey: crypto.randomUUID()
      }
    })
    message.success('复购订单已提交')
    visible.value = false
    emit('success')
  } catch (e: any) {
    if (e) message.error(e?.msg || e?.message || '复购提交失败')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

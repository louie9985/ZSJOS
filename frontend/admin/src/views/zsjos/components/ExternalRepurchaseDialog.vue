<template>
  <el-dialog v-model="visible" title="新增历史客户复购" width="min(820px, calc(100vw - 24px))" destroy-on-close>
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
          ><el-form-item label="客户姓名" prop="customerName"
            ><el-input v-model="form.customerName" /></el-form-item></el-col
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="手机号" prop="customerMobile" :required="!form.customerWechatId.trim()"
            ><el-input v-model="form.customerMobile" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="微信号" :required="!form.customerMobile.trim()"
            ><el-input v-model="form.customerWechatId" /></el-form-item></el-col
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="学员姓名" prop="studentName"
            ><el-input v-model="form.studentName" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="学员性质" prop="studentNature"
            ><el-select v-model="form.studentNature" class="w-100%"
              ><el-option
                v-for="i in option('zsjos_order_student_nature')"
                :key="i.value"
                :label="i.label"
                :value="i.value" /></el-select></el-form-item></el-col
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="客户地区" prop="region"
            ><el-cascader
              v-model="form.region"
              :options="areas"
              :props="{ value: 'selectionCode', label: 'name', children: 'children' }"
              class="w-100%" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="课程" prop="spuRef"
            ><el-select v-model="form.spuRef" filterable class="w-100%" @change="form.skuRef = ''"
              ><el-option
                v-for="i in catalog.spus"
                :key="i.spuRef"
                :label="i.spuName"
                :value="i.spuRef" /></el-select></el-form-item></el-col
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="具体方案" prop="skuRef"
            ><el-select v-model="form.skuRef" filterable class="w-100%"
              ><el-option
                v-for="i in skuOptions"
                :key="i.skuRef"
                :label="i.skuName"
                :value="i.skuRef" /></el-select></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="实收金额" prop="actualAmount"
            ><el-input-number
              v-model="form.actualAmount"
              :min="0"
              :precision="2"
              class="w-100%" /></el-form-item></el-col
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="缴费时间" prop="customerPaidAt"
            ><el-date-picker
              v-model="form.customerPaidAt"
              type="datetime"
              class="w-100%" /></el-form-item></el-col
      ></el-row>
      <el-row :gutter="16"
        ><el-col :xs="24" :sm="12"
          ><el-form-item label="服务周期" prop="servicePeriod"
            ><el-select v-model="form.servicePeriod" class="w-100%"
              ><el-option
                v-for="i in option('zsjos_order_service_period')"
                :key="i.value"
                :label="i.label"
                :value="i.value" /></el-select></el-form-item></el-col
        ><el-col :xs="24" :sm="12"
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
      <el-form-item label="缴费凭证" required>
        <div class="voucher-area">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleVoucherSelect"
            accept="image/jpeg,image/png,image/webp,application/pdf"
            :disabled="vouchers.length >= 6"
          >
            <el-button :disabled="vouchers.length >= 6">选择文件</el-button>
          </el-upload>
          <span class="voucher-hint">1–6 份 JPG、PNG、WebP 或 PDF</span>
          <el-empty v-if="vouchers.length === 0" description="尚未上传缴费凭证" :image-size="56" />
          <div v-for="item in vouchers" :key="item.uid" class="voucher-row">
            <el-image
              v-if="item.status === 'success' && item.url && isImage(item)"
              :src="item.url"
              :preview-src-list="[item.url]"
              fit="cover"
              class="voucher-preview"
            />
            <el-link v-else-if="item.status === 'success' && item.url" :href="item.url" target="_blank">
              预览 PDF
            </el-link>
            <span class="voucher-name">{{ item.name }}</span>
            <el-tag v-if="item.status === 'uploading'" type="info">上传中</el-tag>
            <el-tag v-else-if="item.status === 'failed'" type="danger">{{ item.error }}</el-tag>
            <el-tag v-else type="success">已上传</el-tag>
            <el-button v-if="item.status === 'failed' && item.raw" link type="primary" @click="uploadVoucher(item)">
              重试
            </el-button>
            <el-button link type="danger" @click="removeVoucher(item.uid)">删除</el-button>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="saving" :disabled="!!optionError || hasUploading" @click="submit"
        >提交复购</el-button
      ></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
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
type VoucherItem = {
  uid: string
  name: string
  type: string
  status: 'uploading' | 'success' | 'failed'
  raw?: File
  infraFileId?: number
  url?: string
  error?: string
}
const vouchers = ref<VoucherItem[]>([])
const hasUploading = computed(() => vouchers.value.some((item) => item.status === 'uploading'))
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
  vouchers.value = []
  visible.value = true
  void loadOptions()
}
const isImage = (item: VoucherItem) => item.type.startsWith('image/')
const removeVoucher = (uid: string) => {
  vouchers.value = vouchers.value.filter((item) => item.uid !== uid)
}
const uploadVoucher = async (item: VoucherItem) => {
  if (!item.raw) return
  item.status = 'uploading'
  item.error = undefined
  try {
    const result: any = await MenuApi.uploadSalesOrderVoucher(item.raw)
    item.infraFileId = result.infraFileId
    item.url = result.url
    item.type = result.contentType || item.type
    item.status = 'success'
  } catch (e: any) {
    item.status = 'failed'
    item.error = e?.msg || e?.message || '上传失败'
  }
}
const handleVoucherSelect = (file: UploadFile) => {
  if (!file.raw) return
  if (vouchers.value.length >= 6) {
    message.warning('缴费凭证最多上传 6 份')
    return
  }
  const allowed = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
  const item: VoucherItem = {
    uid: file.uid.toString(),
    name: file.name,
    type: file.raw.type,
    raw: file.raw,
    status: 'failed'
  }
  if (file.raw.size === 0) {
    item.error = '文件不能为空'
  } else if (!allowed.includes(file.raw.type)) {
    item.error = '文件类型不支持'
  } else {
    item.status = 'uploading'
  }
  vouchers.value.push(item)
  if (item.status === 'uploading') void uploadVoucher(item)
}
const submit = async () => {
  await formRef.value?.validate()
  if (hasUploading.value) {
    message.warning('请等待缴费凭证上传完成')
    return
  }
  if (vouchers.value.some((item) => item.status === 'failed')) {
    message.error('有缴费凭证上传失败，请重试或删除')
    return
  }
  if (vouchers.value.length < 1 || vouchers.value.length > 6) {
    message.error('请上传 1–6 份缴费凭证')
    return
  }
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
        paymentVouchers: vouchers.value.map((item) => ({ infraFileId: item.infraFileId })),
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
<style scoped>
.voucher-area { display: flex; width: 100%; flex-direction: column; gap: 8px; }
.voucher-hint { color: var(--el-text-color-secondary); font-size: 12px; }
.voucher-row { display: flex; min-height: 40px; align-items: center; gap: 10px; }
.voucher-preview { width: 40px; height: 40px; border-radius: 4px; }
.voucher-name { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>

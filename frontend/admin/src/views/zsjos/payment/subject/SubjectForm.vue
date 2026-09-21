<template>
  <Dialog :title="dialogTitle" v-model="dialogVisible" width="800px">
    <el-alert v-if="detailError" :title="detailError" type="error" :closable="false">
      <el-button @click="loadDetail">重试</el-button>
    </el-alert>
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="120px"
      v-loading="formLoading"
    >
      <el-form-item label="主体名称" prop="subjectName">
        <el-input v-model="formData.subjectName" placeholder="请输入主体名称" />
      </el-form-item>
      <el-form-item label="主体编码" prop="subjectCode">
        <el-input v-model="formData.subjectCode" placeholder="请输入主体编码" />
        <div class="text-12px text-[var(--el-text-color-secondary)]">
          school 用于未配置产品，company 用于多产品主体冲突；修改编码会影响新支付链接，已有链接保留原主体。
        </div>
      </el-form-item>
      <el-form-item label="商户号" prop="cusid">
        <el-input v-model="formData.cusid" placeholder="请输入商户号" />
      </el-form-item>
      <el-form-item label="商户私钥" prop="merchantPrivateKey">
        <el-input
          v-model="formData.merchantPrivateKey"
          type="textarea"
          :rows="4"
          placeholder="请输入商户私钥"
        />
      </el-form-item>
      <el-form-item label="平台公钥" prop="platformPublicKey">
        <el-input
          v-model="formData.platformPublicKey"
          type="textarea"
          :rows="4"
          placeholder="请输入平台公钥"
        />
      </el-form-item>
      <el-form-item label="应用ID" prop="appid">
        <el-input v-model="formData.appid" placeholder="请输入应用ID" />
      </el-form-item>
      <el-form-item label="机构号" prop="orgid">
        <el-input v-model="formData.orgid" placeholder="请输入机构号" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio
            v-for="item in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="item.value"
            :value="item.value"
            >{{ item.label }}</el-radio
          >
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" placeholder="请输入备注" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading || !!detailError"
        >确 定</el-button
      >
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { getIntDictOptions, DICT_TYPE } from '@/utils/dict'
import * as PaymentSubjectApi from '@/api/zsjos/payment/subject'

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const detailError = ref('')
const detailId = ref<number>()
const formType = ref('')
const formData = ref<PaymentSubjectApi.PaymentSubjectSaveVO>({
  id: undefined,
  subjectName: '',
  subjectCode: '',
  cusid: '',
  merchantPrivateKey: '',
  platformPublicKey: '',
  appid: '',
  orgid: '',
  status: 0,
  remark: ''
})
const formRules = reactive({
  subjectName: [{ required: true, message: '主体名称不能为空', trigger: 'blur' }],
  subjectCode: [{ required: true, message: '主体编码不能为空', trigger: 'blur' }],
  cusid: [{ required: true, message: '商户号不能为空', trigger: 'blur' }],
  merchantPrivateKey: [{ required: true, message: '商户私钥不能为空', trigger: 'blur' }],
  platformPublicKey: [{ required: true, message: '平台公钥不能为空', trigger: 'blur' }],
  appid: [{ required: true, message: '应用ID不能为空', trigger: 'blur' }]
})
const formRef = ref()

/** 打开弹窗 */
const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '添加支付主体' : '修改支付主体'
  formType.value = type
  resetForm()
  detailId.value = id
  detailError.value = ''
  if (id) await loadDetail()
}
defineExpose({ open })

const loadDetail = async () => {
  if (!detailId.value) return
  formLoading.value = true
  detailError.value = ''
  try {
    const data = await PaymentSubjectApi.getPaymentSubject(detailId.value)
    if (!data) {
      detailError.value = '支付主体不存在，请关闭后刷新列表'
      return
    }
    formData.value = data
  } catch {
    detailError.value = '支付主体详情加载失败，请重试'
  } finally {
    formLoading.value = false
  }
}

/** 提交表单 */
const emit = defineEmits(['success'])
const submitForm = async () => {
  if (formLoading.value || detailError.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    const data = formData.value
    if (formType.value === 'create') {
      await PaymentSubjectApi.createPaymentSubject(data)
      message.success(t('common.createSuccess'))
    } else {
      await PaymentSubjectApi.updatePaymentSubject(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    subjectName: '',
    subjectCode: '',
    cusid: '',
    merchantPrivateKey: '',
    platformPublicKey: '',
    appid: '',
    orgid: '',
    status: 0,
    remark: ''
  }
  formRef.value?.resetFields()
}
</script>

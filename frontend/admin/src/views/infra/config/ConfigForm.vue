<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="80px"
    >
      <el-form-item label="参数分类" prop="category">
        <el-input v-model="formData.category" placeholder="请输入参数分类" />
      </el-form-item>
      <el-form-item label="参数名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入参数名称" />
      </el-form-item>
      <el-form-item label="参数键名" prop="key">
        <el-input v-model="formData.key" placeholder="请输入参数键名" :disabled="originalSensitive" />
      </el-form-item>
      <el-form-item label="参数键值" prop="value">
        <el-input v-model="formData.value" :type="formData.sensitive ? 'password' : 'text'"
          :disabled="formData.sensitive && !canEditSecrets"
          :placeholder="formData.sensitive ? (formData.configured ? '已配置，留空保留原密钥' : '请输入密钥') : '请输入参数键值'"
          autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="敏感参数" prop="sensitive">
        <el-switch v-model="formData.sensitive" :disabled="originalSensitive || !canEditSecrets" />
        <span class="ml-2 text-sm">加密保存，不回显、不导出</span>
      </el-form-item>
      <el-form-item v-if="!formData.sensitive" label="是否可见" prop="visible">
        <el-radio-group v-model="formData.visible">
          <el-radio
            v-for="dict in getBoolDictOptions(DICT_TYPE.INFRA_BOOLEAN_STRING)"
            :key="String(dict.value)"
            :value="dict.value"
          >
            {{ dict.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" placeholder="请输入内容" type="textarea" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading || (formData.sensitive && !canEditSecrets)" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getBoolDictOptions } from '@/utils/dict'
import * as ConfigApi from '@/api/infra/config'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'InfraConfigForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formType = ref('') // 表单的类型：create - 新增；update - 修改
const originalSensitive = ref(false)
const canEditSecrets = computed(() => checkPermi(['infra:config:secret-update']))
const formData = ref({
  id: undefined,
  category: '',
  name: '',
  key: '',
  value: '',
  sensitive: false,
  configured: false,
  visible: true,
  remark: ''
})
const formRules = reactive({
  category: [{ required: true, message: '参数分类不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '参数名称不能为空', trigger: 'blur' }],
  key: [{ required: true, message: '参数键名不能为空', trigger: 'blur' }],
  value: [{ validator: (_rule, value, callback) => !formData.value.sensitive && !value?.trim() ? callback(new Error('参数键值不能为空')) : callback(), trigger: 'blur' }],
  visible: [{ required: true, message: '是否可见不能为空', trigger: 'blur' }]
})
const formRef = ref() // 表单 Ref

/** 打开弹窗 */
const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()
  // 修改时，设置数据
  if (id) {
    formLoading.value = true
    try {
      formData.value = await ConfigApi.getConfig(id)
      originalSensitive.value = Boolean(formData.value.sensitive)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  if (!formRef) return
  const valid = await formRef.value.validate()
  if (!valid) return
  // 提交请求
  formLoading.value = true
  try {
    const data = formData.value as ConfigApi.ConfigVO
    if (formType.value === 'create') {
      await ConfigApi.createConfig(data)
      message.success(t('common.createSuccess'))
    } else {
      await ConfigApi.updateConfig(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  originalSensitive.value = false
  formData.value = {
    id: undefined,
    category: '',
    name: '',
    key: '',
    value: '',
    sensitive: false,
    configured: false,
    visible: true,
    remark: ''
  }
  formRef.value?.resetFields()
}
</script>

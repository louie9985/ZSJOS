<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item label="场景名称" prop="name">
        <el-input v-model="formData.name" placeholder="例如：客资指定派单" />
      </el-form-item>
      <el-form-item label="场景编码" prop="code">
        <el-input
          v-model="formData.code"
          :disabled="formType === 'update'"
          placeholder="例如：lead_specified_assignment"
        />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="来源称谓" prop="sourceLabel">
            <el-input v-model="formData.sourceLabel" placeholder="例如：新媒体员工" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="目标称谓" prop="targetLabel">
            <el-input v-model="formData.targetLabel" placeholder="例如：销售专员" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="来源岗位" prop="sourcePostCode">
            <el-select v-model="formData.sourcePostCode" filterable placeholder="请选择来源岗位">
              <el-option
                v-for="post in postOptions"
                :key="post.code"
                :label="post.name"
                :value="post.code"
              >
                <span>{{ post.name }}</span>
                <span class="post-code">{{ post.code }}</span>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="目标岗位" prop="targetPostCode">
            <el-select v-model="formData.targetPostCode" filterable placeholder="请选择目标岗位">
              <el-option
                v-for="post in postOptions"
                :key="post.code"
                :label="post.name"
                :value="post.code"
              >
                <span>{{ post.name }}</span>
                <span class="post-code">{{ post.code }}</span>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio :value="0">启用</el-radio>
          <el-radio :value="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :disabled="formLoading" @click="submitForm">确定</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as PostApi from '@/api/system/post'
import * as UserRelationApi from '@/api/zsjos/userRelation'

defineOptions({ name: 'ZsjosUserRelationSceneForm' })

const message = useMessage()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref<'create' | 'update'>('create')
const formRef = ref()
const postOptions = ref<PostApi.PostSimpleVO[]>([])
const formData = ref<UserRelationApi.UserRelationSceneVO>({
  name: '',
  code: '',
  sourceLabel: '',
  targetLabel: '',
  sourcePostCode: '',
  targetPostCode: '',
  status: 0,
  remark: ''
})
const formRules = reactive({
  name: [{ required: true, message: '场景名称不能为空', trigger: 'blur' }],
  code: [
    { required: true, message: '场景编码不能为空', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]{2,63}$/,
      message: '只能使用小写字母、数字和下划线，并以字母开头',
      trigger: 'blur'
    }
  ],
  sourceLabel: [{ required: true, message: '来源称谓不能为空', trigger: 'blur' }],
  targetLabel: [{ required: true, message: '目标称谓不能为空', trigger: 'blur' }],
  sourcePostCode: [{ required: true, message: '请选择来源岗位', trigger: 'change' }],
  targetPostCode: [{ required: true, message: '请选择目标岗位', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
})

const emit = defineEmits(['success'])

const open = async (type: 'create' | 'update', id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '新增用户关系场景' : '修改用户关系场景'
  formType.value = type
  resetForm()
  postOptions.value = await PostApi.getSimplePostList()
  if (id) {
    formLoading.value = true
    try {
      formData.value = await UserRelationApi.getScene(id)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await UserRelationApi.createScene(formData.value)
      message.success('新增成功')
    } else {
      await UserRelationApi.updateScene(formData.value)
      message.success('修改成功')
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = {
    name: '',
    code: '',
    sourceLabel: '',
    targetLabel: '',
    sourcePostCode: '',
    targetPostCode: '',
    status: 0,
    remark: ''
  }
  formRef.value?.resetFields()
}
</script>

<style scoped>
.post-code {
  float: right;
  margin-left: 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

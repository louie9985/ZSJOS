<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-form-item label="父分类" prop="parentId">
        <el-tree-select
          v-model="formData.parentId"
          :data="parentOptions"
          :props="treeSelectProps"
          check-strictly
          node-key="id"
          class="!w-full"
          placeholder="不选则为顶级分类"
        />
      </el-form-item>
      <el-form-item label="分类名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入分类名称" />
      </el-form-item>
      <el-form-item label="分类编码" prop="code">
        <el-input v-model="formData.code" placeholder="用于拼接资产编号，如 IT" />
      </el-form-item>
      <el-form-item label="管理模式" prop="managementMode">
        <el-segmented
          v-model="formData.managementMode"
          :options="[
            { label: '单件管理', value: 1 },
            { label: '批量管理', value: 2 }
          ]"
          class="!w-full"
        />
      </el-form-item>
      <el-form-item label="计量单位" prop="unit">
        <el-input v-model="formData.unit" placeholder="如 个、本、套、箱" />
      </el-form-item>
      <el-form-item label="交付模式" prop="deliveryMode">
        <el-select
          v-model="formData.deliveryMode"
          clearable
          class="!w-full"
          :placeholder="policyPlaceholder"
        >
          <el-option label="实物入库" :value="1" />
          <el-option label="数字交付" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="持有模式" prop="custodyMode">
        <el-select
          v-model="formData.custodyMode"
          clearable
          class="!w-full"
          :placeholder="policyPlaceholder"
        >
          <el-option label="消耗型" :value="1" />
          <el-option label="需归还型" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="formData.sort" :min="0" class="!w-full" :controls="false" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :value="dict.value"
          >
            {{ dict.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { handleTree } from '@/utils/tree'
import * as CategoryApi from '@/api/eam/category'

defineOptions({ name: 'EamCategoryForm' })

const props = defineProps<{ categoryList: CategoryApi.CategoryVO[] }>()
const emit = defineEmits(['success'])

const { t } = useI18n()
const message = useMessage()
const treeSelectProps: any = { label: 'name', children: 'children', value: 'id' }

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formData = ref<CategoryApi.CategoryVO>({
  parentId: 0,
  name: '',
  code: '',
  sort: 0,
  status: 0,
  managementMode: 1,
  deliveryMode: 1,
  custodyMode: 2,
  unit: '个',
  remark: ''
})
const formRules = reactive({
  parentId: [{ required: true, message: '父分类不能为空', trigger: 'change' }],
  name: [{ required: true, message: '分类名称不能为空', trigger: 'blur' }],
  code: [{ required: true, message: '分类编码不能为空', trigger: 'blur' }],
  managementMode: [{ required: true, message: '管理模式不能为空', trigger: 'change' }],
  unit: [{ required: true, message: '计量单位不能为空', trigger: 'blur' }],
  sort: [{ required: true, message: '排序不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
})
const policyPlaceholder = computed(() =>
  formData.value.parentId === 0 ? '顶级分类必须选择' : '留空继承父分类'
)
const formRef = ref()

/** 编辑时把自身从父分类候选中剔除，避免选出环形结构 */
const parentOptions = computed(() => {
  const list = props.categoryList.filter((item) => item.id !== formData.value.id)
  return [{ id: 0, name: '顶级分类', children: handleTree(list as any, 'id', 'parentId') }]
})

const open = async (type: string, id?: number, parentId?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()
  if (parentId) {
    formData.value.parentId = parentId
  }
  if (id) {
    formLoading.value = true
    try {
      formData.value = await CategoryApi.getCategory(id)
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
    const data = formData.value as CategoryApi.CategoryVO
    if (formType.value === 'create') {
      await CategoryApi.createCategory(data)
      message.success(t('common.createSuccess'))
    } else {
      await CategoryApi.updateCategory(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = {
    parentId: 0,
    name: '',
    code: '',
    sort: 0,
    status: 0,
    managementMode: 1,
    deliveryMode: 1,
    custodyMode: 2,
    unit: '个',
    remark: ''
  }
  formRef.value?.resetFields()
}
</script>

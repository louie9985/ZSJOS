<template>
  <Dialog v-model="dialogVisible" title="发起盘点" width="600px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-form-item label="盘点名称" prop="name">
        <el-input v-model="formData.name" placeholder="如 2026年Q3资产盘点" />
      </el-form-item>

      <el-form-item label="盘点范围" prop="scopeType">
        <el-radio-group v-model="formData.scopeType" @change="formData.scopeValue = undefined">
          <el-radio :value="ScopeType.ALL">全部资产</el-radio>
          <el-radio :value="ScopeType.DEPT">按部门</el-radio>
          <el-radio :value="ScopeType.CATEGORY">按分类</el-radio>
          <el-radio :value="ScopeType.LOCATION">按存放地点</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="formData.scopeType === ScopeType.DEPT" label="部门" prop="scopeValue">
        <el-tree-select
          v-model="scopeDeptId"
          :data="deptTree"
          :props="{ label: 'name', children: 'children', value: 'id' }"
          check-strictly
          node-key="id"
          class="!w-full"
          placeholder="请选择部门"
        />
      </el-form-item>

      <el-form-item
        v-if="formData.scopeType === ScopeType.CATEGORY"
        label="分类"
        prop="scopeValue"
      >
        <el-tree-select
          v-model="scopeCategoryId"
          :data="categoryTree"
          :props="{ label: 'name', children: 'children', value: 'id' }"
          check-strictly
          node-key="id"
          class="!w-full"
          placeholder="请选择分类"
        />
      </el-form-item>

      <el-form-item
        v-if="formData.scopeType === ScopeType.LOCATION"
        label="存放地点"
        prop="scopeValue"
      >
        <el-input v-model="formData.scopeValue" placeholder="多个地点用逗号分隔，支持部分匹配" />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
      </el-form-item>

      <el-alert
        title="创建后系统会按范围快照生成盘点明细，之后台账变动不影响本次盘点的比对基准"
        type="info"
        :closable="false"
        show-icon
      />
    </el-form>

    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { handleTree } from '@/utils/tree'
import * as InventoryApi from '@/api/eam/inventory'
import { ScopeType } from '@/api/eam/inventory'
import * as CategoryApi from '@/api/eam/category'
import * as DeptApi from '@/api/system/dept'

defineOptions({ name: 'EamInventoryForm' })

const emit = defineEmits(['success'])

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const formLoading = ref(false)
const formData = ref<InventoryApi.InventoryVO>(buildEmptyForm())
const formRef = ref()

const deptTree = ref<any[]>([])
const categoryTree = ref<any[]>([])

// 部门/分类选择器绑定单个 ID，提交时统一序列化到 scopeValue 字符串
const scopeDeptId = ref<number>()
const scopeCategoryId = ref<number>()
watch(scopeDeptId, (val) => {
  if (formData.value.scopeType === ScopeType.DEPT) {
    formData.value.scopeValue = val != null ? String(val) : undefined
  }
})
watch(scopeCategoryId, (val) => {
  if (formData.value.scopeType === ScopeType.CATEGORY) {
    formData.value.scopeValue = val != null ? String(val) : undefined
  }
})

const formRules = computed(() => ({
  name: [{ required: true, message: '盘点名称不能为空', trigger: 'blur' }],
  scopeType: [{ required: true, message: '盘点范围不能为空', trigger: 'change' }],
  scopeValue:
    formData.value.scopeType === ScopeType.ALL
      ? []
      : [{ required: true, message: '请指定盘点范围', trigger: 'change' }]
}))

function buildEmptyForm(): InventoryApi.InventoryVO {
  return {
    name: '',
    scopeType: ScopeType.ALL,
    scopeValue: undefined,
    remark: ''
  }
}

const open = async () => {
  dialogVisible.value = true
  formData.value = buildEmptyForm()
  scopeDeptId.value = undefined
  scopeCategoryId.value = undefined
  formRef.value?.resetFields()
}
defineExpose({ open })

const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    await InventoryApi.createInventory(formData.value)
    message.success(t('common.createSuccess'))
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

onMounted(async () => {
  deptTree.value = handleTree(await DeptApi.getSimpleDeptList())
  categoryTree.value = handleTree((await CategoryApi.getCategoryList()) as any, 'id', 'parentId')
})
</script>

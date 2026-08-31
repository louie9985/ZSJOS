<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="资产名称" prop="name">
            <el-input v-model="formData.name" placeholder="请输入资产名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="分类" prop="categoryId">
            <el-tree-select
              v-model="formData.categoryId"
              :data="categoryTree"
              :props="treeSelectProps"
              check-strictly
              node-key="id"
              class="!w-full"
              placeholder="请选择分类"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="管理模式">
            <el-input
              :model-value="selectedCategory?.managementMode === 2 ? '批量管理' : '单件管理'"
              disabled
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="`数量（${selectedCategory?.unit || '个'}）`" prop="quantity">
            <el-input-number
              v-model="formData.quantity"
              :min="1"
              :step="1"
              :precision="0"
              :disabled="selectedCategory?.managementMode !== 2"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="购入日期" prop="purchaseDate">
            <el-date-picker
              v-model="formData.purchaseDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="!w-full"
              placeholder="请选择购入日期"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="使用部门" prop="useDeptId">
            <el-tree-select
              v-model="formData.useDeptId"
              :data="deptTree"
              :props="treeSelectProps"
              check-strictly
              node-key="id"
              clearable
              class="!w-full"
              placeholder="请选择使用部门"
              @clear="handleDeptClear"
              @update:model-value="handleDeptChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="使用员工" prop="useEmployeeId">
            <HrmEmployeeSelect v-model="formData.useEmployeeId" placeholder="请选择使用员工" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="资产来源">
            <el-select v-model="formData.source" clearable class="!w-full">
              <el-option
                v-for="item in getIntDictOptions('eam_asset_source')"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="上传附件">
            <el-upload
              action="/admin-api/infra/file/upload"
              :file-list="
                (formData.fileUrls || []).map((url) => ({ name: url.split('/').pop() || url, url }))
              "
              :on-success="(res) => (formData.fileUrls = [...(formData.fileUrls || []), res.data])"
              :on-remove="
                (_file, files) => (formData.fileUrls = files.map((item) => item.url || ''))
              "
            >
              <el-button>选择文件</el-button>
            </el-upload>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="存放地点" prop="location">
            <el-input v-model="formData.location" placeholder="如 总部三楼研发区" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="formData.remark"
              type="textarea"
              :rows="2"
              placeholder="请输入备注"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 分类驱动的动态字段 -->
      <el-divider content-position="left">
        <span class="text-sm text-gray-500">分类自定义字段</span>
      </el-divider>
      <DynamicFields
        :model-value="formData.extFields || {}"
        :category-id="formData.categoryId"
        @update:model-value="formData.extFields = $event"
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
import { getIntDictOptions } from '@/utils/dict'
import * as AssetApi from '@/api/eam/asset'
import * as DeptApi from '@/api/system/dept'
import HrmEmployeeSelect from '@/views/hrm/employee/components/HrmEmployeeSelect.vue'
import DynamicFields from './DynamicFields.vue'

defineOptions({ name: 'EamAssetForm' })

const props = defineProps<{ categoryTree: any[] }>()
const emit = defineEmits(['success'])

const { t } = useI18n()
const message = useMessage()
const treeSelectProps: any = { label: 'name', children: 'children', value: 'id' }

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formData = ref<AssetApi.AssetVO>(buildEmptyForm())
const formRef = ref()

const deptTree = ref<any[]>([])

const selectedCategory = computed(() => {
  const find = (nodes: any[]): any => {
    for (const node of nodes) {
      if (node.id === formData.value.categoryId) return node
      const child = find(node.children || [])
      if (child) return child
    }
  }
  return find(props.categoryTree)
})

const formRules = reactive({
  name: [{ required: true, message: '资产名称不能为空', trigger: 'blur' }],
  categoryId: [{ required: true, message: '分类不能为空', trigger: 'change' }]
})

// 员工归属必须落在其所属部门；清空部门时同步清空员工，避免后端按员工部门自动补回。
const handleDeptClear = () => {
  formData.value.useDeptId = undefined
  formData.value.useEmployeeId = undefined
}

const handleDeptChange = (value: number | undefined | null) => {
  formData.value.useDeptId = value ?? undefined
  if (value == null) formData.value.useEmployeeId = undefined
}

function buildEmptyForm(): AssetApi.AssetVO {
  return {
    name: '',
    categoryId: undefined as any,
    quantity: 1,
    brand: '',
    specification: '',
    sn: '',
    barcode: '',
    originalValue: undefined,
    netValue: undefined,
    source: undefined,
    warrantyDate: undefined,
    expectedLife: undefined,
    purchaseDate: undefined,
    useDeptId: undefined,
    useEmployeeId: undefined,
    location: '',
    remark: '',
    fileUrls: [],
    extFields: {}
  }
}

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = buildEmptyForm()
  formRef.value?.resetFields()

  if (id) {
    formLoading.value = true
    try {
      const data = await AssetApi.getAsset(id)
      formData.value = { ...data, extFields: data.extFields || {} }
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

const submitForm = async () => {
  await formRef.value.validate()
  // 清空部门后不允许残留员工 ID；后端会按员工所属部门自动补回部门。
  if (formData.value.useDeptId == null) formData.value.useEmployeeId = undefined
  if (selectedCategory.value?.managementMode !== 2) {
    formData.value.quantity = 1
  }
  formLoading.value = true
  try {
    // 显式组装保存请求，清空选择器时用 null 覆盖打开表单时的旧归属值。
    const data = {
      ...formData.value,
      useDeptId: formData.value.useDeptId ?? null,
      useEmployeeId: formData.value.useEmployeeId ?? null
    } as AssetApi.AssetVO
    if (formType.value === 'create') {
      await AssetApi.createAsset(data)
      message.success(t('common.createSuccess'))
    } else {
      await AssetApi.updateAsset(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

watch(
  () => selectedCategory.value?.managementMode,
  (mode) => {
    if (mode !== 2) formData.value.quantity = 1
  }
)

onMounted(async () => {
  deptTree.value = handleTree(await DeptApi.getSimpleDeptList())
})
</script>

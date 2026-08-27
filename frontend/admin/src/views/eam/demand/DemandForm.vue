<template>
  <Dialog v-model="visible" :title="dialogTitle" width="900px">
    <el-form ref="formRef" v-loading="loading" :model="formData" label-width="90px">
      <el-form-item
        v-if="!fixedEmployeeId"
        label="需求员工"
        prop="employeeId"
        :rules="[{ required: true, message: '请选择需求员工', trigger: 'change' }]"
      >
        <el-select
          v-model="formData.employeeId"
          filterable
          remote
          reserve-keyword
          :remote-method="searchEmployees"
          :loading="employeeLoading"
          class="!w-full"
          placeholder="按姓名或工号搜索员工"
        >
          <el-option
            v-for="employee in employees"
            :key="employee.id"
            :label="employeeLabel(employee)"
            :value="employee.id"
            :disabled="!employee.userId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="申请事由" prop="reason">
        <el-input
          v-model="formData.reason"
          type="textarea"
          :rows="2"
          placeholder="请填写使用场景或补充说明"
        />
      </el-form-item>

      <div
        v-for="(item, index) in formData.items"
        :key="index"
        class="mb-16px border border-solid border-[var(--el-border-color)] p-16px"
      >
        <div class="mb-12px flex items-center justify-between">
          <span class="font-medium">需求明细 {{ index + 1 }}</span>
          <el-button v-if="formData.items.length > 1" link type="danger" @click="removeItem(index)">
            删除
          </el-button>
        </div>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item
              label="物品名称"
              :prop="`items.${index}.name`"
              :rules="[{ required: true, message: '请输入物品名称', trigger: 'blur' }]"
            >
              <el-input v-model="item.name" placeholder="如 笔记本电脑" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item
              label="资产分类"
              :prop="`items.${index}.categoryId`"
              :rules="[{ required: true, message: '请选择已确认策略的分类', trigger: 'change' }]"
            >
              <el-tree-select
                v-model="item.categoryId"
                :data="categoryTree"
                :props="treeProps"
                node-key="id"
                check-strictly
                filterable
                class="!w-full"
                placeholder="请选择分类"
                @change="changeCategory(item)"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item
              label="数量"
              :prop="`items.${index}.quantity`"
              :rules="[{ required: true, message: '请输入数量', trigger: 'change' }]"
            >
              <el-input-number v-model="item.quantity" :min="1" :precision="0" class="!w-full" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="单位">
              <el-input v-model="item.unit" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <DynamicFields
          :category-id="item.categoryId"
          :model-value="item.extFields || {}"
          context="collection"
          @update:model-value="item.extFields = $event"
        />
      </div>
      <el-button plain class="!w-full" @click="addItem">
        <Icon icon="ep:plus" class="mr-5px" />增加明细
      </el-button>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交需求审批</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import * as ProcurementApi from '@/api/eam/procurement'
import * as CategoryApi from '@/api/eam/category'
import * as EmployeeApi from '@/api/hrm/employee'
import * as EmployeeAssetApi from '@/api/eam/employeeAsset'
import { handleTree } from '@/utils/tree'
import DynamicFields from '@/views/eam/asset/DynamicFields.vue'

defineOptions({ name: 'EamDemandForm' })
const emit = defineEmits(['success'])
const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const taskId = ref<number>()
const fixedEmployeeId = ref<number>()
const employeeLoading = ref(false)
const formRef = ref()
const categories = ref<CategoryApi.CategoryVO[]>([])
type EmployeeOption = EmployeeApi.HrmEmployeeVO & { id: number }
const employees = ref<EmployeeOption[]>([])
const treeProps = { label: 'name', children: 'children', value: 'id' }
const dialogTitle = computed(() => (taskId.value ? '填写入职配资需求' : '新建办公资产需求'))

const emptyItem = (): ProcurementApi.DemandItemVO => ({
  name: '',
  categoryId: undefined as unknown as number,
  quantity: 1,
  extFields: {}
})
const formData = reactive<ProcurementApi.DemandVO>({
  employeeId: undefined,
  reason: '',
  items: [emptyItem()]
})
const categoryTree = computed(() =>
  handleTree(
    categories.value.filter(
      (item) => item.status === 0 && item.effectiveDeliveryMode && item.effectiveCustodyMode
    ) as any,
    'id',
    'parentId'
  )
)

const employeeLabel = (employee: EmployeeApi.HrmEmployeeVO) =>
  `${employee.name}${employee.jobNumber ? `（${employee.jobNumber}）` : ''}${employee.userId ? '' : '（未绑定账号）'}`

const searchEmployees = async (keyword = '') => {
  employeeLoading.value = true
  try {
    const params = { pageNo: 1, pageSize: 50, name: keyword || undefined } as any
    const page = await EmployeeApi.getEmployeeSimplePage(params)
    employees.value = page.list.filter((employee) => employee.id != null) as EmployeeOption[]
  } finally {
    employeeLoading.value = false
  }
}

const changeCategory = (item: ProcurementApi.DemandItemVO) => {
  const category = categories.value.find((row) => row.id === item.categoryId)
  item.unit = category?.unit
  item.extFields = {}
}
const addItem = () => formData.items.push(emptyItem())
const removeItem = (index: number) => formData.items.splice(index, 1)

const open = async (options?: { employeeId?: number; taskId?: number }) => {
  visible.value = true
  taskId.value = options?.taskId
  fixedEmployeeId.value = options?.employeeId
  formData.employeeId = options?.employeeId
  formData.reason = ''
  formData.items = [emptyItem()]
  formRef.value?.clearValidate()
  loading.value = true
  try {
    const [categoryRows] = await Promise.all([
      CategoryApi.getCategoryList(),
      fixedEmployeeId.value ? Promise.resolve() : searchEmployees()
    ])
    categories.value = categoryRows as CategoryApi.CategoryVO[]
  } finally {
    loading.value = false
  }
}
defineExpose({ open })

const submit = async () => {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (taskId.value) {
      await EmployeeAssetApi.submitProvisioning(taskId.value, { demand: formData })
      message.success('入职配资需求已提交统一审批中心')
    } else {
      await ProcurementApi.createDemand(formData)
      message.success('需求已提交统一审批中心')
    }
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>
